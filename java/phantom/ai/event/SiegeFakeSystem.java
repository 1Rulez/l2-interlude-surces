package phantom.ai.event;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.l2jmega.L2DatabaseFactory;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.data.MapRegionTable;
import com.l2jmega.gameserver.data.MapRegionTable.TeleportType;
import com.l2jmega.gameserver.instancemanager.CastleManager;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.entity.Castle;
import com.l2jmega.gameserver.model.entity.Siege;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.gameserver.model.pledge.Clan;
import com.l2jmega.gameserver.model.zone.ZoneId;

import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.FakePlayerManager;
import phantom.ai.CombatAI;
import phantom.helpers.FakeHelpers;

/**
 * System that makes existing fake players participate in castle sieges.
 * Fake players from clans registered for siege will teleport to the siege zone
 * and fight during the siege.
 */
@SuppressWarnings({"javadoc", "static-method"})
public class SiegeFakeSystem {

   private static volatile SiegeFakeSystem _instance;

   // Tracks fake players currently participating in a siege, keyed by castle ID
   private final Map<Integer, List<FakePlayer>> _siegeFakes = new ConcurrentHashMap<>();
   private final Map<Integer, Map<Integer, Boolean>> _siegeJoinDecisions = new ConcurrentHashMap<>();
   private final Map<Integer, Long> _siegeStartedAt = new ConcurrentHashMap<>();
   private final Map<Integer, Long> _lastRedeployAttemptAt = new ConcurrentHashMap<>();
   private static final long REDEPLOY_INTERVAL_MS = 10000L;

   private SiegeFakeSystem() {
      ThreadPool.scheduleAtFixedRate(this::checkSieges, 5000, 5000);
   }

   public static SiegeFakeSystem getInstance() {
      if (_instance == null) {
         synchronized (SiegeFakeSystem.class) {
            if (_instance == null) {
               _instance = new SiegeFakeSystem();
            }
         }
      }
      return _instance;
   }

   /**
    * Periodically check for active sieges and cleanup ended ones.
    */
   private void checkSieges() {
      if (!FakePlayerConfig.FAKE_SIEGE_ENABLED) {
         return;
      }

      for (Castle castle : CastleManager.getInstance().getCastles()) {
         final Siege siege = castle.getSiege();
         if (siege == null) {
            continue;
         }

         if (siege.isInProgress()) {
            if (!_siegeStartedAt.containsKey(castle.getCastleId())) {
               _siegeStartedAt.put(castle.getCastleId(), System.currentTimeMillis());
               deployFakesToSiege(castle, siege);
            } else {
               final long now = System.currentTimeMillis();
               final long lastAttempt = _lastRedeployAttemptAt.getOrDefault(castle.getCastleId(), 0L);
               if (now - lastAttempt >= REDEPLOY_INTERVAL_MS) {
                  _lastRedeployAttemptAt.put(castle.getCastleId(), now);
                  deployFakesToSiege(castle, siege);
               }
            }
         } else {
            cleanupSiege(castle.getCastleId());
         }
      }
   }

   private void deployFakesToSiege(Castle castle, Siege siege) {
      final int castleId = castle.getCastleId();
      final List<FakePlayer> deployed = _siegeFakes.computeIfAbsent(castleId, _ -> new CopyOnWriteArrayList<>());
      _siegeJoinDecisions.computeIfAbsent(castleId, _ -> new ConcurrentHashMap<>());

      deployed.removeIf(f -> f == null || !f.isOnline());

      final List<Clan> attackerClans = siege.getAttackerClans();
      final List<Clan> defenderClans = siege.getDefenderClans();
      final int attackerLimit = Math.max(0, FakePlayerConfig.getFakeSiegeMaxAttackersForCastle(castleId));
      final int defenderLimit = Math.max(0, FakePlayerConfig.getFakeSiegeMaxDefendersForCastle(castleId));

      deployClanFakes(castleId, attackerClans, castle, siege, deployed, (byte) 1, countDeployedForSide(deployed, (byte) 1), attackerLimit);
      deployClanFakes(castleId, defenderClans, castle, siege, deployed, (byte) 2, countDeployedForSide(deployed, (byte) 2), defenderLimit);
   }

