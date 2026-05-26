package phantom.ai.autospawn;

import java.util.ArrayList;
import java.util.List;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.commons.random.Rnd;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.FakePlayerManager;
import phantom.ai.walker.CitizenAI;

public class TownAutoSpawnAI {
   public static List<Location> locs = new ArrayList<>();
   public static int fakesCount = Rnd.get(FakePlayerConfig.TOWN_AUTO_SPAWN_FAKE_COUNT_MIN, FakePlayerConfig.TOWN_AUTO_SPAWN_FAKE_COUNT_MAX);
   private boolean _spawnScheduled = false;

   private TownAutoSpawnAI() {
   }

   public synchronized void scheduleSpawnIfEnabled() {
      if (!FakePlayerConfig.ALLOW_FAKE_PLAYER_AUTO_SPAWN || !FakePlayerConfig.TOWN_ALLOW_FAKE_PLAYER_AUTO_SPAWN || _spawnScheduled) {
         return;
      }

      _spawnScheduled = true;
      ThreadPool.schedule(new TownAutoSpawnAI.spawnPhantoms(), FakePlayerConfig.TOWN_AUTO_SPAWN_DELAY_TIME * 1000L);
   }

   public void loadData() {
      locs = FakePlayerConfig.TOWN_AUTO_SPAWN_LIST_LOCS;

      if (locs == null || locs.isEmpty()) {
         return;
      }

      try {
         Location loc = null;
         for (int i = 0; i < fakesCount; i++) {
            loc = Rnd.get(locs);
            if (loc == null) {
               continue;
            }

            int x = loc.getX() + Rnd.get(250);
            int y = loc.getY() + Rnd.get(250);
            int z = loc.getZ();
            FakePlayer fake = FakePlayerManager.spawnPlayer(x, y, z);
            fake.setLastCords(x, y, z);
            fake.setFakeAi(new CitizenAI(fake));
         }
      } catch (Exception var4) {
         var4.printStackTrace();
      }
   }

   public static TownAutoSpawnAI getInstance() {
      return TownAutoSpawnAI.SingletonHolder._instance;
   }

   private static class SingletonHolder {
      protected static final TownAutoSpawnAI _instance = new TownAutoSpawnAI();
   }

   private class spawnPhantoms implements Runnable {
      @Override
      public void run() {
         synchronized (TownAutoSpawnAI.this) {
            _spawnScheduled = false;
         }

         if (!FakePlayerConfig.ALLOW_FAKE_PLAYER_AUTO_SPAWN || !FakePlayerConfig.TOWN_ALLOW_FAKE_PLAYER_AUTO_SPAWN) {
            return;
         }

         TownAutoSpawnAI.this.loadData();
      }
   }
}
