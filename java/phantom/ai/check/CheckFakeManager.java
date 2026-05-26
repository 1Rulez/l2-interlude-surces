package phantom.ai.check;

import com.l2jmega.commons.concurrent.ThreadPool;
import phantom.FakePlayerConfig;

public class CheckFakeManager {
   private CheckFakeManager() {
      if (FakePlayerConfig.CHECK_FAKE_PLAYERS_AREA) {
         ThreadPool
            .scheduleAtFixedRate(
               new CheckFakeManager.CheckFakeTask(),
               FakePlayerConfig.CHECK_FAKE_PLAYERS_START_TIME * 60 * 1000,
               FakePlayerConfig.CHECK_FAKE_PLAYERS_RESTART_TIME * 60 * 1000
            );
      }
   }

   public static CheckFakeManager getInstance() {
      return CheckFakeManager.SingletonHolder._instance;
   }

   private class CheckFakeTask implements Runnable {
      @Override
      public void run() {
      }
   }

   private static class SingletonHolder {
      protected static final CheckFakeManager _instance = new CheckFakeManager();
   }
}
