package phantom.ai.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import phantom.FakePlayer;

public class MultiTvTAI {
   public static List<FakePlayer> _multitvtFakes = new CopyOnWriteArrayList<>();

   public static boolean spawnPhantoms() {
      return false;
   }

   public static boolean unspawnPhantoms() {
      try {
         for (FakePlayer fp : _multitvtFakes) {
            if (fp != null) {
               fp.despawnPlayer();
            }
         }
         _multitvtFakes.clear();
         return true;
      } catch (Exception e) {
         e.printStackTrace();
         return false;
      }
   }
}
