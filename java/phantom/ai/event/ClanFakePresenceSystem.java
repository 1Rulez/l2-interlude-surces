package phantom.ai.event;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.l2jmega.Config;
import com.l2jmega.L2DatabaseFactory;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.data.MapRegionTable;
import com.l2jmega.gameserver.instancemanager.CastleManager;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.zone.type.L2TownZone;
import com.l2jmega.gameserver.model.entity.Castle;
import com.l2jmega.gameserver.model.entity.Siege;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.gameserver.model.pledge.Clan;
import com.l2jmega.gameserver.model.pledge.ClanMember;
import com.l2jmega.gameserver.model.zone.ZoneId;

import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.FakePlayerManager;
import phantom.ai.walker.GiranWalkerAI;

@SuppressWarnings({"javadoc", "static-method"})
public final class ClanFakePresenceSystem {
   private static final long STARTUP_RESTORE_DELAY_MS = 30000L;
   private static final long STARTUP_BATCH_DELAY_MS = 1500L;
   private static final int STARTUP_BATCH_SIZE = 20;
   private static final long PRESENCE_TICK_DELAY_MS = 90000L;
   private static final int MAX_CYCLE_CHANGES_PER_CLAN = 2;
   /** Town of Giran in {@link MapRegionTable#getTown(int)} (same id as core town list). */
   private static final int GIRAN_TOWN_ID = 9;
   /** Random offset around Giran town spawn so clan fakes do not stack on one tile. */
   private static final int GIRAN_CLAN_RESTORE_SPREAD = 650;
   private static final List<Location> POST_SIEGE_CLAN_TOWN_SPAWNS = List.of(
      new Location(82698, 148638, -3473),
      new Location(83464, 148616, -3400),
      new Location(81625, 148980, -3464),
      new Location(147928, 25832, -2008),
      new Location(145632, 26880, -2200),
      new Location(111456, 219376, -3544),
      new Location(112536, 220792, -3544),
      new Location(148200, -55450, -2734),
      new Location(147520, -55840, -2784),
      new Location(43712, -47808, -792),
      new Location(44120, -48936, -792),
      new Location(11624, 15688, -4584),
      new Location(12424, 16512, -4584)
   );

   private static volatile ClanFakePresenceSystem _instance;

   private ClanFakePresenceSystem() {
      ThreadPool.schedule(this::cleanupExcessClanFakesOnStartup, 1000L);
      ThreadPool.schedule(this::restoreClanFakesOnStartup, STARTUP_RESTORE_DELAY_MS);
      ThreadPool.scheduleAtFixedRate(this::rebalanceClanPresence, PRESENCE_TICK_DELAY_MS, PRESENCE_TICK_DELAY_MS);
   }

   public static ClanFakePresenceSystem getInstance() {
      if (!FakePlayerConfig.ALLOW_FAKE_CLAN_PLAYERS) {
         return null;
      }

      if (_instance == null) {
         synchronized (ClanFakePresenceSystem.class) {
            if (_instance == null) {
               _instance = new ClanFakePresenceSystem();
            }
         }
      }
      return _instance;
   }

   /**
    * Random point in Town of Giran with spread (not one pile). Falls back if town zone missing.
    */
   private static Location randomGiranClanRestoreSpawn() {
      final L2TownZone giran = MapRegionTable.getTown(GIRAN_TOWN_ID);
      if (giran != null && giran.getSpawnLoc() != null) {
         final Location base = giran.getSpawnLoc();
         final int s = GIRAN_CLAN_RESTORE_SPREAD;
         return new Location(
            base.getX() + Rnd.get(-s, s),
            base.getY() + Rnd.get(-s, s),
            base.getZ()
         );
      }
      return new Location(82600 + Rnd.get(-450, 450), 148600 + Rnd.get(-450, 450), -3464);
   }

   /**
    * Idle clan fakes (not farm/PvP/event) spawn at Giran with {@link GiranWalkerAI}; others use saved coordinates and default class AI.
    */
   private static boolean isIdleCityClanFake(FakePlayer fake) {
      if (fake == null) {
         return false;
      }
      return !fake.isFakePvp()
         && !fake.isFakeFarm()
         && !fake.isFakeEvent()
         && !fake.isFakeKTBEvent()
         && !fake.isTour()
         && !fake.isInOlympiadMode();
   }

