package phantom.ai.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import phantom.FakePlayer;

public class LastManAI {
   public static List<FakePlayer> _lmFakes = new CopyOnWriteArrayList<>();

   public static boolean spawnPhantoms() {
      return false;
   }

   public static boolean unspawnPhantoms() {
      try {
         for (FakePlayer fp : _lmFakes) {
            if (fp != null) {
               fp.despawnPlayer();
            }
         }
         _lmFakes.clear();
         return true;
      } catch (Exception e) {
         e.printStackTrace();
         return false;
      }
   }
}
