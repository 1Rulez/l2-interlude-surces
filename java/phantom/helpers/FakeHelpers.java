package phantom.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.l2jmega.Config;
import com.l2jmega.gameserver.data.CharTemplateTable;
import com.l2jmega.gameserver.data.ItemTable;
import com.l2jmega.gameserver.data.sql.ClanTable;
import com.l2jmega.gameserver.data.xml.HennaData;
import com.l2jmega.gameserver.data.SkillTable;
import com.l2jmega.gameserver.idfactory.IdFactory;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.pledge.Clan;
import com.l2jmega.gameserver.model.pledge.ClanMember;
import com.l2jmega.gameserver.model.L2Skill;
import com.l2jmega.gameserver.model.actor.Creature;
import com.l2jmega.gameserver.model.actor.appearance.PcAppearance;
import com.l2jmega.gameserver.model.actor.template.PlayerTemplate;
import com.l2jmega.gameserver.model.base.ClassId;
import com.l2jmega.gameserver.model.base.Sex;
import com.l2jmega.gameserver.model.base.Experience;
import com.l2jmega.gameserver.model.base.ClassRace;
import com.l2jmega.gameserver.model.item.kind.Armor;
import com.l2jmega.gameserver.model.item.kind.Item;
import com.l2jmega.gameserver.model.item.kind.Weapon;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.model.item.type.ArmorType;
import com.l2jmega.gameserver.model.item.type.CrystalType;
import com.l2jmega.gameserver.model.item.type.WeaponType;
import com.l2jmega.gameserver.model.itemcontainer.Inventory;
import com.l2jmega.gameserver.model.itemcontainer.PcInventory;
import com.l2jmega.commons.random.Rnd;
import phantom.FakePlayer;
import phantom.FakeAccountService;
import phantom.FakePlayerConfig;
import phantom.FakePlayerNameManager;
import phantom.ai.FakePlayerAI;
import phantom.ai.FakePlayerUtilsAI;
import phantom.ai.FallbackAI;
import phantom.ai.classes.AdventurerAI;
import phantom.ai.classes.ArchmageAI;
import phantom.ai.classes.CardinalAI;
import phantom.ai.classes.DominatorAI;
import phantom.ai.classes.DreadnoughtAI;
import phantom.ai.classes.DuelistAI;
import phantom.ai.classes.GhostHunterAI;
import phantom.ai.classes.GhostSentinelAI;
import phantom.ai.classes.GrandKhavatariAI;
import phantom.ai.classes.MoonlightSentinelAI;
import phantom.ai.classes.MysticMuseAI;
import phantom.ai.classes.PhoenixKnightAI;
import phantom.ai.classes.HellKnightAI;
import phantom.ai.classes.EvasTemplarAI;
import phantom.ai.classes.SaggitariusAI;
import phantom.ai.classes.SoultakerAI;
import phantom.ai.classes.StormScreamerAI;
import phantom.ai.classes.TitanAI;
import phantom.ai.classes.WindRiderAI;

public class FakeHelpers {
   private static final int NOBLESSE_BLESSING_SKILL_ID = 1323;

   public static Class<? extends Creature> getTestTargetClass() {
      return Creature.class;
   }

   public static int getTestTargetRange() {
      return FakePlayerConfig.FAKE_FARM_RADIUS > 0 ? FakePlayerConfig.FAKE_FARM_RADIUS : 1000;
   }

   public static FakePlayer createRandomTvTFakePlayer(String accountName) {
      String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();
      String playerTitle = FakePlayerNameManager.INSTANCE.getRandomTitleFromWordlist();
      ClassId classId = getThirdTvTAllowedClasses().get(Rnd.get(0, getThirdTvTAllowedClasses().size() - 1));
      PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
      PcAppearance app = getRandomAppearance(template.getRace());
      Player base = FakeAccountService.createPersistentFakeCharacter(accountName, playerName, classId, app.getSex(), app);
      int objectId = (base != null) ? base.getObjectId() : IdFactory.getInstance().getNextId();
      FakePlayer player = new FakePlayer(objectId, template, accountName, app);
      player.setName(playerName);
      player.setAccessLevel(0);
      player.setBaseClass(player.getClassId());
      if (!FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE.isEmpty()) {
         player.setTitle(FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE);
      } else {
         player.setTitle(playerTitle);
      }
      setLevel(player, 81);
      player.rewardSkills();
      getPotionSkills(player);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_HENNA) {
         giveHennaByClass(player);
      }

