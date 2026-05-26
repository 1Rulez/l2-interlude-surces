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

public class AutoSpawnAI {
   public static List<Location> locs = new ArrayList<>();
   public static int fakesCount = Rnd.get(FakePlayerConfig.AUTO_SPAWN_FAKE_COUNT_MIN, FakePlayerConfig.AUTO_SPAWN_FAKE_COUNT_MAX);
   private boolean _spawnScheduled = false;

   private AutoSpawnAI() {
   }

   public synchronized void scheduleSpawnIfEnabled() {
      if (!FakePlayerConfig.ALLOW_FAKE_PLAYER_AUTO_SPAWN || _spawnScheduled) {
         return;
      }

      _spawnScheduled = true;
      ThreadPool.schedule(new AutoSpawnAI.spawnPhantoms(), FakePlayerConfig.AUTO_SPAWN_DELAY_TIME * 1000L);
   }

   public void loadData() {
      locs = FakePlayerConfig.FAKE_AUTO_SPAWN_LIST_LOCS;

      if (locs == null || locs.isEmpty()) {
         return;
      }

      try {
         Location loc = null;

         for (int i = 0; i < fakesCount; i++) {
            loc = locs.get(Rnd.get(locs.size()));
            int x = loc.getX() + Rnd.get(250);
            int y = loc.getY() + Rnd.get(250);
            int z = loc.getZ();
            int totalChance = FakePlayerConfig.AUTO_SPAWN_ARCHER_PERCENT
               + FakePlayerConfig.AUTO_SPAWN_NUKER_PERCENT
               + FakePlayerConfig.AUTO_SPAWN_WARRIOR_PERCENT
               + FakePlayerConfig.AUTO_SPAWN_DAGGER_PERCENT
               + FakePlayerConfig.AUTO_SPAWN_TANKER_PERCENT;
            if (totalChance != 0) {
               int roll = Rnd.get(totalChance);
               int current = 0;
               FakePlayer fake = null;
               if ((current = current + FakePlayerConfig.AUTO_SPAWN_ARCHER_PERCENT) > roll) {
                  fake = FakePlayerManager.spawnArcher(x, y, z);
               } else if ((current = current + FakePlayerConfig.AUTO_SPAWN_NUKER_PERCENT) > roll) {
                  fake = FakePlayerManager.spawnNuker(x, y, z);
               } else if ((current = current + FakePlayerConfig.AUTO_SPAWN_WARRIOR_PERCENT) > roll) {
                  fake = FakePlayerManager.spawnWarrior(x, y, z);
               } else if ((current = current + FakePlayerConfig.AUTO_SPAWN_DAGGER_PERCENT) > roll) {
                  fake = FakePlayerManager.spawnDagger(x, y, z);
               } else if ((current = current + FakePlayerConfig.AUTO_SPAWN_TANKER_PERCENT) > roll) {
                  fake = FakePlayerManager.spawnTanker(x, y, z);
               }

               if (fake != null) {
                  fake.setLastCords(fake.getX(), fake.getY(), fake.getZ());
                  fake.setFakeAi(new CitizenAI(fake));
                  if (fake.getFakeAi() == null) {
                     System.out.println("[WARNING] FakePlayer " + fake.getName() + " spawned with no AI.");
                  }
               }
            }
         }
      } catch (Exception var10) {
         var10.printStackTrace();
      }
   }

   public static AutoSpawnAI getInstance() {
      return AutoSpawnAI.SingletonHolder._instance;
   }

   private static class SingletonHolder {
      protected static final AutoSpawnAI _instance = new AutoSpawnAI();
   }

   private class spawnPhantoms implements Runnable {
      @Override
      public void run() {
         synchronized (AutoSpawnAI.this) {
            _spawnScheduled = false;
         }

         if (!FakePlayerConfig.ALLOW_FAKE_PLAYER_AUTO_SPAWN) {
            return;
         }

         AutoSpawnAI.this.loadData();
      }
   }
}
