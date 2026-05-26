package phantom.ai.walker;

public class WalkNode {
   private int _x;
   private int _y;
   private int _z;
   private int _stayIterations;

   public WalkNode(int x, int y, int z, int stayIterations) {
      this._x = x;
      this._y = y;
      this._z = z;
      this._stayIterations = stayIterations;
   }

   public int getX() {
      return this._x;
   }

   public int getY() {
      return this._y;
   }

   public int getZ() {
      return this._z;
   }

   public int getStayIterations() {
      return this._stayIterations;
   }
}