      giveArmorsByClass(player, true);
      giveWeaponsByClass(player, true);
      giveJewelsByClass(player, true);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY) {
         giveAcessoryByClass(player);
      }

      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS) {
         giveAcessoryByFirstClass(player);
      }

      giveBuffsByClass(player);
      player.heal();
      return player;
   }

   public static FakePlayer createRandomFakePlayer(String accountName) {
      ClassId classId = getThirdClasses().get(Rnd.get(0, getThirdClasses().size() - 1));
      return createFakePlayer(accountName, classId);
   }

   public static FakePlayer createFakePlayer(String accountName, ClassId classId) {
      String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();
      String playerTitle = FakePlayerNameManager.INSTANCE.getRandomTitleFromWordlist();
      PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
      PcAppearance app = getRandomAppearance(template.getRace());
      // Persist base character in DB so fake is visible in DB-based statistics.
      Player base = FakeAccountService.createPersistentFakeCharacter(accountName, playerName, classId, app.getSex(), app);
      int objectId = (base != null) ? base.getObjectId() : IdFactory.getInstance().getNextId();
      FakePlayer player = new FakePlayer(objectId, template, accountName, app);
      player.setName(playerName);
      player.setAccessLevel(0);
      player.setBaseClass(player.getClassId());
      if (!FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE.isEmpty()) {
         player.setTitle(FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE);
      } else {
         player.setTitle(playerTitle);
      }
      setLevel(player, 81);
      player.rewardSkills();
      getPotionSkills(player);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_HENNA) {
         giveHennaByClass(player);
      }

      giveArmorsByClass(player, true);
      giveWeaponsByClass(player, true);
      giveJewelsByClass(player, true);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY) {
         giveAcessoryByClass(player);
      }

      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS) {
         giveAcessoryByFirstClass(player);
      }

      giveBuffsByClass(player);
      player.heal();
      return player;
   }

   public static FakePlayer createRandomClanFakePlayer(String accountName) {
      return createRandomClanFakePlayer(accountName, null);
   }

   public static FakePlayer createRandomClanFakePlayer(String accountName, Clan preferredClan) {
      String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();
      String playerTitle = FakePlayerNameManager.INSTANCE.getRandomTitleFromWordlist();
      Clan clan = preferredClan != null ? preferredClan : ClanTable.getInstance().getClan(FakePlayerUtilsAI.getRandomClan());
      ClassId classId = chooseRandomClanClassId(clan);
      PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
      PcAppearance app = getRandomAppearance(template.getRace());
      Player base = FakeAccountService.createPersistentFakeCharacter(accountName, playerName, classId, app.getSex(), app);
      int objectId = (base != null) ? base.getObjectId() : IdFactory.getInstance().getNextId();
      FakePlayer player = new FakePlayer(objectId, template, accountName, app);
      player.setName(playerName);
      player.setAccessLevel(0);
      player.setBaseClass(player.getClassId());
      if (clan != null) {
         clan.addClanMember(player);
      }

      if (!FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE.isEmpty()) {
         player.setTitle(FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE);
      } else {
         player.setTitle(playerTitle);
      }
      setLevel(player, 81);
      player.rewardSkills();
      getPotionSkills(player);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_HENNA) {
         giveHennaByClass(player);
      }

      giveArmorsByClass(player, true);
      giveWeaponsByClass(player, true);
      giveJewelsByClass(player, true);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY) {
         giveAcessoryByClass(player);
      }

      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS) {
         giveAcessoryByFirstClass(player);
      }

      giveBuffsByClass(player);
      player.heal();
      return player;
   }

   private static ClassId chooseRandomClanClassId(Clan clan) {
      if (clan == null) {
         return getThirdClasses().get(Rnd.get(0, getThirdClasses().size() - 1));
      }

      final int nextClanSize = Math.max(1, clan.getMembersCount() + 1);
      final int tankTarget = Math.max(0, Math.min(nextClanSize, Math.round(nextClanSize * FakePlayerConfig.FAKE_CLAN_RANDOM_TANK_PERCENT / 100.0f)));
      final int archerTarget = Math.max(0, Math.min(nextClanSize, Math.round(nextClanSize * FakePlayerConfig.FAKE_CLAN_RANDOM_ARCHER_PERCENT / 100.0f)));
      final int mageTarget = Math.max(0, Math.min(nextClanSize, Math.round(nextClanSize * FakePlayerConfig.FAKE_CLAN_RANDOM_MAGE_PERCENT / 100.0f)));

      int tankCount = 0;
      int archerCount = 0;
      int mageCount = 0;

      for (ClanMember member : clan.getMembers()) {
         if (member == null) {
            continue;
         }

         final int memberClassId = member.getClassId();
         if (containsClassId(getTankerThirdClasses(), memberClassId)) {
            tankCount++;
         } else if (containsClassId(getArcherThirdClasses(), memberClassId)) {
            archerCount++;
         } else if (containsClassId(getNukerThirdClasses(), memberClassId)) {
            mageCount++;
         }
      }

      final int tankDeficit = Math.max(0, tankTarget - tankCount);
      final int archerDeficit = Math.max(0, archerTarget - archerCount);
      final int mageDeficit = Math.max(0, mageTarget - mageCount);
      final int totalDeficit = tankDeficit + archerDeficit + mageDeficit;

      if (totalDeficit > 0) {
         final int roll = Rnd.get(1, totalDeficit);
         int cumulative = tankDeficit;
         if (roll <= cumulative) {
            return getTankerThirdClasses().get(Rnd.get(0, getTankerThirdClasses().size() - 1));
         }

         cumulative += archerDeficit;
         if (roll <= cumulative) {
            return getArcherThirdClasses().get(Rnd.get(0, getArcherThirdClasses().size() - 1));
         }

         return getNukerThirdClasses().get(Rnd.get(0, getNukerThirdClasses().size() - 1));
      }

      final List<ClassId> remainderClasses = getClanRemainderThirdClasses();
      if (!remainderClasses.isEmpty()) {
         return remainderClasses.get(Rnd.get(0, remainderClasses.size() - 1));
      }

      return getThirdClasses().get(Rnd.get(0, getThirdClasses().size() - 1));
   }

   private static boolean containsClassId(List<ClassId> classes, int classId) {
      for (ClassId candidate : classes) {
         if (candidate.getId() == classId) {
            return true;
         }
      }
      return false;
   }

   private static List<ClassId> getClanRemainderThirdClasses() {
      final List<ClassId> remainder = new ArrayList<>();
      for (ClassId classId : getThirdClasses()) {
         if (containsClassId(getTankerThirdClasses(), classId.getId())) {
            continue;
         }
         if (containsClassId(getArcherThirdClasses(), classId.getId())) {
            continue;
         }
         if (containsClassId(getNukerThirdClasses(), classId.getId())) {
            continue;
         }
         remainder.add(classId);
      }
      return remainder;
   }

   public static FakePlayer createArcherFakePlayer(String accountName) {
      String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();
      String playerTitle = FakePlayerNameManager.INSTANCE.getRandomTitleFromWordlist();
      ClassId classId = getArcherThirdClasses().get(Rnd.get(0, getArcherThirdClasses().size() - 1));
      PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
      PcAppearance app = getRandomAppearance(template.getRace());
      Player base = FakeAccountService.createPersistentFakeCharacter(accountName, playerName, classId, app.getSex(), app);
      int objectId = (base != null) ? base.getObjectId() : IdFactory.getInstance().getNextId();
      FakePlayer player = new FakePlayer(objectId, template, accountName, app);
      player.setName(playerName);
      player.setAccessLevel(0);
      player.setBaseClass(player.getClassId());
      if (!FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE.isEmpty()) {
         player.setTitle(FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE);
      } else {
         player.setTitle(playerTitle);
      }
      setLevel(player, 81);
      player.rewardSkills();
      getPotionSkills(player);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_HENNA) {
         giveHennaByClass(player);
      }

      giveArmorsByClass(player, true);
      giveWeaponsByClass(player, true);
      giveJewelsByClass(player, true);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY) {
         giveAcessoryByClass(player);
      }

      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS) {
         giveAcessoryByFirstClass(player);
      }

      giveBuffsByClass(player);
      player.heal();
      return player;
   }

   public static FakePlayer createArcherClanFakePlayer(String accountName) {
      String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();
      String playerTitle = FakePlayerNameManager.INSTANCE.getRandomTitleFromWordlist();
      ClassId classId = getArcherThirdClasses().get(Rnd.get(0, getArcherThirdClasses().size() - 1));
      PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
      PcAppearance app = getRandomAppearance(template.getRace());
      Clan clan = ClanTable.getInstance().getClan(FakePlayerUtilsAI.getRandomClan());
      Player base = FakeAccountService.createPersistentFakeCharacter(accountName, playerName, classId, app.getSex(), app);
      int objectId = (base != null) ? base.getObjectId() : IdFactory.getInstance().getNextId();
      FakePlayer player = new FakePlayer(objectId, template, accountName, app);
      player.setName(playerName);
      player.setAccessLevel(0);
      player.setBaseClass(player.getClassId());
      if (clan != null) {
         clan.addClanMember(player);
      }

      if (!FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE.isEmpty()) {
         player.setTitle(FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE);
      } else {
         player.setTitle(playerTitle);
      }
      setLevel(player, 81);
      player.rewardSkills();
      getPotionSkills(player);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_HENNA) {
         giveHennaByClass(player);
      }

      giveArmorsByClass(player, true);
      giveWeaponsByClass(player, true);
      giveJewelsByClass(player, true);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY) {
         giveAcessoryByClass(player);
      }

      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS) {
         giveAcessoryByFirstClass(player);
      }

      giveBuffsByClass(player);
      player.heal();
      return player;
   }

   public static FakePlayer createNukerFakePlayer(String accountName) {
      String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();
      String playerTitle = FakePlayerNameManager.INSTANCE.getRandomTitleFromWordlist();
      ClassId classId = getNukerThirdClasses().get(Rnd.get(0, getNukerThirdClasses().size() - 1));
      PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
      PcAppearance app = getRandomAppearance(template.getRace());
      Player base = FakeAccountService.createPersistentFakeCharacter(accountName, playerName, classId, app.getSex(), app);
      int objectId = (base != null) ? base.getObjectId() : IdFactory.getInstance().getNextId();
      FakePlayer player = new FakePlayer(objectId, template, accountName, app);
      player.setName(playerName);
      player.setAccessLevel(0);
      player.setBaseClass(player.getClassId());
      if (!FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE.isEmpty()) {
         player.setTitle(FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE);
      } else {
         player.setTitle(playerTitle);
      }
      setLevel(player, 81);
      player.rewardSkills();
      getPotionSkills(player);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_HENNA) {
         giveHennaByClass(player);
      }

      giveArmorsByClass(player, true);
      giveWeaponsByClass(player, true);
      giveJewelsByClass(player, true);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY) {
         giveAcessoryByClass(player);
      }

      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS) {
         giveAcessoryByFirstClass(player);
      }

      giveBuffsByClass(player);
      player.heal();
      return player;
   }

   public static FakePlayer createNukerClanFakePlayer(String accountName) {
      String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();
      String playerTitle = FakePlayerNameManager.INSTANCE.getRandomTitleFromWordlist();
      ClassId classId = getNukerThirdClasses().get(Rnd.get(0, getNukerThirdClasses().size() - 1));
      PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
      PcAppearance app = getRandomAppearance(template.getRace());
      Clan clan = ClanTable.getInstance().getClan(FakePlayerUtilsAI.getRandomClan());
      Player base = FakeAccountService.createPersistentFakeCharacter(accountName, playerName, classId, app.getSex(), app);
      int objectId = (base != null) ? base.getObjectId() : IdFactory.getInstance().getNextId();
      FakePlayer player = new FakePlayer(objectId, template, accountName, app);
      player.setName(playerName);
      player.setAccessLevel(0);
      player.setBaseClass(player.getClassId());
      if (clan != null) {
         clan.addClanMember(player);
      }

      if (!FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE.isEmpty()) {
         player.setTitle(FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE);
      } else {
         player.setTitle(playerTitle);
      }
      setLevel(player, 81);
      player.rewardSkills();
      getPotionSkills(player);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_HENNA) {
         giveHennaByClass(player);
      }

      giveArmorsByClass(player, true);
      giveWeaponsByClass(player, true);
      giveJewelsByClass(player, true);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY) {
         giveAcessoryByClass(player);
      }

      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS) {
         giveAcessoryByFirstClass(player);
      }

      giveBuffsByClass(player);
      player.heal();
      return player;
   }

   public static FakePlayer createWarriorFakePlayer(String accountName) {
      String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();
      String playerTitle = FakePlayerNameManager.INSTANCE.getRandomTitleFromWordlist();
      ClassId classId = getWarriorThirdClasses().get(Rnd.get(0, getWarriorThirdClasses().size() - 1));
      PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
      PcAppearance app = getRandomAppearance(template.getRace());
      Player base = FakeAccountService.createPersistentFakeCharacter(accountName, playerName, classId, app.getSex(), app);
      int objectId = (base != null) ? base.getObjectId() : IdFactory.getInstance().getNextId();
      FakePlayer player = new FakePlayer(objectId, template, accountName, app);
      player.setName(playerName);
      player.setAccessLevel(0);
      player.setBaseClass(player.getClassId());
      if (!FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE.isEmpty()) {
         player.setTitle(FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE);
      } else {
         player.setTitle(playerTitle);
      }
      setLevel(player, 81);
      player.rewardSkills();
      getPotionSkills(player);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_HENNA) {
         giveHennaByClass(player);
      }

      giveArmorsByClass(player, true);
      giveWeaponsByClass(player, true);
      giveJewelsByClass(player, true);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY) {
         giveAcessoryByClass(player);
      }

      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS) {
         giveAcessoryByFirstClass(player);
      }

      giveBuffsByClass(player);
      player.heal();
      return player;
   }

   public static FakePlayer createWarriorClanFakePlayer(String accountName) {
      String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();
      String playerTitle = FakePlayerNameManager.INSTANCE.getRandomTitleFromWordlist();
      ClassId classId = getWarriorThirdClasses().get(Rnd.get(0, getWarriorThirdClasses().size() - 1));
      PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
      PcAppearance app = getRandomAppearance(template.getRace());
      Clan clan = ClanTable.getInstance().getClan(FakePlayerUtilsAI.getRandomClan());
      Player base = FakeAccountService.createPersistentFakeCharacter(accountName, playerName, classId, app.getSex(), app);
      int objectId = (base != null) ? base.getObjectId() : IdFactory.getInstance().getNextId();
      FakePlayer player = new FakePlayer(objectId, template, accountName, app);
      player.setName(playerName);
      player.setAccessLevel(0);
      player.setBaseClass(player.getClassId());
      if (clan != null) {
         clan.addClanMember(player);
      }

      if (!FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE.isEmpty()) {
         player.setTitle(FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE);
      } else {
         player.setTitle(playerTitle);
      }
      setLevel(player, 81);
      player.rewardSkills();
      getPotionSkills(player);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_HENNA) {
         giveHennaByClass(player);
      }

      giveArmorsByClass(player, true);
      giveWeaponsByClass(player, true);
      giveJewelsByClass(player, true);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY) {
         giveAcessoryByClass(player);
      }

      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS) {
         giveAcessoryByFirstClass(player);
      }

      giveBuffsByClass(player);
      player.heal();
      return player;
   }

   public static FakePlayer createDaggerFakePlayer(String accountName) {
      String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();
      String playerTitle = FakePlayerNameManager.INSTANCE.getRandomTitleFromWordlist();
      ClassId classId = getDaggerThirdClasses().get(Rnd.get(0, getDaggerThirdClasses().size() - 1));
      PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
      PcAppearance app = getRandomAppearance(template.getRace());
      Player base = FakeAccountService.createPersistentFakeCharacter(accountName, playerName, classId, app.getSex(), app);
      int objectId = (base != null) ? base.getObjectId() : IdFactory.getInstance().getNextId();
      FakePlayer player = new FakePlayer(objectId, template, accountName, app);
      player.setName(playerName);
      player.setAccessLevel(0);
      player.setBaseClass(player.getClassId());
      if (!FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE.isEmpty()) {
         player.setTitle(FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE);
      } else {
         player.setTitle(playerTitle);
      }
      setLevel(player, 81);
      player.rewardSkills();
      getPotionSkills(player);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_HENNA) {
         giveHennaByClass(player);
      }

      giveArmorsByClass(player, true);
      giveWeaponsByClass(player, true);
      giveJewelsByClass(player, true);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY) {
         giveAcessoryByClass(player);
      }

      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS) {
         giveAcessoryByFirstClass(player);
      }

      giveBuffsByClass(player);
      player.heal();
      return player;
   }

   public static FakePlayer createDaggerClanFakePlayer(String accountName) {
      String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();
      String playerTitle = FakePlayerNameManager.INSTANCE.getRandomTitleFromWordlist();
      ClassId classId = getDaggerThirdClasses().get(Rnd.get(0, getDaggerThirdClasses().size() - 1));
      PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
      PcAppearance app = getRandomAppearance(template.getRace());
      Clan clan = ClanTable.getInstance().getClan(FakePlayerUtilsAI.getRandomClan());
      Player base = FakeAccountService.createPersistentFakeCharacter(accountName, playerName, classId, app.getSex(), app);
      int objectId = (base != null) ? base.getObjectId() : IdFactory.getInstance().getNextId();
      FakePlayer player = new FakePlayer(objectId, template, accountName, app);
      player.setName(playerName);
      player.setAccessLevel(0);
      player.setBaseClass(player.getClassId());
      if (clan != null) {
         clan.addClanMember(player);
      }

      if (!FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE.isEmpty()) {
         player.setTitle(FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE);
      } else {
         player.setTitle(playerTitle);
      }
      setLevel(player, 81);
      player.rewardSkills();
      getPotionSkills(player);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_HENNA) {
         giveHennaByClass(player);
      }

      giveArmorsByClass(player, true);
      giveWeaponsByClass(player, true);
      giveJewelsByClass(player, true);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY) {
         giveAcessoryByClass(player);
      }

      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS) {
         giveAcessoryByFirstClass(player);
      }

      giveBuffsByClass(player);
      player.heal();
      return player;
   }

   public static FakePlayer createTankerFakePlayer(String accountName) {
      String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();
      String playerTitle = FakePlayerNameManager.INSTANCE.getRandomTitleFromWordlist();
      ClassId classId = getTankerThirdClasses().get(Rnd.get(0, getTankerThirdClasses().size() - 1));
      PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
      PcAppearance app = getRandomAppearance(template.getRace());
      Player base = FakeAccountService.createPersistentFakeCharacter(accountName, playerName, classId, app.getSex(), app);
      int objectId = (base != null) ? base.getObjectId() : IdFactory.getInstance().getNextId();
      FakePlayer player = new FakePlayer(objectId, template, accountName, app);
      player.setName(playerName);
      player.setAccessLevel(0);
      player.setBaseClass(player.getClassId());
      Clan clan = ClanTable.getInstance().getClan(FakePlayerUtilsAI.getRandomClan());
      if (clan != null) {
         player.setClan(clan);
         clan.addClanMember(player);
      }

      if (!FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE.isEmpty()) {
         player.setTitle(FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE);
      } else {
         player.setTitle(playerTitle);
      }
      setLevel(player, 81);
      player.rewardSkills();
      getPotionSkills(player);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_HENNA) {
         giveHennaByClass(player);
      }

      giveArmorsByClass(player, true);
      giveWeaponsByClass(player, true);
      giveJewelsByClass(player, true);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY) {
         giveAcessoryByFirstClass(player);
      }

      giveBuffsByClass(player);
      player.heal();
      return player;
   }

   public static FakePlayer createTankerClanFakePlayer(String accountName) {
      String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();
      String playerTitle = FakePlayerNameManager.INSTANCE.getRandomTitleFromWordlist();
      ClassId classId = getTankerThirdClasses().get(Rnd.get(0, getTankerThirdClasses().size() - 1));
      PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
      PcAppearance app = getRandomAppearance(template.getRace());
      Clan clan = ClanTable.getInstance().getClan(FakePlayerUtilsAI.getRandomClan());
      Player base = FakeAccountService.createPersistentFakeCharacter(accountName, playerName, classId, app.getSex(), app);
      int objectId = (base != null) ? base.getObjectId() : IdFactory.getInstance().getNextId();
      FakePlayer player = new FakePlayer(objectId, template, accountName, app);
      player.setName(playerName);
      player.setAccessLevel(0);
      player.setBaseClass(player.getClassId());
      if (clan != null) {
         clan.addClanMember(player);
      }

      if (!FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE.isEmpty()) {
         player.setTitle(FakePlayerConfig.FAKE_PLAYER_FIXED_TITLE);
      } else {
         player.setTitle(playerTitle);
      }
      setLevel(player, 81);
      player.rewardSkills();
      getPotionSkills(player);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_HENNA) {
         giveHennaByClass(player);
      }

      giveArmorsByClass(player, true);
      giveWeaponsByClass(player, true);
      giveJewelsByClass(player, true);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY) {
         giveAcessoryByClass(player);
      }

      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS) {
         giveAcessoryByFirstClass(player);
      }

      giveBuffsByClass(player);
      player.heal();
      return player;
   }

   public static void giveArmorsByClass(FakePlayer player, boolean randomlyEnchant) {
      List<Integer> itemIds;
      itemIds = new ArrayList<>();
      label38:
      switch (player.getClassId()) {
         case ARCHMAGE:
         case SOULTAKER:
         case HIEROPHANT:
         case ARCANA_LORD:
         case CARDINAL:
         case MYSTIC_MUSE:
         case ELEMENTAL_MASTER:
         case EVAS_SAINT:
         case STORM_SCREAMER:
         case SPECTRAL_MASTER:
         case SHILLIEN_SAINT:
         case DOMINATOR:
         case DOOMCRYER:
            int randomRobe = Rnd.get(4);
            switch (randomRobe) {
               case 0:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_ROB_ARMOR_1;
                  break label38;
               case 1:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_ROB_ARMOR_2;
                  break label38;
               case 2:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_ROB_ARMOR_3;
                  break label38;
               case 3:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_ROB_ARMOR_4;
                  break label38;
               default:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_ROB_ARMOR_1;
                  break label38;
            }
         case DUELIST:
         case DREADNOUGHT:
         case PHOENIX_KNIGHT:
         case SWORD_MUSE:
         case HELL_KNIGHT:
         case SPECTRAL_DANCER:
         case EVAS_TEMPLAR:
         case SHILLIEN_TEMPLAR:
         case SHILLIEN_KNIGHT:
         case TITAN:
         case MAESTRO:
         case FORTUNE_SEEKER:
            int randomHeavy = Rnd.get(4);
            switch (randomHeavy) {
               case 0:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_HEAVY_ARMOR_1;
                  break label38;
               case 1:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_HEAVY_ARMOR_2;
                  break label38;
               case 2:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_HEAVY_ARMOR_3;
                  break label38;
               case 3:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_HEAVY_ARMOR_4;
                  break label38;
               default:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_HEAVY_ARMOR_1;
                  break label38;
            }
         case SAGGITARIUS:
         case ADVENTURER:
         case WIND_RIDER:
         case MOONLIGHT_SENTINEL:
         case GHOST_HUNTER:
         case GHOST_SENTINEL:
         case GRAND_KHAVATARI:
            int randomLight = Rnd.get(4);
            switch (randomLight) {
               case 0:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_LIGHT_ARMOR_1;
                  break;
               case 1:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_LIGHT_ARMOR_2;
                  break;
               case 2:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_LIGHT_ARMOR_3;
                  break;
               case 3:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_LIGHT_ARMOR_4;
                  break;
               default:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_LIGHT_ARMOR_1;
            }
            break;
         default:
            break;
      }

      for (int id : itemIds) {
         if (id <= 0) {
            continue;
         }
         player.getInventory().addItem("Armors", id, 1, player, null);
         ItemInstance item = player.getInventory().getItemByItemId(id);
         if (item == null) {
            continue;
         }
         if (randomlyEnchant) {
            item.setEnchantLevel(Rnd.get(FakePlayerConfig.MIN_ENCHANT_ARMOR, FakePlayerConfig.MAX_ENCHANT_ARMOR));
         }

         player.getInventory().equipItemAndRecord(item);
         player.getInventory().reloadEquippedItems();
         player.broadcastCharInfo();
      }

      ensureFeetEquipped(player);
      player.getInventory().reloadEquippedItems();
   }

   public static void giveWeaponsByClass(FakePlayer player, boolean randomlyEnchant) {
      List<Integer> itemIds = new ArrayList<>();
      switch (player.getClassId()) {
         case ARCHMAGE:
         case SOULTAKER:
         case HIEROPHANT:
         case ARCANA_LORD:
         case CARDINAL:
         case MYSTIC_MUSE:
         case ELEMENTAL_MASTER:
         case EVAS_SAINT:
         case STORM_SCREAMER:
         case SPECTRAL_MASTER:
         case SHILLIEN_SAINT:
         case DOMINATOR:
         case DOOMCRYER:
            int magicId = getRandomMagicWeapon();
            if (magicId <= 0)
               magicId = getRandomSword();
            if (magicId <= 0)
               magicId = 81;
            itemIds = Arrays.asList(magicId, getRandomShield());
            break;
         case DUELIST:
         case SPECTRAL_DANCER:
            itemIds = Arrays.asList(getRandomDualSword());
            break;
         case DREADNOUGHT:
            itemIds = Arrays.asList(getRandomSpear());
            break;
         case PHOENIX_KNIGHT:
         case SWORD_MUSE:
         case HELL_KNIGHT:
         case EVAS_TEMPLAR:
         case SHILLIEN_KNIGHT:
         case SHILLIEN_TEMPLAR:
            itemIds = Arrays.asList(getRandomSword(), getRandomShield());
            break;
         default:
            itemIds = Arrays.asList(getRandomSword(), getRandomShield());
            break;
         case TITAN:
            itemIds = Arrays.asList(getRandomBigSword());
            break;
         case MAESTRO:
         case FORTUNE_SEEKER:
            itemIds = Arrays.asList(getRandomSword(), getRandomShield());
            break;
         case SAGGITARIUS:
         case MOONLIGHT_SENTINEL:
         case GHOST_SENTINEL:
            itemIds = Arrays.asList(getRandomBow());
            break;
         case ADVENTURER:
         case WIND_RIDER:
         case GHOST_HUNTER:
            itemIds = Arrays.asList(getRandomDagger());
            break;
         case GRAND_KHAVATARI:
            itemIds = Arrays.asList(getRandomFist());
      }

      for (int id : itemIds) {
         if (id <= 0) {
            continue;
         }
         player.getInventory().addItem("Weapon", id, 1, player, null);
         ItemInstance item = player.getInventory().getItemByItemId(id);
         if (item == null) {
            continue;
         }
         if (randomlyEnchant) {
            item.setEnchantLevel(Rnd.get(FakePlayerConfig.MIN_ENCHANT_WEAPON, FakePlayerConfig.MAX_ENCHANT_WEAPON));
         }

         player.getInventory().equipItemAndRecord(item);
         player.getInventory().reloadEquippedItems();
      }
   }

   public static void giveJewelsByClass(FakePlayer player, boolean randomlyEnchant) {
      List<int[]> itemIds = new ArrayList<>();
      switch (player.getClassId()) {
         case ARCHMAGE:
         case SOULTAKER:
         case HIEROPHANT:
         case ARCANA_LORD:
         case CARDINAL:
         case MYSTIC_MUSE:
         case ELEMENTAL_MASTER:
         case EVAS_SAINT:
         case STORM_SCREAMER:
         case SPECTRAL_MASTER:
         case SHILLIEN_SAINT:
         case DOMINATOR:
         case DOOMCRYER:
         case DUELIST:
         case DREADNOUGHT:
         case PHOENIX_KNIGHT:
         case SWORD_MUSE:
         case HELL_KNIGHT:
         case SPECTRAL_DANCER:
         case EVAS_TEMPLAR:
         case TITAN:
         case MAESTRO:
         case SAGGITARIUS:
         case ADVENTURER:
         case WIND_RIDER:
         case MOONLIGHT_SENTINEL:
         case GHOST_HUNTER:
         case GHOST_SENTINEL:
         case FORTUNE_SEEKER:
         case GRAND_KHAVATARI:
         case SHILLIEN_KNIGHT:
         case SHILLIEN_TEMPLAR:
            int randomJewels = Rnd.get(2);
            switch (randomJewels) {
               case 0:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_JEWELS_1;
                  break;
               case 1:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_JEWELS_2;
                  break;
               default:
                  itemIds = FakePlayerConfig.LIST_PHANTOM_JEWELS_1;
               }
         default:
            for (int[] id : itemIds) {
               if (id[0] <= 0) {
                  continue;
               }
               ItemInstance item = player.getInventory().addItem("Jewels", id[0], id[1], player, null);
               if (item == null) {
                  continue;
               }
               if (randomlyEnchant) {
                  item.setEnchantLevel(Rnd.get(FakePlayerConfig.MIN_ENCHANT_JEWEL, FakePlayerConfig.MAX_ENCHANT_JEWEL));
               }

               player.getInventory().equipItemAndRecord(item);
               player.getInventory().reloadEquippedItems();
            }
      }

      ensureCompleteClassLoadout(player, randomlyEnchant);
   }

   public static void giveAcessoryByClass(FakePlayer player) {
      List<Integer> itemIds = new ArrayList<>();
      switch (player.getClassId()) {
         case ARCHMAGE:
         case SOULTAKER:
         case HIEROPHANT:
         case ARCANA_LORD:
         case CARDINAL:
         case MYSTIC_MUSE:
         case ELEMENTAL_MASTER:
         case EVAS_SAINT:
         case STORM_SCREAMER:
         case SPECTRAL_MASTER:
         case SHILLIEN_SAINT:
         case DOMINATOR:
         case DOOMCRYER:
         case DUELIST:
         case DREADNOUGHT:
         case PHOENIX_KNIGHT:
         case SWORD_MUSE:
         case HELL_KNIGHT:
         case SPECTRAL_DANCER:
         case EVAS_TEMPLAR:
         case TITAN:
         case MAESTRO:
         case SAGGITARIUS:
         case ADVENTURER:
         case WIND_RIDER:
         case MOONLIGHT_SENTINEL:
         case GHOST_HUNTER:
         case GHOST_SENTINEL:
         case FORTUNE_SEEKER:
         case GRAND_KHAVATARI:
         case SHILLIEN_KNIGHT:
         case SHILLIEN_TEMPLAR:
            itemIds = Arrays.asList(getRandomAcessory());
         default:
            for (int id : itemIds) {
               if (id <= 0) {
                  continue;
               }
               player.getInventory().addItem("Accessory", id, 1, player, null);
               ItemInstance item = player.getInventory().getItemByItemId(id);
               if (item == null) {
                  continue;
               }
               player.getInventory().equipItemAndRecord(item);
               player.getInventory().reloadEquippedItems();
            }
      }
   }

   public static void giveAcessoryByFirstClass(FakePlayer player) {
      int itemId = getRandomAcessory();
      if (itemId > 0) {
         ItemInstance item = player.getInventory().addItem("Accessory", itemId, 1, player, null);
         if (item == null) {
            System.out.println("[FakeHelpers] Failed to give accessory: player=" + player.getName() + ", classId=" + player.getClassId() + ", itemId=" + itemId);
         }
         player.getInventory().equipItemAndRecord(item);
         player.getInventory().reloadEquippedItems();
      }
   }

   public static void giveHennaByClass(FakePlayer player) {
      List<Integer> hennaList = new ArrayList<>();
      switch (player.getClassId()) {
         case ARCHMAGE:
         case SOULTAKER:
         case MYSTIC_MUSE:
         case STORM_SCREAMER:
            hennaList = FakePlayerConfig.NUKER_HENNA_LIST;
         case HIEROPHANT:
         case ARCANA_LORD:
         case CARDINAL:
         case ELEMENTAL_MASTER:
         case EVAS_SAINT:
         case SPECTRAL_MASTER:
         case SHILLIEN_SAINT:
         case DOMINATOR:
         case DOOMCRYER:
         case DREADNOUGHT:
         case SWORD_MUSE:
         case HELL_KNIGHT:
             hennaList = FakePlayerConfig.TANKER_HENNA_LIST;
             break;
         case SPECTRAL_DANCER:
         case EVAS_TEMPLAR:
             hennaList = FakePlayerConfig.TANKER_HENNA_LIST;
             break;
         case SHILLIEN_TEMPLAR:
         case TITAN:
         case MAESTRO:
         case WIND_RIDER:
         case GHOST_HUNTER:
         case FORTUNE_SEEKER:
         default:
            break;
         case DUELIST:
         case GRAND_KHAVATARI:
            hennaList = FakePlayerConfig.WARRIOR_HENNA_LIST;
            break;
         case PHOENIX_KNIGHT:
            hennaList = FakePlayerConfig.TANKER_HENNA_LIST;
            break;
         case SAGGITARIUS:
         case MOONLIGHT_SENTINEL:
         case GHOST_SENTINEL:
            hennaList = FakePlayerConfig.ARCHER_HENNA_LIST;
            break;
         case ADVENTURER:
            hennaList = FakePlayerConfig.DAGGER_HENNA_LIST;
      }

      for (Integer hennaId : hennaList) {
         if (hennaId > 0) {
            player.addHenna(HennaData.getInstance().getHenna(hennaId));
         }
      }
   }

   public static List<ClassId> getThirdTvTAllowedClasses() {
      List<ClassId> classes = new ArrayList<>();
      classes.add(ClassId.SAGGITARIUS);
      classes.add(ClassId.ARCHMAGE);
      classes.add(ClassId.SOULTAKER);
      classes.add(ClassId.MYSTIC_MUSE);
      classes.add(ClassId.STORM_SCREAMER);
      classes.add(ClassId.MOONLIGHT_SENTINEL);
      classes.add(ClassId.GHOST_SENTINEL);
      classes.add(ClassId.DOMINATOR);
      classes.add(ClassId.DUELIST);
      classes.add(ClassId.GRAND_KHAVATARI);
      classes.add(ClassId.ADVENTURER);
      classes.add(ClassId.PHOENIX_KNIGHT);
      classes.add(ClassId.HELL_KNIGHT);
      classes.add(ClassId.EVAS_TEMPLAR);
       return classes;
   }

   public static List<ClassId> getThirdClasses() {
      List<ClassId> classes = new ArrayList<>();
      classes.add(ClassId.SAGGITARIUS);
      classes.add(ClassId.ARCHMAGE);
      classes.add(ClassId.SOULTAKER);
      classes.add(ClassId.MYSTIC_MUSE);
      classes.add(ClassId.STORM_SCREAMER);
      classes.add(ClassId.MOONLIGHT_SENTINEL);
      classes.add(ClassId.GHOST_SENTINEL);
      classes.add(ClassId.ADVENTURER);
      classes.add(ClassId.WIND_RIDER);
      classes.add(ClassId.DOMINATOR);
      classes.add(ClassId.TITAN);
      classes.add(ClassId.CARDINAL);
      classes.add(ClassId.DUELIST);
      classes.add(ClassId.GRAND_KHAVATARI);
      classes.add(ClassId.DREADNOUGHT);
      classes.add(ClassId.PHOENIX_KNIGHT);
      classes.add(ClassId.HELL_KNIGHT);
      classes.add(ClassId.EVAS_TEMPLAR);
       return classes;
   }

   public static List<ClassId> getArcherThirdClasses() {
      List<ClassId> classes = new ArrayList<>();
      classes.add(ClassId.SAGGITARIUS);
      classes.add(ClassId.MOONLIGHT_SENTINEL);
      classes.add(ClassId.GHOST_SENTINEL);
      return classes;
   }

   public static List<ClassId> getNukerThirdClasses() {
      List<ClassId> classes = new ArrayList<>();
      classes.add(ClassId.ARCHMAGE);
      classes.add(ClassId.SOULTAKER);
      classes.add(ClassId.MYSTIC_MUSE);
      classes.add(ClassId.STORM_SCREAMER);
      classes.add(ClassId.DOMINATOR);
      return classes;
   }

   public static List<ClassId> getWarriorThirdClasses() {
      List<ClassId> classes = new ArrayList<>();
      classes.add(ClassId.GRAND_KHAVATARI);
      classes.add(ClassId.DUELIST);
      classes.add(ClassId.TITAN);
      return classes;
   }

   public static List<ClassId> getDaggerThirdClasses() {
      List<ClassId> classes = new ArrayList<>();
      classes.add(ClassId.ADVENTURER);
      classes.add(ClassId.GHOST_HUNTER);
      classes.add(ClassId.WIND_RIDER);
      return classes;
   }

    public static List<ClassId> getTankerThirdClasses() {
       List<ClassId> classes = new ArrayList<>();
       classes.add(ClassId.PHOENIX_KNIGHT);
       classes.add(ClassId.HELL_KNIGHT);
       classes.add(ClassId.EVAS_TEMPLAR);
       return classes;
    }

   public static List<ClassId> getHealerThirdClasses() {
      List<ClassId> classes = new ArrayList<>();
      classes.add(ClassId.CARDINAL);
      classes.add(ClassId.EVAS_SAINT);
      classes.add(ClassId.SHILLIEN_SAINT);
      classes.add(ClassId.HIEROPHANT);
      classes.add(ClassId.DOOMCRYER);
      return classes;
   }

   public static FakePlayer createHealerFakePlayer(String accountName) {
      return createHealerFakePlayerByClass(accountName, getHealerThirdClasses().get(Rnd.get(0, getHealerThirdClasses().size() - 1)));
   }

   public static FakePlayer createHealerFakePlayerByClass(String accountName, ClassId classId) {
      String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();
      String playerTitle = FakePlayerNameManager.INSTANCE.getRandomTitleFromWordlist();
      PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
      PcAppearance app = getRandomAppearance(template.getRace());
      Player base = FakeAccountService.createPersistentFakeCharacter(accountName, playerName, classId, app.getSex(), app);
      int objectId = (base != null) ? base.getObjectId() : IdFactory.getInstance().getNextId();
      FakePlayer player = new FakePlayer(objectId, template, accountName, app);
      player.setName(playerName);
      player.setAccessLevel(0);
      player.setTitle(playerTitle);
      player.setBaseClass(player.getClassId());
      player.setIsRunning(true);
      setLevel(player, 81);
      player.rewardSkills();
      giveArmorsByClass(player, false);
      giveWeaponsByClass(player, false);
      giveJewelsByClass(player, false);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY) {
         giveAcessoryByClass(player);
      }
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS) {
         giveAcessoryByFirstClass(player);
      }
      player.heal();
      return player;
   }

   public static FakePlayer createHealerClanFakePlayer(String accountName) {
      String playerName = FakePlayerNameManager.INSTANCE.getRandomAvailableName();
      String playerTitle = FakePlayerNameManager.INSTANCE.getRandomTitleFromWordlist();
      ClassId classId = getHealerThirdClasses().get(Rnd.get(0, getHealerThirdClasses().size() - 1));
      PlayerTemplate template = CharTemplateTable.getInstance().getTemplate(classId);
      PcAppearance app = getRandomAppearance(template.getRace());
      Clan clan = ClanTable.getInstance().getClan(FakePlayerUtilsAI.getRandomClan());
      Player base = FakeAccountService.createPersistentFakeCharacter(accountName, playerName, classId, app.getSex(), app);
      int objectId = (base != null) ? base.getObjectId() : IdFactory.getInstance().getNextId();
      FakePlayer player = new FakePlayer(objectId, template, accountName, app);
      player.setName(playerName);
      player.setAccessLevel(0);
      if (clan != null) {
         player.setClan(clan);
         player.setTitle(playerTitle);
         player.setPledgeClass(ClanMember.calculatePledgeClass(player));
         clan.addClanMember(player);
      } else {
         player.setTitle(playerTitle);
      }
      player.setBaseClass(player.getClassId());
      player.setIsRunning(true);
      setLevel(player, 81);
      player.rewardSkills();
      giveArmorsByClass(player, false);
      giveWeaponsByClass(player, false);
      giveJewelsByClass(player, false);
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY) {
         giveAcessoryByClass(player);
      }
      if (FakePlayerConfig.ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS) {
         giveAcessoryByFirstClass(player);
      }
      player.heal();
      return player;
   }

   public static Map<ClassId, Class<? extends FakePlayerAI>> getAllAIs() {
      Map<ClassId, Class<? extends FakePlayerAI>> ais = new HashMap<>();
      ais.put(ClassId.STORM_SCREAMER, StormScreamerAI.class);
      ais.put(ClassId.MYSTIC_MUSE, MysticMuseAI.class);
      ais.put(ClassId.ARCHMAGE, ArchmageAI.class);
      ais.put(ClassId.SOULTAKER, SoultakerAI.class);
      ais.put(ClassId.SAGGITARIUS, SaggitariusAI.class);
      ais.put(ClassId.MOONLIGHT_SENTINEL, MoonlightSentinelAI.class);
      ais.put(ClassId.GHOST_SENTINEL, GhostSentinelAI.class);
      ais.put(ClassId.ADVENTURER, AdventurerAI.class);
      ais.put(ClassId.WIND_RIDER, WindRiderAI.class);
      ais.put(ClassId.GHOST_HUNTER, GhostHunterAI.class);
      ais.put(ClassId.DOMINATOR, DominatorAI.class);
      ais.put(ClassId.TITAN, TitanAI.class);
      ais.put(ClassId.CARDINAL, CardinalAI.class);
      ais.put(ClassId.EVAS_SAINT, CardinalAI.class);
      ais.put(ClassId.SHILLIEN_SAINT, CardinalAI.class);
      ais.put(ClassId.HIEROPHANT, CardinalAI.class);
      ais.put(ClassId.DOOMCRYER, CardinalAI.class);
      ais.put(ClassId.DUELIST, DuelistAI.class);
      ais.put(ClassId.GRAND_KHAVATARI, GrandKhavatariAI.class);
      ais.put(ClassId.DREADNOUGHT, DreadnoughtAI.class);
      ais.put(ClassId.PHOENIX_KNIGHT, PhoenixKnightAI.class);
      ais.put(ClassId.HELL_KNIGHT, HellKnightAI.class);
      ais.put(ClassId.EVAS_TEMPLAR, EvasTemplarAI.class);
       return ais;
   }

   public static PcAppearance getRandomAppearance(ClassRace race) {
      boolean randomSex = Rnd.get(0, 1) != 0;
      int hairStyle = Rnd.get(0, 3);
      int hairColor = Rnd.get(0, 3);
      int faceId = Rnd.get(0, 2);
      return new PcAppearance((byte)faceId, (byte)hairColor, (byte)hairStyle, randomSex ? Sex.FEMALE : Sex.MALE);
   }

   public static void setLevel(FakePlayer player, int level) {
      if (level >= 1 && level <= 81) {
         long pXp = player.getExp();
         long tXp = Experience.LEVEL[81];
         if (pXp > tXp) {
            player.removeExpAndSp(pXp - tXp, 0);
         } else if (pXp < tXp) {
            player.addExpAndSp(tXp - pXp, 0);
         }
      }
   }

   public static Class<? extends FakePlayerAI> getAIbyClassId(ClassId classId) {
      Class<? extends FakePlayerAI> ai = getAllAIs().get(classId);
      return ai == null ? FallbackAI.class : ai;
   }

   public static void ensureOlympiadLoadout(FakePlayer player) {
      if (player == null) {
         return;
      }

      unequipCombatPaperdoll(player);
      removeStaleFakeEquipment(player, true);
      unequipInvalidOlympiadGradeGear(player);
      final ArmorType preferredType = getPreferredArmorType(player);
      final List<Integer> selectedOlympiadSetIds = sanitizeConfiguredArmorSet(getRandomConfiguredOlympiadArmorSetIds(preferredType), preferredType, true);
      removeArmorPiecesOutsideSelectedSet(player, selectedOlympiadSetIds);

      Armor chest = equipOlympiadArmorPiece(player, preferredType, Item.SLOT_CHEST | Item.SLOT_FULL_ARMOR, selectedOlympiadSetIds);
      if (chest == null || chest.getBodyPart() != Item.SLOT_FULL_ARMOR) {
         equipOlympiadArmorPiece(player, preferredType, Item.SLOT_LEGS, selectedOlympiadSetIds);
      }

      equipOlympiadArmorPiece(player, preferredType, Item.SLOT_HEAD, selectedOlympiadSetIds);
      equipOlympiadArmorPiece(player, preferredType, Item.SLOT_GLOVES, selectedOlympiadSetIds);
      equipOlympiadArmorPiece(player, preferredType, Item.SLOT_FEET, selectedOlympiadSetIds);

      Weapon weapon = equipOlympiadWeapon(player);
      if (shouldEquipShield(player, weapon)) {
         equipOlympiadShield(player);
      }

      equipOlympiadAccessory(player, Item.SLOT_NECK, 1);
      equipOlympiadAccessory(player, Item.SLOT_L_EAR, 2);
      equipOlympiadAccessory(player, Item.SLOT_L_FINGER, 2);
      ensureFeetEquipped(player, preferredType, selectedOlympiadSetIds);
      unequipInvalidOlympiadGradeGear(player);
      player.getInventory().reloadEquippedItems();
   }

   private static void unequipInvalidOlympiadGradeGear(FakePlayer player) {
      if (player == null || FakePlayerConfig.FAKE_OLYMPIAD_ARMOR_GRADE == null) {
         return;
      }

      final int[] olympiadSlots = new int[] {
         Inventory.PAPERDOLL_REAR,
         Inventory.PAPERDOLL_LEAR,
         Inventory.PAPERDOLL_NECK,
         Inventory.PAPERDOLL_RFINGER,
         Inventory.PAPERDOLL_LFINGER,
         Inventory.PAPERDOLL_HEAD,
         Inventory.PAPERDOLL_RHAND,
         Inventory.PAPERDOLL_LHAND,
         Inventory.PAPERDOLL_GLOVES,
         Inventory.PAPERDOLL_CHEST,
         Inventory.PAPERDOLL_LEGS,
         Inventory.PAPERDOLL_FEET
      };

      for (int slot : olympiadSlots) {
         final ItemInstance equipped = player.getInventory().getPaperdollItem(slot);
         if (equipped == null || equipped.getItem() == null) {
            continue;
         }

         final Item equippedItem = equipped.getItem();
         if (!isValidOlympiadItem(equippedItem) || !matchesConfiguredOlympiadGrade(equippedItem)) {
            player.getInventory().unEquipItemInSlot(slot);
         }
      }
   }

   public static void ensureCombatLoadout(FakePlayer player) {
      if (player == null) {
         return;
      }

      // Force re-equip of regular combat setup (non-olympiad).
      unequipCombatPaperdoll(player);
      removeStaleFakeEquipment(player, false);
      ensureCompleteClassLoadout(player, false);
   }

   // Drops equipped weapons/armor/jewelry not present in current fake/Olympiad config (stale items after config edits).
   private static void removeStaleFakeEquipment(FakePlayer player, boolean olympiad) {
      if (player == null) {
         return;
      }

      final Set<Integer> allowed = olympiad ? buildAllowedOlympiadEquipmentIds() : buildAllowedCombatEquipmentIds();
      addFakeInventoryKeepItemIds(allowed);
      if (allowed.isEmpty()) {
         return;
      }

      final List<ItemInstance> snapshot = new ArrayList<>(player.getInventory().getItems());
      for (ItemInstance item : snapshot) {
         if (item == null || item.getItem() == null) {
            continue;
         }

         final Item template = item.getItem();
         if (!template.isEquipable() || template.isPetItem()) {
            continue;
         }

         if (!(template instanceof Weapon) && !(template instanceof Armor)) {
            continue;
         }

         final int itemId = item.getItemId();
         if (allowed.contains(itemId)) {
            continue;
         }

         player.getInventory().destroyItem("FakeStaleGear", item, player, null);
      }
   }

   private static void addFakeInventoryKeepItemIds(Set<Integer> allowed) {
      if (allowed == null) {
         return;
      }

      allowed.add(PcInventory.ADENA_ID);
      allowed.add(PcInventory.ANCIENT_ADENA_ID);
      allowed.add(17);
      allowed.add(1341);
      allowed.add(1342);
      allowed.add(1343);
      allowed.add(1344);
      allowed.add(1345);
      if (FakePlayerConfig.FAKE_PLAYER_ARROW > 0) {
         allowed.add(FakePlayerConfig.FAKE_PLAYER_ARROW);
      }
      if (FakePlayerConfig.FAKE_PLAYER_SOULSHOT > 0) {
         allowed.add(FakePlayerConfig.FAKE_PLAYER_SOULSHOT);
      }
      if (FakePlayerConfig.FAKE_PLAYER_BLESSED_SOULSHOT > 0) {
         allowed.add(FakePlayerConfig.FAKE_PLAYER_BLESSED_SOULSHOT);
      }
   }

   private static void addIntListToSet(Set<Integer> target, List<Integer> source) {
      if (target == null || source == null || source.isEmpty()) {
         return;
      }

      for (Integer id : source) {
         if (id != null && id > 0) {
            target.add(id);
         }
      }
   }

   private static Set<Integer> buildAllowedCombatEquipmentIds() {
      final Set<Integer> s = new HashSet<>();
      for (ArmorType t : new ArmorType[] { ArmorType.MAGIC, ArmorType.HEAVY, ArmorType.LIGHT }) {
         addIntListToSet(s, getConfiguredCombatArmorIds(t));
      }

      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_BOW);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_DAGGER);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_SWORD);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_SPEAR);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_DUAL);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_FIST);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_BIG_SWORD);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_MAGIC);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_SHIELD);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_ACCESSORY);

      for (int[] pair : FakePlayerConfig.LIST_PHANTOM_JEWELS_1) {
         if (pair != null && pair.length > 0 && pair[0] > 0) {
            s.add(pair[0]);
         }
      }
      for (int[] pair : FakePlayerConfig.LIST_PHANTOM_JEWELS_2) {
         if (pair != null && pair.length > 0 && pair[0] > 0) {
            s.add(pair[0]);
         }
      }

      return s;
   }

   private static Set<Integer> buildAllowedOlympiadEquipmentIds() {
      final Set<Integer> s = new HashSet<>();
      for (ArmorType t : new ArmorType[] { ArmorType.MAGIC, ArmorType.HEAVY, ArmorType.LIGHT }) {
         addIntListToSet(s, getConfiguredOlympiadArmorIds(t));
      }

      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_OLYMPIAD_BOW);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_OLYMPIAD_DAGGER);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_OLYMPIAD_SWORD);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_OLYMPIAD_SPEAR);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_OLYMPIAD_DUAL);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_OLYMPIAD_FIST);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_OLYMPIAD_BIG_SWORD);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_OLYMPIAD_MAGIC);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_OLYMPIAD_SHIELD);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_OLYMPIAD_ACCESSORY);
      addIntListToSet(s, FakePlayerConfig.LIST_FAKE_ACCESSORY);

      return s;
   }

   private static void unequipCombatPaperdoll(FakePlayer player) {
      if (player == null) {
         return;
      }

      final int[] combatSlots = new int[] {
         Inventory.PAPERDOLL_REAR,
         Inventory.PAPERDOLL_LEAR,
         Inventory.PAPERDOLL_NECK,
         Inventory.PAPERDOLL_RFINGER,
         Inventory.PAPERDOLL_LFINGER,
         Inventory.PAPERDOLL_HEAD,
         Inventory.PAPERDOLL_RHAND,
         Inventory.PAPERDOLL_LHAND,
         Inventory.PAPERDOLL_GLOVES,
         Inventory.PAPERDOLL_CHEST,
         Inventory.PAPERDOLL_LEGS,
         Inventory.PAPERDOLL_FEET
      };

      for (int slot : combatSlots) {
         if (player.getInventory().getPaperdollItem(slot) != null) {
            player.getInventory().unEquipItemInSlot(slot);
         }
      }
   }

   private static Weapon equipOlympiadWeapon(FakePlayer player) {
      final Weapon weapon = findBestOlympiadWeapon(player);
      if (weapon == null) {
         return null;
      }

      equipOlympiadItemCopies(player, weapon.getItemId(), 1);
      return weapon;
   }

   private static Armor equipOlympiadShield(FakePlayer player) {
      final Armor shield = findBestOlympiadShield();
      if (shield == null) {
         return null;
      }

      equipOlympiadItemCopies(player, shield.getItemId(), 1);
      return shield;
   }

   private static Armor equipOlympiadArmorPiece(FakePlayer player, ArmorType preferredType, int bodyPartMask, List<Integer> selectedSetIds) {
      Armor armor = findBestOlympiadArmorFromSet(selectedSetIds, preferredType, bodyPartMask);
      if (armor == null && !hasConfiguredArmorSet(selectedSetIds)) {
         armor = findBestOlympiadArmor(preferredType, bodyPartMask);
      }
      if (armor == null && !hasConfiguredArmorSet(selectedSetIds)) {
         armor = findBestOlympiadArmorAnyType(bodyPartMask);
      }
      if (armor == null) {
         return null;
      }

      equipOlympiadItemCopies(player, armor.getItemId(), 1);
      return armor;
   }

   private static boolean hasConfiguredArmorSet(List<Integer> selectedSetIds) {
      return selectedSetIds != null && !selectedSetIds.isEmpty();
   }

   // Removes body-armor not in the chosen set list (jewels, cloak, shield unchanged).
   private static void removeArmorPiecesOutsideSelectedSet(FakePlayer player, List<Integer> selectedSetIds) {
      if (player == null || !hasConfiguredArmorSet(selectedSetIds)) {
         return;
      }

      final Set<Integer> allow = new HashSet<>();
      for (Integer id : selectedSetIds) {
         if (id != null && id > 0) {
            allow.add(id);
         }
      }
      if (allow.isEmpty()) {
         return;
      }

      final int[] paperdollArmorSlots = new int[] {
         Inventory.PAPERDOLL_HEAD,
         Inventory.PAPERDOLL_CHEST,
         Inventory.PAPERDOLL_LEGS,
         Inventory.PAPERDOLL_GLOVES,
         Inventory.PAPERDOLL_FEET
      };
      for (int slot : paperdollArmorSlots) {
         final ItemInstance equipped = player.getInventory().getPaperdollItem(slot);
         if (equipped == null || !(equipped.getItem() instanceof Armor)) {
            continue;
         }
         final Armor ar = (Armor) equipped.getItem();
         if (!isConfigurableArmorSetPiece(ar)) {
            continue;
         }
         if (allow.contains(equipped.getItemId())) {
            continue;
         }
         player.getInventory().unEquipItemInSlot(slot);
         player.getInventory().destroyItem("FakeSetArmor", equipped, player, null);
      }

      final List<ItemInstance> snapshot = new ArrayList<>(player.getInventory().getItems());
      for (ItemInstance item : snapshot) {
         if (item == null || item.getItem() == null || !(item.getItem() instanceof Armor)) {
            continue;
         }
         final Armor ar = (Armor) item.getItem();
         if (!isConfigurableArmorSetPiece(ar)) {
            continue;
         }
         if (allow.contains(item.getItemId())) {
            continue;
         }
         if (item.isEquipped()) {
            player.getInventory().unEquipItemInBodySlotAndRecord(item);
         }
         player.getInventory().destroyItem("FakeSetArmor", item, player, null);
      }
   }

   private static boolean isConfigurableArmorSetPiece(Armor armor) {
      if (armor == null) {
         return false;
      }
      if (armor.getType2() == Item.TYPE2_ACCESSORY) {
         return false;
      }
      if (armor.getItemType() == ArmorType.SHIELD) {
         return false;
      }
      final int bp = armor.getBodyPart();
      if ((bp & Item.SLOT_BACK) != 0) {
         return false;
      }
      return (bp & (Item.SLOT_HEAD | Item.SLOT_CHEST | Item.SLOT_LEGS | Item.SLOT_GLOVES | Item.SLOT_FEET | Item.SLOT_FULL_ARMOR)) != 0;
   }

   private static void equipOlympiadAccessory(FakePlayer player, int bodyPartMask, int count) {
      final Armor accessory = findBestOlympiadAccessory(bodyPartMask);
      if (accessory == null) {
         return;
      }

      equipOlympiadItemCopies(player, accessory.getItemId(), count);
   }

   private static void equipOlympiadItemCopies(FakePlayer player, int itemId, int count) {
      if (itemId <= 0 || player == null || count <= 0) {
         return;
      }

      final List<ItemInstance> ownedItems = new ArrayList<>();
      for (ItemInstance item : player.getInventory().getItems()) {
         if (item != null && item.getItemId() == itemId) {
            ownedItems.add(item);
         }
      }

      while (ownedItems.size() < count) {
         final ItemInstance created = player.getInventory().addItem("FakeOlympiad", itemId, 1, player, null);
         if (created == null) {
            break;
         }
         ownedItems.add(created);
      }

      for (int index = 0; index < count && index < ownedItems.size(); index++) {
         final ItemInstance item = ownedItems.get(index);
         if (item == null) {
            continue;
         }

         applyOlympiadEnchant(item);
         if (!item.isEquipped()) {
            player.getInventory().equipItemAndRecord(item);
         }
      }
   }

   private static void ensureFeetEquipped(FakePlayer player) {
      if (player == null) {
         return;
      }
      ensureFeetEquipped(player, getPreferredArmorType(player), new ArrayList<>());
   }

   private static void ensureFeetEquipped(FakePlayer player, ArmorType preferredType, List<Integer> selectedSetIds) {
      if (player == null) {
         return;
      }

      if (player.getInventory().getPaperdollItemId(Inventory.PAPERDOLL_FEET) > 0) {
         return;
      }

      for (ItemInstance item : player.getInventory().getItems()) {
         if (item == null || item.getItem() == null || !(item.getItem() instanceof Armor)) {
            continue;
         }

         final Armor armor = (Armor) item.getItem();
         if (armor.getBodyPart() != Item.SLOT_FEET || armor.getItemType() != preferredType) {
            continue;
         }
         if (selectedSetIds != null && !selectedSetIds.isEmpty() && !selectedSetIds.contains(item.getItemId())) {
            continue;
         }

         player.getInventory().equipItemAndRecord(item);
         return;
      }

      Armor boots = findBestOlympiadArmorFromSet(selectedSetIds, preferredType, Item.SLOT_FEET);
      if (boots == null && !hasConfiguredArmorSet(selectedSetIds)) {
         boots = findBestOlympiadArmor(preferredType, Item.SLOT_FEET);
      }
      if (boots != null) {
         equipOlympiadItemCopies(player, boots.getItemId(), 1);
      }
   }

   private static void ensureCompleteClassLoadout(FakePlayer player, boolean randomlyEnchant) {
      if (player == null) {
         return;
      }

      final ArmorType preferredType = getPreferredArmorType(player);
      final List<Integer> selectedCombatSetIds = sanitizeConfiguredArmorSet(getRandomConfiguredCombatArmorSetIds(preferredType), preferredType, false);
      removeArmorPiecesOutsideSelectedSet(player, selectedCombatSetIds);

      final Armor chest = ensureArmorPieceEquipped(player, preferredType, Item.SLOT_CHEST | Item.SLOT_FULL_ARMOR, randomlyEnchant, selectedCombatSetIds);
      if (chest == null || chest.getBodyPart() != Item.SLOT_FULL_ARMOR) {
         ensureArmorPieceEquipped(player, preferredType, Item.SLOT_LEGS, randomlyEnchant, selectedCombatSetIds);
      }

      ensureArmorPieceEquipped(player, preferredType, Item.SLOT_HEAD, randomlyEnchant, selectedCombatSetIds);
      ensureArmorPieceEquipped(player, preferredType, Item.SLOT_GLOVES, randomlyEnchant, selectedCombatSetIds);
      ensureArmorPieceEquipped(player, preferredType, Item.SLOT_FEET, randomlyEnchant, selectedCombatSetIds);

      final Weapon weapon = ensureWeaponEquipped(player, randomlyEnchant);
      if (shouldEquipShield(player, weapon)) {
         ensureShieldEquipped(player, randomlyEnchant);
      }

      ensureAccessoryEquipped(player, Item.SLOT_NECK, 1, randomlyEnchant);
      ensureAccessoryEquipped(player, Item.SLOT_L_EAR, 2, randomlyEnchant);
      ensureAccessoryEquipped(player, Item.SLOT_L_FINGER, 2, randomlyEnchant);
      ensureBowAmmo(player);
      player.getInventory().reloadEquippedItems();
   }

   private static List<Integer> sanitizeConfiguredArmorSet(List<Integer> selectedSetIds, ArmorType preferredType, boolean olympiad) {
      if (!hasConfiguredArmorSet(selectedSetIds)) {
         return new ArrayList<>();
      }

      boolean hasChestOrFull = false;
      boolean hasLegsOrFull = false;
      boolean hasHead = false;
      boolean hasGloves = false;
      boolean hasFeet = false;

      for (int itemId : selectedSetIds) {
         final Item item = ItemTable.getInstance().getTemplate(itemId);
         if (!(item instanceof Armor)) {
            continue;
         }

         final Armor armor = (Armor) item;
         if (olympiad) {
            if (!isValidOlympiadItem(armor)) {
               continue;
            }
         } else if (!isValidCombatItem(armor)) {
            continue;
         }

         final int bodyPart = armor.getBodyPart();
         if ((bodyPart & (Item.SLOT_CHEST | Item.SLOT_FULL_ARMOR)) != 0) {
            hasChestOrFull = true;
         }
         if ((bodyPart & (Item.SLOT_LEGS | Item.SLOT_FULL_ARMOR)) != 0) {
            hasLegsOrFull = true;
         }
         if ((bodyPart & Item.SLOT_HEAD) != 0) {
            hasHead = true;
         }
         if ((bodyPart & Item.SLOT_GLOVES) != 0) {
            hasGloves = true;
         }
         if ((bodyPart & Item.SLOT_FEET) != 0) {
            hasFeet = true;
         }
      }

      if (!(hasChestOrFull && hasLegsOrFull && hasHead && hasGloves && hasFeet)) {
         // Incomplete/misconfigured set causes mixed or missing pieces.
         // Treat it as "no forced set" and let normal best-item fallback equip a full loadout.
         return new ArrayList<>();
      }

      return selectedSetIds;
   }

   private static Armor ensureArmorPieceEquipped(FakePlayer player, ArmorType preferredType, int bodyPartMask, boolean randomlyEnchant, List<Integer> selectedSetIds) {
      if (player == null) {
         return null;
      }
      final boolean enforceArmorType = requiresStrictArmorType(bodyPartMask);

      if (isArmorSlotSatisfied(player, bodyPartMask)) {
         final ItemInstance equipped = getEquippedArmorForMask(player, bodyPartMask);
         if (equipped != null && equipped.getItem() instanceof Armor) {
            if (selectedSetIds == null || selectedSetIds.isEmpty() || selectedSetIds.contains(equipped.getItemId())) {
               return (Armor) equipped.getItem();
            }
         }
      }

      ItemInstance bestOwned = null;
      int bestGrade = Integer.MIN_VALUE;
      for (ItemInstance item : player.getInventory().getItems()) {
         if (item == null || !(item.getItem() instanceof Armor)) {
            continue;
         }

         final Armor armor = (Armor) item.getItem();
         if ((enforceArmorType && armor.getItemType() != preferredType) || (armor.getBodyPart() & bodyPartMask) == 0) {
            continue;
         }
         if (selectedSetIds != null && !selectedSetIds.isEmpty() && !selectedSetIds.contains(item.getItemId())) {
            continue;
         }

         final int grade = armor.getCrystalType().ordinal();
         if (bestOwned == null || grade > bestGrade) {
            bestOwned = item;
            bestGrade = grade;
         }
      }

      if (bestOwned != null && bestOwned.getItem() instanceof Armor) {
         player.getInventory().equipItemAndRecord(bestOwned);
         return (Armor) bestOwned.getItem();
      }

      Armor armor = findBestCombatArmorFromSet(selectedSetIds, preferredType, bodyPartMask);
      if (armor == null && !hasConfiguredArmorSet(selectedSetIds)) {
         armor = findBestCombatArmor(preferredType, bodyPartMask);
      }
      if (armor == null && !hasConfiguredArmorSet(selectedSetIds)) {
         armor = findBestCombatArmorAnyType(bodyPartMask);
      }
      if (armor != null) {
         ensureItemCopies(player, armor.getItemId(), 1, randomlyEnchant);
      }
      return armor;
   }

   private static Weapon ensureWeaponEquipped(FakePlayer player, boolean randomlyEnchant) {
      if (player == null) {
         return null;
      }

      final Weapon activeWeapon = player.getActiveWeaponItem();
      final List<Integer> configuredWeaponIdsRaw = getConfiguredCombatWeaponIds(player);
      final List<Integer> configuredWeaponIds = configuredWeaponIdsRaw != null ? configuredWeaponIdsRaw : java.util.Collections.<Integer>emptyList();
      final boolean hasConfiguredList = !configuredWeaponIds.isEmpty();
      if (activeWeapon != null) {
         if (!hasConfiguredList || configuredWeaponIds.contains(activeWeapon.getItemId())) {
            return activeWeapon;
         }
         // Weapon is not allowed for this class config: force re-equip from allowed pool.
         player.getInventory().unEquipItemInSlot(Inventory.PAPERDOLL_RHAND);
         player.getInventory().unEquipItemInSlot(Inventory.PAPERDOLL_LHAND);
      }

      ItemInstance bestOwned = null;
      int bestGrade = Integer.MIN_VALUE;
      for (ItemInstance item : player.getInventory().getItems()) {
         if (item == null || !(item.getItem() instanceof Weapon)) {
            continue;
         }

         final Weapon weapon = (Weapon) item.getItem();
         if (hasConfiguredList && !configuredWeaponIds.contains(weapon.getItemId())) {
            continue;
         }
         if (!matchesPreferredWeaponType(player, weapon)) {
            continue;
         }

         final int grade = weapon.getCrystalType().ordinal();
         if (bestOwned == null || grade > bestGrade) {
            bestOwned = item;
            bestGrade = grade;
         }
      }

      if (bestOwned != null && bestOwned.getItem() instanceof Weapon) {
         player.getInventory().equipItemAndRecord(bestOwned);
         return (Weapon) bestOwned.getItem();
      }

      // If multiple IDs are configured for the class, choose randomly from allowed pool.
      final Weapon weapon = findRandomConfiguredCombatWeapon(player);
      if (weapon != null) {
         ensureItemCopies(player, weapon.getItemId(), 1, randomlyEnchant);
      }
      return weapon;
   }

   private static Armor ensureShieldEquipped(FakePlayer player, boolean randomlyEnchant) {
      if (player == null || player.getInventory().getPaperdollItemId(Inventory.PAPERDOLL_LHAND) > 0) {
         return null;
      }

      ItemInstance bestOwned = null;
      int bestGrade = Integer.MIN_VALUE;
      for (ItemInstance item : player.getInventory().getItems()) {
         if (item == null || !(item.getItem() instanceof Armor)) {
            continue;
         }

         final Armor armor = (Armor) item.getItem();
         if (armor.getBodyPart() != Item.SLOT_L_HAND || armor.getItemType() != ArmorType.SHIELD) {
            continue;
         }

         final int grade = armor.getCrystalType().ordinal();
         if (bestOwned == null || grade > bestGrade) {
            bestOwned = item;
            bestGrade = grade;
         }
      }

      if (bestOwned != null && bestOwned.getItem() instanceof Armor) {
         player.getInventory().equipItemAndRecord(bestOwned);
         return (Armor) bestOwned.getItem();
      }

      final Armor shield = findBestCombatShield();
      if (shield != null) {
         ensureItemCopies(player, shield.getItemId(), 1, randomlyEnchant);
      }
      return shield;
   }

   private static void ensureAccessoryEquipped(FakePlayer player, int bodyPartMask, int count, boolean randomlyEnchant) {
      if (player == null || getEquippedAccessoryCount(player, bodyPartMask) >= count) {
         return;
      }

      final List<ItemInstance> candidates = new ArrayList<>();
      for (ItemInstance item : player.getInventory().getItems()) {
         if (item == null || !(item.getItem() instanceof Armor)) {
            continue;
         }
         final Armor armor = (Armor) item.getItem();
         if (armor.getType2() != Item.TYPE2_ACCESSORY || (armor.getBodyPart() & bodyPartMask) == 0) {
            continue;
         }
         candidates.add(item);
      }

      candidates.sort((a, b) -> {
         final Armor aa = (Armor) a.getItem();
         final Armor bb = (Armor) b.getItem();
         return Integer.compare(bb.getCrystalType().ordinal(), aa.getCrystalType().ordinal());
      });

      for (ItemInstance candidate : candidates) {
         if (getEquippedAccessoryCount(player, bodyPartMask) >= count) {
            return;
         }
         if (!candidate.isEquipped()) {
            player.getInventory().equipItemAndRecord(candidate);
         }
      }

      final Armor accessory = findBestCombatAccessory(bodyPartMask);
      if (accessory != null && getEquippedAccessoryCount(player, bodyPartMask) < count) {
         ensureItemCopies(player, accessory.getItemId(), count, randomlyEnchant);
      }
   }

   private static void ensureBowAmmo(FakePlayer player) {
      if (player == null) {
         return;
      }
      final Weapon weapon = player.getActiveWeaponItem();
      if (weapon == null || weapon.getItemType() != WeaponType.BOW) {
         return;
      }

      final int arrowItemId = getArrowItemIdForWeapon(weapon, player.getLevel());
      if (arrowItemId <= 0) {
         return;
      }

      final ItemInstance arrow = player.getInventory().getItemByItemId(arrowItemId);
      if (arrow == null || arrow.getCount() < 1500) {
         final long missing = 1500 - (arrow != null ? arrow.getCount() : 0);
         if (missing > 0) {
            player.addItem("FakeArrows", arrowItemId, (int) missing, null, false);
         }
      }
   }

   public static int getArrowItemIdForWeapon(Weapon weapon, int playerLevel) {
      if (weapon != null && weapon.getItemType() == WeaponType.BOW) {
         switch (weapon.getCrystalType()) {
            case NONE:
               return 17;
            case D:
               return 1341;
            case C:
               return 1342;
            case B:
               return 1343;
            case A:
               return 1344;
            case S:
               return FakePlayerConfig.FAKE_PLAYER_ARROW > 0 ? FakePlayerConfig.FAKE_PLAYER_ARROW : 1345;
            default:
               break;
         }
      }

      if (playerLevel < 20) {
         return 17;
      } else if (playerLevel < 40) {
         return 1341;
      } else if (playerLevel < 52) {
         return 1342;
      } else if (playerLevel < 61) {
         return 1343;
      } else if (playerLevel < 76) {
         return 1344;
      }

      return FakePlayerConfig.FAKE_PLAYER_ARROW > 0 ? FakePlayerConfig.FAKE_PLAYER_ARROW : 1345;
   }

   private static void ensureItemCopies(FakePlayer player, int itemId, int count, boolean randomlyEnchant) {
      if (player == null || itemId <= 0 || count <= 0) {
         return;
      }

      final List<ItemInstance> ownedItems = new ArrayList<>();
      for (ItemInstance item : player.getInventory().getItems()) {
         if (item != null && item.getItemId() == itemId) {
            ownedItems.add(item);
         }
      }

      while (ownedItems.size() < count) {
         final ItemInstance created = player.getInventory().addItem("FakeLoadout", itemId, 1, player, null);
         if (created == null) {
            break;
         }
         applyStandardEnchant(created, randomlyEnchant);
         ownedItems.add(created);
      }

      for (int index = 0; index < count && index < ownedItems.size(); index++) {
         final ItemInstance item = ownedItems.get(index);
         if (item == null) {
            continue;
         }

         applyStandardEnchant(item, randomlyEnchant);
         if (!item.isEquipped()) {
            player.getInventory().equipItemAndRecord(item);
         }
      }
   }

   private static void applyStandardEnchant(ItemInstance item, boolean randomlyEnchant) {
      if (item == null || !randomlyEnchant) {
         return;
      }

      final int type2 = item.getItem().getType2();
      final int minEnchant = (type2 == Item.TYPE2_WEAPON) ? FakePlayerConfig.MIN_ENCHANT_WEAPON
         : (type2 == Item.TYPE2_ACCESSORY ? FakePlayerConfig.MIN_ENCHANT_JEWEL : FakePlayerConfig.MIN_ENCHANT_ARMOR);
      final int maxEnchant = (type2 == Item.TYPE2_WEAPON) ? FakePlayerConfig.MAX_ENCHANT_WEAPON
         : (type2 == Item.TYPE2_ACCESSORY ? FakePlayerConfig.MAX_ENCHANT_JEWEL : FakePlayerConfig.MAX_ENCHANT_ARMOR);
      if (maxEnchant >= minEnchant) {
         item.setEnchantLevel(rollEnchantByProfile(minEnchant, maxEnchant));
      }
   }

   private static int rollEnchantByProfile(int minEnchant, int maxEnchant) {
      if (maxEnchant <= minEnchant) {
         return minEnchant;
      }

      if (!FakePlayerConfig.FAKE_ENCHANT_PROFILE_ENABLED) {
         return Rnd.get(minEnchant, maxEnchant);
      }

      final int diff = maxEnchant - minEnchant;
      final int lowMax = minEnchant + Math.max(1, (diff * 35) / 100);
      final int midMin = Math.min(maxEnchant, lowMax + 1);
      final int midMax = minEnchant + Math.max(1, (diff * 75) / 100);
      final int highMin = Math.min(maxEnchant, midMax + 1);

      int lowChance = Math.max(0, FakePlayerConfig.FAKE_ENCHANT_LOW_CHANCE);
      int midChance = Math.max(0, FakePlayerConfig.FAKE_ENCHANT_MID_CHANCE);
      int highChance = Math.max(0, FakePlayerConfig.FAKE_ENCHANT_HIGH_CHANCE);

      final int total = lowChance + midChance + highChance;
      if (total <= 0) {
         return Rnd.get(minEnchant, maxEnchant);
      }

      final int roll = Rnd.get(total);
      if (roll < lowChance) {
         return Rnd.get(minEnchant, Math.min(lowMax, maxEnchant));
      }
      if (roll < lowChance + midChance) {
         final int from = Math.min(midMin, maxEnchant);
         final int to = Math.min(Math.max(from, midMax), maxEnchant);
         return Rnd.get(from, to);
      }

      final int from = Math.min(highMin, maxEnchant);
      return Rnd.get(from, maxEnchant);
   }

   private static boolean isArmorSlotSatisfied(FakePlayer player, int bodyPartMask) {
      if (player == null) {
         return false;
      }

      if ((bodyPartMask & (Item.SLOT_CHEST | Item.SLOT_FULL_ARMOR)) != 0) {
         return player.getInventory().getPaperdollItemId(Inventory.PAPERDOLL_CHEST) > 0;
      }
      if ((bodyPartMask & Item.SLOT_LEGS) != 0) {
         return player.getInventory().getPaperdollItemId(Inventory.PAPERDOLL_CHEST) > 0
            && player.getInventory().getPaperdollItemByL2ItemId(Item.SLOT_CHEST) != null
            && player.getInventory().getPaperdollItemByL2ItemId(Item.SLOT_CHEST).getItem().getBodyPart() == Item.SLOT_FULL_ARMOR
            || player.getInventory().getPaperdollItemId(Inventory.PAPERDOLL_LEGS) > 0;
      }
      if ((bodyPartMask & Item.SLOT_HEAD) != 0) {
         return player.getInventory().getPaperdollItemId(Inventory.PAPERDOLL_HEAD) > 0;
      }
      if ((bodyPartMask & Item.SLOT_GLOVES) != 0) {
         return player.getInventory().getPaperdollItemId(Inventory.PAPERDOLL_GLOVES) > 0;
      }
      if ((bodyPartMask & Item.SLOT_FEET) != 0) {
         return player.getInventory().getPaperdollItemId(Inventory.PAPERDOLL_FEET) > 0;
      }
      return false;
   }

   private static ItemInstance getEquippedArmorForMask(FakePlayer player, int bodyPartMask) {
      if (player == null) {
         return null;
      }

      if ((bodyPartMask & (Item.SLOT_CHEST | Item.SLOT_FULL_ARMOR)) != 0) {
         return player.getInventory().getPaperdollItemByL2ItemId(Item.SLOT_CHEST);
      }
      if ((bodyPartMask & Item.SLOT_LEGS) != 0) {
         return player.getInventory().getPaperdollItemByL2ItemId(Item.SLOT_LEGS);
      }
      if ((bodyPartMask & Item.SLOT_HEAD) != 0) {
         return player.getInventory().getPaperdollItemByL2ItemId(Item.SLOT_HEAD);
      }
      if ((bodyPartMask & Item.SLOT_GLOVES) != 0) {
         return player.getInventory().getPaperdollItemByL2ItemId(Item.SLOT_GLOVES);
      }
      if ((bodyPartMask & Item.SLOT_FEET) != 0) {
         return player.getInventory().getPaperdollItemByL2ItemId(Item.SLOT_FEET);
      }
      return null;
   }

   private static int getEquippedAccessoryCount(FakePlayer player, int bodyPartMask) {
      if (player == null) {
         return 0;
      }

      switch (bodyPartMask) {
         case Item.SLOT_NECK:
            return player.getInventory().getPaperdollItemId(Inventory.PAPERDOLL_NECK) > 0 ? 1 : 0;
         case Item.SLOT_L_EAR:
            int earCount = 0;
            if (player.getInventory().getPaperdollItemId(Inventory.PAPERDOLL_LEAR) > 0) {
               earCount++;
            }
            if (player.getInventory().getPaperdollItemId(Inventory.PAPERDOLL_REAR) > 0) {
               earCount++;
            }
            return earCount;
         case Item.SLOT_L_FINGER:
            int fingerCount = 0;
            if (player.getInventory().getPaperdollItemId(Inventory.PAPERDOLL_LFINGER) > 0) {
               fingerCount++;
            }
            if (player.getInventory().getPaperdollItemId(Inventory.PAPERDOLL_RFINGER) > 0) {
               fingerCount++;
            }
            return fingerCount;
         default:
            return 0;
      }
   }

   private static void applyOlympiadEnchant(ItemInstance item) {
      if (item == null) {
         return;
      }

      int olympiadLimit = Math.max(0, FakePlayerConfig.FAKE_OLYMPIAD_MAX_ENCHANT);
      if (Config.ALT_OLY_ENCHANT_LIMIT >= 0) {
         olympiadLimit = Math.min(olympiadLimit, Config.ALT_OLY_ENCHANT_LIMIT);
      }
      final int type2 = item.getItem().getType2();
      final int minSource = (type2 == Item.TYPE2_WEAPON) ? FakePlayerConfig.MIN_ENCHANT_WEAPON
         : (type2 == Item.TYPE2_ACCESSORY ? FakePlayerConfig.MIN_ENCHANT_JEWEL : FakePlayerConfig.MIN_ENCHANT_ARMOR);
      final int maxSource = (type2 == Item.TYPE2_WEAPON) ? FakePlayerConfig.MAX_ENCHANT_WEAPON
         : (type2 == Item.TYPE2_ACCESSORY ? FakePlayerConfig.MAX_ENCHANT_JEWEL : FakePlayerConfig.MAX_ENCHANT_ARMOR);
      final int minEnchant = Math.min(minSource, olympiadLimit);
      final int maxEnchant = Math.min(Math.max(maxSource, minEnchant), olympiadLimit);
      final int targetEnchant = (maxEnchant > minEnchant) ? Rnd.get(minEnchant, maxEnchant) : maxEnchant;
      if (item.getEnchantLevel() != targetEnchant) {
         item.setEnchantLevel(targetEnchant);
      }
   }

   private static Weapon findBestOlympiadWeapon(FakePlayer player) {
      final Weapon configuredWeapon = findRandomConfiguredOlympiadWeapon(player);
      if (configuredWeapon != null) {
         return configuredWeapon;
      }

      Weapon bestWeapon = null;
      int bestScore = Integer.MIN_VALUE;

      for (Item item : ItemTable.getInstance().getAllItems()) {
         if (!(item instanceof Weapon) || !isValidOlympiadItem(item)) {
            continue;
         }

         final Weapon weapon = (Weapon) item;
         if (!matchesPreferredWeaponType(player, weapon)) {
            continue;
         }
         if (!matchesConfiguredOlympiadGrade(weapon)) {
            continue;
         }

         final int score = getOlympiadItemScore(weapon);
         if (score > bestScore) {
            bestScore = score;
            bestWeapon = weapon;
         }
      }

      return bestWeapon;
   }

   private static Weapon findRandomConfiguredCombatWeapon(FakePlayer player) {
      if (player == null) {
         return null;
      }

      final List<Weapon> candidates = new ArrayList<>();
      for (int itemId : getConfiguredCombatWeaponIds(player)) {
         final Item item = ItemTable.getInstance().getTemplate(itemId);
         if (!(item instanceof Weapon) || !isValidCombatItem(item)) {
            continue;
         }

         final Weapon weapon = (Weapon) item;
         if (!matchesPreferredWeaponType(player, weapon)) {
            continue;
         }
         if (!matchesConfiguredOlympiadGrade(weapon)) {
            continue;
         }
         candidates.add(weapon);
      }

      if (candidates.isEmpty()) {
         return findBestCombatWeapon(player);
      }

      return candidates.get(Rnd.get(candidates.size()));
   }

   private static Weapon findRandomConfiguredOlympiadWeapon(FakePlayer player) {
      if (player == null) {
         return null;
      }

      final List<Weapon> candidates = new ArrayList<>();
      for (int itemId : getConfiguredOlympiadWeaponIds(player)) {
         final Item item = ItemTable.getInstance().getTemplate(itemId);
         if (!(item instanceof Weapon) || !isValidOlympiadItem(item)) {
            continue;
         }

         final Weapon weapon = (Weapon) item;
         if (!matchesPreferredWeaponType(player, weapon)) {
            continue;
         }
         candidates.add(weapon);
      }

      if (candidates.isEmpty()) {
         return findConfiguredOlympiadWeapon(player);
      }

      return candidates.get(Rnd.get(candidates.size()));
   }

   private static Weapon findBestCombatWeapon(FakePlayer player) {
      if (player == null) {
         return null;
      }

      Weapon bestWeapon = null;
      int bestScore = Integer.MIN_VALUE;
      for (int itemId : getConfiguredCombatWeaponIds(player)) {
         final Item item = ItemTable.getInstance().getTemplate(itemId);
         if (!(item instanceof Weapon) || !isValidCombatItem(item)) {
            continue;
         }

         final Weapon weapon = (Weapon) item;
         if (!matchesPreferredWeaponType(player, weapon)) {
            continue;
         }

         final int score = getOlympiadItemScore(weapon);
         if (score > bestScore) {
            bestScore = score;
            bestWeapon = weapon;
         }
      }

      return bestWeapon;
   }

   private static Armor findBestOlympiadShield() {
      final Armor configuredShield = findConfiguredOlympiadShield();
      if (configuredShield != null) {
         return configuredShield;
      }

      Armor bestShield = null;
      int bestScore = Integer.MIN_VALUE;

      for (Item item : ItemTable.getInstance().getAllItems()) {
         if (!(item instanceof Armor) || !isValidOlympiadItem(item)) {
            continue;
         }

         final Armor armor = (Armor) item;
         if (armor.getBodyPart() != Item.SLOT_L_HAND || armor.getItemType() != ArmorType.SHIELD) {
            continue;
         }
         if (!matchesConfiguredOlympiadGrade(armor)) {
            continue;
         }

         final int score = getOlympiadItemScore(armor);
         if (score > bestScore) {
            bestScore = score;
            bestShield = armor;
         }
      }

      return bestShield;
   }

   private static Armor findBestCombatShield() {
      Armor bestShield = null;
      int bestScore = Integer.MIN_VALUE;

      for (int itemId : FakePlayerConfig.LIST_FAKE_SHIELD) {
         final Item item = ItemTable.getInstance().getTemplate(itemId);
         if (!(item instanceof Armor) || !isValidCombatItem(item)) {
            continue;
         }

         final Armor armor = (Armor) item;
         if (armor.getBodyPart() != Item.SLOT_L_HAND || armor.getItemType() != ArmorType.SHIELD) {
            continue;
         }

         final int score = getOlympiadItemScore(armor);
         if (score > bestScore) {
            bestScore = score;
            bestShield = armor;
         }
      }

      return bestShield;
   }

   private static Armor findBestOlympiadArmor(ArmorType preferredType, int bodyPartMask) {
      final Armor configuredArmor = findConfiguredOlympiadArmor(preferredType, bodyPartMask);
      if (configuredArmor != null) {
         return configuredArmor;
      }

      Armor bestArmor = null;
      int bestScore = Integer.MIN_VALUE;

      for (Item item : ItemTable.getInstance().getAllItems()) {
         if (!(item instanceof Armor) || !isValidOlympiadItem(item)) {
            continue;
         }

         final Armor armor = (Armor) item;
         if (armor.getItemType() != preferredType) {
            continue;
         }

         if (!matchesConfiguredOlympiadArmorGrade(armor)) {
            continue;
         }

         if ((armor.getBodyPart() & bodyPartMask) == 0) {
            continue;
         }

         final int score = getOlympiadItemScore(armor);
         if (score > bestScore) {
            bestScore = score;
            bestArmor = armor;
         }
      }

      return bestArmor;
   }

   private static Armor findBestCombatArmor(ArmorType preferredType, int bodyPartMask) {
      Armor bestArmor = null;
      int bestScore = Integer.MIN_VALUE;

      for (int itemId : getConfiguredCombatArmorIds(preferredType)) {
         final Item item = ItemTable.getInstance().getTemplate(itemId);
         if (!(item instanceof Armor) || !isValidCombatItem(item)) {
            continue;
         }

         final Armor armor = (Armor) item;
         if (armor.getItemType() != preferredType || (armor.getBodyPart() & bodyPartMask) == 0) {
            continue;
         }

         final int score = getOlympiadItemScore(armor);
         if (score > bestScore) {
            bestScore = score;
            bestArmor = armor;
         }
      }

      return bestArmor;
   }

   private static Armor findBestOlympiadArmorAnyType(int bodyPartMask) {
      final Armor configuredArmor = findConfiguredOlympiadArmorAnyType(bodyPartMask);
      if (configuredArmor != null) {
         return configuredArmor;
      }

      Armor bestArmor = null;
      int bestScore = Integer.MIN_VALUE;

      for (Item item : ItemTable.getInstance().getAllItems()) {
         if (!(item instanceof Armor) || !isValidOlympiadItem(item)) {
            continue;
         }

         final Armor armor = (Armor) item;
         if (!matchesConfiguredOlympiadArmorGrade(armor)) {
            continue;
         }

         if ((armor.getBodyPart() & bodyPartMask) == 0) {
            continue;
         }

         final int score = getOlympiadItemScore(armor);
         if (score > bestScore) {
            bestScore = score;
            bestArmor = armor;
         }
      }

      return bestArmor;
   }

   private static Armor findBestCombatArmorAnyType(int bodyPartMask) {
      Armor bestArmor = null;
      int bestScore = Integer.MIN_VALUE;

      for (ArmorType type : new ArmorType[] { ArmorType.MAGIC, ArmorType.HEAVY, ArmorType.LIGHT }) {
         final Armor armor = findBestCombatArmor(type, bodyPartMask);
         if (armor == null) {
            continue;
         }

         final int score = getOlympiadItemScore(armor);
         if (score > bestScore) {
            bestScore = score;
            bestArmor = armor;
         }
      }

      return bestArmor;
   }

   private static Armor findBestOlympiadAccessory(int bodyPartMask) {
      final Armor configuredAccessory = findConfiguredOlympiadAccessory(bodyPartMask);
      if (configuredAccessory != null) {
         return configuredAccessory;
      }

      Armor bestAccessory = null;
      int bestScore = Integer.MIN_VALUE;

      for (Item item : ItemTable.getInstance().getAllItems()) {
         if (!(item instanceof Armor) || !isValidOlympiadItem(item)) {
            continue;
         }

         final Armor armor = (Armor) item;
         if (armor.getType2() != Item.TYPE2_ACCESSORY || (armor.getBodyPart() & bodyPartMask) == 0) {
            continue;
         }
         if (!matchesConfiguredOlympiadGrade(armor)) {
            continue;
         }

         final int score = getOlympiadItemScore(armor);
         if (score > bestScore) {
            bestScore = score;
            bestAccessory = armor;
         }
      }

      return bestAccessory;
   }

   private static Armor findBestCombatAccessory(int bodyPartMask) {
      Armor bestAccessory = null;
      int bestScore = Integer.MIN_VALUE;

      for (int itemId : FakePlayerConfig.LIST_FAKE_ACCESSORY) {
         final Item item = ItemTable.getInstance().getTemplate(itemId);
         if (!(item instanceof Armor) || !isValidCombatItem(item)) {
            continue;
         }

         final Armor armor = (Armor) item;
         if (armor.getType2() != Item.TYPE2_ACCESSORY || (armor.getBodyPart() & bodyPartMask) == 0) {
            continue;
         }

         final int score = getOlympiadItemScore(armor);
         if (score > bestScore) {
            bestScore = score;
            bestAccessory = armor;
         }
      }

      return bestAccessory;
   }

   private static Weapon findConfiguredOlympiadWeapon(FakePlayer player) {
      if (player == null) {
         return null;
      }

      Weapon bestWeapon = null;
      int bestScore = Integer.MIN_VALUE;
      for (int itemId : getConfiguredOlympiadWeaponIds(player)) {
         final Item item = ItemTable.getInstance().getTemplate(itemId);
         if (!(item instanceof Weapon) || !isValidOlympiadItem(item)) {
            continue;
         }

         final Weapon weapon = (Weapon) item;
         if (!matchesPreferredWeaponType(player, weapon)) {
            continue;
         }
         if (!matchesConfiguredOlympiadGrade(weapon)) {
            continue;
         }

         final int score = getOlympiadItemScore(weapon);
         if (score > bestScore) {
            bestScore = score;
            bestWeapon = weapon;
         }
      }

      return bestWeapon;
   }

   private static Armor findConfiguredOlympiadShield() {
      Armor bestShield = null;
      int bestScore = Integer.MIN_VALUE;

      for (int itemId : FakePlayerConfig.LIST_FAKE_OLYMPIAD_SHIELD) {
         final Item item = ItemTable.getInstance().getTemplate(itemId);
         if (!(item instanceof Armor) || !isValidOlympiadItem(item)) {
            continue;
         }

         final Armor armor = (Armor) item;
         if (armor.getBodyPart() != Item.SLOT_L_HAND || armor.getItemType() != ArmorType.SHIELD) {
            continue;
         }
         if (!matchesConfiguredOlympiadGrade(armor)) {
            continue;
         }

         final int score = getOlympiadItemScore(armor);
         if (score > bestScore) {
            bestScore = score;
            bestShield = armor;
         }
      }

      return bestShield;
   }

   private static Armor findConfiguredOlympiadArmor(ArmorType preferredType, int bodyPartMask) {
      Armor bestArmor = null;
      int bestScore = Integer.MIN_VALUE;

      for (int itemId : getConfiguredOlympiadArmorIds(preferredType)) {
         final Item item = ItemTable.getInstance().getTemplate(itemId);
         if (!(item instanceof Armor) || !isValidOlympiadItem(item)) {
            continue;
         }

         final Armor armor = (Armor) item;
         if (armor.getItemType() != preferredType || !matchesConfiguredOlympiadArmorGrade(armor) || (armor.getBodyPart() & bodyPartMask) == 0) {
            continue;
         }

         final int score = getOlympiadItemScore(armor);
         if (score > bestScore) {
            bestScore = score;
            bestArmor = armor;
         }
      }

      return bestArmor;
   }

   private static Armor findConfiguredOlympiadArmorAnyType(int bodyPartMask) {
      Armor bestArmor = null;
      int bestScore = Integer.MIN_VALUE;

      for (ArmorType type : new ArmorType[] { ArmorType.MAGIC, ArmorType.HEAVY, ArmorType.LIGHT }) {
         final Armor armor = findConfiguredOlympiadArmor(type, bodyPartMask);
         if (armor == null) {
            continue;
         }

         final int score = getOlympiadItemScore(armor);
         if (score > bestScore) {
            bestScore = score;
            bestArmor = armor;
         }
      }

      return bestArmor;
   }

   private static Armor findBestOlympiadArmorFromSet(List<Integer> setIds, ArmorType preferredType, int bodyPartMask) {
      if (setIds == null || setIds.isEmpty()) {
         return null;
      }
      final boolean enforceArmorType = requiresStrictArmorType(bodyPartMask);

      Armor bestArmor = null;
      int bestScore = Integer.MIN_VALUE;
      for (int itemId : setIds) {
         final Item item = ItemTable.getInstance().getTemplate(itemId);
         if (!(item instanceof Armor) || !isValidOlympiadItem(item)) {
            continue;
         }

         final Armor armor = (Armor) item;
         if ((enforceArmorType && armor.getItemType() != preferredType) || !matchesConfiguredOlympiadArmorGrade(armor) || (armor.getBodyPart() & bodyPartMask) == 0) {
            continue;
         }

         final int score = getOlympiadItemScore(armor);
         if (score > bestScore) {
            bestScore = score;
            bestArmor = armor;
         }
      }
      return bestArmor;
   }

   private static Armor findBestCombatArmorFromSet(List<Integer> setIds, ArmorType preferredType, int bodyPartMask) {
      if (setIds == null || setIds.isEmpty()) {
         return null;
      }
      final boolean enforceArmorType = requiresStrictArmorType(bodyPartMask);

      Armor bestArmor = null;
      int bestScore = Integer.MIN_VALUE;
      for (int itemId : setIds) {
         final Item item = ItemTable.getInstance().getTemplate(itemId);
         if (!(item instanceof Armor) || !isValidCombatItem(item)) {
            continue;
         }

         final Armor armor = (Armor) item;
         if ((enforceArmorType && armor.getItemType() != preferredType) || (armor.getBodyPart() & bodyPartMask) == 0) {
            continue;
         }

         final int score = getOlympiadItemScore(armor);
         if (score > bestScore) {
            bestScore = score;
            bestArmor = armor;
         }
      }
      return bestArmor;
   }

   private static boolean requiresStrictArmorType(int bodyPartMask) {
      return (bodyPartMask & (Item.SLOT_CHEST | Item.SLOT_LEGS | Item.SLOT_FULL_ARMOR)) != 0;
   }

   private static Armor findConfiguredOlympiadAccessory(int bodyPartMask) {
      Armor bestAccessory = null;
      int bestScore = Integer.MIN_VALUE;

      for (int itemId : FakePlayerConfig.LIST_FAKE_OLYMPIAD_ACCESSORY) {
         final Item item = ItemTable.getInstance().getTemplate(itemId);
         if (!(item instanceof Armor) || !isValidOlympiadItem(item)) {
            continue;
         }

         final Armor armor = (Armor) item;
         if (armor.getType2() != Item.TYPE2_ACCESSORY || (armor.getBodyPart() & bodyPartMask) == 0) {
            continue;
         }
         if (!matchesConfiguredOlympiadGrade(armor)) {
            continue;
         }

         final int score = getOlympiadItemScore(armor);
         if (score > bestScore) {
            bestScore = score;
            bestAccessory = armor;
         }
      }

      return bestAccessory;
   }

   private static boolean matchesConfiguredOlympiadArmorGrade(Armor armor) {
      if (armor == null) {
         return false;
      }

      return matchesConfiguredOlympiadGrade(armor);
   }

   private static boolean matchesConfiguredOlympiadGrade(Item item) {
      if (item == null) {
         return false;
      }

      final CrystalType itemGrade = item.getCrystalType();
      if (itemGrade == null) {
         return false;
      }

      final CrystalType configuredGrade = FakePlayerConfig.FAKE_OLYMPIAD_ARMOR_GRADE;
      return configuredGrade == null || !itemGrade.isGreater(configuredGrade);
   }

   private static List<Integer> getConfiguredOlympiadWeaponIds(FakePlayer player) {
      if (player == null) {
         return new ArrayList<>();
      }

      switch (player.getClassId()) {
         case PHOENIX_KNIGHT:
         case SWORD_MUSE:
         case HELL_KNIGHT:
         case EVAS_TEMPLAR:
         case SHILLIEN_KNIGHT:
         case SHILLIEN_TEMPLAR:
            return FakePlayerConfig.LIST_FAKE_OLYMPIAD_SWORD;
         case SAGGITARIUS:
         case MOONLIGHT_SENTINEL:
         case GHOST_SENTINEL:
            return FakePlayerConfig.LIST_FAKE_OLYMPIAD_BOW;
         case ADVENTURER:
         case WIND_RIDER:
         case GHOST_HUNTER:
            return FakePlayerConfig.LIST_FAKE_OLYMPIAD_DAGGER;
         case DREADNOUGHT:
            return FakePlayerConfig.LIST_FAKE_OLYMPIAD_SPEAR;
         case TITAN:
            return FakePlayerConfig.LIST_FAKE_OLYMPIAD_BIG_SWORD;
         case DUELIST:
         case SPECTRAL_DANCER:
            return FakePlayerConfig.LIST_FAKE_OLYMPIAD_DUAL;
         case GRAND_KHAVATARI:
            return FakePlayerConfig.LIST_FAKE_OLYMPIAD_FIST;
         case ARCHMAGE:
         case SOULTAKER:
         case HIEROPHANT:
         case ARCANA_LORD:
         case CARDINAL:
         case MYSTIC_MUSE:
         case ELEMENTAL_MASTER:
         case EVAS_SAINT:
         case STORM_SCREAMER:
         case SPECTRAL_MASTER:
         case SHILLIEN_SAINT:
         case DOMINATOR:
         case DOOMCRYER:
            return FakePlayerConfig.LIST_FAKE_OLYMPIAD_MAGIC;
         default:
            return FakePlayerConfig.LIST_FAKE_OLYMPIAD_SWORD;
      }
   }

   private static List<Integer> getConfiguredCombatWeaponIds(FakePlayer player) {
      if (player == null) {
         return new ArrayList<>();
      }

      switch (player.getClassId()) {
         case SAGGITARIUS:
         case MOONLIGHT_SENTINEL:
         case GHOST_SENTINEL:
            return FakePlayerConfig.LIST_FAKE_BOW;
         case ADVENTURER:
         case WIND_RIDER:
         case GHOST_HUNTER:
            return FakePlayerConfig.LIST_FAKE_DAGGER;
         case DREADNOUGHT:
            return FakePlayerConfig.LIST_FAKE_SPEAR;
         case TITAN:
            return FakePlayerConfig.LIST_FAKE_BIG_SWORD;
         case DUELIST:
         case SPECTRAL_DANCER:
            return FakePlayerConfig.LIST_FAKE_DUAL;
         case GRAND_KHAVATARI:
            return FakePlayerConfig.LIST_FAKE_FIST;
         case ARCHMAGE:
         case SOULTAKER:
         case HIEROPHANT:
         case ARCANA_LORD:
         case CARDINAL:
         case MYSTIC_MUSE:
         case ELEMENTAL_MASTER:
         case EVAS_SAINT:
         case STORM_SCREAMER:
         case SPECTRAL_MASTER:
         case SHILLIEN_SAINT:
         case DOMINATOR:
         case DOOMCRYER:
            return FakePlayerConfig.LIST_FAKE_MAGIC;
         default:
            return FakePlayerConfig.LIST_FAKE_SWORD;
      }
   }

   private static List<Integer> getConfiguredOlympiadArmorIds(ArmorType type) {
      final List<Integer> itemIds = new ArrayList<>();

      if (type == ArmorType.MAGIC) {
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_FAKE_OLYMPIAD_ROBE_ARMOR_1);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_FAKE_OLYMPIAD_ROBE_ARMOR_2);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_FAKE_OLYMPIAD_ROBE_ARMOR_3);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_FAKE_OLYMPIAD_ROBE_ARMOR_4);
      } else if (type == ArmorType.HEAVY) {
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_1);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_2);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_3);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_4);
      } else if (type == ArmorType.LIGHT) {
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_1);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_2);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_3);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_4);
      }

      return itemIds;
   }

   private static List<Integer> getRandomConfiguredOlympiadArmorSetIds(ArmorType type) {
      return getRandomNonEmptyArmorSet(
         type == ArmorType.MAGIC ? FakePlayerConfig.LIST_FAKE_OLYMPIAD_ROBE_ARMOR_1 : type == ArmorType.HEAVY ? FakePlayerConfig.LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_1 : FakePlayerConfig.LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_1,
         type == ArmorType.MAGIC ? FakePlayerConfig.LIST_FAKE_OLYMPIAD_ROBE_ARMOR_2 : type == ArmorType.HEAVY ? FakePlayerConfig.LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_2 : FakePlayerConfig.LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_2,
         type == ArmorType.MAGIC ? FakePlayerConfig.LIST_FAKE_OLYMPIAD_ROBE_ARMOR_3 : type == ArmorType.HEAVY ? FakePlayerConfig.LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_3 : FakePlayerConfig.LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_3,
         type == ArmorType.MAGIC ? FakePlayerConfig.LIST_FAKE_OLYMPIAD_ROBE_ARMOR_4 : type == ArmorType.HEAVY ? FakePlayerConfig.LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_4 : FakePlayerConfig.LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_4
      );
   }

   private static List<Integer> getConfiguredCombatArmorIds(ArmorType type) {
      final List<Integer> itemIds = new ArrayList<>();

      if (type == ArmorType.MAGIC) {
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_PHANTOM_ROB_ARMOR_1);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_PHANTOM_ROB_ARMOR_2);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_PHANTOM_ROB_ARMOR_3);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_PHANTOM_ROB_ARMOR_4);
      } else if (type == ArmorType.HEAVY) {
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_PHANTOM_HEAVY_ARMOR_1);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_PHANTOM_HEAVY_ARMOR_2);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_PHANTOM_HEAVY_ARMOR_3);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_PHANTOM_HEAVY_ARMOR_4);
      } else if (type == ArmorType.LIGHT) {
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_PHANTOM_LIGHT_ARMOR_1);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_PHANTOM_LIGHT_ARMOR_2);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_PHANTOM_LIGHT_ARMOR_3);
         addAllConfiguredIds(itemIds, FakePlayerConfig.LIST_PHANTOM_LIGHT_ARMOR_4);
      }

      return itemIds;
   }

   private static List<Integer> getRandomConfiguredCombatArmorSetIds(ArmorType type) {
      return getRandomNonEmptyArmorSet(
         type == ArmorType.MAGIC ? FakePlayerConfig.LIST_PHANTOM_ROB_ARMOR_1 : type == ArmorType.HEAVY ? FakePlayerConfig.LIST_PHANTOM_HEAVY_ARMOR_1 : FakePlayerConfig.LIST_PHANTOM_LIGHT_ARMOR_1,
         type == ArmorType.MAGIC ? FakePlayerConfig.LIST_PHANTOM_ROB_ARMOR_2 : type == ArmorType.HEAVY ? FakePlayerConfig.LIST_PHANTOM_HEAVY_ARMOR_2 : FakePlayerConfig.LIST_PHANTOM_LIGHT_ARMOR_2,
         type == ArmorType.MAGIC ? FakePlayerConfig.LIST_PHANTOM_ROB_ARMOR_3 : type == ArmorType.HEAVY ? FakePlayerConfig.LIST_PHANTOM_HEAVY_ARMOR_3 : FakePlayerConfig.LIST_PHANTOM_LIGHT_ARMOR_3,
         type == ArmorType.MAGIC ? FakePlayerConfig.LIST_PHANTOM_ROB_ARMOR_4 : type == ArmorType.HEAVY ? FakePlayerConfig.LIST_PHANTOM_HEAVY_ARMOR_4 : FakePlayerConfig.LIST_PHANTOM_LIGHT_ARMOR_4
      );
   }

   @SafeVarargs
   private static List<Integer> getRandomNonEmptyArmorSet(List<Integer>... sets) {
      final List<List<Integer>> available = new ArrayList<>();
      if (sets != null) {
         for (List<Integer> set : sets) {
            if (set == null || set.isEmpty()) {
               continue;
            }
            final List<Integer> sanitized = new ArrayList<>();
            for (Integer id : set) {
               if (id != null && id > 0) {
                  sanitized.add(id);
               }
            }
            if (!sanitized.isEmpty()) {
               available.add(sanitized);
            }
         }
      }
      if (available.isEmpty()) {
         return new ArrayList<>();
      }
      return available.get(Rnd.get(available.size()));
   }

   private static void addAllConfiguredIds(List<Integer> target, List<Integer> source) {
      if (target == null || source == null || source.isEmpty()) {
         return;
      }

      for (Integer itemId : source) {
         if (itemId != null && itemId > 0) {
            target.add(itemId);
         }
      }
   }

   private static boolean isValidOlympiadItem(Item item) {
      if (item == null || !item.isEquipable()) {
         return false;
      }

      if (item.isHeroItem() || item.isOlyRestrictedItem() || item.isOlyRestricted() || item.isPetItem()) {
         return false;
      }

      final CrystalType crystalType = item.getCrystalType();
      if (crystalType == null || crystalType == CrystalType.NONE) {
         return false;
      }

      return item.getBodyPart() != Item.SLOT_UNDERWEAR && item.getBodyPart() != Item.SLOT_BACK && item.getBodyPart() != Item.SLOT_FACE
         && item.getBodyPart() != Item.SLOT_HAIR && item.getBodyPart() != Item.SLOT_HAIRALL;
   }

   private static boolean isValidCombatItem(Item item) {
      if (item == null || !item.isEquipable()) {
         return false;
      }

      if (item.isHeroItem() || item.isPetItem()) {
         return false;
      }

      return item.getBodyPart() != Item.SLOT_UNDERWEAR && item.getBodyPart() != Item.SLOT_BACK && item.getBodyPart() != Item.SLOT_FACE
         && item.getBodyPart() != Item.SLOT_HAIR && item.getBodyPart() != Item.SLOT_HAIRALL;
   }

   private static int getOlympiadItemScore(Item item) {
      return (item.getCrystalType().getId() * 1_000_000) + (item.getCrystalCount() * 100) + item.getReferencePrice();
   }

   private static boolean matchesPreferredWeaponType(FakePlayer player, Weapon weapon) {
      if (weapon == null) {
         return false;
      }

      final WeaponType type = weapon.getItemType();
      switch (player.getClassId()) {
         case SAGGITARIUS:
         case MOONLIGHT_SENTINEL:
         case GHOST_SENTINEL:
            return type == WeaponType.BOW;
         case ADVENTURER:
         case WIND_RIDER:
         case GHOST_HUNTER:
            return type == WeaponType.DAGGER;
         case PHOENIX_KNIGHT:
         case SWORD_MUSE:
         case HELL_KNIGHT:
         case EVAS_TEMPLAR:
         case SHILLIEN_KNIGHT:
         case SHILLIEN_TEMPLAR:
         case MAESTRO:
         case FORTUNE_SEEKER:
            return type == WeaponType.SWORD || type == WeaponType.BLUNT;
         case DREADNOUGHT:
            return type == WeaponType.POLE;
         case TITAN:
            return type == WeaponType.BIGSWORD || type == WeaponType.BIGBLUNT;
         case DUELIST:
         case SPECTRAL_DANCER:
            return type == WeaponType.DUAL;
         case GRAND_KHAVATARI:
            return type == WeaponType.FIST || type == WeaponType.DUALFIST;
         default:
            return weapon.isMagical() && (type == WeaponType.SWORD || type == WeaponType.BLUNT || type == WeaponType.BIGBLUNT);
      }
   }

   private static ArmorType getPreferredArmorType(FakePlayer player) {
      switch (player.getClassId()) {
         case ARCHMAGE:
         case SOULTAKER:
         case HIEROPHANT:
         case ARCANA_LORD:
         case CARDINAL:
         case MYSTIC_MUSE:
         case ELEMENTAL_MASTER:
         case EVAS_SAINT:
         case STORM_SCREAMER:
         case SPECTRAL_MASTER:
         case SHILLIEN_SAINT:
         case DOMINATOR:
         case DOOMCRYER:
            return ArmorType.MAGIC;
         case DUELIST:
         case DREADNOUGHT:
         case PHOENIX_KNIGHT:
         case SWORD_MUSE:
         case HELL_KNIGHT:
         case SPECTRAL_DANCER:
         case EVAS_TEMPLAR:
         case SHILLIEN_TEMPLAR:
         case SHILLIEN_KNIGHT:
         case TITAN:
         case MAESTRO:
         case FORTUNE_SEEKER:
            return ArmorType.HEAVY;
         case SAGGITARIUS:
         case ADVENTURER:
         case WIND_RIDER:
         case MOONLIGHT_SENTINEL:
         case GHOST_HUNTER:
         case GHOST_SENTINEL:
             return ArmorType.LIGHT;
         case GRAND_KHAVATARI:
            return ArmorType.HEAVY;
         default:
            return ArmorType.HEAVY;
      }
   }

   private static boolean shouldEquipShield(FakePlayer player, Weapon weapon) {
      if (weapon == null || weapon.getBodyPart() != Item.SLOT_R_HAND) {
         return false;
      }

      switch (player.getClassId()) {
         case PHOENIX_KNIGHT:
         case SWORD_MUSE:
         case HELL_KNIGHT:
         case EVAS_TEMPLAR:
         case SHILLIEN_KNIGHT:
         case SHILLIEN_TEMPLAR:
         case MAESTRO:
         case FORTUNE_SEEKER:
         case CARDINAL:
         case HIEROPHANT:
         case DOOMCRYER:
         case DOMINATOR:
         case ARCANA_LORD:
         case EVAS_SAINT:
         case SHILLIEN_SAINT:
            return true;
         default:
            return false;
      }
   }

   public static int getRandomDagger() {
      return FakePlayerConfig.LIST_FAKE_DAGGER.isEmpty() ? 0 : FakePlayerConfig.LIST_FAKE_DAGGER.get(Rnd.get(FakePlayerConfig.LIST_FAKE_DAGGER.size()));
   }

   public static int getRandomBow() {
      return FakePlayerConfig.LIST_FAKE_BOW.isEmpty() ? 0 : FakePlayerConfig.LIST_FAKE_BOW.get(Rnd.get(FakePlayerConfig.LIST_FAKE_BOW.size()));
   }

   public static int getRandomSword() {
      return FakePlayerConfig.LIST_FAKE_SWORD.isEmpty() ? 0 : FakePlayerConfig.LIST_FAKE_SWORD.get(Rnd.get(FakePlayerConfig.LIST_FAKE_SWORD.size()));
   }

   public static int getRandomSpear() {
      return FakePlayerConfig.LIST_FAKE_SPEAR.isEmpty() ? 0 : FakePlayerConfig.LIST_FAKE_SPEAR.get(Rnd.get(FakePlayerConfig.LIST_FAKE_SPEAR.size()));
   }

   public static int getRandomDualSword() {
      return FakePlayerConfig.LIST_FAKE_DUAL.isEmpty() ? 0 : FakePlayerConfig.LIST_FAKE_DUAL.get(Rnd.get(FakePlayerConfig.LIST_FAKE_DUAL.size()));
   }

   public static int getRandomFist() {
      return FakePlayerConfig.LIST_FAKE_FIST.isEmpty() ? 0 : FakePlayerConfig.LIST_FAKE_FIST.get(Rnd.get(FakePlayerConfig.LIST_FAKE_FIST.size()));
   }

   public static int getRandomBigSword() {
      return FakePlayerConfig.LIST_FAKE_BIG_SWORD.isEmpty() ? 0 : FakePlayerConfig.LIST_FAKE_BIG_SWORD.get(Rnd.get(FakePlayerConfig.LIST_FAKE_BIG_SWORD.size()));
   }

   public static int getRandomMagicWeapon() {
      return FakePlayerConfig.LIST_FAKE_MAGIC.isEmpty() ? 0 : FakePlayerConfig.LIST_FAKE_MAGIC.get(Rnd.get(FakePlayerConfig.LIST_FAKE_MAGIC.size()));
   }

   public static int getRandomShield() {
      return FakePlayerConfig.LIST_FAKE_SHIELD.isEmpty() ? 0 : FakePlayerConfig.LIST_FAKE_SHIELD.get(Rnd.get(FakePlayerConfig.LIST_FAKE_SHIELD.size()));
   }

   public static int getRandomAcessory() {
      return FakePlayerConfig.LIST_FAKE_ACCESSORY.isEmpty() ? 0 : FakePlayerConfig.LIST_FAKE_ACCESSORY.get(Rnd.get(FakePlayerConfig.LIST_FAKE_ACCESSORY.size()));
   }

   public static void getPotionSkills(FakePlayer player) {
      for (Integer skillid : FakePlayerConfig.FAKE_POTIONS_SKILLS) {
         L2Skill skill = SkillTable.getInstance().getInfo(skillid, SkillTable.getInstance().getMaxLevel(skillid));
         if (skill != null) {
            player.addSkill(skill, false);
         }
      }
   }

   public static void grantGrandBossAccessPasses(FakePlayer player) {
      if (player == null) {
         return;
      }

      grantBlessedResurrectionScrolls(player);
      ensureMinimumItemCount(player, Config.QUEST_BAIUM, 100L);
      ensureMinimumItemCount(player, Config.QUEST_VALAKAS, 100L);
      ensureMinimumItemCount(player, Config.QUEST_ANTHARAS, 100L);
      ensureMinimumItemCount(player, Config.QUEST_SAILREN, 100L);
      ensureMinimumItemCount(player, Config.QUEST_FRINTEZZA, 100L);
   }

   public static void grantBlessedResurrectionScrolls(FakePlayer player) {
      if (player == null) {
         return;
      }

      ensureMinimumItemCount(player, 3936, 100L);
   }

   private static void ensureMinimumItemCount(FakePlayer player, int itemId, long minimumCount) {
      if (player == null || itemId <= 0 || minimumCount <= 0L) {
         return;
      }

      final ItemInstance item = player.getInventory().getItemByItemId(itemId);
      final long currentCount = item != null ? item.getCount() : 0L;
      if (currentCount >= minimumCount) {
         return;
      }

      player.getInventory().addItem("FakeBossAccess", itemId, (int) Math.min(Integer.MAX_VALUE, minimumCount - currentCount), player, null);
   }

   /**
    * Полный набор бафов как у Scheme Buffer / CB ({@link Config#FIGHTER_BUFF_LIST} / {@link Config#MAGE_BUFF_LIST}).
    * @param player фантом
    */
   public static void applySchemeBufferFullBuffs(FakePlayer player) {
      if (player == null) {
         return;
      }
      if (isOlympiadContext(player)) {
         return;
      }

      player.stopAllEffectsExceptThoseThatLastThroughDeath();
      List<Integer> buffList = isMageClassForSchemeBuffs(player.getClassId()) ? Config.MAGE_BUFF_LIST : Config.FIGHTER_BUFF_LIST;
      if (buffList == null || buffList.isEmpty()) {
         player.heal();
         return;
      }

      for (int skillId : buffList) {
         L2Skill skill = SkillTable.getInstance().getInfo(skillId, SkillTable.getInstance().getMaxLevel(skillId));
         if (skill != null) {
            skill.getEffects(player, player);
         }
      }
      applyNoblesseBlessing(player);
      player.heal();
   }

   public static boolean reapplyMissingSchemeBuffs(FakePlayer player) {
      if (player == null) {
         return false;
      }
      if (isOlympiadContext(player)) {
         return false;
      }

      final List<Integer> buffList = isMageClassForSchemeBuffs(player.getClassId()) ? Config.MAGE_BUFF_LIST : Config.FIGHTER_BUFF_LIST;
      boolean appliedAny = false;
      if (buffList != null) {
         for (int skillId : buffList) {
            if (player.getFirstEffect(skillId) != null) {
               continue;
            }

            final L2Skill skill = SkillTable.getInstance().getInfo(skillId, SkillTable.getInstance().getMaxLevel(skillId));
            if (skill == null) {
               continue;
            }

            skill.getEffects(player, player);
            appliedAny = true;
         }
      }

      if (player.getFirstEffect(NOBLESSE_BLESSING_SKILL_ID) == null) {
         applyNoblesseBlessing(player);
         appliedAny = true;
      }

      if (appliedAny) {
         player.heal();
      }
      return appliedAny;
   }

   public static void applyNoblesseBlessing(FakePlayer player) {
      if (player == null) {
         return;
      }

      final L2Skill noblesse = SkillTable.getInstance().getInfo(NOBLESSE_BLESSING_SKILL_ID, 1);
      if (noblesse != null) {
         noblesse.getEffects(player, player);
      }
   }

   private static boolean isMageClassForSchemeBuffs(ClassId classId) {
      if (classId == null) {
         return false;
      }
      switch (classId) {
         case ARCHMAGE:
         case SOULTAKER:
         case MYSTIC_MUSE:
         case STORM_SCREAMER:
         case DOMINATOR:
         case CARDINAL:
         case HIEROPHANT:
         case EVAS_SAINT:
         case SHILLIEN_SAINT:
            return true;
         default:
            return false;
      }
   }

   public static void giveBuffsByClass(FakePlayer player) {
      // Requested behavior: all fake players should run with full scheme buffs.
      if (isOlympiadContext(player)) {
         return;
      }
      applySchemeBufferFullBuffs(player);
   }
   
   private static boolean isOlympiadContext(FakePlayer player) {
      return player.isInOlympiadMode()
         || player.isOlympiadProtection()
         || player.getOlympiadGameId() != -1;
   }
}
