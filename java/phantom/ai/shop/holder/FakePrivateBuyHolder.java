package phantom.ai.shop.holder;

public class FakePrivateBuyHolder {
   private int _id;
   private int _minCount;
   private int _maxCount;
   private int _minPrice;
   private int _maxPrice;
   private int _listChance;

   public FakePrivateBuyHolder(int buyId, int countMin, int countMax, int priceMin, int priceMax) {
      this._id = buyId;
      this._minCount = countMin;
      this._maxCount = countMax;
      this._minPrice = priceMin;
      this._maxPrice = priceMax;
      this._listChance = 100;
   }

   public FakePrivateBuyHolder(int buyId, int countMin, int countMax, int priceMin, int priceMax, int rewardChance) {
      this._id = buyId;
      this._minCount = countMin;
      this._maxCount = countMax;
      this._minPrice = priceMin;
      this._maxPrice = priceMax;
      this._listChance = rewardChance;
   }

   public int getBuyId() {
      return this._id;
   }

   public int getCountMin() {
      return this._minCount;
   }

   public int getCountMax() {
      return this._maxCount;
   }

   public int getPriceMin() {
      return this._minPrice;
   }

   public int getPriceMax() {
      return this._maxPrice;
   }

   public int getListChance() {
      return this._listChance;
   }

   public void setBuyId(int id) {
      this._id = id;
   }

   public void setCountMin(int min) {
      this._minCount = min;
   }

   public void setCountMax(int max) {
      this._maxCount = max;
   }

   public void setPriceMin(int min) {
      this._minPrice = min;
   }

   public void setPriceMax(int max) {
      this._maxPrice = max;
   }

   public void setListChance(int chance) {
      this._listChance = chance;
   }
}
