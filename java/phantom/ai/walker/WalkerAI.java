package phantom.ai.walker;

import java.util.LinkedList;
import java.util.Queue;
import com.l2jmega.commons.random.Rnd;
import phantom.FakePlayer;
import phantom.ai.FakePlayerAI;

public abstract class WalkerAI extends FakePlayerAI {
   protected Queue<WalkNode> _walkNodes;
   private WalkNode _currentWalkNode;
   private int currentStayIterations = 0;
   protected boolean isWalking = false;

   public WalkerAI(FakePlayer character) {
      super(character);
   }

   public Queue<WalkNode> getWalkNodes() {
      return this._walkNodes;
   }

   protected void addWalkNode(WalkNode walkNode) {
      this._walkNodes.add(walkNode);
   }

   @Override
   public void setup() {
      super.setup();
      this._walkNodes = new LinkedList<>();
      this.setWalkNodes();
   }

   @Override
   public void thinkAndAct() {
      this.setBusyThinking(true);
      this.handleDeath();
      if (!this._walkNodes.isEmpty()) {
         if (this.isWalking && this.userReachedDestination(this._currentWalkNode)) {
            if (this.currentStayIterations < this._currentWalkNode.getStayIterations()) {
               this.currentStayIterations++;
               this.setBusyThinking(false);
               return;
            }

            this._currentWalkNode = null;
            this.currentStayIterations = 0;
            this.isWalking = false;
         }

         if (!this.isWalking && this._currentWalkNode == null) {
            switch (this.getWalkerType()) {
               case RANDOM:
                  this._currentWalkNode = (WalkNode)this.getWalkNodes().toArray()[Rnd.get(0, this.getWalkNodes().size() - 1)];
                  break;
               case LINEAR:
                  this._currentWalkNode = this.getWalkNodes().poll();
                  this._walkNodes.add(this._currentWalkNode);
            }

            this._fakePlayer.getFakeAi().moveTo(this._currentWalkNode.getX(), this._currentWalkNode.getY(), this._currentWalkNode.getZ());
            this.isWalking = true;
         }

         this.setBusyThinking(false);
      }
   }

   protected boolean userReachedDestination(WalkNode targetWalkNode) {
      int dx = this._fakePlayer.getX() - targetWalkNode.getX();
      int dy = this._fakePlayer.getY() - targetWalkNode.getY();
      int dz = this._fakePlayer.getZ() - targetWalkNode.getZ();
      return (dx * dx + dy * dy) <= 10000 && Math.abs(dz) <= 200;
   }

   protected abstract WalkerType getWalkerType();

   protected abstract void setWalkNodes();
}