   private static FakePlayer restoreClanFakeToGiranTown(int objectId) {
      final Player existing = World.getInstance().getPlayer(objectId);
      if (existing instanceof FakePlayer) {
         final FakePlayer fake = (FakePlayer) existing;
         if (!FakePlayerManager.getFakePlayers().contains(fake)) {
            FakePlayerManager.registerFakePlayer(fake);
         }
         return fake;
      }

      final Player restored = Player.restore(objectId);
      if (!(restored instanceof FakePlayer)) {
         return null;
      }
      final FakePlayer fake = (FakePlayer) restored;
      final boolean idleCity = isIdleCityClanFake(fake);
      final int x;
      final int y;
      final int z;
      if (idleCity) {
         final Location loc = randomGiranClanRestoreSpawn();
         x = loc.getX();
         y = loc.getY();
         z = loc.getZ();
      } else {
         x = fake.getPosition().getX();
         y = fake.getPosition().getY();
         z = fake.getPosition().getZ();
      }
      final FakePlayer spawned = FakePlayerManager.spawnRestoredFakeIntoWorld(fake, x, y, z);
      if (spawned != null && idleCity) {
         spawned.setFakeAi(new GiranWalkerAI(spawned));
      }
      return spawned;
   }

   private void restoreClanFakesOnStartup() {
      final Map<Integer, List<Integer>> fakeIdsByClan = loadAllClanFakeIdsByClan();
      final List<Integer> startupRestoreIds = new ArrayList<>();
      for (List<Integer> clanIds : fakeIdsByClan.values()) {
         if (clanIds == null || clanIds.isEmpty()) {
            continue;
         }

         final List<Integer> shuffled = new ArrayList<>(clanIds);
         Collections.shuffle(shuffled);
         final int desiredOnline = getDesiredOnlineCount(shuffled.size());
         final int toRestore = Math.min(desiredOnline, shuffled.size());
         for (int index = 0; index < toRestore; index++) {
            startupRestoreIds.add(shuffled.get(index));
         }
      }

      int index = 0;
      int delay = 0;
      while (index < startupRestoreIds.size()) {
         final List<Integer> batch = startupRestoreIds.subList(index, Math.min(index + STARTUP_BATCH_SIZE, startupRestoreIds.size()));
         ThreadPool.schedule(() -> {
            for (Integer objectId : batch) {
               if (objectId != null) {
                  restoreClanFakeToGiranTown(objectId);
               }
            }
         }, delay);

         index += STARTUP_BATCH_SIZE;
         delay += STARTUP_BATCH_DELAY_MS;
      }
   }

   private void cleanupExcessClanFakesOnStartup() {
      if (!FakePlayerConfig.FAKE_CLAN_CLEANUP_ENABLED) {
         return;
      }

      final int clanCap = Math.max(1, Config.ALT_MAX_NUM_OF_MEMBERS_IN_CLAN);
      final int fillPercent = Math.max(1, Math.min(100, FakePlayerConfig.FAKE_CLAN_CLEANUP_FILL_PERCENT));
      final int maxPerClan = Math.max(1, (int) Math.floor(clanCap * (fillPercent / 100.0)));
      final Map<Integer, List<Integer>> fakeIdsByClan = loadAllClanFakeIdsByClan();
      for (Map.Entry<Integer, List<Integer>> entry : fakeIdsByClan.entrySet()) {
         final Clan clan = com.l2jmega.gameserver.data.sql.ClanTable.getInstance().getClan(entry.getKey());
         if (clan == null) {
            continue;
         }

         final List<Integer> fakeIds = new ArrayList<>(entry.getValue());
         if (fakeIds.size() <= maxPerClan) {
            continue;
         }

         Collections.sort(fakeIds);
         final List<Integer> toRemove = fakeIds.subList(maxPerClan, fakeIds.size());
         for (Integer objectId : new ArrayList<>(toRemove)) {
            if (objectId == null || !clan.isMember(objectId)) {
               continue;
            }

            final ClanMember member = clan.getClanMember(objectId);
            final Player onlinePlayer = member != null ? member.getPlayerInstance() : null;
            if (onlinePlayer instanceof FakePlayer) {
               ((FakePlayer) onlinePlayer).despawnPlayer();
            }

            clan.removeClanMember(objectId, 0L);
         }
      }
   }

