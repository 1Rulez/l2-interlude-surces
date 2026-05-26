package phantom.ai.walker;

import com.l2jmega.commons.random.Rnd;
import phantom.FakePlayer;

public class GiranWalkerAI extends WalkerAI {
   public GiranWalkerAI(FakePlayer character) {
      super(character);
   }

   @Override
   protected WalkerType getWalkerType() {
      return WalkerType.RANDOM;
   }

   @Override
   protected void setWalkNodes() {
      this._walkNodes.add(new WalkNode(82248, 148600, -3464, Rnd.get(5, 20)));
      this._walkNodes.add(new WalkNode(82072, 147560, -3464, Rnd.get(5, 20)));
      this._walkNodes.add(new WalkNode(82792, 147832, -3464, Rnd.get(5, 20)));
      this._walkNodes.add(new WalkNode(83320, 147976, -3400, Rnd.get(5, 20)));
      this._walkNodes.add(new WalkNode(84584, 148536, -3400, Rnd.get(5, 20)));
      this._walkNodes.add(new WalkNode(83384, 149256, -3400, Rnd.get(5, 20)));
      this._walkNodes.add(new WalkNode(82936, 148968, -3464, Rnd.get(5, 15)));
      this._walkNodes.add(new WalkNode(81976, 148264, -3464, Rnd.get(5, 15)));
      this._walkNodes.add(new WalkNode(83656, 148168, -3400, Rnd.get(5, 20)));
      this._walkNodes.add(new WalkNode(82536, 149064, -3464, Rnd.get(5, 15)));
   }
}
