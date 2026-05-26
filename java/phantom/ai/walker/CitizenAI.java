package phantom.ai.walker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.gameserver.model.actor.ai.CtrlIntention;
import com.l2jmega.gameserver.data.MapRegionTable;
import com.l2jmega.gameserver.geoengine.GeoEngine;
import com.l2jmega.gameserver.model.WorldObject;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.gameserver.model.actor.Creature;
import com.l2jmega.gameserver.model.actor.instance.Buffer;
import com.l2jmega.gameserver.model.actor.instance.Merchant;
import com.l2jmega.gameserver.model.actor.Npc;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.actor.instance.Gatekeeper;
import com.l2jmega.gameserver.model.actor.instance.WarehouseKeeper;
import com.l2jmega.util.Util;
import com.l2jmega.commons.random.Rnd;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.ai.FakePlayerAI;
import phantom.ai.FakePlayerUtilsAI;
import phantom.helpers.FakeGatekeeperTeleportSupport;

public class CitizenAI extends FakePlayerAI {
   private static final String[] MOODS = new String[]{
      "check shop", "check warehouse", "check gatekeeper", "check buffer", "sit and relax", "walk around", "follow player", "private store"
   };
   protected Future<?> _citizenTask = null;
   protected Future<?> _citizenTeleportTask = null;
   protected Future<?> _citizenTeleportBackTask = null;
   protected Future<?> _citizenMoveTask = null;

   public CitizenAI(FakePlayer character) {
      super(character);
      this.setup();
   }

   @Override
   public void setup() {
      this._fakePlayer.setIsRunning(true);
   }

   @Override
   public void thinkAndAct() {
      if (this._fakePlayer.isInOlympiadMode()) {
         return;
      }

      this.setBusyThinking(true);
      this.handleDeath();
      this.scheduleDespawnOnce(FakePlayerConfig.DESPAWN_CITIZEN_RANDOM_TIME_1 * 60 * 1000, FakePlayerConfig.DESPAWN_CITIZEN_RANDOM_TIME_2 * 60 * 1000);
      if (Rnd.get(1, 1000000) <= FakePlayerConfig.FAKE_CHANCE_TO_TALK_SOCIAL) {
         FakePlayerUtilsAI.maybeAnnounce(this._fakePlayer);
      }
      FakePlayerUtilsAI.maybeAnnounceNormalChat(this._fakePlayer);

      if (this._citizenTask == null || this._citizenTask.isDone()) {
         this._citizenTask = ThreadPool
            .scheduleAtFixedRate(() -> this.startRoamingInTown(),
               Rnd.get(5, 15) * 1000,
               Rnd.get(FakePlayerConfig.FAKE_CITIZEN_ROAMING_MIN_INTERVAL, FakePlayerConfig.FAKE_CITIZEN_ROAMING_MAX_INTERVAL) * 1000);
      }
      if (this._citizenMoveTask == null || this._citizenMoveTask.isDone()) {
         this._citizenMoveTask = ThreadPool
            .scheduleAtFixedRate(() -> this.randomMove(),
               Rnd.get(3, 8) * 1000,
               Rnd.get(FakePlayerConfig.FAKE_CITIZEN_RANDOM_MOVE_MIN_INTERVAL, FakePlayerConfig.FAKE_CITIZEN_RANDOM_MOVE_MAX_INTERVAL) * 1000);
      }
      if (this._citizenTeleportTask == null || this._citizenTeleportTask.isDone()) {
         this._citizenTeleportTask = ThreadPool.scheduleAtFixedRate(() -> this.checkTeleport(), 15000L, 30000L);
      }
      this.setBusyThinking(false);
   }

