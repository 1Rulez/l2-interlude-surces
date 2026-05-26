package phantom.ai.shop;

import com.l2jmega.gameserver.network.serverpackets.PrivateStoreMsgBuy;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.model.actor.instance.Player;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.ai.FakePlayerAI;
import phantom.ai.FakePlayerUtilsAI;
import phantom.ai.shop.holder.FakePrivateBuyHolder;

public class PrivateStoreBuyAI extends FakePlayerAI {
   private boolean _storeInitialized = false;

   public PrivateStoreBuyAI(FakePlayer character) {
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
      this.getFake().addItem("Adena", 57, 1000000000, this.getFake(), false);

      for (FakePrivateBuyHolder buyItem : FakePlayerConfig.FAKE_PLAYER_PRIVATE_BUY_LIST) {
         if (Rnd.get(100) <= buyItem.getListChance()) {
            int cMin = buyItem.getCountMin();
            int cMax = buyItem.getCountMax();
            int pMin = buyItem.getPriceMin();
            int pMax = buyItem.getPriceMax();
            int count = Rnd.get(Math.min(cMin, cMax), Math.max(cMin, cMax));
            int price = Rnd.get(Math.min(pMin, pMax), Math.max(pMin, pMax));
            this.getFake().getBuyList().addItemByItemId(buyItem.getBuyId(), count, price);
         }
      }

      this.getFake().getBuyList().setTitle(FakePlayerUtilsAI.getRandomPrivateBuyTitle());
      this.getFake().sitDown();
      this.getFake().setStoreType(Player.StoreType.BUY);
      this.getFake().broadcastUserInfo();
      this.getFake().broadcastPacket(new PrivateStoreMsgBuy(this.getFake()));
   }

   public FakePlayer getFake() {
      return this._fakePlayer;
   }
}