   private void rebalanceClanPresence() {
      final Map<Integer, List<Integer>> offlineByClan = loadOfflineClanFakeIdsByClan();
      for (Map.Entry<Integer, List<Integer>> entry : offlineByClan.entrySet()) {
         final Clan clan = com.l2jmega.gameserver.data.sql.ClanTable.getInstance().getClan(entry.getKey());
         if (clan == null) {
            continue;
         }

         if (isClanInActiveSiege(clan)) {
            continue;
         }

         final List<FakePlayer> online = getOnlineClanFakes(clan);
         final int totalCount = online.size() + entry.getValue().size();
         if (totalCount == 0) {
            continue;
         }

         final int desiredOnline = getDesiredOnlineCount(totalCount);
         if (online.size() > desiredOnline) {
            final List<FakePlayer> candidates = new ArrayList<>();
            for (FakePlayer fake : online) {
               if (canCycleOffline(fake)) {
                  candidates.add(fake);
               }
            }

            Collections.shuffle(candidates);
            int toDespawn = Math.min(MAX_CYCLE_CHANGES_PER_CLAN, online.size() - desiredOnline);
            for (FakePlayer fake : candidates) {
               if (toDespawn <= 0) {
                  break;
               }
               fake.despawnPlayer();
               toDespawn--;
            }
         } else if (online.size() < desiredOnline) {
            final List<Integer> offlineIds = new ArrayList<>(entry.getValue());
            Collections.shuffle(offlineIds);
            int toLogin = Math.min(MAX_CYCLE_CHANGES_PER_CLAN, desiredOnline - online.size());
            for (Integer objectId : offlineIds) {
               if (toLogin <= 0) {
                  break;
               }
               if (restoreClanFakeToGiranTown(objectId) != null) {
                  toLogin--;
               }
            }
         }
      }
   }

   public void onSiegeEnd(Castle castle, List<FakePlayer> deployed) {
      if (castle == null || deployed == null || deployed.isEmpty()) {
         return;
      }

      final List<FakePlayer> candidates = new ArrayList<>();
      for (FakePlayer fake : deployed) {
         if (fake != null && fake.isOnline() && canCycleOffline(fake)) {
            candidates.add(fake);
         }
      }

      if (candidates.isEmpty()) {
         return;
      }

      Collections.shuffle(candidates);
      final int toDespawn = Math.min(Math.max(1, candidates.size() / 4), 3);
      for (int index = 0; index < toDespawn; index++) {
         final FakePlayer fake = candidates.get(index);
         ThreadPool.schedule(() -> {
            if (fake != null && fake.isOnline() && canCycleOffline(fake)) {
               fake.despawnPlayer();
            }
         }, Rnd.get(30000, 150000));
      }

      for (FakePlayer fake : deployed) {
         if (fake == null || !fake.isOnline() || fake.isInSiege()) {
            continue;
         }

         final Location townSpawn = getRandomPostSiegeTownSpawn(castle, fake);
         if (townSpawn != null) {
            ThreadPool.schedule(() -> {
               if (fake.isOnline() && canRelocateAfterSiege(fake)) {
                  fake.teleToLocation(
                     townSpawn.getX() + Rnd.get(-90, 90),
                     townSpawn.getY() + Rnd.get(-90, 90),
                     townSpawn.getZ(),
                     0
                  );
               }
            }, Rnd.get(5000, 25000));
         }
      }
   }

   private boolean isClanInActiveSiege(Clan clan) {
      if (clan == null) {
         return false;
      }

      for (Castle castle : CastleManager.getInstance().getCastles()) {
         final Siege siege = castle.getSiege();
         if (siege != null && siege.isInProgress() && siege.checkSides(clan)) {
            return true;
         }
      }

      return false;
   }

   private boolean canCycleOffline(FakePlayer fake) {
      if (fake == null || !fake.isOnline()) {
         return false;
      }

      if (fake.isInCombat() || fake.isDead() || fake.isInSiege() || fake.getSiegeState() > 0 || fake.isInOlympiadMode() || fake.isFakeEvent() || fake.isTour() || fake.isFakeKTBEvent()) {
         return false;
      }

      if ((fake.isFakePvp() || fake.isFakeFarm()) && !fake.isInsideZone(ZoneId.TOWN) && !fake.isInsideZone(ZoneId.PEACE)) {
         return false;
      }

      return true;
   }

