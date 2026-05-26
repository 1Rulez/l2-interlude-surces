package phantom.task;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import phantom.FakePlayer;

public class AITask implements Runnable {
   private static final Logger _log = Logger.getLogger(AITask.class.getName());
   private final List<FakePlayer> _fakePlayers;
   private final int _from;
   private final int _to;

   public AITask(List<FakePlayer> fakePlayers, int from, int to) {
      this._fakePlayers = fakePlayers;
      this._from = from;
      this._to = to;
   }

   @Override
   public void run() {
      int total = _fakePlayers.size();
      if (this._from < total) {
         int safeTo = Math.min(this._to, total);
         List<FakePlayer> fakePlayers = _fakePlayers.subList(this._from, safeTo);

         for (FakePlayer fp : fakePlayers) {
            if (fp == null || fp.getFakeAi() == null || fp.getFakeAi().isBusyThinking()) {
               continue;
            }

            try {
               fp.getFakeAi().thinkAndAct();
            } catch (Exception e) {
               _log.log(Level.WARNING, "AITask failed for fake " + fp.getName() + " classId=" + fp.getClassId() + " ai=" + fp.getFakeAi().getClass().getSimpleName(), e);
               // Safety valve: avoid permanent AI freeze when an exception happens
               // while a specific AI implementation keeps busyThinking=true.
               fp.getFakeAi().setBusyThinking(false);
            }
         }
      }
   }
}
