package phantom.ai.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.events.BossEvent;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.FakePlayerManager;

public class KillTheBossAI {
   private static final Logger _log = Logger.getLogger(KillTheBossAI.class.getName());
   public static List<FakePlayer> _ktbFakes = new CopyOnWriteArrayList<>();

   public static boolean spawnPhantoms() {
      List<Location> locs = FakePlayerConfig.KTB_FAKE_PLAYER_LIST_LOCS;
      if (locs == null || locs.isEmpty()) {
         return false;
      }

      int count = Rnd.get(FakePlayerConfig.KTB_FAKE_PLAYER_COUNT_MIN, FakePlayerConfig.KTB_FAKE_PLAYER_COUNT_MAX);
      final int joinChance = Math.max(0, Math.min(100, FakePlayerConfig.KTB_FAKE_JOIN_CHANCE));
      int attempts = 0;
      final int maxAttempts = Math.max(count * 5, count);

      try {
         while (_ktbFakes.size() < count && attempts++ < maxAttempts) {
            if (joinChance < 100 && Rnd.get(100) >= joinChance) {
               continue;
            }
            Location loc = locs.get(Rnd.get(locs.size()));
            FakePlayer fp = FakePlayerManager.spawnEventPlayer(loc.getX() + Rnd.get(-210, 210), loc.getY() + Rnd.get(-210, 210), loc.getZ());
            if (fp == null) {
               continue;
            }
            if (SiegeFakeSystem.getInstance().isClanInActiveSiege(fp.getClanId())) {
               fp.setFakeKTBEvent(false);
               fp.despawnPlayer();
               continue;
            }
            fp.setFakeKTBEvent(true);
            fp.assignDefaultAI();
            _ktbFakes.add(fp);

            BossEvent.getInstance().addPlayer(fp);
         }

         _log.info("[Fake KTB]: Spawned " + _ktbFakes.size() + " fake players for Boss Event.");
         return true;
      } catch (Exception e) {
         _log.warning("[Fake KTB]: Error spawning phantoms: " + e.getMessage());
         e.printStackTrace();
         return false;
      }
   }

   public static boolean unspawnPhantoms() {
      try {
         for (FakePlayer fp : _ktbFakes) {
            if (fp != null) {
               fp.setFakeKTBEvent(false);
               BossEvent.getInstance().removePlayer(fp);
               fp.despawnPlayer();
            }
         }
         _ktbFakes.clear();
         return true;
      } catch (Exception e) {
         e.printStackTrace();
         return false;
      }
   }
}