   private boolean deployClanFakes(int castleId, List<Clan> clans, Castle castle, Siege siege, List<FakePlayer> deployed, byte siegeState, int deployedOnSide, int maxPerSide) {
      if (clans == null || clans.isEmpty()) {
         return false;
      }

      for (Clan clan : clans) {
         if (hasReachedSideLimit(deployedOnSide, maxPerSide)) {
            break;
         }

         final Set<Integer> processedIds = new HashSet<>();

         for (Player member : clan.getOnlineMembers()) {
            if (!(member instanceof FakePlayer)) {
               continue;
            }

            final FakePlayer fake = (FakePlayer) member;
            processedIds.add(fake.getObjectId());
            if (!isAlreadyDeployed(fake, deployed, siegeState) && hasReachedSideLimit(deployedOnSide, maxPerSide)) {
               continue;
            }

            if (!isAlreadyDeployed(fake, deployed, siegeState) && !shouldJoinSiege(castleId, fake.getObjectId())) {
               continue;
            }

            if (prepareFakeForSiege(fake, castle, siege, deployed, siegeState)) {
               deployedOnSide++;
            }
         }

         for (Integer objectId : loadOfflineFakeIdsForClan(clan.getClanId())) {
            if (!processedIds.add(objectId)) {
               continue;
            }

            if (hasReachedSideLimit(deployedOnSide, maxPerSide)) {
               break;
            }

            final Player worldPlayer = World.getInstance().getPlayer(objectId);
            if (worldPlayer instanceof FakePlayer) {
               final FakePlayer fake = (FakePlayer) worldPlayer;
               if (!isAlreadyDeployed(fake, deployed, siegeState) && !shouldJoinSiege(castleId, fake.getObjectId())) {
                  continue;
               }

               if (prepareFakeForSiege(fake, castle, siege, deployed, siegeState)) {
                  deployedOnSide++;
               }
               continue;
            }

            if (!shouldJoinSiege(castleId, objectId)) {
               continue;
            }

            final Location spawn = resolveClanLeaderSquadSpawn(clan, siege, siegeState, objectId);
            if (spawn == null) {
               continue;
            }

            final FakePlayer restored = FakePlayerManager.loginRestoredFakePlayer(objectId, spawn.getX(), spawn.getY(), spawn.getZ());
            if (restored != null && prepareFakeForSiege(restored, castle, siege, deployed, siegeState)) {
               deployedOnSide++;
            }
         }
      }
      return false;
   }

   private boolean prepareFakeForSiege(FakePlayer fake, Castle castle, Siege siege, List<FakePlayer> deployed, byte siegeState) {
      if (!canParticipateInSiege(fake)) {
         return false;
      }

      final boolean isNewDeployment = !isAlreadyDeployed(fake, deployed, siegeState);

      final Location spawn = resolveDeploymentSpawn(fake, castle, siege, siegeState);
      if (spawn == null && isNewDeployment) {
         return false;
      }

      fake.setSiegeState(siegeState);
      fake.setIsInSiege(true);

      if (isNewDeployment) {
         // Ensure siege participants never keep olympiad gear and always enter with full combat buffs.
         FakeHelpers.ensureCombatLoadout(fake);
         FakeHelpers.applySchemeBufferFullBuffs(fake);
      } else {
         // Keep long sieges stable by restoring any missing scheme buffs.
         FakeHelpers.reapplyMissingSchemeBuffs(fake);
      }

      if (spawn != null && isNewDeployment) {
         final boolean leaderSquadSpawn = fake.getClan() != null
            && resolveClanLeaderSquadSpawn(fake.getClan(), siege, siegeState, fake.getObjectId()) != null;
         final int memberSpread = leaderSquadSpawn ? 30 : (siegeState == 1 ? FakePlayerConfig.FAKE_SIEGE_ATTACKER_MEMBER_SPREAD_RADIUS : 200);
         fake.teleToLocation(spawn.getX() + Rnd.get(-memberSpread, memberSpread), spawn.getY() + Rnd.get(-memberSpread, memberSpread), spawn.getZ(), 20);
      }

      if (!deployed.contains(fake)) {
         deployed.add(fake);
      }

      if (fake.getFakeAi() == null || !(fake.getFakeAi() instanceof CombatAI)) {
         fake.assignDefaultAI();
      }

      if (fake.getFakeAi() != null) {
         fake.getFakeAi().setBusyThinking(false);
      }

      if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
         fake.say("[SIEGE] Joining siege of " + castle.getName());
      }