   private boolean canRelocateAfterSiege(FakePlayer fake) {
      if (fake == null || !fake.isOnline() || fake.isInSiege() || fake.getSiegeState() > 0 || fake.isInCombat() || fake.isDead()) {
         return false;
      }

      if (fake.isFakeEvent() || fake.isTour() || fake.isFakeKTBEvent() || fake.isInOlympiadMode()) {
         return false;
      }

      return true;
   }

   private int getDesiredOnlineCount(int totalCount) {
      if (totalCount <= 0) {
         return 0;
      }

      final int minPercent = Math.max(0, Math.min(100, FakePlayerConfig.FAKE_CLAN_PRESENCE_ONLINE_MIN_PERCENT));
      final int maxPercent = Math.max(minPercent, Math.min(100, FakePlayerConfig.FAKE_CLAN_PRESENCE_ONLINE_MAX_PERCENT));
      int minOnline = (int) Math.ceil(totalCount * (minPercent / 100.0));
      int maxOnline = (int) Math.ceil(totalCount * (maxPercent / 100.0));

      minOnline = Math.max(1, Math.min(totalCount, minOnline));
      maxOnline = Math.max(minOnline, Math.min(totalCount, maxOnline));

      if (maxOnline == minOnline) {
         return minOnline;
      }

      return Rnd.get(minOnline, maxOnline);
   }

   private List<FakePlayer> getOnlineClanFakes(Clan clan) {
      final List<FakePlayer> result = new ArrayList<>();
      for (Player player : clan.getOnlineMembers()) {
         if (player instanceof FakePlayer) {
            result.add((FakePlayer) player);
         }
      }
      return result;
   }

   private Map<Integer, List<Integer>> loadAllClanFakeIdsByClan() {
      final Map<Integer, List<Integer>> result = new LinkedHashMap<>();
      try (Connection con = L2DatabaseFactory.getInstance().getConnection();
           PreparedStatement ps = con.prepareStatement("SELECT obj_Id, clanid FROM characters WHERE clanid > 0 AND account_name LIKE 'fakeacc%' ORDER BY clanid, obj_Id ASC")) {
         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               final int clanId = rs.getInt("clanid");
               result.putIfAbsent(clanId, new ArrayList<>());
               result.get(clanId).add(rs.getInt("obj_Id"));
            }
         }
      } catch (Exception e) {
      }
      return result;
   }

   private Location getRandomPostSiegeTownSpawn(Castle castle, FakePlayer fake) {
      final List<Location> pool = new ArrayList<>(POST_SIEGE_CLAN_TOWN_SPAWNS);
      if (castle != null && castle.getCastleZone() != null && castle.getCastleZone().getSpawnLoc() != null) {
         pool.add(new Location(castle.getCastleZone().getSpawnLoc()));
      }
      if (castle != null && castle.getSiegeZone() != null && castle.getSiegeZone().getSpawnLoc() != null) {
         pool.add(new Location(castle.getSiegeZone().getSpawnLoc()));
      }

      if (pool.isEmpty()) {
         return null;
      }

      if (fake != null && fake.getClanId() > 0) {
         final int clanBias = Math.abs(fake.getClanId()) % pool.size();
         if (Rnd.get(100) < 45) {
            return pool.get(clanBias);
         }
      }

      return pool.get(Rnd.get(pool.size()));
   }

   private Map<Integer, List<Integer>> loadOfflineClanFakeIdsByClan() {
      final Map<Integer, List<Integer>> result = new LinkedHashMap<>();
      try (Connection con = L2DatabaseFactory.getInstance().getConnection();
           PreparedStatement ps = con.prepareStatement("SELECT obj_Id, clanid FROM characters WHERE clanid > 0 AND account_name LIKE 'fakeacc%' AND online=0 ORDER BY clanid")) {
         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               final int clanId = rs.getInt("clanid");
               result.putIfAbsent(clanId, new ArrayList<>());
               result.get(clanId).add(rs.getInt("obj_Id"));
            }
         }
      } catch (Exception e) {
      }

      for (FakePlayer fake : FakePlayerManager.getFakePlayers()) {
         if (fake == null || !fake.isOnline() || fake.getClanId() <= 0) {
            continue;
         }
         result.putIfAbsent(fake.getClanId(), new ArrayList<>());
      }

      return result;
   }
}
