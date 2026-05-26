package phantom.ai;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import com.l2jmega.gameserver.data.SkillTable;
import com.l2jmega.gameserver.geoengine.GeoEngine;
import com.l2jmega.gameserver.model.WorldObject;
import com.l2jmega.gameserver.model.L2Skill;
import com.l2jmega.gameserver.model.ShotType;
import com.l2jmega.gameserver.model.actor.Creature;
import com.l2jmega.gameserver.model.actor.instance.GrandBoss;
import com.l2jmega.gameserver.model.actor.instance.Monster;
import com.l2jmega.gameserver.model.actor.instance.RaidBoss;
import com.l2jmega.gameserver.model.item.kind.Weapon;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.model.base.ClassId;
import com.l2jmega.gameserver.model.zone.ZoneId;
import com.l2jmega.commons.random.Rnd;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.helpers.FakeHelpers;
import phantom.model.BotSkill;
import phantom.model.HealingSpell;
import phantom.model.OffensiveSpell;
import phantom.model.SupportSpell;

public abstract class CombatAI extends FakePlayerAI {
   private static final long FULL_BUFF_REAPPLY_INTERVAL_MS = 15000L;
   private long _lastFullBuffReapplyAt;
   private long _lastLoadoutRepairAt = 0L;
   private static final long LOADOUT_REPAIR_INTERVAL_MS = 45000L;

   private static final int CLEANSE_SKILL_ID = 1409;

   public CombatAI(FakePlayer character) {
      super(character);
   }

   /**
    * Cleanse из selfSupport берёт текущую цель; на обычных мобах не кастуем — только рейд/эпик (GrandBoss).
    *
    * @return {@code true}, если cleanse допустим (нет цели, цель не моб, или рейд/гранд); {@code false} для обычного моба
    */
   private boolean isCleanseAllowedForCurrentTarget() {
      final WorldObject t = this._fakePlayer.getTarget();
      if (t == null) {
         return true;
      }
      if (!(t instanceof Monster)) {
         return true;
      }
      return t instanceof RaidBoss || t instanceof GrandBoss;
   }

   protected boolean tryAttackingUsingMageOffensiveSkill() {
      if (!(this._fakePlayer.getTarget() instanceof Creature)) {
         return false;
      }

      if (this._fakePlayer.isSpawnProtected()) {
         this._fakePlayer.setSpawnProtection(false);
      }

      final Creature target = (Creature) this._fakePlayer.getTarget();
      final List<OffensiveSpell> spellsOrdered = this.getOffensiveSpells()
         .stream()
         .sorted((o1, o2) -> Integer.compare(o1.getPriority(), o2.getPriority()))
         .collect(Collectors.toList());

      for (OffensiveSpell offensiveSpell : spellsOrdered) {
         final L2Skill skill = this._fakePlayer.getSkill(offensiveSpell.getSkillId());
         if (skill == null) {
            continue;
         }

         if (this._fakePlayer.checkUseMagicConditions(skill, true, false)) {
            this.castSpell(skill);
            return true;
         }
      }

      final int preferredRange = getPreferredMageFallbackRange();
      if (preferredRange > 0 && maybeMoveToPawn(target, preferredRange)) {
         return true;
      }
      // Keep mages in caster behavior: if no spell can be cast right now,
      // don't force melee auto-attack.
      return false;
   }

   protected int getPreferredMageFallbackRange() {
      int preferredRange = 0;
      for (OffensiveSpell offensiveSpell : this.getOffensiveSpells()) {
         final L2Skill skill = this._fakePlayer.getSkill(offensiveSpell.getSkillId());
         if (skill != null && skill.getCastRange() > 0) {
            preferredRange = Math.max(preferredRange, skill.getCastRange());
         }
      }
      return preferredRange > 0 ? Math.max(200, preferredRange - 40) : 600;
   }

   protected void tryAttackingUsingFighterOffensiveSkill() {
      if (this._fakePlayer.getTarget() != null && this._fakePlayer.getTarget() instanceof Creature) {
         if (this._fakePlayer.isSpawnProtected()) {
            this._fakePlayer.setSpawnProtection(false);
         }

         boolean skillUsed = false;
         if (Rnd.nextDouble() < this.changeOfUsingSkill() && this.getOffensiveSpells() != null && !this.getOffensiveSpells().isEmpty()) {
            L2Skill skill = this.getRandomAvaiableFighterSpellForTarget();
            if (skill != null) {
               this.castSpell(skill);
               skillUsed = true;
            }
         }
         // Если скилл не был применён, всегда инициировать автоатаку
         if (!skillUsed) {
            this._fakePlayer.forceAutoAttack((Creature)this._fakePlayer.getTarget());
         }
      }
   }

