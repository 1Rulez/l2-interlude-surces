package phantom.task;

import phantom.FakePlayerTaskManager;

public class AITaskRunner implements Runnable {
   @Override
   public void run() {
      FakePlayerTaskManager.INSTANCE.runAITick();
   }
}
