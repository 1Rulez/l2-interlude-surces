package phantom.ai.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import phantom.FakePlayer;

public class DeathMatchAI {
   public static List<FakePlayer> _dmFakes = new CopyOnWriteArrayList<>();

   public static boolean spawnPhantoms() {
      return false;
   }

   public static boolean unspawnPhantoms() {
      try {
         for (FakePlayer fp : _dmFakes) {
            if (fp != null) {
               fp.despawnPlayer();
            }
         }
         _dmFakes.clear();
         return true;
      } catch (Exception e) {
         e.printStackTrace();
         return false;
      }
   }
}