      return isNewDeployment;
   }

   private boolean canParticipateInSiege(FakePlayer fake) {
      if (fake == null || !fake.isOnline() || fake.isInOlympiadMode() || fake.isFakeEvent() || fake.isTour() || fake.isFakeKTBEvent()) {
         return false;
      }

      // Do not force non-siege fakes into siege while they are already busy in open PvP/combat.
      if (!fake.isInSiege() && fake.getSiegeState() <= 0 && isBusyWithPvpCombat(fake)) {
         return false;
      }

      return true;
   }

   private boolean isBusyWithPvpCombat(FakePlayer fake) {
      return fake.isFakePvp()
         || fake.getPvpFlag() > 0
         || fake.isInsideZone(ZoneId.PVP)
         || fake.isInsideZone(ZoneId.PVP_CUSTOM);
   }

   private int countDeployedForSide(List<FakePlayer> deployed, byte siegeState) {
      int count = 0;
      for (FakePlayer fake : deployed) {
         if (fake != null && fake.isOnline() && fake.getSiegeState() == siegeState) {
            count++;
         }
      }
      return count;
   }

   private boolean hasReachedSideLimit(int deployedOnSide, int maxPerSide) {
      return maxPerSide > 0 && deployedOnSide >= maxPerSide;
   }

   private boolean shouldJoinSiege(int castleId, int objectId) {
      if (FakePlayerConfig.FAKE_SIEGE_STICKY_JOIN_CHANCE) {
         final Map<Integer, Boolean> decisions = _siegeJoinDecisions.computeIfAbsent(castleId, _ -> new ConcurrentHashMap<>());
         final Boolean cached = decisions.get(objectId);
         if (cached != null) {
            return cached;
         }

         final boolean result = rollJoinChance();
         decisions.put(objectId, result);
         return result;
      }

      return rollJoinChance();
   }

   private boolean rollJoinChance() {
      final int chance = FakePlayerConfig.FAKE_SIEGE_JOIN_CHANCE;
      return chance >= 100 || (chance > 0 && Rnd.get(100) < chance);
   }

   private boolean isAlreadyDeployed(FakePlayer fake, List<FakePlayer> deployed, byte siegeState) {
      return fake != null && fake.isOnline() && fake.getSiegeState() == siegeState && deployed.contains(fake);
   }

   private Location resolveDeploymentSpawn(FakePlayer fake, Castle castle, Siege siege, byte siegeState) {
      if (fake != null && siege != null && castle != null && fake.getClan() != null) {
         final Location leaderSquadSpawn = resolveClanLeaderSquadSpawn(fake.getClan(), siege, siegeState, fake.getObjectId());
         if (leaderSquadSpawn != null) {
            return leaderSquadSpawn;
         }

         if (siegeState == 1) {
            final Location configuredSpawn = FakePlayerConfig.getFakeSiegeAttackerSpawnForCastle(castle.getCastleId());
            if (configuredSpawn != null) {
               return configuredSpawn;
            }
            if (castle.getCastleZone() != null) {
               final Location exteriorCastleSpawn = castle.getCastleZone().getChaoticSpawnLoc();
               if (exteriorCastleSpawn != null) {
                  return exteriorCastleSpawn;
               }
            }
            return MapRegionTable.getInstance().getLocationToTeleport(fake, TeleportType.SIEGE_FLAG);
         }

         if (siegeState == 2) {
            return MapRegionTable.getInstance().getLocationToTeleport(fake, TeleportType.CASTLE);
         }

         return MapRegionTable.getInstance().getLocationToTeleport(fake, TeleportType.TOWN);
      }
      return null;
   }

   private Location resolveClanLeaderSquadSpawn(Clan clan, Siege siege, byte siegeState, int memberSeed) {
      if (clan == null || siege == null) {
         return null;
      }

      final com.l2jmega.gameserver.model.pledge.ClanMember leaderMember = clan.getLeader();
      if (leaderMember == null) {
         return null;
      }

      final Player leader = leaderMember.getPlayerInstance();
      if (leader == null || !leader.isOnline() || leader.isDead() || leader instanceof FakePlayer) {
         return null;
      }

      if (leader.getClanId() != clan.getClanId()) {
         return null;
      }

      final Siege leaderSiege = CastleManager.getInstance().getSiege(leader);
      if (leaderSiege != siege && leader.getSiegeState() <= 0) {
         return null;
      }

      final int memberIndex = resolveClanMemberIndex(memberSeed, clan);
      final int ring = Math.max(0, memberIndex / 6);
      final int slot = Math.max(0, memberIndex % 6);
      final int spreadBase = siegeState == 1 ? 120 : 90;
      final int radius = spreadBase + (ring * 70);
      final double angle = Math.toRadians(slot * 60.0);

      final int spawnX = leader.getX() + (int) Math.round(Math.cos(angle) * radius);
      final int spawnY = leader.getY() + (int) Math.round(Math.sin(angle) * radius);
      final int spawnZ = leader.getZ();
      return new Location(spawnX, spawnY, spawnZ);
   }

   private int resolveClanMemberIndex(int memberSeed, Clan clan) {
      if (clan == null || memberSeed <= 0) {
         return 0;
      }

      final Player[] onlineMembers = clan.getOnlineMembers();
      for (int i = 0; i < onlineMembers.length; i++) {
         if (onlineMembers[i] != null && onlineMembers[i].getObjectId() == memberSeed) {
            return i;
         }
      }

      return Math.abs(memberSeed % 12);
   }

   private List<Integer> loadOfflineFakeIdsForClan(int clanId) {
      final List<Integer> objectIds = new ArrayList<>();

      try (Connection con = L2DatabaseFactory.getInstance().getConnection();
           PreparedStatement ps = con.prepareStatement("SELECT obj_Id FROM characters WHERE clanid=? AND account_name LIKE 'fakeacc%' AND online=0")) {
         ps.setInt(1, clanId);

         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               objectIds.add(rs.getInt("obj_Id"));
            }
         }
      } catch (Exception e) {
         if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
            e.printStackTrace();
         }
      }

      return objectIds;
   }

   /**
    * Clean up fake players after siege ends.
    */
   private void cleanupSiege(int castleId) {
      final List<FakePlayer> deployed = _siegeFakes.remove(castleId);
      _siegeJoinDecisions.remove(castleId);
      _siegeStartedAt.remove(castleId);
      _lastRedeployAttemptAt.remove(castleId);
      if (deployed == null || deployed.isEmpty()) {
         return;
      }

      final Castle castle = CastleManager.getInstance().getCastleById(castleId);

      for (FakePlayer fake : deployed) {
         if (fake == null || !fake.isOnline()) {
            continue;
         }

         fake.setSiegeState((byte) 0);
         fake.setIsInSiege(false);

         // Teleport back to town
         fake.teleToLocation(com.l2jmega.gameserver.data.MapRegionTable.TeleportType.TOWN);
      }

      if (castle != null && FakePlayerConfig.ALLOW_FAKE_CLAN_PLAYERS) {
         final ClanFakePresenceSystem presenceSystem = ClanFakePresenceSystem.getInstance();
         if (presenceSystem != null) {
            presenceSystem.onSiegeEnd(castle, deployed);
         }
      }
   }

   /**
    * Called when a siege starts - triggers immediate deployment.
    */
   public void onSiegeStart(Castle castle) {
      if (!FakePlayerConfig.FAKE_SIEGE_ENABLED || castle == null) {
         return;
      }

      final Siege siege = castle.getSiege();
      if (siege != null) {
         final int castleId = castle.getCastleId();
         _siegeStartedAt.put(castleId, System.currentTimeMillis());
         _lastRedeployAttemptAt.put(castleId, 0L);
         deployFakesToSiege(castle, siege);
      }
   }

   public boolean isAttackerRallyActive(int castleId) {
      final long startedAt = _siegeStartedAt.getOrDefault(castleId, 0L);
      if (startedAt == 0L) {
         return false;
      }

      final long rallyMs = Math.max(0, FakePlayerConfig.FAKE_SIEGE_ATTACKER_RALLY_SECONDS) * 1000L;
      if (rallyMs <= 0L) {
         return false;
      }

      final long now = System.currentTimeMillis();
      // Hard cap rally from siege start to avoid permanent "stand and wait" state
      // when deploy completion marker doesn't arrive for any reason.
      if (now - startedAt >= rallyMs) {
         return false;
      }

      return true;
   }

   /**
    * Called when a siege ends - cleanup.
    */
   public void onSiegeEnd(Castle castle) {
      if (castle != null) {
         cleanupSiege(castle.getCastleId());
      }
   }

   /**
    * Check if a fake player is participating in any siege.
    */
   public boolean isInSiege(FakePlayer fake) {
      for (List<FakePlayer> list : _siegeFakes.values()) {
         if (list.contains(fake)) {
            return true;
         }
      }
      return false;
   }

   public boolean isClanInActiveSiege(int clanId) {
      if (clanId <= 0) {
         return false;
      }

      for (Castle castle : CastleManager.getInstance().getCastles()) {
         final Siege siege = castle.getSiege();
         if (siege == null || !siege.isInProgress()) {
            continue;
         }

         for (Clan clan : siege.getAttackerClans()) {
            if (clan != null && clan.getClanId() == clanId) {
               return true;
            }
         }

         for (Clan clan : siege.getDefenderClans()) {
            if (clan != null && clan.getClanId() == clanId) {
               return true;
            }
         }
      }

      return false;
   }
}
