package phantom.ai.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.events.CTF;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.FakePlayerManager;

public class CaptureTheFlagAI {
   private static final Logger _log = Logger.getLogger(CaptureTheFlagAI.class.getName());
   public static List<FakePlayer> _ctfFakes = new CopyOnWriteArrayList<>();

   public static boolean spawnPhantoms() {
      List<Location> locs = FakePlayerConfig.CTF_FAKE_PLAYER_LIST_LOCS;
      if (locs == null || locs.isEmpty() || CTF._teams == null || CTF._teams.size() < 2) {
         return false;
      }

      int count = Rnd.get(FakePlayerConfig.CTF_FAKE_PLAYER_COUNT_MIN, FakePlayerConfig.CTF_FAKE_PLAYER_COUNT_MAX);
      final int joinChance = Math.max(0, Math.min(100, FakePlayerConfig.CTF_FAKE_JOIN_CHANCE));
      int attempts = 0;
      final int maxAttempts = Math.max(count * 5, count);

      try {
         while (_ctfFakes.size() < count && attempts++ < maxAttempts) {
            if (joinChance < 100 && Rnd.get(100) >= joinChance) {
               continue;
            }
            Location loc = locs.get(Rnd.get(locs.size()));
            FakePlayer fp = FakePlayerManager.spawnEventPlayer(loc.getX() + Rnd.get(-210, 210), loc.getY() + Rnd.get(-210, 210), loc.getZ());
            if (fp == null) {
               continue;
            }
            if (SiegeFakeSystem.getInstance().isClanInActiveSiege(fp.getClanId())) {
               fp.setFakeEvent(false);
               fp.despawnPlayer();
               continue;
            }
            fp.setFakeEvent(true);
            fp.assignDefaultAI();
            _ctfFakes.add(fp);

            String teamName = CTF._teams.get((_ctfFakes.size() - 1) % CTF._teams.size());
            CTF.addPlayer(fp, teamName);
         }

         _log.info("[Fake CTF]: Spawned " + _ctfFakes.size() + " fake players for CTF.");
         return true;
      } catch (Exception e) {
         _log.warning("[Fake CTF]: Error spawning phantoms: " + e.getMessage());
         e.printStackTrace();
         return false;
      }
   }

   public static boolean unspawnPhantoms() {
      try {
         for (FakePlayer fp : _ctfFakes) {
            if (fp != null) {
               fp._inEventCTF = false;
               fp.despawnPlayer();
            }
         }
         _ctfFakes.clear();
         return true;
      } catch (Exception e) {
         e.printStackTrace();
         return false;
      }
   }
}