   public void startRoamingInTown() {
      if (this._fakePlayer.isInOlympiadMode()) {
         return;
      }

      if (this._fakePlayer.isDead()) {
         this._fakePlayer.doRevive();
         Location loc = MapRegionTable.getInstance().getLocationToTeleport(this._fakePlayer, MapRegionTable.TeleportType.TOWN);
         this.teleportToLocation(loc.getX(), loc.getY(), loc.getZ(), 50);
         return;
      }

      this._fakePlayer.setMood(Rnd.get(MOODS));
      int _shopChecked = 0;
      int _warehouseChecked = 0;
      int _teleporterChecked = 0;
      int _npcBufferChecked = 0;
      int _playerChecked = 0;
      int _playerStoreChecked = 0;
      int maxChecksWh = FakePlayerConfig.FAKE_PLAYER_ROAMING_MAX_WH_CHECKS;
      int maxChecksShop = FakePlayerConfig.FAKE_PLAYER_ROAMING_MAX_SHOP_CHECKS;
      int maxChecksTeleporter = FakePlayerConfig.FAKE_PLAYER_ROAMING_MAX_TELEPORT_CHECKS;
      int maxChecksBufferNpc = FakePlayerConfig.FAKE_PLAYER_ROAMING_MAX_BUFFER_CHECKS;
      int maxChecksPlayer = FakePlayerConfig.FAKE_PLAYER_ROAMING_MAX_PLAYER_CHECKS;
      int maxChecksPlayerStore = FakePlayerConfig.FAKE_PLAYER_ROAMING_MAX_PL_STORE_CHECKS;
      if (this._fakePlayer != null) {
         if (this._fakePlayer.getMood().equals("")) {
            this.startRoamingInTown();
         }

         Creature target = null;
         if (this._fakePlayer.getMood().contains("check warehouse")
            && Rnd.get(100) <= FakePlayerConfig.FAKE_PLAYER_WH_CHECK_CHANCE
            && _warehouseChecked < maxChecksWh) {
            if (this._fakePlayer.isSitting()) {
               this._fakePlayer.standUp();
            }

            List<Npc> list = new ArrayList<>();

            for (Npc npc : this._fakePlayer.getKnownTypeInRadius(Npc.class, 2000)) {
               if (npc != null
                  && GeoEngine.getInstance().canSeeTarget(this._fakePlayer, npc)
                  && shouldUseNpcForRoaming(npc, WarehouseKeeper.class)) {
                  list.add(npc);
               }
            }

            if (list.isEmpty()) {
               _warehouseChecked = maxChecksWh;
            }

            target = Rnd.get(list);
            _warehouseChecked++;
         }

         if (this._fakePlayer.getMood().contains("check shop")
            && Rnd.get(100) <= FakePlayerConfig.FAKE_PLAYER_SHOP_CHECK_CHANCE
            && _shopChecked < maxChecksShop) {
            if (this._fakePlayer.isSitting()) {
               this._fakePlayer.standUp();
            }

            List<Npc> list = new ArrayList<>();

            for (Npc npcx : this._fakePlayer.getKnownTypeInRadius(Npc.class, 2000)) {
               if (npcx != null
                  && GeoEngine.getInstance().canSeeTarget(this._fakePlayer, npcx)
                  && shouldUseNpcForRoaming(npcx, Merchant.class)) {
                  list.add(npcx);
               }
            }

            if (list.isEmpty()) {
               _shopChecked = maxChecksShop;
            }

            target = Rnd.get(list);
            _shopChecked++;
         }

         if (this._fakePlayer.getMood().contains("check gatekeeper")
            && Rnd.get(100) <= FakePlayerConfig.FAKE_PLAYER_TELEPORT_CHECK_CHANCE
            && _teleporterChecked < maxChecksTeleporter) {
            if (this._fakePlayer.isSitting()) {
               this._fakePlayer.standUp();
            }

            List<Npc> list = new ArrayList<>();

            for (Npc npcxx : this._fakePlayer.getKnownTypeInRadius(Npc.class, 2000)) {
               if (npcxx != null
                  && GeoEngine.getInstance().canSeeTarget(this._fakePlayer, npcxx)
                  && shouldUseNpcForRoaming(npcxx, Gatekeeper.class)) {
                  list.add(npcxx);
               }
            }

            if (list.isEmpty()) {
               _teleporterChecked = maxChecksTeleporter;
            }

            target = Rnd.get(list);
            _teleporterChecked++;
         }

         if (this._fakePlayer.getMood().contains("check buffer")
            && Rnd.get(100) <= FakePlayerConfig.FAKE_PLAYER_BUFFER_CHECK_CHANCE
            && _npcBufferChecked < maxChecksBufferNpc) {
            if (this._fakePlayer.isSitting()) {
               this._fakePlayer.standUp();
            }

            List<Npc> list = new ArrayList<>();

            for (Npc npcxxx : this._fakePlayer.getKnownTypeInRadius(Npc.class, 2000)) {
               if (npcxxx != null
                  && GeoEngine.getInstance().canSeeTarget(this._fakePlayer, npcxxx)
                  && shouldUseNpcForRoaming(npcxxx, Buffer.class)) {
                  list.add(npcxxx);
               }
            }

            if (list.isEmpty()) {
               _npcBufferChecked = maxChecksBufferNpc;
            }

            target = Rnd.get(list);
            _npcBufferChecked++;
         }

         if (this._fakePlayer.getMood().contains("sit and relax") && Rnd.get(100) <= FakePlayerConfig.FAKE_PLAYER_RELAX_CHECK_CHANCE) {
            target = null;
            this._fakePlayer.setTarget(null);
            if (this._fakePlayer.isSitting()) {
               this._fakePlayer.standUp();
            } else {
               this._fakePlayer.sitDown();
            }
         }

         if (this._fakePlayer.getMood().contains("follow player")
            && Rnd.get(100) <= FakePlayerConfig.FAKE_PLAYER_PLAYER_CHECK_CHANCE
            && _playerChecked < maxChecksPlayer) {
            if (this._fakePlayer.isSitting()) {
               this._fakePlayer.standUp();
            }

            List<Player> list = new ArrayList<>();

            for (Player player : this._fakePlayer.getKnownTypeInRadius(Player.class, 2000)) {
               if (player != null && GeoEngine.getInstance().canSeeTarget(this._fakePlayer, player)) {
                  list.add(player);
               }
            }

            if (list.isEmpty()) {
               _playerChecked = maxChecksPlayer;
            }

            target = Rnd.get(list);
            _playerChecked++;
         }

         if (this._fakePlayer.getMood().contains("private store")
            && Rnd.get(100) <= FakePlayerConfig.FAKE_PLAYER_PL_STORE_CHECK_CHANCE
            && _playerStoreChecked < maxChecksPlayerStore) {
            if (this._fakePlayer.isSitting()) {
               this._fakePlayer.standUp();
            }

            List<Player> list = new ArrayList<>();

            for (Player playerx : this._fakePlayer.getKnownTypeInRadius(Player.class, 2000)) {
               if (playerx != null && GeoEngine.getInstance().canSeeTarget(this._fakePlayer, playerx) && playerx.getStoreType() != Player.StoreType.NONE) {
                  list.add(playerx);
               }
            }

            if (list.isEmpty()) {
               _playerStoreChecked = maxChecksPlayerStore;
            }

            target = Rnd.get(list);
            _playerStoreChecked++;
         }

         if (this._fakePlayer.getMood().contains("walk around") && Rnd.get(100) <= FakePlayerConfig.FAKE_PLAYER_WALK_CHECK_CHANCE) {
            target = null;
            this._fakePlayer.setTarget(null);
            this.randomMove();
         }

         if (target != null) {
            this._fakePlayer.setTarget(target);
            this._fakePlayer.getAI().setIntention(CtrlIntention.INTERACT, target);
         }
      }
   }

