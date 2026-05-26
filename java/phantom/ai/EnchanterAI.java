package phantom.ai;

import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.model.item.kind.Weapon;
import com.l2jmega.commons.random.Rnd;
import phantom.FakePlayer;
import phantom.helpers.FakeHelpers;

public class EnchanterAI extends FakePlayerAI {
   private int _enchantIterations = 0;
   private int _maxEnchant = 25;
   private final int iterationsForAction = Rnd.get(3, 5);

   public EnchanterAI(FakePlayer character) {
      super(character);
   }

   @Override
   public void setup() {
      super.setup();
      ItemInstance weapon = this._fakePlayer.getActiveWeaponInstance();
      weapon = this.checkIfWeaponIsExistsEquipped(weapon);
      weapon.setEnchantLevel(0);
      this._fakePlayer.broadcastCharInfo();
   }

   @Override
   public void thinkAndAct() {
      this.handleDeath();
      this.setBusyThinking(true);
      if (this._enchantIterations % this.iterationsForAction == 0) {
         ItemInstance weapon = this._fakePlayer.getActiveWeaponInstance();
         weapon = this.checkIfWeaponIsExistsEquipped(weapon);
         double chance = getSuccessChance(weapon);
         int currentEnchantLevel = weapon.getEnchantLevel();
         if (currentEnchantLevel < this._maxEnchant || this.serverHasUnlimitedMax()) {
            if (!(Rnd.nextDouble() < chance) && weapon.getEnchantLevel() >= 4) {
               this.destroyFailedItem(weapon);
            } else {
               weapon.setEnchantLevel(currentEnchantLevel + 1);
               this._fakePlayer.broadcastCharInfo();
            }
         }
      }

      this._enchantIterations++;
      this.setBusyThinking(false);
   }

   private void destroyFailedItem(ItemInstance weapon) {
      this._fakePlayer.getInventory().destroyItem("Enchant", weapon, this._fakePlayer, null);
      this._fakePlayer.broadcastUserInfo();
      this._fakePlayer.setActiveEnchantItem(null);
   }

   private static double getSuccessChance(ItemInstance weapon) {
      double chance = 0.0;
      if (((Weapon)weapon.getItem()).isMagical()) {
         chance = weapon.getEnchantLevel() > 14 ? 30.0 : 30.0;
      } else {
         chance = weapon.getEnchantLevel() > 14 ? 30.0 : 30.0;
      }

      return chance;
   }

   private boolean serverHasUnlimitedMax() {
      return this._maxEnchant == 0;
   }

   private ItemInstance checkIfWeaponIsExistsEquipped(ItemInstance weapon) {
      if (weapon == null) {
         FakeHelpers.giveWeaponsByClass(this._fakePlayer, false);
         weapon = this._fakePlayer.getActiveWeaponInstance();
      }

      return weapon;
   }
}