   private long _idleStartTime = 0;
   private long _lastFullBuffTopUpAt = 0L;
   private static final long FULL_BUFF_TOPUP_INTERVAL_MS = 120000L;

   @Override
   public void thinkAndAct() {
      this.handleDeath();

      if (this._fakePlayer.isFakePvp() && !this._fakePlayer.isInsideZone(ZoneId.TOWN) && !this._fakePlayer.isInsideZone(ZoneId.PEACE)) {
         WorldObject target = this._fakePlayer.getTarget();
         boolean hasTarget = target != null && (!(target instanceof Creature) || !((Creature)target).isDead());
         final boolean inActivePvpZone = this._fakePlayer.isInsideZone(ZoneId.CUSTOM) || this._fakePlayer.isInsideZone(ZoneId.PVP_CUSTOM);
         
         if (!hasTarget && !this._fakePlayer.isDead()) {
            if (this._idleStartTime == 0) {
               this._idleStartTime = System.currentTimeMillis();
            } else if (!inActivePvpZone && !isActiveSiegeParticipant() && System.currentTimeMillis() - this._idleStartTime > 30000) {
               this._fakePlayer.setFakePvp(false);
               this._fakePlayer.setFakeFarm(true);
               this._fakePlayer.assignDefaultAI();
               if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
                   this._fakePlayer.say("[DEBUG] Switched to Farm mode due to 30s inactivity.");
               }
               this._idleStartTime = 0;
            }
         } else {
            this._idleStartTime = 0;
         }
      } else {
         this._idleStartTime = 0;
      }

