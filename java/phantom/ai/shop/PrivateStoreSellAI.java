package phantom.ai.shop;

import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.network.serverpackets.PrivateStoreMsgSell;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.model.actor.instance.Player;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.ai.FakePlayerAI;
import phantom.ai.FakePlayerUtilsAI;
import phantom.ai.shop.holder.FakePrivateSellHolder;

public class PrivateStoreSellAI extends FakePlayerAI {
   private boolean _storeInitialized = false;

   public PrivateStoreSellAI(FakePlayer character) {
      super(character);
      this.setup();
   }

   @Override
   public void setup() {
      this._fakePlayer.setIsRunning(true);
   }

   @Override
   public void thinkAndAct() {
      if (_storeInitialized) {
         return;
      }
      _storeInitialized = true;
      this.setBusyThinking(true);

      for (FakePrivateSellHolder sellItem : FakePlayerConfig.FAKE_PLAYER_PRIVATE_SELL_LIST) {
         if (Rnd.get(100) <= sellItem.getListChance()) {
            int cMin = sellItem.getCountMin();
            int cMax = sellItem.getCountMax();
            int count = Rnd.get(Math.min(cMin, cMax), Math.max(cMin, cMax));
            this.getFake().addItem("List", sellItem.getSellId(), count, this.getFake(), false);
         }
      }

      for (ItemInstance itemDrop : this.getFake().getInventory().getItems()) {
         if (!itemDrop.isEquipped()) {
            int basePrice = itemDrop.getItem().getReferencePrice();
            if (basePrice <= 0)
               basePrice = Rnd.get(50000, 500000);
            int price = (int)(basePrice * (1.5 + Rnd.get(10, 30) / 10.0));
            this.getFake().getSellList().addItem(itemDrop.getObjectId(), itemDrop.getCount(), Math.max(price, 1000));
         }
      }

      this.getFake().getSellList().setTitle(FakePlayerUtilsAI.getRandomPrivateSellTitle());
      this.getFake().getSellList().setPackaged(false);
      this.getFake().sitDown();
      this.getFake().setStoreType(Player.StoreType.SELL);
      this.getFake().broadcastUserInfo();
      this.getFake().broadcastPacket(new PrivateStoreMsgSell(this.getFake()));
   }

   public FakePlayer getFake() {
      return this._fakePlayer;
   }
}
