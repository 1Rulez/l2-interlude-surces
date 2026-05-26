package phantom.ai.shop.holder;

public class FakePrivateSellHolder {
   private int _id;
   private int _minCount;
   private int _maxCount;
   private int _listChance;

   public FakePrivateSellHolder(int buyId, int countMin, int countMax) {
      this._id = buyId;
      this._minCount = countMin;
      this._maxCount = countMax;
      this._listChance = 100;
   }

   public FakePrivateSellHolder(int buyId, int countMin, int countMax, int rewardChance) {
      this._id = buyId;
      this._minCount = countMin;
      this._maxCount = countMax;
      this._listChance = rewardChance;
   }

   public int getSellId() {
      return this._id;
   }

   public int getCountMin() {
      return this._minCount;
   }

   public int getCountMax() {
      return this._maxCount;
   }

   public int getListChance() {
      return this._listChance;
   }

   public void setSellId(int id) {
      this._id = id;
   }

   public void setCountMin(int min) {
      this._minCount = min;
   }

   public void setCountMax(int max) {
      this._maxCount = max;
   }

   public void setListChance(int chance) {
      this._listChance = chance;
   }
}