      ensureFullBuffsInPvp();
   }

   protected int getShotId() {
      int playerLevel = this._fakePlayer.getLevel();
      if (playerLevel < 20) {
         return this.getShotType() == ShotType.SOULSHOT ? 1835 : 3947;
      } else if (playerLevel >= 20 && playerLevel < 40) {
         return this.getShotType() == ShotType.SOULSHOT ? 1463 : 3948;
      } else if (playerLevel >= 40 && playerLevel < 52) {
         return this.getShotType() == ShotType.SOULSHOT ? 1464 : 3949;
      } else if (playerLevel >= 52 && playerLevel < 61) {
         return this.getShotType() == ShotType.SOULSHOT ? 1465 : 3950;
      } else if (playerLevel >= 61 && playerLevel < 76) {
         return this.getShotType() == ShotType.SOULSHOT ? 1466 : 3951;
      } else if (playerLevel >= 76) {
         if (this.getShotType() == ShotType.SOULSHOT) {
            return FakePlayerConfig.FAKE_PLAYER_SOULSHOT > 0 ? FakePlayerConfig.FAKE_PLAYER_SOULSHOT : 1467;
         }
         return FakePlayerConfig.FAKE_PLAYER_BLESSED_SOULSHOT > 0 ? FakePlayerConfig.FAKE_PLAYER_BLESSED_SOULSHOT : 3952;
      } else {
         return 0;
      }
   }

   protected int getArrowId() {
      final Weapon activeWeapon = this._fakePlayer.getActiveWeaponItem();
      if (activeWeapon != null) {
         return FakeHelpers.getArrowItemIdForWeapon(activeWeapon, this._fakePlayer.getLevel());
      }
      int playerLevel = this._fakePlayer.getLevel();
      if (playerLevel < 20) {
         return 17; // Wooden Arrow
      } else if (playerLevel < 40) {
         return 1341; // Bone Arrow
      } else if (playerLevel < 52) {
         return 1342; // Steel Arrow
      } else if (playerLevel < 61) {
         return 1343; // Silver Arrow
      } else if (playerLevel < 76) {
         return 1344; // Mithril Arrow
      } else {
         // Если FAKE_PLAYER_ARROW не задан или 0, используем Mithril Arrow
         return (FakePlayerConfig.FAKE_PLAYER_ARROW > 0) ? FakePlayerConfig.FAKE_PLAYER_ARROW : 1344;
      }
   }

   protected void handleShots() {
      ensureLoadoutIntegrity();
      handlePotions();
      final int shotId = this.getShotId();
      final int physicalShotId = resolvePhysicalShotId();
      final int magicalShotId = resolveMagicalShotId();

      ensureShotStock(shotId, 2500);
      ensureShotStock(physicalShotId, 1200);
      ensureShotStock(magicalShotId, 1200);

      if (shotId > 0 && !this._fakePlayer.getAutoSoulShot().contains(shotId)) {
         this._fakePlayer.addAutoSoulShot(shotId);
      }
      if (physicalShotId > 0 && !this._fakePlayer.getAutoSoulShot().contains(physicalShotId)) {
         this._fakePlayer.addAutoSoulShot(physicalShotId);
      }
      if (magicalShotId > 0 && !this._fakePlayer.getAutoSoulShot().contains(magicalShotId)) {
         this._fakePlayer.addAutoSoulShot(magicalShotId);
      }
      this._fakePlayer.rechargeShots(true, true);

      // Стрелы (Player.addItem — чтобы при луке вызвался checkAndEquipArrows)
      int arrowId = FakeHelpers.getArrowItemIdForWeapon(this._fakePlayer.getActiveWeaponItem(), this._fakePlayer.getLevel());
      if (arrowId > 0) {
         if (this._fakePlayer.getInventory().getItemByItemId(arrowId) != null) {
            if (this._fakePlayer.getInventory().getItemByItemId(arrowId).getCount() <= 100) {
               this._fakePlayer.addItem("", arrowId, 1000, null, false);
               if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) this._fakePlayer.say("[DEBUG] Купил стрелы id="+arrowId);
            }
         } else {
            this._fakePlayer.addItem("", arrowId, 1000, null, false);
            if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) this._fakePlayer.say("[DEBUG] Купил стрелы id="+arrowId);
         }
      }

      // Consumables: Spirit Ore, Soul Ore, Einhasad's Holy Water.
      ensureConsumable(3031, 500);  // Spirit Ore
      ensureConsumable(1785, 500);  // Soul Ore
      ensureConsumable(8874, 100);  // Einhasad's Holy Water
      if (this._fakePlayer.getClassId() == ClassId.SOULTAKER) {
         ensureConsumable(2508, 10000); // Cursed Bone
      }
   }

   private int resolvePhysicalShotId() {
      final Weapon activeWeapon = this._fakePlayer.getActiveWeaponItem();
      if (activeWeapon != null) {
         switch (activeWeapon.getCrystalType()) {
            case NONE:
               return 1835;
            case D:
               return 1463;
            case C:
               return 1464;
            case B:
               return 1465;
            case A:
               return 1466;
            case S:
               return FakePlayerConfig.FAKE_PLAYER_SOULSHOT > 0 ? FakePlayerConfig.FAKE_PLAYER_SOULSHOT : 1467;
            default:
               break;
         }
      }
      return FakePlayerConfig.FAKE_PLAYER_SOULSHOT > 0 ? FakePlayerConfig.FAKE_PLAYER_SOULSHOT : 1467;
   }

   private int resolveMagicalShotId() {
      final Weapon activeWeapon = this._fakePlayer.getActiveWeaponItem();
      if (activeWeapon != null) {
         switch (activeWeapon.getCrystalType()) {
            case NONE:
               return 3947;
            case D:
               return 3948;
            case C:
               return 3949;
            case B:
               return 3950;
            case A:
               return 3951;
            case S:
               return FakePlayerConfig.FAKE_PLAYER_BLESSED_SOULSHOT > 0 ? FakePlayerConfig.FAKE_PLAYER_BLESSED_SOULSHOT : 3952;
            default:
               break;
         }
      }
      return FakePlayerConfig.FAKE_PLAYER_BLESSED_SOULSHOT > 0 ? FakePlayerConfig.FAKE_PLAYER_BLESSED_SOULSHOT : 3952;
   }

   private void ensureShotStock(int shotId, int replenishAmount) {
      if (shotId <= 0 || replenishAmount <= 0) {
         return;
      }

      ItemInstance shot = this._fakePlayer.getInventory().getItemByItemId(shotId);
      if (shot == null || shot.getCount() <= 50) {
         this._fakePlayer.getInventory().addItem("FakeShot", shotId, replenishAmount, this._fakePlayer, null);
      }
   }

   private void ensureLoadoutIntegrity() {
      final long now = System.currentTimeMillis();
      if (now - _lastLoadoutRepairAt < LOADOUT_REPAIR_INTERVAL_MS) {
         return;
      }
      _lastLoadoutRepairAt = now;

      if (this._fakePlayer.isInOlympiadMode()) {
         if (isMissingCoreCombatSlot()) {
            FakeHelpers.ensureOlympiadLoadout(this._fakePlayer);
         }
      } else if (isMissingCoreCombatSlot()) {
         FakeHelpers.ensureCombatLoadout(this._fakePlayer);
      }
   }

   private boolean isMissingCoreCombatSlot() {
      final boolean hasChest = this._fakePlayer.getInventory().getPaperdollItemId(com.l2jmega.gameserver.model.itemcontainer.Inventory.PAPERDOLL_CHEST) > 0;
      final boolean hasLegs = this._fakePlayer.getInventory().getPaperdollItemId(com.l2jmega.gameserver.model.itemcontainer.Inventory.PAPERDOLL_LEGS) > 0;
      final boolean hasHead = this._fakePlayer.getInventory().getPaperdollItemId(com.l2jmega.gameserver.model.itemcontainer.Inventory.PAPERDOLL_HEAD) > 0;
      final boolean hasGloves = this._fakePlayer.getInventory().getPaperdollItemId(com.l2jmega.gameserver.model.itemcontainer.Inventory.PAPERDOLL_GLOVES) > 0;
      final boolean hasFeet = this._fakePlayer.getInventory().getPaperdollItemId(com.l2jmega.gameserver.model.itemcontainer.Inventory.PAPERDOLL_FEET) > 0;
      final boolean hasWeapon = this._fakePlayer.getInventory().getPaperdollItemId(com.l2jmega.gameserver.model.itemcontainer.Inventory.PAPERDOLL_RHAND) > 0;
      final boolean hasNeck = this._fakePlayer.getInventory().getPaperdollItemId(com.l2jmega.gameserver.model.itemcontainer.Inventory.PAPERDOLL_NECK) > 0;
      final boolean hasLear = this._fakePlayer.getInventory().getPaperdollItemId(com.l2jmega.gameserver.model.itemcontainer.Inventory.PAPERDOLL_LEAR) > 0;
      final boolean hasRear = this._fakePlayer.getInventory().getPaperdollItemId(com.l2jmega.gameserver.model.itemcontainer.Inventory.PAPERDOLL_REAR) > 0;
      final boolean hasLfinger = this._fakePlayer.getInventory().getPaperdollItemId(com.l2jmega.gameserver.model.itemcontainer.Inventory.PAPERDOLL_LFINGER) > 0;
      final boolean hasRfinger = this._fakePlayer.getInventory().getPaperdollItemId(com.l2jmega.gameserver.model.itemcontainer.Inventory.PAPERDOLL_RFINGER) > 0;

      // One-piece armor can occupy chest and legs.
      final boolean hasBodyArmor = hasChest && (hasLegs || this._fakePlayer.getInventory().getPaperdollItemByL2ItemId(com.l2jmega.gameserver.model.item.kind.Item.SLOT_CHEST) != null
         && this._fakePlayer.getInventory().getPaperdollItemByL2ItemId(com.l2jmega.gameserver.model.item.kind.Item.SLOT_CHEST).getItem().getBodyPart() == com.l2jmega.gameserver.model.item.kind.Item.SLOT_FULL_ARMOR);

      return !hasBodyArmor || !hasHead || !hasGloves || !hasFeet || !hasWeapon || !hasNeck || !hasLear || !hasRear || !hasLfinger || !hasRfinger;
   }

   private void ensureConsumable(int itemId, int replenishAmount) {
      ItemInstance item = this._fakePlayer.getInventory().getItemByItemId(itemId);
      if (item == null || item.getCount() <= 20) {
         this._fakePlayer.getInventory().addItem("FakeConsumable", itemId, replenishAmount, this._fakePlayer, null);
      }
   }

   protected void ensureFullBuffsInPvp() {
      if (!this._fakePlayer.isFakePvp() || this._fakePlayer.isDead()) {
         return;
      }

      final long now = System.currentTimeMillis();
      if (now - _lastFullBuffTopUpAt < FULL_BUFF_TOPUP_INTERVAL_MS) {
         return;
      }

      _lastFullBuffTopUpAt = now;
      FakeHelpers.giveBuffsByClass(this._fakePlayer);
   }

   public HealingSpell getRandomAvaiableHealingSpellForTarget() {
      if (this.getHealingSpells().isEmpty()) {
         return null;
      }
      List<HealingSpell> spellsOrdered = this.getHealingSpells()
         .stream()
         .sorted((o1, o2) -> Integer.compare(o1.getPriority(), o2.getPriority()))
         .collect(Collectors.toList());
      int skillListSize = spellsOrdered.size();
      BotSkill skill = this.waitAndPickAvailablePrioritisedSpell(spellsOrdered, skillListSize);
      return (HealingSpell)skill;
   }

   protected BotSkill getRandomAvaiableMageSpellForTarget() {
      List<OffensiveSpell> spellsOrdered = this.getOffensiveSpells()
         .stream()
         .sorted((o1, o2) -> Integer.compare(o1.getPriority(), o2.getPriority()))
         .collect(Collectors.toList());
      int skillListSize = spellsOrdered.size();
      return this.waitAndPickAvailablePrioritisedSpell(spellsOrdered, skillListSize);
   }

   private BotSkill waitAndPickAvailablePrioritisedSpell(List<? extends BotSkill> spellsOrdered, int skillListSize) {
      if (spellsOrdered != null && !spellsOrdered.isEmpty()) {
         int skillIndex = 0;
         BotSkill botSkill = spellsOrdered.get(skillIndex);
         WorldObject target = this._fakePlayer.getTarget();
         if (target != null && target instanceof Creature && !((Creature)target).isDead()) {
            this._fakePlayer.getCurrentSkill().setCtrlPressed(!target.isInsideZone(ZoneId.PEACE));
            L2Skill skill = this._fakePlayer.getSkill(botSkill.getSkillId());
            if (skill != null && skill.getCastRange() > 0) {
               target = this._fakePlayer.getTarget();
               if (target == null || !(target instanceof Creature) || ((Creature)target).isDead()) {
                  return null;
               }

               if (!GeoEngine.getInstance().canSeeTarget(this._fakePlayer, target)) {
                  if (target instanceof Creature && !((Creature)target).isDead()) {
                     this.moveToPawn(target, 100);
                  }

                  return null;
               }
            }

            while (!this._fakePlayer.checkUseMagicConditions(skill, true, false)) {
               this._isBusyThinking = true;
               if (!this._fakePlayer.isDead() && !this._fakePlayer.isOutOfControl()) {
                  if (skillIndex >= 0 && skillIndex < skillListSize) {
                     botSkill = spellsOrdered.get(skillIndex);
                     skill = this._fakePlayer.getSkill(botSkill.getSkillId());
                     skillIndex++;
                     continue;
                  }

                  return null;
               }

               return null;
            }

            return botSkill;
         }
         this._fakePlayer.getCurrentSkill().setCtrlPressed(false);
         return null;
      }
      return null;
   }

   protected L2Skill getRandomAvaiableFighterSpellForTarget() {
      List<OffensiveSpell> spellsOrdered = this.getOffensiveSpells()
         .stream()
         .sorted((o1, o2) -> Integer.compare(o1.getPriority(), o2.getPriority()))
         .collect(Collectors.toList());
      int skillIndex = 0;
      int skillListSize = spellsOrdered.size();
      L2Skill skill = this._fakePlayer.getSkill(spellsOrdered.get(skillIndex).getSkillId());
      if (this._fakePlayer.getTarget() != null) {
         this._fakePlayer.getCurrentSkill().setCtrlPressed(!this._fakePlayer.getTarget().isInsideZone(ZoneId.PEACE));
      } else {
         this._fakePlayer.getCurrentSkill().setCtrlPressed(false);
      }

      while (!this._fakePlayer.checkUseMagicConditions(skill, true, false)) {
         if (skillIndex < 0 || skillIndex >= skillListSize) {
            return null;
         }

         skill = this._fakePlayer.getSkill(spellsOrdered.get(skillIndex).getSkillId());
         skillIndex++;
      }

      if (!this._fakePlayer.checkUseMagicConditions(skill, true, false)) {
         this._fakePlayer.forceAutoAttack((Creature)this._fakePlayer.getTarget());
         return null;
      }
      return skill;
   }

   protected void handlePotions() {
      double hpMult = FakePlayerConfig.FAKE_VIRTUAL_POTION_HP_MULTIPLIER;
      double mpMult = FakePlayerConfig.FAKE_VIRTUAL_POTION_MP_MULTIPLIER;
      double cpMult = FakePlayerConfig.FAKE_VIRTUAL_POTION_CP_MULTIPLIER;

      double hpPercent = _fakePlayer.getCurrentHp() / _fakePlayer.getMaxHp();
      double mpPercent = _fakePlayer.getCurrentMp() / _fakePlayer.getMaxMp();
      double cpPercent = _fakePlayer.getCurrentCp() / _fakePlayer.getMaxCp();

      if (hpMult > 0 && hpPercent < 0.7) {
         double heal = _fakePlayer.getMaxHp() * (0.2 + Rnd.get(10) / 100.0) * hpMult;
         _fakePlayer.setCurrentHp(Math.min(_fakePlayer.getCurrentHp() + heal, _fakePlayer.getMaxHp()));
      }
      if (mpMult > 0 && mpPercent < 0.5) {
         double restore = _fakePlayer.getMaxMp() * (0.15 + Rnd.get(10) / 100.0) * mpMult;
         _fakePlayer.setCurrentMp(Math.min(_fakePlayer.getCurrentMp() + restore, _fakePlayer.getMaxMp()));
      }
      if (cpMult > 0 && cpPercent < 0.5) {
         double cpRestore = _fakePlayer.getMaxCp() * (0.2 + Rnd.get(10) / 100.0) * cpMult;
         _fakePlayer.setCurrentCp(Math.min(_fakePlayer.getCurrentCp() + cpRestore, _fakePlayer.getMaxCp()));
      }
   }

   protected void selfSupportBuffs() {
      final long now = System.currentTimeMillis();
      if (now - _lastFullBuffReapplyAt >= FULL_BUFF_REAPPLY_INTERVAL_MS) {
         if (phantom.helpers.FakeHelpers.reapplyMissingSchemeBuffs(this._fakePlayer)) {
            _lastFullBuffReapplyAt = now;
            return;
         }
         _lastFullBuffReapplyAt = now;
      }

      List<Integer> activeEffects = Arrays.stream(this._fakePlayer.getAllEffects()).map(x -> x.getSkill().getId()).collect(Collectors.toList());

      for (SupportSpell selfBuff : this.getSelfSupportSpells()) {
         if (!activeEffects.contains(selfBuff.getSkillId())) {
            L2Skill skill = SkillTable.getInstance().getInfo(selfBuff.getSkillId(), this._fakePlayer.getSkillLevel(selfBuff.getSkillId()));
            if (this._fakePlayer.checkUseMagicConditions(skill, true, false)) {
               switch (selfBuff.getCondition()) {
                  case LESSHPPERCENT:
                     if (Math.round(100.0 / this._fakePlayer.getMaxHp() * this._fakePlayer.getCurrentHp()) <= selfBuff.getConditionValue()) {
                        this.castSelfSpell(skill);
                     }
                     break;
                  case MISSINGCP:
                     if (this.getMissingCombatPoint() >= selfBuff.getConditionValue()) {
                        this.castSelfSpell(skill);
                     }
                     break;
                  case MISSINGMP:
                     if (this.getMissingMana() >= selfBuff.getConditionValue()) {
                        this.castSelfSpell(skill);
                     }
                     break;
                  case NONE:
                     if (selfBuff.getSkillId() == CLEANSE_SKILL_ID && !this.isCleanseAllowedForCurrentTarget()) {
                        break;
                     }
                     this.castSelfSpell(skill);
                     break;
                  default:
                     break;
               }
            }
         }
      }
   }

   private double getMissingCombatPoint() {
      return this._fakePlayer.getMaxCp() - this._fakePlayer.getCurrentCp();
   }

   private double getMissingMana() {
      return this._fakePlayer.getMaxMp() - this._fakePlayer.getCurrentMp();
   }

   protected double changeOfUsingSkill() {
      return 1.0;
   }

   protected abstract ShotType getShotType();

   protected abstract List<OffensiveSpell> getOffensiveSpells();

   protected abstract List<HealingSpell> getHealingSpells();

   protected abstract List<SupportSpell> getSelfSupportSpells();
}