   private static boolean shouldUseNpcForRoaming(Npc npc, Class<?> defaultRoleType) {
      if (npc == null) {
         return false;
      }

      final boolean roleMatch = defaultRoleType != null && defaultRoleType.isInstance(npc);
      final int[] configuredNpcIds = FakePlayerConfig.FAKE_PLAYER_ALLOWED_NPC_TO_WALK;
      final boolean hasConfiguredIds = configuredNpcIds != null && configuredNpcIds.length > 0;
      final boolean configuredMatch = hasConfiguredIds && Util.contains(configuredNpcIds, npc.getNpcId());

      // Keep default behavior by role, but also allow explicit custom IDs from config.
      return roleMatch || configuredMatch;
   }

   public void randomMove() {
      if (this._fakePlayer.isInOlympiadMode()) {
         return;
      }

      Location loc = new Location(this._fakePlayer.getX() + Rnd.get(-400, 400), this._fakePlayer.getY() + Rnd.get(-400, 400), this._fakePlayer.getZ());
      if (GeoEngine.getInstance()
            .canMoveToTargetLoc(this._fakePlayer.getX(), this._fakePlayer.getY(), this._fakePlayer.getZ(), loc.getX(), loc.getY(), loc.getZ())
         != null) {
         this._fakePlayer.getFakeAi().moveTo(loc.getX(), loc.getY(), loc.getZ());
      }
   }

   public void checkTeleport() {
      if (this._fakePlayer.isInOlympiadMode() || this._fakePlayer.isOlympiadProtection() || this._fakePlayer.getOlympiadGameId() != -1) {
         return;
      }

      for (WorldObject wh : this._fakePlayer.getKnownTypeInRadius(Gatekeeper.class, 50)) {
         if (!(wh instanceof Gatekeeper) || !(this._fakePlayer.getTarget() instanceof Gatekeeper) || this._fakePlayer.getTarget() != wh) {
            continue;
         }

         if (this._fakePlayer.isInOlympiadMode() || this._fakePlayer.isOlympiadProtection() || this._fakePlayer.getOlympiadGameId() != -1) {
            return;
         }

         if (FakeGatekeeperTeleportSupport.useRandomGlobalTeleport(this._fakePlayer, (Gatekeeper) wh)) {
            this._fakePlayer.setTarget(null);
         }
         return;
      }
   }
}
