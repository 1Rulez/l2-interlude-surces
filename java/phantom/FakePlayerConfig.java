package phantom;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import com.l2jmega.commons.config.ExProperties;
import com.l2jmega.gameserver.model.item.type.CrystalType;
import com.l2jmega.gameserver.model.location.Location;
import phantom.ai.shop.holder.FakePrivateBuyHolder;
import phantom.ai.shop.holder.FakePrivateSellHolder;

public class FakePlayerConfig {
   protected static final Logger _log = Logger.getLogger(FakePlayerConfig.class.getName());
   @SuppressWarnings("unused")
   private static final String PHANTOM_FILE = "./config/custom/phantom/FakePlayers.properties";
   private static final String PHANTOM_CLAN_FILE = "./config/custom/phantom/FakeClanPlayers.properties";
   public static int FAKE_CHANCE_TO_TALK_SOCIAL;
   public static int FAKE_CHANCE_TO_TALK_DIED;
   public static int FAKE_CHANCE_TO_TALK_KILLED;
   public static int FAKE_SOCIAL_CHANCE;
   public static int FAKE_SIT_CHANCE;
   public static int[] FAKE_PLAYER_ALLOWED_NPC_TO_WALK;
   public static boolean FAKE_PLAYERS_DEBUG;
   public static boolean HIDE_TIMED_ITEM_TASK_LOG_FOR_FAKES;
   public static int FAKE_PLAYER_ROAMING_MAX_WH_CHECKS;
   public static int FAKE_PLAYER_ROAMING_MAX_SHOP_CHECKS;
   public static int FAKE_PLAYER_ROAMING_MAX_TELEPORT_CHECKS;
   public static int FAKE_PLAYER_ROAMING_MAX_BUFFER_CHECKS;
   public static int FAKE_PLAYER_ROAMING_MAX_PLAYER_CHECKS;
   public static int FAKE_PLAYER_ROAMING_MAX_PL_STORE_CHECKS;
   public static int FAKE_PLAYER_WH_CHECK_CHANCE;
   public static int FAKE_PLAYER_SHOP_CHECK_CHANCE;
   public static int FAKE_PLAYER_TELEPORT_CHECK_CHANCE;
   public static int FAKE_PLAYER_BUFFER_CHECK_CHANCE;
   public static int FAKE_PLAYER_RELAX_CHECK_CHANCE;
   public static int FAKE_PLAYER_WALK_CHECK_CHANCE;
   public static int FAKE_PLAYER_PLAYER_CHECK_CHANCE;
   public static int FAKE_PLAYER_PL_STORE_CHECK_CHANCE;
   public static List<FakePrivateBuyHolder> FAKE_PLAYER_PRIVATE_BUY_LIST = new ArrayList<>();
   public static List<FakePrivateSellHolder> FAKE_PLAYER_PRIVATE_SELL_LIST = new ArrayList<>();
   public static boolean CHECK_FAKE_PLAYERS_AREA;
   public static int CHECK_FAKE_PLAYERS_START_TIME;
   public static int CHECK_FAKE_PLAYERS_RESTART_TIME;
   public static int FAKE_PLAYER_ARROW;
   public static int FAKE_PLAYER_SOULSHOT;
   public static int FAKE_PLAYER_BLESSED_SOULSHOT;
   public static int DESPAWN_CITIZEN_RANDOM_TIME_1;
   public static int DESPAWN_CITIZEN_RANDOM_TIME_2;
   public static int DESPAWN_PVP_RANDOM_TIME_1;
   public static int DESPAWN_PVP_RANDOM_TIME_2;
   public static double FAKE_HP_REGEN_MULTIPLIER;
   public static double FAKE_MP_REGEN_MULTIPLIER;
   public static double FAKE_CP_REGEN_MULTIPLIER;
   public static double FAKE_VIRTUAL_POTION_HP_MULTIPLIER;
   public static double FAKE_VIRTUAL_POTION_MP_MULTIPLIER;
   public static double FAKE_VIRTUAL_POTION_CP_MULTIPLIER;
   public static String FAKE_PLAYER_COLOR_NAME;
   public static String FAKE_PLAYER_COLOR_TITLE;
   public static String FAKE_PLAYER_FIXED_TITLE;
   public static String FAKE_PLAYER_CLAN_FIXED_TITLE;
   public static String CLAN_ID;
   public static List<Integer> LIST_CLAN_ID;
   public static int MIN_ENCHANT_ARMOR;
   public static int MAX_ENCHANT_ARMOR;
   public static int MIN_ENCHANT_WEAPON;
   public static int MAX_ENCHANT_WEAPON;
   public static int MIN_ENCHANT_JEWEL;
   public static int MAX_ENCHANT_JEWEL;
   public static boolean FAKE_ENCHANT_PROFILE_ENABLED;
   public static int FAKE_ENCHANT_LOW_CHANCE;
   public static int FAKE_ENCHANT_MID_CHANCE;
   public static int FAKE_ENCHANT_HIGH_CHANCE;
   public static CrystalType FAKE_OLYMPIAD_ARMOR_GRADE;
   public static int FAKE_OLYMPIAD_MAX_ENCHANT;
   public static String PHANTOM_ROB_ARMOR_1;
   public static List<Integer> LIST_PHANTOM_ROB_ARMOR_1 = new ArrayList<>();
   public static String PHANTOM_ROB_ARMOR_2;
   public static List<Integer> LIST_PHANTOM_ROB_ARMOR_2 = new ArrayList<>();
   public static String PHANTOM_ROB_ARMOR_3;
   public static List<Integer> LIST_PHANTOM_ROB_ARMOR_3 = new ArrayList<>();
   public static String PHANTOM_ROB_ARMOR_4;
   public static List<Integer> LIST_PHANTOM_ROB_ARMOR_4 = new ArrayList<>();
   public static String PHANTOM_HEAVY_ARMOR_1;
   public static List<Integer> LIST_PHANTOM_HEAVY_ARMOR_1 = new ArrayList<>();
   public static String PHANTOM_HEAVY_ARMOR_2;
   public static List<Integer> LIST_PHANTOM_HEAVY_ARMOR_2 = new ArrayList<>();
   public static String PHANTOM_HEAVY_ARMOR_3;
   public static List<Integer> LIST_PHANTOM_HEAVY_ARMOR_3 = new ArrayList<>();
   public static String PHANTOM_HEAVY_ARMOR_4;
   public static List<Integer> LIST_PHANTOM_HEAVY_ARMOR_4 = new ArrayList<>();
   public static String PHANTOM_LIGHT_ARMOR_1;
   public static List<Integer> LIST_PHANTOM_LIGHT_ARMOR_1 = new ArrayList<>();
   public static String PHANTOM_LIGHT_ARMOR_2;
   public static List<Integer> LIST_PHANTOM_LIGHT_ARMOR_2 = new ArrayList<>();
   public static String PHANTOM_LIGHT_ARMOR_3;
   public static List<Integer> LIST_PHANTOM_LIGHT_ARMOR_3 = new ArrayList<>();
   public static String PHANTOM_LIGHT_ARMOR_4;
   public static List<Integer> LIST_PHANTOM_LIGHT_ARMOR_4 = new ArrayList<>();
   public static String FAKE_OLYMPIAD_ROBE_ARMOR_1;
   public static List<Integer> LIST_FAKE_OLYMPIAD_ROBE_ARMOR_1 = new ArrayList<>();
   public static String FAKE_OLYMPIAD_ROBE_ARMOR_2;
   public static List<Integer> LIST_FAKE_OLYMPIAD_ROBE_ARMOR_2 = new ArrayList<>();
   public static String FAKE_OLYMPIAD_ROBE_ARMOR_3;
   public static List<Integer> LIST_FAKE_OLYMPIAD_ROBE_ARMOR_3 = new ArrayList<>();
   public static String FAKE_OLYMPIAD_ROBE_ARMOR_4;
   public static List<Integer> LIST_FAKE_OLYMPIAD_ROBE_ARMOR_4 = new ArrayList<>();
   public static String FAKE_OLYMPIAD_HEAVY_ARMOR_1;
   public static List<Integer> LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_1 = new ArrayList<>();
   public static String FAKE_OLYMPIAD_HEAVY_ARMOR_2;
   public static List<Integer> LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_2 = new ArrayList<>();
   public static String FAKE_OLYMPIAD_HEAVY_ARMOR_3;
   public static List<Integer> LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_3 = new ArrayList<>();
   public static String FAKE_OLYMPIAD_HEAVY_ARMOR_4;
   public static List<Integer> LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_4 = new ArrayList<>();
   public static String FAKE_OLYMPIAD_LIGHT_ARMOR_1;
   public static List<Integer> LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_1 = new ArrayList<>();
   public static String FAKE_OLYMPIAD_LIGHT_ARMOR_2;
   public static List<Integer> LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_2 = new ArrayList<>();
   public static String FAKE_OLYMPIAD_LIGHT_ARMOR_3;
   public static List<Integer> LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_3 = new ArrayList<>();
   public static String FAKE_OLYMPIAD_LIGHT_ARMOR_4;
   public static List<Integer> LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_4 = new ArrayList<>();
   public static String FAKE_BOW_ID;
   public static List<Integer> LIST_FAKE_BOW;
   public static String FAKE_DAGGER_ID;
   public static List<Integer> LIST_FAKE_DAGGER;
   public static String FAKE_SWORD_ID;
   public static List<Integer> LIST_FAKE_SWORD;
   public static String FAKE_SPEAR_ID;
   public static List<Integer> LIST_FAKE_SPEAR;
   public static String FAKE_DUAL_ID;
   public static List<Integer> LIST_FAKE_DUAL;
   public static String FAKE_FIST_ID;
   public static List<Integer> LIST_FAKE_FIST;
   public static String FAKE_BIGSWORD_ID;
   public static List<Integer> LIST_FAKE_BIG_SWORD;
   public static String FAKE_MAGIC_ID;
   public static List<Integer> LIST_FAKE_MAGIC;
   public static String FAKE_SHIELD_ID;
   public static List<Integer> LIST_FAKE_SHIELD;
   public static String FAKE_OLYMPIAD_BOW_ID;
   public static List<Integer> LIST_FAKE_OLYMPIAD_BOW;
   public static String FAKE_OLYMPIAD_DAGGER_ID;
   public static List<Integer> LIST_FAKE_OLYMPIAD_DAGGER;
   public static String FAKE_OLYMPIAD_SWORD_ID;
   public static List<Integer> LIST_FAKE_OLYMPIAD_SWORD;
   public static String FAKE_OLYMPIAD_SPEAR_ID;
   public static List<Integer> LIST_FAKE_OLYMPIAD_SPEAR;
   public static String FAKE_OLYMPIAD_DUAL_ID;
   public static List<Integer> LIST_FAKE_OLYMPIAD_DUAL;
   public static String FAKE_OLYMPIAD_FIST_ID;
   public static List<Integer> LIST_FAKE_OLYMPIAD_FIST;
   public static String FAKE_OLYMPIAD_BIGSWORD_ID;
   public static List<Integer> LIST_FAKE_OLYMPIAD_BIG_SWORD;
   public static String FAKE_OLYMPIAD_MAGIC_ID;
   public static List<Integer> LIST_FAKE_OLYMPIAD_MAGIC;
   public static String FAKE_OLYMPIAD_SHIELD_ID;
   public static List<Integer> LIST_FAKE_OLYMPIAD_SHIELD;
   public static int[] PHANTOM_JEWELS_1;
   public static List<int[]> LIST_PHANTOM_JEWELS_1 = new ArrayList<>();
   public static int[] PHANTOM_JEWELS_2;
   public static List<int[]> LIST_PHANTOM_JEWELS_2 = new ArrayList<>();
   public static boolean ALLOW_FAKE_PLAYERS_ACCESSORY;
   public static String FAKE_ACCESSORY_ID;
   public static List<Integer> LIST_FAKE_ACCESSORY;
   public static String FAKE_OLYMPIAD_ACCESSORY_ID;
   public static List<Integer> LIST_FAKE_OLYMPIAD_ACCESSORY;
   public static boolean ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS;
   public static String NUKER_BUFFER;
   public static List<Integer> NUKER_BUFFER_LIST = new ArrayList<>();
   public static String ARCHER_BUFFER;
   public static List<Integer> ARCHER_BUFFER_LIST = new ArrayList<>();
   public static String WARRIOR_BUFFER;
   public static List<Integer> WARRIOR_BUFFER_LIST = new ArrayList<>();
   public static String TANKER_BUFFER;
   public static List<Integer> TANKER_BUFFER_LIST = new ArrayList<>();
   public static String DAGGER_BUFFER;
   public static List<Integer> DAGGER_BUFFER_LIST = new ArrayList<>();
   public static String RANDOM_BUFFER;
   public static List<Integer> RANDOM_BUFFER_LIST = new ArrayList<>();
   public static boolean ALLOW_FAKE_PLAYERS_HENNA;
   public static String NUKER_HENNA;
   public static List<Integer> NUKER_HENNA_LIST = new ArrayList<>();
   public static String ARCHER_HENNA;
   public static List<Integer> ARCHER_HENNA_LIST = new ArrayList<>();
   public static String WARRIOR_HENNA;
   public static List<Integer> WARRIOR_HENNA_LIST = new ArrayList<>();
   public static String DAGGER_HENNA;
   public static List<Integer> DAGGER_HENNA_LIST = new ArrayList<>();
   public static String TANKER_HENNA;
   public static List<Integer> TANKER_HENNA_LIST = new ArrayList<>();
   public static String FAKE_POTIONS;
   public static ArrayList<Integer> FAKE_POTIONS_SKILLS = new ArrayList<>();
   public static boolean ALLOW_FAKE_PLAYER_TVT;
   public static int TVT_FAKE_PLAYER_COUNT_MIN;
   public static int TVT_FAKE_PLAYER_COUNT_MAX;
   public static List<Location> TVT_FAKE_PLAYER_LIST_LOCS = new ArrayList<>();
   public static boolean ALLOW_FAKE_PLAYER_CTF;
   public static int CTF_FAKE_PLAYER_COUNT_MIN;
   public static int CTF_FAKE_PLAYER_COUNT_MAX;
   public static List<Location> CTF_FAKE_PLAYER_LIST_LOCS = new ArrayList<>();
   public static boolean ALLOW_FAKE_PLAYER_KTB;
   public static int KTB_FAKE_PLAYER_COUNT_MIN;
   public static int KTB_FAKE_PLAYER_COUNT_MAX;
   public static int KTB_FAKE_JOIN_CHANCE;
   public static List<Location> KTB_FAKE_PLAYER_LIST_LOCS = new ArrayList<>();
   public static int TVT_FAKE_JOIN_CHANCE;
   public static int CTF_FAKE_JOIN_CHANCE;
   public static boolean ALLOW_FAKE_PLAYER_TOURNAMENT;
   public static int TOURNAMENT_FAKE_COUNT_MIN;
   public static int TOURNAMENT_FAKE_COUNT_MAX;
   public static int TOURNAMENT_FAKE_JOIN_CHANCE;
   public static List<Location> FAKE_TOURNAMENT_LIST_LOCS = new ArrayList<>();
   public static boolean ALLOW_FAKE_PLAYER_ANONYMOUS_PVP;
   public static int ANONYMOUS_PVP_FAKE_PLAYER_COUNT_MIN;
   public static int ANONYMOUS_PVP_FAKE_PLAYER_COUNT_MAX;
   public static int ANONYMOUS_PVP_FAKE_JOIN_CHANCE;
   public static boolean ALLOW_FAKE_PLAYER_ULTIMATE_ZONE;
   public static int ULTIMATE_ZONE_FAKE_PLAYER_COUNT_MIN;
   public static int ULTIMATE_ZONE_FAKE_PLAYER_COUNT_MAX;
   public static int ULTIMATE_ZONE_FAKE_JOIN_CHANCE;
   public static boolean ALLOW_FAKE_PLAYER_AUTO_SPAWN;
   public static int AUTO_SPAWN_FAKE_COUNT_MIN;
   public static int AUTO_SPAWN_FAKE_COUNT_MAX;
   public static List<Location> FAKE_AUTO_SPAWN_LIST_LOCS = new ArrayList<>();
   public static int AUTO_SPAWN_DELAY_TIME;
   public static int AUTO_SPAWN_ARCHER_PERCENT;
   public static int AUTO_SPAWN_NUKER_PERCENT;
   public static int AUTO_SPAWN_WARRIOR_PERCENT;
   public static int AUTO_SPAWN_DAGGER_PERCENT;
   public static int AUTO_SPAWN_TANKER_PERCENT;
   public static boolean TOWN_ALLOW_FAKE_PLAYER_AUTO_SPAWN;
   public static int TOWN_AUTO_SPAWN_FAKE_COUNT_MIN;
   public static int TOWN_AUTO_SPAWN_FAKE_COUNT_MAX;
   public static List<Location> TOWN_AUTO_SPAWN_LIST_LOCS = new ArrayList<>();
   public static int TOWN_AUTO_SPAWN_DELAY_TIME;
   public static int FAKE_TRADE_ACCEPT_CHANCE;
   public static int FAKE_TRADE_CONFIRM_CHANCE;
   public static int FAKE_TRADE_ACCEPT_DELAY_MIN;
   public static int FAKE_TRADE_ACCEPT_DELAY_MAX;
   public static int FAKE_TRADE_CONFIRM_DELAY_MIN;
   public static int FAKE_TRADE_CONFIRM_DELAY_MAX;
   public static int FAKE_CITIZEN_ROAMING_MIN_INTERVAL;
   public static int FAKE_CITIZEN_ROAMING_MAX_INTERVAL;
   public static int FAKE_CITIZEN_RANDOM_MOVE_MIN_INTERVAL;
   public static int FAKE_CITIZEN_RANDOM_MOVE_MAX_INTERVAL;
   public static int FAKE_TELL_RESPOND_CHANCE;
   public static int FAKE_TELL_RESPONSE_DELAY_MIN;
   public static int FAKE_TELL_RESPONSE_DELAY_MAX;
   public static int FAKE_TELL_CONVERSATION_TIMEOUT;
   public static int FAKE_TELL_INITIATE_AFTER_SILENCE;
   public static int FAKE_TELL_INITIATE_CHANCE;
   public static int FAKE_TELL_MIN_GAP_BETWEEN_MESSAGES;
   public static int FAKE_PARTY_RESPOND_CHANCE;
   public static boolean ALLOW_FAKE_CLAN_PLAYERS;
   public static boolean FAKE_CLAN_LF_INVITE_ENABLED;
   public static int FAKE_CLAN_LF_INVITE_CHANCE;
   public static int FAKE_CLAN_LF_INVITE_COOLDOWN_SECONDS;
   public static String FAKE_CLAN_LF_INVITE_TRIGGERS;
   public static int FAKE_FARM_RADIUS;
   public static int FAKE_PARTY_FARM_RADIUS;
   public static boolean PHANTOM_RES;
   public static int FAKE_PARTY_RES_CHAT_CHANCE;
   public static int FAKE_PARTY_RES_ACCEPT_CHANCE;
   public static int FAKE_PARTY_RES_REQUEST_MIN_ITERATION;
   public static int FAKE_PARTY_RES_REQUEST_MAX_ITERATION;
   public static int FAKE_PARTY_RES_TIMEOUT_BASE_ITERATIONS;
   public static int FAKE_PARTY_RES_TIMEOUT_EXTRA_PVP_ITERATIONS;
   public static int FAKE_PARTY_RES_TIMEOUT_EXTRA_RAID_ITERATIONS;
   public static int FAKE_PARTY_RES_TIMEOUT_EXTRA_FARM_ITERATIONS;
   public static String FAKE_PARTY_RES_CHAT_MESSAGES;
   public static boolean SAVE_ADMIN_SPAWNED_FAKES;
   public static boolean FAKE_CLAN_CLEANUP_ENABLED;
   public static int FAKE_CLAN_CLEANUP_FILL_PERCENT;
   public static int FAKE_CLAN_PRESENCE_ONLINE_MIN_PERCENT;
   public static int FAKE_CLAN_PRESENCE_ONLINE_MAX_PERCENT;
   public static int FAKE_CLAN_RANDOM_TANK_PERCENT;
   public static int FAKE_CLAN_RANDOM_ARCHER_PERCENT;
   public static int FAKE_CLAN_RANDOM_MAGE_PERCENT;
   public static boolean FAKE_FARM_HIT_BACK;
   public static boolean FAKE_PARTY_IGNORE_XP_PENALTY;
   public static boolean TOURNAMENT_FAKE_JOIN_1X1;
   public static boolean TOURNAMENT_FAKE_JOIN_2X2;
   public static boolean TOURNAMENT_FAKE_JOIN_5X5;
   public static int TOURNAMENT_FAKE_JOIN_WEIGHT_1X1;
   public static int TOURNAMENT_FAKE_JOIN_WEIGHT_2X2;
   public static int TOURNAMENT_FAKE_JOIN_WEIGHT_5X5;
   public static int TOURNAMENT_FAKE_CHAT_INITIAL_DELAY_MS;
   public static int TOURNAMENT_FAKE_CHAT_INTERVAL_MS;
   
   // Advanced Combat AI Settings
   public static boolean FAKE_ADVANCED_POSITIONING;
   public static boolean FAKE_CC_CHAIN_ENABLED;
   public static boolean FAKE_PARTY_PVP_COORDINATION;
   public static boolean FAKE_EMOTION_ENABLED;
   public static int FAKE_EMOTION_CHANGE_CHANCE;
   public static boolean FAKE_TARGET_PRIORITY_ENABLED;
   public static boolean FAKE_PRIORITIZE_HEALERS;
   public static boolean FAKE_PRIORITIZE_MAGES;
   public static boolean FAKE_KITING_ENABLED;
   public static int FAKE_KITE_DISTANCE;
   public static boolean FAKE_STRAFE_ENABLED;
   public static int FAKE_STRAFE_RADIUS;
   
   // Advanced Chat Settings
   public static boolean FAKE_CONTEXT_CHAT_ENABLED;
   public static boolean FAKE_CHAT_MEMORY_ENABLED;
   public static int FAKE_CHAT_MEMORY_SIZE;
   
   public static int getFakeSiegeMaxAttackersForCastle(int castleId)
   {
      switch (castleId)
      {
         case 1: return FAKE_SIEGE_MAX_ATTACKERS_GLUDIO;
         case 2: return FAKE_SIEGE_MAX_ATTACKERS_DION;
         case 3: return FAKE_SIEGE_MAX_ATTACKERS_GIRAN;
         case 4: return FAKE_SIEGE_MAX_ATTACKERS_OREN;
         case 5: return FAKE_SIEGE_MAX_ATTACKERS_ADEN;
         case 6: return FAKE_SIEGE_MAX_ATTACKERS_INNADRIL;
         case 7: return FAKE_SIEGE_MAX_ATTACKERS_GODDARD;
         case 8: return FAKE_SIEGE_MAX_ATTACKERS_RUNE;
         case 9: return FAKE_SIEGE_MAX_ATTACKERS_SCHUTT;
         default: return FAKE_SIEGE_MAX_ATTACKERS;
      }
   }
   
   public static int getFakeSiegeMaxDefendersForCastle(int castleId)
   {
      switch (castleId)
      {
         case 1: return FAKE_SIEGE_MAX_DEFENDERS_GLUDIO;
         case 2: return FAKE_SIEGE_MAX_DEFENDERS_DION;
         case 3: return FAKE_SIEGE_MAX_DEFENDERS_GIRAN;
         case 4: return FAKE_SIEGE_MAX_DEFENDERS_OREN;
         case 5: return FAKE_SIEGE_MAX_DEFENDERS_ADEN;
         case 6: return FAKE_SIEGE_MAX_DEFENDERS_INNADRIL;
         case 7: return FAKE_SIEGE_MAX_DEFENDERS_GODDARD;
         case 8: return FAKE_SIEGE_MAX_DEFENDERS_RUNE;
         case 9: return FAKE_SIEGE_MAX_DEFENDERS_SCHUTT;
         default: return FAKE_SIEGE_MAX_DEFENDERS;
      }
   }

   public static Location getFakeSiegeAttackerSpawnForCastle(int castleId)
   {
      switch (castleId)
      {
         case 1: return FAKE_SIEGE_ATTACKER_SPAWN_GLUDIO;
         case 2: return FAKE_SIEGE_ATTACKER_SPAWN_DION;
         case 3: return FAKE_SIEGE_ATTACKER_SPAWN_GIRAN;
         case 4: return FAKE_SIEGE_ATTACKER_SPAWN_OREN;
         case 5: return FAKE_SIEGE_ATTACKER_SPAWN_ADEN;
         case 6: return FAKE_SIEGE_ATTACKER_SPAWN_INNADRIL;
         case 7: return FAKE_SIEGE_ATTACKER_SPAWN_GODDARD;
         case 8: return FAKE_SIEGE_ATTACKER_SPAWN_RUNE;
         case 9: return FAKE_SIEGE_ATTACKER_SPAWN_SCHUTT;
         default: return null;
      }
   }
   public static boolean FAKE_MODERN_SLANG_ENABLED;
   public static int FAKE_CHAT_RESPONSE_DELAY_MIN;
   public static int FAKE_CHAT_RESPONSE_DELAY_MAX;
   public static int FAKE_CHAT_RESPOND_GREETING;
   public static int FAKE_CHAT_RESPOND_QUESTION;
   public static int FAKE_CHAT_RESPOND_TRADE;
   public static int FAKE_CHAT_RESPOND_PVP;
   
   // Olympiad Settings
   public static boolean FAKE_OLYMPIAD_ENABLED;
   public static int FAKE_OLYMPIAD_MIN_LEVEL;
   public static int FAKE_OLYMPIAD_REGISTER_CHANCE;
   public static int FAKE_OLYMPIAD_CLASSED_COUNT;
   public static int FAKE_OLYMPIAD_NON_CLASSED_COUNT;
   public static int FAKE_OLYMPIAD_MAX_PER_CLASS;
   public static boolean FAKE_OLYMPIAD_ALLOW_1V1;
   public static boolean FAKE_OLYMPIAD_REGISTER_PHANTOM_ON_TIMEOUT;
   public static int FAKE_OLYMPIAD_MATCH_TIMEOUT_SECONDS;
   public static boolean FAKE_OLYMPIAD_NO_DESPAWN;
   
   // Raid Helper Settings
   public static boolean FAKE_RAID_HELPER_ENABLED;
   public static int FAKE_RAID_HELPER_MIN_PLAYER_LEVEL;
   public static int FAKE_RAID_HELP_REQUEST_CHANCE;
   public static int FAKE_RAID_MAX_HELPERS;
   public static int FAKE_RAID_HELPER_SPAWN_RANGE;
   public static int FAKE_RAID_HELPER_DESPAWN_DELAY;

   // Siege Settings
   public static boolean FAKE_SIEGE_ENABLED;
   public static int FAKE_SIEGE_MAX_ATTACKERS;
   public static int FAKE_SIEGE_MAX_DEFENDERS;
   public static int FAKE_SIEGE_MAX_ATTACKERS_GLUDIO;
   public static int FAKE_SIEGE_MAX_ATTACKERS_DION;
   public static int FAKE_SIEGE_MAX_ATTACKERS_GIRAN;
   public static int FAKE_SIEGE_MAX_ATTACKERS_OREN;
   public static int FAKE_SIEGE_MAX_ATTACKERS_ADEN;
   public static int FAKE_SIEGE_MAX_ATTACKERS_INNADRIL;
   public static int FAKE_SIEGE_MAX_ATTACKERS_GODDARD;
   public static int FAKE_SIEGE_MAX_ATTACKERS_RUNE;
   public static int FAKE_SIEGE_MAX_ATTACKERS_SCHUTT;
   public static int FAKE_SIEGE_MAX_DEFENDERS_GLUDIO;
   public static int FAKE_SIEGE_MAX_DEFENDERS_DION;
   public static int FAKE_SIEGE_MAX_DEFENDERS_GIRAN;
   public static int FAKE_SIEGE_MAX_DEFENDERS_OREN;
   public static int FAKE_SIEGE_MAX_DEFENDERS_ADEN;
   public static int FAKE_SIEGE_MAX_DEFENDERS_INNADRIL;
   public static int FAKE_SIEGE_MAX_DEFENDERS_GODDARD;
   public static int FAKE_SIEGE_MAX_DEFENDERS_RUNE;
   public static int FAKE_SIEGE_MAX_DEFENDERS_SCHUTT;
   public static int FAKE_SIEGE_JOIN_CHANCE;
   public static boolean FAKE_SIEGE_STICKY_JOIN_CHANCE;
   public static int FAKE_SIEGE_ATTACKER_RESPAWN_SECONDS;
   public static int FAKE_SIEGE_ATTACKER_CLAN_SPREAD_RADIUS;
   public static int FAKE_SIEGE_ATTACKER_MEMBER_SPREAD_RADIUS;
   public static int FAKE_SIEGE_ATTACKER_RALLY_SECONDS;
   public static Location FAKE_SIEGE_ATTACKER_SPAWN_GLUDIO;
   public static Location FAKE_SIEGE_ATTACKER_SPAWN_DION;
   public static Location FAKE_SIEGE_ATTACKER_SPAWN_GIRAN;
   public static Location FAKE_SIEGE_ATTACKER_SPAWN_OREN;
   public static Location FAKE_SIEGE_ATTACKER_SPAWN_ADEN;
   public static Location FAKE_SIEGE_ATTACKER_SPAWN_INNADRIL;
   public static Location FAKE_SIEGE_ATTACKER_SPAWN_GODDARD;
   public static Location FAKE_SIEGE_ATTACKER_SPAWN_RUNE;
   public static Location FAKE_SIEGE_ATTACKER_SPAWN_SCHUTT;
   public static int FAKE_SIEGE_DEFENDER_RESPAWN_SECONDS;
   public static int FAKE_SIEGE_RESPAWN_WAVE_WINDOW_MS;
   public static int FAKE_SIEGE_RESPAWN_WAVE_JITTER_MS;

   // Clan War Settings
   public static boolean FAKE_CLAN_WAR_ENABLED;
   public static int FAKE_CLAN_WAR_ATTACK_CHANCE;
   public static int FAKE_CLAN_WAR_SEARCH_RADIUS;
   public static boolean FAKE_CLAN_WAR_ATTACK_FAKES;

   // Clan invite acceptance
   public static boolean FAKE_ALLOW_CLAN_INVITE;
   public static int FAKE_CLAN_INVITE_ACCEPT_CHANCE;

   // Normal chat settings
   public static boolean FAKE_NORMAL_CHAT_ENABLED;
   public static int FAKE_NORMAL_CHAT_CHANCE;
   public static int FAKE_NORMAL_CHAT_MIN_INTERVAL;
   public static int FAKE_NORMAL_CHAT_MAX_INTERVAL;
   public static int FAKE_NORMAL_CHAT_TO_REAL_CHANCE;
   public static int FAKE_NORMAL_CHAT_TO_FAKE_CHANCE;
   
   // Human-Like Behavior Settings
   public static boolean FAKE_HUMAN_REACTION_DELAY_ENABLED;
   public static int FAKE_HUMAN_REACTION_DELAY_MIN;
   public static int FAKE_HUMAN_REACTION_DELAY_MAX;
   public static boolean FAKE_HUMAN_ERROR_ENABLED;
   public static int FAKE_HUMAN_ERROR_RATE;

   /**
    * Value of {@code AutoSpawnAllowFakePlayer}. When false, server skips world/town auto-spawn and
    * fakes must not register for Olympiad or automated events (TvT, CTF, tournament, etc.).
 * @return 
    */
   public static boolean isAutomatedFakePopulationEnabled() {
      return ALLOW_FAKE_PLAYER_AUTO_SPAWN;
   }

   public static void init() {
      ExProperties phantom = load("./config/custom/phantom/FakePlayers.properties");
      ExProperties phantomClan = load(PHANTOM_CLAN_FILE);
      FAKE_CHANCE_TO_TALK_SOCIAL = phantom.getProperty("FakeTalkChance", 3000);
      FAKE_CHANCE_TO_TALK_DIED = phantom.getProperty("FakeTalkChanceDied", 3000);
      FAKE_CHANCE_TO_TALK_KILLED = phantom.getProperty("FakeTalkChanceKilled", 3000);
      FAKE_NORMAL_CHAT_ENABLED = phantom.getProperty("FakeNormalChatEnabled", true);
      FAKE_NORMAL_CHAT_CHANCE = phantom.getProperty("FakeNormalChatChance", 70);
      FAKE_NORMAL_CHAT_MIN_INTERVAL = phantom.getProperty("FakeNormalChatMinInterval", 30);
      FAKE_NORMAL_CHAT_MAX_INTERVAL = phantom.getProperty("FakeNormalChatMaxInterval", 120);
      FAKE_NORMAL_CHAT_TO_REAL_CHANCE = phantom.getProperty("FakeNormalChatToRealChance", 30);
      FAKE_NORMAL_CHAT_TO_FAKE_CHANCE = phantom.getProperty("FakeNormalChatToFakeChance", 20);
      FAKE_SOCIAL_CHANCE = phantom.getProperty("FakeSocialChance", 3000);
      FAKE_SIT_CHANCE = phantom.getProperty("FakeSitChance", 10);
      FAKE_PLAYER_ALLOWED_NPC_TO_WALK = phantom.getProperty("FakeRoamingNpcs", new int[0]);
      FAKE_PLAYERS_DEBUG = phantom.getProperty("FakePlayerDebug", false);
      HIDE_TIMED_ITEM_TASK_LOG_FOR_FAKES = phantom.getProperty("HideTimedItemTaskLogForFakes", true);
      FAKE_PLAYER_ROAMING_MAX_WH_CHECKS = phantom.getProperty("FakeRoamingMaxWhChecks", 2);
      FAKE_PLAYER_ROAMING_MAX_SHOP_CHECKS = phantom.getProperty("FakeRoamingMaxShopChecks", 2);
      FAKE_PLAYER_ROAMING_MAX_TELEPORT_CHECKS = phantom.getProperty("FakeRoamingMaxTeleportChecks", 2);
      FAKE_PLAYER_ROAMING_MAX_BUFFER_CHECKS = phantom.getProperty("FakeRoamingMaxBufferChecks", 2);
      FAKE_PLAYER_ROAMING_MAX_PLAYER_CHECKS = phantom.getProperty("FakeRoamingMaxPlayerChecks", 2);
      FAKE_PLAYER_ROAMING_MAX_PL_STORE_CHECKS = phantom.getProperty("FakeRoamingMaxPlayerStoreChecks", 2);
      FAKE_PLAYER_WH_CHECK_CHANCE = phantom.getProperty("FakeWarehouseChecksChance", 2);
      FAKE_PLAYER_SHOP_CHECK_CHANCE = phantom.getProperty("FakeShopChecksChance", 2);
      FAKE_PLAYER_TELEPORT_CHECK_CHANCE = phantom.getProperty("FakeTeleportChecksChance", 2);
      FAKE_PLAYER_BUFFER_CHECK_CHANCE = phantom.getProperty("FakeBufferChecksChance", 2);
      FAKE_PLAYER_RELAX_CHECK_CHANCE = phantom.getProperty("FakeRelaxChecksChance", 2);
      FAKE_PLAYER_WALK_CHECK_CHANCE = phantom.getProperty("FakeWalkAroundChecksChance", 2);
      FAKE_PLAYER_PLAYER_CHECK_CHANCE = phantom.getProperty("FakePlayerAroundChecksChance", 2);
      FAKE_PLAYER_PL_STORE_CHECK_CHANCE = phantom.getProperty("FakePlayerStoreAroundChecksChance", 2);
      FAKE_TRADE_ACCEPT_CHANCE = phantom.getProperty("FakeTradeAcceptChance", 60);
      FAKE_TRADE_CONFIRM_CHANCE = phantom.getProperty("FakeTradeConfirmChance", 70);
      FAKE_TRADE_ACCEPT_DELAY_MIN = phantom.getProperty("FakeTradeAcceptDelayMin", 2000);
      FAKE_TRADE_ACCEPT_DELAY_MAX = phantom.getProperty("FakeTradeAcceptDelayMax", 6000);
      FAKE_TRADE_CONFIRM_DELAY_MIN = phantom.getProperty("FakeTradeConfirmDelayMin", 3000);
      FAKE_TRADE_CONFIRM_DELAY_MAX = phantom.getProperty("FakeTradeConfirmDelayMax", 8000);
      FAKE_CITIZEN_ROAMING_MIN_INTERVAL = phantom.getProperty("FakeCitizenRoamingMinInterval", 15);
      FAKE_CITIZEN_ROAMING_MAX_INTERVAL = phantom.getProperty("FakeCitizenRoamingMaxInterval", 40);
      FAKE_CITIZEN_RANDOM_MOVE_MIN_INTERVAL = phantom.getProperty("FakeCitizenRandomMoveMinInterval", 5);
      FAKE_CITIZEN_RANDOM_MOVE_MAX_INTERVAL = phantom.getProperty("FakeCitizenRandomMoveMaxInterval", 15);
      FAKE_TELL_RESPOND_CHANCE = phantom.getProperty("FakeTellRespondChance", 50);
      FAKE_TELL_RESPONSE_DELAY_MIN = phantom.getProperty("FakeTellResponseDelayMin", 5);
      FAKE_TELL_RESPONSE_DELAY_MAX = phantom.getProperty("FakeTellResponseDelayMax", 25);
      FAKE_TELL_CONVERSATION_TIMEOUT = phantom.getProperty("FakeTellConversationTimeout", 300);
      FAKE_TELL_INITIATE_AFTER_SILENCE = phantom.getProperty("FakeTellInitiateAfterSilence", 60);
      FAKE_TELL_INITIATE_CHANCE = phantom.getProperty("FakeTellInitiateChance", 15);
      FAKE_TELL_MIN_GAP_BETWEEN_MESSAGES = phantom.getProperty("FakeTellMinGapBetweenMessages", 45);
      FAKE_PARTY_RESPOND_CHANCE = phantom.getProperty("FakePartyRespondChance", 40);
      ALLOW_FAKE_CLAN_PLAYERS = getMergedBoolean(phantomClan, phantom, "AllowFakeClanPlayers", true);
      FAKE_CLAN_LF_INVITE_ENABLED = phantom.getProperty("FakeClanLfInviteEnabled", true);
      FAKE_CLAN_LF_INVITE_CHANCE = phantom.getProperty("FakeClanLfInviteChance", 45);
      FAKE_CLAN_LF_INVITE_COOLDOWN_SECONDS = phantom.getProperty("FakeClanLfInviteCooldownSeconds", 120);
      FAKE_CLAN_LF_INVITE_TRIGGERS = phantom.getProperty("FakeClanLfInviteTriggers", "lf clan;looking for clan;need clan;ищу клан");
      FAKE_FARM_RADIUS = phantom.getProperty("FakeFarmMobRadius", 1000);
      FAKE_PARTY_FARM_RADIUS = phantom.getProperty("FakePartyFarmMobRadius", 1500);
      PHANTOM_RES = phantom.getProperty("PhantomRes", true);
      FAKE_PARTY_RES_CHAT_CHANCE = phantom.getProperty("FakePartyResChatChance", 80);
      FAKE_PARTY_RES_ACCEPT_CHANCE = phantom.getProperty("FakePartyResAcceptChance", 50);
      FAKE_PARTY_RES_REQUEST_MIN_ITERATION = phantom.getProperty("FakePartyResRequestMinIteration", 1);
      FAKE_PARTY_RES_REQUEST_MAX_ITERATION = phantom.getProperty("FakePartyResRequestMaxIteration", 3);
      FAKE_PARTY_RES_TIMEOUT_BASE_ITERATIONS = phantom.getProperty("FakePartyResTimeoutBaseIterations", 10);
      FAKE_PARTY_RES_TIMEOUT_EXTRA_PVP_ITERATIONS = phantom.getProperty("FakePartyResTimeoutExtraPvpIterations", 8);
      FAKE_PARTY_RES_TIMEOUT_EXTRA_RAID_ITERATIONS = phantom.getProperty("FakePartyResTimeoutExtraRaidIterations", 15);
      FAKE_PARTY_RES_TIMEOUT_EXTRA_FARM_ITERATIONS = phantom.getProperty("FakePartyResTimeoutExtraFarmIterations", 5);
      FAKE_PARTY_RES_CHAT_MESSAGES = phantom.getProperty("FakePartyResChatMessages", "res plz;can res?;res me pls;need res");
      FAKE_CLAN_LF_INVITE_ENABLED = getMergedBoolean(phantomClan, phantom, "FakeClanLfInviteEnabled", FAKE_CLAN_LF_INVITE_ENABLED);
      FAKE_CLAN_LF_INVITE_CHANCE = getMergedInt(phantomClan, phantom, "FakeClanLfInviteChance", FAKE_CLAN_LF_INVITE_CHANCE);
      FAKE_CLAN_LF_INVITE_COOLDOWN_SECONDS = getMergedInt(phantomClan, phantom, "FakeClanLfInviteCooldownSeconds", FAKE_CLAN_LF_INVITE_COOLDOWN_SECONDS);
      FAKE_CLAN_LF_INVITE_TRIGGERS = getMergedString(phantomClan, phantom, "FakeClanLfInviteTriggers", FAKE_CLAN_LF_INVITE_TRIGGERS);
      SAVE_ADMIN_SPAWNED_FAKES = phantom.getProperty("SaveAdminSpawnedFakes", false);
      FAKE_CLAN_CLEANUP_ENABLED = phantom.getProperty("FakeClanCleanupEnabled", true);
      FAKE_CLAN_CLEANUP_FILL_PERCENT = Math.max(1, Math.min(100, phantom.getProperty("FakeClanCleanupFillPercent", 60)));
      FAKE_CLAN_PRESENCE_ONLINE_MIN_PERCENT = Math.max(0, Math.min(100, phantom.getProperty("FakeClanPresenceOnlineMinPercent", 25)));
      FAKE_CLAN_PRESENCE_ONLINE_MAX_PERCENT = Math.max(0, Math.min(100, phantom.getProperty("FakeClanPresenceOnlineMaxPercent", 55)));
      if (FAKE_CLAN_PRESENCE_ONLINE_MAX_PERCENT < FAKE_CLAN_PRESENCE_ONLINE_MIN_PERCENT) {
         FAKE_CLAN_PRESENCE_ONLINE_MAX_PERCENT = FAKE_CLAN_PRESENCE_ONLINE_MIN_PERCENT;
      }
      FAKE_CLAN_RANDOM_TANK_PERCENT = Math.max(0, Math.min(100, phantom.getProperty("FakeClanRandomTankPercent", 12)));
      FAKE_CLAN_RANDOM_ARCHER_PERCENT = Math.max(0, Math.min(100, phantom.getProperty("FakeClanRandomArcherPercent", 22)));
      FAKE_CLAN_RANDOM_MAGE_PERCENT = Math.max(0, Math.min(100, phantom.getProperty("FakeClanRandomMagePercent", 22)));
      SAVE_ADMIN_SPAWNED_FAKES = getMergedBoolean(phantomClan, phantom, "SaveAdminSpawnedFakes", SAVE_ADMIN_SPAWNED_FAKES);
      FAKE_CLAN_CLEANUP_ENABLED = getMergedBoolean(phantomClan, phantom, "FakeClanCleanupEnabled", FAKE_CLAN_CLEANUP_ENABLED);
      FAKE_CLAN_CLEANUP_FILL_PERCENT = Math.max(1, Math.min(100, getMergedInt(phantomClan, phantom, "FakeClanCleanupFillPercent", FAKE_CLAN_CLEANUP_FILL_PERCENT)));
      FAKE_CLAN_PRESENCE_ONLINE_MIN_PERCENT = Math.max(0, Math.min(100, getMergedInt(phantomClan, phantom, "FakeClanPresenceOnlineMinPercent", FAKE_CLAN_PRESENCE_ONLINE_MIN_PERCENT)));
      FAKE_CLAN_PRESENCE_ONLINE_MAX_PERCENT = Math.max(0, Math.min(100, getMergedInt(phantomClan, phantom, "FakeClanPresenceOnlineMaxPercent", FAKE_CLAN_PRESENCE_ONLINE_MAX_PERCENT)));
      if (FAKE_CLAN_PRESENCE_ONLINE_MAX_PERCENT < FAKE_CLAN_PRESENCE_ONLINE_MIN_PERCENT) {
         FAKE_CLAN_PRESENCE_ONLINE_MAX_PERCENT = FAKE_CLAN_PRESENCE_ONLINE_MIN_PERCENT;
      }
      FAKE_CLAN_RANDOM_TANK_PERCENT = Math.max(0, Math.min(100, getMergedInt(phantomClan, phantom, "FakeClanRandomTankPercent", FAKE_CLAN_RANDOM_TANK_PERCENT)));
      FAKE_CLAN_RANDOM_ARCHER_PERCENT = Math.max(0, Math.min(100, getMergedInt(phantomClan, phantom, "FakeClanRandomArcherPercent", FAKE_CLAN_RANDOM_ARCHER_PERCENT)));
      FAKE_CLAN_RANDOM_MAGE_PERCENT = Math.max(0, Math.min(100, getMergedInt(phantomClan, phantom, "FakeClanRandomMagePercent", FAKE_CLAN_RANDOM_MAGE_PERCENT)));
      FAKE_FARM_HIT_BACK = phantom.getProperty("FakeFarmHitBack", true);
      FAKE_PARTY_IGNORE_XP_PENALTY = phantom.getProperty("FakePartyIgnoreXpPenalty", true);
      TOURNAMENT_FAKE_JOIN_1X1 = phantom.getProperty("TournamentFakeJoin1x1", true);
      TOURNAMENT_FAKE_JOIN_2X2 = phantom.getProperty("TournamentFakeJoin2x2", false);
      TOURNAMENT_FAKE_JOIN_5X5 = phantom.getProperty("TournamentFakeJoin5x5", false);
      TOURNAMENT_FAKE_JOIN_WEIGHT_1X1 = Math.max(0, phantom.getProperty("TournamentFakeJoinWeight1x1", 34));
      TOURNAMENT_FAKE_JOIN_WEIGHT_2X2 = Math.max(0, phantom.getProperty("TournamentFakeJoinWeight2x2", 33));
      TOURNAMENT_FAKE_JOIN_WEIGHT_5X5 = Math.max(0, phantom.getProperty("TournamentFakeJoinWeight5x5", 33));
      TOURNAMENT_FAKE_CHAT_INITIAL_DELAY_MS = Math.max(1000, phantom.getProperty("TournamentFakeChatInitialDelayMs", 8000));
      TOURNAMENT_FAKE_CHAT_INTERVAL_MS = Math.max(2000, phantom.getProperty("TournamentFakeChatIntervalMs", 12000));
      
      // Advanced Combat AI Settings
      FAKE_ADVANCED_POSITIONING = phantom.getProperty("FakeAdvancedPositioning", true);
      FAKE_CC_CHAIN_ENABLED = phantom.getProperty("FakeCCChainEnabled", true);
      FAKE_PARTY_PVP_COORDINATION = phantom.getProperty("FakePartyPvPCoordination", true);
      FAKE_EMOTION_ENABLED = phantom.getProperty("FakeEmotionEnabled", true);
      FAKE_EMOTION_CHANGE_CHANCE = phantom.getProperty("FakeEmotionChangeChance", 30);
      FAKE_TARGET_PRIORITY_ENABLED = phantom.getProperty("FakeTargetPriorityEnabled", true);
      FAKE_PRIORITIZE_HEALERS = phantom.getProperty("FakePrioritizeHealers", true);
      FAKE_PRIORITIZE_MAGES = phantom.getProperty("FakePrioritizeMages", true);
      FAKE_KITING_ENABLED = phantom.getProperty("FakeKitingEnabled", true);
      FAKE_KITE_DISTANCE = phantom.getProperty("FakeKiteDistance", 300);
      FAKE_STRAFE_ENABLED = phantom.getProperty("FakeStrafeEnabled", true);
      FAKE_STRAFE_RADIUS = phantom.getProperty("FakeStrafeRadius", 150);
      
      // Advanced Chat Settings
      FAKE_CONTEXT_CHAT_ENABLED = phantom.getProperty("FakeContextChatEnabled", true);
      FAKE_CHAT_MEMORY_ENABLED = phantom.getProperty("FakeChatMemoryEnabled", true);
      FAKE_CHAT_MEMORY_SIZE = phantom.getProperty("FakeChatMemorySize", 20);
      FAKE_MODERN_SLANG_ENABLED = phantom.getProperty("FakeModernSlangEnabled", true);
      FAKE_CHAT_RESPONSE_DELAY_MIN = phantom.getProperty("FakeChatResponseDelayMin", 2000);
      FAKE_CHAT_RESPONSE_DELAY_MAX = phantom.getProperty("FakeChatResponseDelayMax", 8000);
      FAKE_CHAT_RESPOND_GREETING = phantom.getProperty("FakeChatRespondGreeting", 80);
      FAKE_CHAT_RESPOND_QUESTION = phantom.getProperty("FakeChatRespondQuestion", 60);
      FAKE_CHAT_RESPOND_TRADE = phantom.getProperty("FakeChatRespondTrade", 50);
      FAKE_CHAT_RESPOND_PVP = phantom.getProperty("FakeChatRespondPvP", 70);
      
      // Olympiad Settings
      FAKE_OLYMPIAD_ENABLED = phantom.getProperty("FakeOlympiadEnabled", true);
      FAKE_OLYMPIAD_MIN_LEVEL = phantom.getProperty("FakeOlympiadMinLevel", 76);
      FAKE_OLYMPIAD_REGISTER_CHANCE = phantom.getProperty("FakeOlympiadRegisterChance", 50);
      FAKE_OLYMPIAD_CLASSED_COUNT = phantom.getProperty("FakeOlympiadClassedCount", -1);
      FAKE_OLYMPIAD_NON_CLASSED_COUNT = phantom.getProperty("FakeOlympiadNonClassedCount", -1);
      FAKE_OLYMPIAD_MAX_PER_CLASS = phantom.getProperty("FakeOlympiadMaxPerClass", 3);
      FAKE_OLYMPIAD_ALLOW_1V1 = phantom.getProperty("FakeOlympiadAllow1v1", true);
      FAKE_OLYMPIAD_REGISTER_PHANTOM_ON_TIMEOUT = phantom.getProperty("FakeOlympiadRegisterPhantomOnTimeout", true);
      FAKE_OLYMPIAD_MATCH_TIMEOUT_SECONDS = phantom.getProperty("FakeOlympiadMatchTimeoutSeconds", 20);
      FAKE_OLYMPIAD_NO_DESPAWN = phantom.getProperty("FakeOlympiadNoDespawn", false);
      
      // Raid Helper Settings
      FAKE_RAID_HELPER_ENABLED = phantom.getProperty("FakeRaidHelperEnabled", true);
      FAKE_RAID_HELPER_MIN_PLAYER_LEVEL = phantom.getProperty("FakeRaidHelperMinPlayerLevel", 70);
      FAKE_RAID_HELP_REQUEST_CHANCE = phantom.getProperty("FakeRaidHelpRequestChance", 30);
      FAKE_RAID_MAX_HELPERS = phantom.getProperty("FakeRaidMaxHelpers", 3);
      FAKE_RAID_HELPER_SPAWN_RANGE = phantom.getProperty("FakeRaidHelperSpawnRange", 300);
      FAKE_RAID_HELPER_DESPAWN_DELAY = phantom.getProperty("FakeRaidHelperDespawnDelay", 300000);
      
      // Siege Settings
      FAKE_SIEGE_ENABLED = phantom.getProperty("FakeSiegeEnabled", false);
      FAKE_SIEGE_MAX_ATTACKERS = phantom.getProperty("FakeSiegeMaxAttackers", 10);
      FAKE_SIEGE_MAX_DEFENDERS = phantom.getProperty("FakeSiegeMaxDefenders", 10);
      FAKE_SIEGE_MAX_ATTACKERS_GLUDIO = phantom.getProperty("FakeSiegeMaxAttackers_Gludio", FAKE_SIEGE_MAX_ATTACKERS);
      FAKE_SIEGE_MAX_ATTACKERS_DION = phantom.getProperty("FakeSiegeMaxAttackers_Dion", FAKE_SIEGE_MAX_ATTACKERS);
      FAKE_SIEGE_MAX_ATTACKERS_GIRAN = phantom.getProperty("FakeSiegeMaxAttackers_Giran", FAKE_SIEGE_MAX_ATTACKERS);
      FAKE_SIEGE_MAX_ATTACKERS_OREN = phantom.getProperty("FakeSiegeMaxAttackers_Oren", FAKE_SIEGE_MAX_ATTACKERS);
      FAKE_SIEGE_MAX_ATTACKERS_ADEN = phantom.getProperty("FakeSiegeMaxAttackers_Aden", FAKE_SIEGE_MAX_ATTACKERS);
      FAKE_SIEGE_MAX_ATTACKERS_INNADRIL = phantom.getProperty("FakeSiegeMaxAttackers_Innadril", FAKE_SIEGE_MAX_ATTACKERS);
      FAKE_SIEGE_MAX_ATTACKERS_GODDARD = phantom.getProperty("FakeSiegeMaxAttackers_Goddard", FAKE_SIEGE_MAX_ATTACKERS);
      FAKE_SIEGE_MAX_ATTACKERS_RUNE = phantom.getProperty("FakeSiegeMaxAttackers_Rune", FAKE_SIEGE_MAX_ATTACKERS);
      FAKE_SIEGE_MAX_ATTACKERS_SCHUTT = phantom.getProperty("FakeSiegeMaxAttackers_Schuttgart", FAKE_SIEGE_MAX_ATTACKERS);
      FAKE_SIEGE_MAX_DEFENDERS_GLUDIO = phantom.getProperty("FakeSiegeMaxDefenders_Gludio", FAKE_SIEGE_MAX_DEFENDERS);
      FAKE_SIEGE_MAX_DEFENDERS_DION = phantom.getProperty("FakeSiegeMaxDefenders_Dion", FAKE_SIEGE_MAX_DEFENDERS);
      FAKE_SIEGE_MAX_DEFENDERS_GIRAN = phantom.getProperty("FakeSiegeMaxDefenders_Giran", FAKE_SIEGE_MAX_DEFENDERS);
      FAKE_SIEGE_MAX_DEFENDERS_OREN = phantom.getProperty("FakeSiegeMaxDefenders_Oren", FAKE_SIEGE_MAX_DEFENDERS);
      FAKE_SIEGE_MAX_DEFENDERS_ADEN = phantom.getProperty("FakeSiegeMaxDefenders_Aden", FAKE_SIEGE_MAX_DEFENDERS);
      FAKE_SIEGE_MAX_DEFENDERS_INNADRIL = phantom.getProperty("FakeSiegeMaxDefenders_Innadril", FAKE_SIEGE_MAX_DEFENDERS);
      FAKE_SIEGE_MAX_DEFENDERS_GODDARD = phantom.getProperty("FakeSiegeMaxDefenders_Goddard", FAKE_SIEGE_MAX_DEFENDERS);
      FAKE_SIEGE_MAX_DEFENDERS_RUNE = phantom.getProperty("FakeSiegeMaxDefenders_Rune", FAKE_SIEGE_MAX_DEFENDERS);
      FAKE_SIEGE_MAX_DEFENDERS_SCHUTT = phantom.getProperty("FakeSiegeMaxDefenders_Schuttgart", FAKE_SIEGE_MAX_DEFENDERS);
      FAKE_SIEGE_JOIN_CHANCE = phantom.getProperty("FakeSiegeJoinChance", 70);
      FAKE_SIEGE_STICKY_JOIN_CHANCE = phantom.getProperty("FakeSiegeStickyJoinChance", true);
      FAKE_SIEGE_ATTACKER_RESPAWN_SECONDS = phantom.getProperty("FakeSiegeAttackerRespawnSeconds", -1);
      FAKE_SIEGE_ATTACKER_CLAN_SPREAD_RADIUS = phantom.getProperty("FakeSiegeAttackerClanSpreadRadius", 260);
      FAKE_SIEGE_ATTACKER_MEMBER_SPREAD_RADIUS = phantom.getProperty("FakeSiegeAttackerMemberSpreadRadius", 70);
      FAKE_SIEGE_ATTACKER_RALLY_SECONDS = phantom.getProperty("FakeSiegeAttackerRallySeconds", 18);
      FAKE_SIEGE_ATTACKER_SPAWN_GLUDIO = parseLocation(phantom.getProperty("FakeSiegeAttackerSpawn_Gludio", ""));
      FAKE_SIEGE_ATTACKER_SPAWN_DION = parseLocation(phantom.getProperty("FakeSiegeAttackerSpawn_Dion", ""));
      FAKE_SIEGE_ATTACKER_SPAWN_GIRAN = parseLocation(phantom.getProperty("FakeSiegeAttackerSpawn_Giran", ""));
      FAKE_SIEGE_ATTACKER_SPAWN_OREN = parseLocation(phantom.getProperty("FakeSiegeAttackerSpawn_Oren", ""));
      FAKE_SIEGE_ATTACKER_SPAWN_ADEN = parseLocation(phantom.getProperty("FakeSiegeAttackerSpawn_Aden", ""));
      FAKE_SIEGE_ATTACKER_SPAWN_INNADRIL = parseLocation(phantom.getProperty("FakeSiegeAttackerSpawn_Innadril", ""));
      FAKE_SIEGE_ATTACKER_SPAWN_GODDARD = parseLocation(phantom.getProperty("FakeSiegeAttackerSpawn_Goddard", ""));
      FAKE_SIEGE_ATTACKER_SPAWN_RUNE = parseLocation(phantom.getProperty("FakeSiegeAttackerSpawn_Rune", ""));
      FAKE_SIEGE_ATTACKER_SPAWN_SCHUTT = parseLocation(phantom.getProperty("FakeSiegeAttackerSpawn_Schuttgart", ""));
      FAKE_SIEGE_DEFENDER_RESPAWN_SECONDS = phantom.getProperty("FakeSiegeDefenderRespawnSeconds", 5);
      FAKE_SIEGE_RESPAWN_WAVE_WINDOW_MS = phantom.getProperty("FakeSiegeRespawnWaveWindowSeconds", phantom.getProperty("FakeSiegeRespawnWaveWindowMs", 3500) / 1000) * 1000;
      FAKE_SIEGE_RESPAWN_WAVE_JITTER_MS = phantom.getProperty("FakeSiegeRespawnWaveJitterSeconds", phantom.getProperty("FakeSiegeRespawnWaveJitterMs", 600) / 1000) * 1000;
      FAKE_SIEGE_ENABLED = getMergedBoolean(phantomClan, phantom, "FakeSiegeEnabled", FAKE_SIEGE_ENABLED);
      FAKE_SIEGE_MAX_ATTACKERS = getMergedInt(phantomClan, phantom, "FakeSiegeMaxAttackers", FAKE_SIEGE_MAX_ATTACKERS);
      FAKE_SIEGE_MAX_DEFENDERS = getMergedInt(phantomClan, phantom, "FakeSiegeMaxDefenders", FAKE_SIEGE_MAX_DEFENDERS);
      FAKE_SIEGE_MAX_ATTACKERS_GLUDIO = getMergedInt(phantomClan, phantom, "FakeSiegeMaxAttackers_Gludio", FAKE_SIEGE_MAX_ATTACKERS_GLUDIO);
      FAKE_SIEGE_MAX_ATTACKERS_DION = getMergedInt(phantomClan, phantom, "FakeSiegeMaxAttackers_Dion", FAKE_SIEGE_MAX_ATTACKERS_DION);
      FAKE_SIEGE_MAX_ATTACKERS_GIRAN = getMergedInt(phantomClan, phantom, "FakeSiegeMaxAttackers_Giran", FAKE_SIEGE_MAX_ATTACKERS_GIRAN);
      FAKE_SIEGE_MAX_ATTACKERS_OREN = getMergedInt(phantomClan, phantom, "FakeSiegeMaxAttackers_Oren", FAKE_SIEGE_MAX_ATTACKERS_OREN);
      FAKE_SIEGE_MAX_ATTACKERS_ADEN = getMergedInt(phantomClan, phantom, "FakeSiegeMaxAttackers_Aden", FAKE_SIEGE_MAX_ATTACKERS_ADEN);
      FAKE_SIEGE_MAX_ATTACKERS_INNADRIL = getMergedInt(phantomClan, phantom, "FakeSiegeMaxAttackers_Innadril", FAKE_SIEGE_MAX_ATTACKERS_INNADRIL);
      FAKE_SIEGE_MAX_ATTACKERS_GODDARD = getMergedInt(phantomClan, phantom, "FakeSiegeMaxAttackers_Goddard", FAKE_SIEGE_MAX_ATTACKERS_GODDARD);
      FAKE_SIEGE_MAX_ATTACKERS_RUNE = getMergedInt(phantomClan, phantom, "FakeSiegeMaxAttackers_Rune", FAKE_SIEGE_MAX_ATTACKERS_RUNE);
      FAKE_SIEGE_MAX_ATTACKERS_SCHUTT = getMergedInt(phantomClan, phantom, "FakeSiegeMaxAttackers_Schuttgart", FAKE_SIEGE_MAX_ATTACKERS_SCHUTT);
      FAKE_SIEGE_MAX_DEFENDERS_GLUDIO = getMergedInt(phantomClan, phantom, "FakeSiegeMaxDefenders_Gludio", FAKE_SIEGE_MAX_DEFENDERS_GLUDIO);
      FAKE_SIEGE_MAX_DEFENDERS_DION = getMergedInt(phantomClan, phantom, "FakeSiegeMaxDefenders_Dion", FAKE_SIEGE_MAX_DEFENDERS_DION);
      FAKE_SIEGE_MAX_DEFENDERS_GIRAN = getMergedInt(phantomClan, phantom, "FakeSiegeMaxDefenders_Giran", FAKE_SIEGE_MAX_DEFENDERS_GIRAN);
      FAKE_SIEGE_MAX_DEFENDERS_OREN = getMergedInt(phantomClan, phantom, "FakeSiegeMaxDefenders_Oren", FAKE_SIEGE_MAX_DEFENDERS_OREN);
      FAKE_SIEGE_MAX_DEFENDERS_ADEN = getMergedInt(phantomClan, phantom, "FakeSiegeMaxDefenders_Aden", FAKE_SIEGE_MAX_DEFENDERS_ADEN);
      FAKE_SIEGE_MAX_DEFENDERS_INNADRIL = getMergedInt(phantomClan, phantom, "FakeSiegeMaxDefenders_Innadril", FAKE_SIEGE_MAX_DEFENDERS_INNADRIL);
      FAKE_SIEGE_MAX_DEFENDERS_GODDARD = getMergedInt(phantomClan, phantom, "FakeSiegeMaxDefenders_Goddard", FAKE_SIEGE_MAX_DEFENDERS_GODDARD);
      FAKE_SIEGE_MAX_DEFENDERS_RUNE = getMergedInt(phantomClan, phantom, "FakeSiegeMaxDefenders_Rune", FAKE_SIEGE_MAX_DEFENDERS_RUNE);
      FAKE_SIEGE_MAX_DEFENDERS_SCHUTT = getMergedInt(phantomClan, phantom, "FakeSiegeMaxDefenders_Schuttgart", FAKE_SIEGE_MAX_DEFENDERS_SCHUTT);
      FAKE_SIEGE_JOIN_CHANCE = getMergedInt(phantomClan, phantom, "FakeSiegeJoinChance", FAKE_SIEGE_JOIN_CHANCE);
      FAKE_SIEGE_STICKY_JOIN_CHANCE = getMergedBoolean(phantomClan, phantom, "FakeSiegeStickyJoinChance", FAKE_SIEGE_STICKY_JOIN_CHANCE);
      FAKE_SIEGE_ATTACKER_RESPAWN_SECONDS = getMergedInt(phantomClan, phantom, "FakeSiegeAttackerRespawnSeconds", FAKE_SIEGE_ATTACKER_RESPAWN_SECONDS);
      FAKE_SIEGE_ATTACKER_CLAN_SPREAD_RADIUS = getMergedInt(phantomClan, phantom, "FakeSiegeAttackerClanSpreadRadius", FAKE_SIEGE_ATTACKER_CLAN_SPREAD_RADIUS);
      FAKE_SIEGE_ATTACKER_MEMBER_SPREAD_RADIUS = getMergedInt(phantomClan, phantom, "FakeSiegeAttackerMemberSpreadRadius", FAKE_SIEGE_ATTACKER_MEMBER_SPREAD_RADIUS);
      FAKE_SIEGE_ATTACKER_RALLY_SECONDS = getMergedInt(phantomClan, phantom, "FakeSiegeAttackerRallySeconds", FAKE_SIEGE_ATTACKER_RALLY_SECONDS);
      FAKE_SIEGE_ATTACKER_SPAWN_GLUDIO = parseLocation(getMergedString(phantomClan, phantom, "FakeSiegeAttackerSpawn_Gludio", locationToProperty(FAKE_SIEGE_ATTACKER_SPAWN_GLUDIO)));
      FAKE_SIEGE_ATTACKER_SPAWN_DION = parseLocation(getMergedString(phantomClan, phantom, "FakeSiegeAttackerSpawn_Dion", locationToProperty(FAKE_SIEGE_ATTACKER_SPAWN_DION)));
      FAKE_SIEGE_ATTACKER_SPAWN_GIRAN = parseLocation(getMergedString(phantomClan, phantom, "FakeSiegeAttackerSpawn_Giran", locationToProperty(FAKE_SIEGE_ATTACKER_SPAWN_GIRAN)));
      FAKE_SIEGE_ATTACKER_SPAWN_OREN = parseLocation(getMergedString(phantomClan, phantom, "FakeSiegeAttackerSpawn_Oren", locationToProperty(FAKE_SIEGE_ATTACKER_SPAWN_OREN)));
      FAKE_SIEGE_ATTACKER_SPAWN_ADEN = parseLocation(getMergedString(phantomClan, phantom, "FakeSiegeAttackerSpawn_Aden", locationToProperty(FAKE_SIEGE_ATTACKER_SPAWN_ADEN)));
      FAKE_SIEGE_ATTACKER_SPAWN_INNADRIL = parseLocation(getMergedString(phantomClan, phantom, "FakeSiegeAttackerSpawn_Innadril", locationToProperty(FAKE_SIEGE_ATTACKER_SPAWN_INNADRIL)));
      FAKE_SIEGE_ATTACKER_SPAWN_GODDARD = parseLocation(getMergedString(phantomClan, phantom, "FakeSiegeAttackerSpawn_Goddard", locationToProperty(FAKE_SIEGE_ATTACKER_SPAWN_GODDARD)));
      FAKE_SIEGE_ATTACKER_SPAWN_RUNE = parseLocation(getMergedString(phantomClan, phantom, "FakeSiegeAttackerSpawn_Rune", locationToProperty(FAKE_SIEGE_ATTACKER_SPAWN_RUNE)));
      FAKE_SIEGE_ATTACKER_SPAWN_SCHUTT = parseLocation(getMergedString(phantomClan, phantom, "FakeSiegeAttackerSpawn_Schuttgart", locationToProperty(FAKE_SIEGE_ATTACKER_SPAWN_SCHUTT)));
      FAKE_SIEGE_DEFENDER_RESPAWN_SECONDS = getMergedInt(phantomClan, phantom, "FakeSiegeDefenderRespawnSeconds", FAKE_SIEGE_DEFENDER_RESPAWN_SECONDS);
      FAKE_SIEGE_RESPAWN_WAVE_WINDOW_MS = getMergedInt(phantomClan, phantom, "FakeSiegeRespawnWaveWindowSeconds", FAKE_SIEGE_RESPAWN_WAVE_WINDOW_MS / 1000) * 1000;
      FAKE_SIEGE_RESPAWN_WAVE_JITTER_MS = getMergedInt(phantomClan, phantom, "FakeSiegeRespawnWaveJitterSeconds", FAKE_SIEGE_RESPAWN_WAVE_JITTER_MS / 1000) * 1000;
      
      // Clan War Settings
      FAKE_CLAN_WAR_ENABLED = phantom.getProperty("FakeClanWarEnabled", false);
      FAKE_CLAN_WAR_ATTACK_CHANCE = phantom.getProperty("FakeClanWarAttackChance", 30);
      FAKE_CLAN_WAR_SEARCH_RADIUS = phantom.getProperty("FakeClanWarSearchRadius", 1500);
      FAKE_CLAN_WAR_ATTACK_FAKES = phantom.getProperty("FakeClanWarAttackFakes", false);
      FAKE_ALLOW_CLAN_INVITE = phantom.getProperty("FakeAllowClanInvite", true);
      FAKE_CLAN_INVITE_ACCEPT_CHANCE = phantom.getProperty("FakeClanInviteAcceptChance", 50);
      FAKE_CLAN_WAR_ENABLED = getMergedBoolean(phantomClan, phantom, "FakeClanWarEnabled", FAKE_CLAN_WAR_ENABLED);
      FAKE_CLAN_WAR_ATTACK_CHANCE = getMergedInt(phantomClan, phantom, "FakeClanWarAttackChance", FAKE_CLAN_WAR_ATTACK_CHANCE);
      FAKE_CLAN_WAR_SEARCH_RADIUS = getMergedInt(phantomClan, phantom, "FakeClanWarSearchRadius", FAKE_CLAN_WAR_SEARCH_RADIUS);
      FAKE_CLAN_WAR_ATTACK_FAKES = getMergedBoolean(phantomClan, phantom, "FakeClanWarAttackFakes", FAKE_CLAN_WAR_ATTACK_FAKES);
      FAKE_ALLOW_CLAN_INVITE = getMergedBoolean(phantomClan, phantom, "FakeAllowClanInvite", FAKE_ALLOW_CLAN_INVITE);
      FAKE_CLAN_INVITE_ACCEPT_CHANCE = getMergedInt(phantomClan, phantom, "FakeClanInviteAcceptChance", FAKE_CLAN_INVITE_ACCEPT_CHANCE);
      
      // Human-Like Behavior Settings
      FAKE_HUMAN_REACTION_DELAY_ENABLED = phantom.getProperty("FakeHumanReactionDelayEnabled", true);
      FAKE_HUMAN_REACTION_DELAY_MIN = phantom.getProperty("FakeHumanReactionDelayMin", 100);
      FAKE_HUMAN_REACTION_DELAY_MAX = phantom.getProperty("FakeHumanReactionDelayMax", 500);
      FAKE_HUMAN_ERROR_ENABLED = phantom.getProperty("FakeHumanErrorEnabled", true);
      FAKE_HUMAN_ERROR_RATE = phantom.getProperty("FakeHumanErrorRate", 5);
      
      FAKE_PLAYER_PRIVATE_BUY_LIST = parseBuyList(phantom, "FakePrivateBuyList");
      FAKE_PLAYER_PRIVATE_SELL_LIST = parseSellList(phantom, "FakePrivateSellList");
      CHECK_FAKE_PLAYERS_AREA = phantom.getProperty("AllowFakePlayerCheck", false);
      CHECK_FAKE_PLAYERS_START_TIME = phantom.getProperty("FakeCheckStartTime", 1);
      CHECK_FAKE_PLAYERS_RESTART_TIME = phantom.getProperty("FakeCheckRestartTime", 1);
      FAKE_PLAYER_ARROW = phantom.getProperty("FakePlayerArrow", 0);
      FAKE_PLAYER_SOULSHOT = phantom.getProperty("FakePlayerSoulShot", 0);
      FAKE_PLAYER_BLESSED_SOULSHOT = phantom.getProperty("FakePlayerBlessedSoulShot", 0);
      DESPAWN_CITIZEN_RANDOM_TIME_1 = phantom.getProperty("FakeCitizenDespawnMinTime", 1);
      DESPAWN_CITIZEN_RANDOM_TIME_2 = phantom.getProperty("FakeCitizenDespawnMaxTime", 1);
      DESPAWN_PVP_RANDOM_TIME_1 = phantom.getProperty("FakePvpDespawnMinTime", 1);
      DESPAWN_PVP_RANDOM_TIME_2 = phantom.getProperty("FakePvpDespawnMaxTime", 1);
      FAKE_HP_REGEN_MULTIPLIER = phantom.getProperty("FakeHpRegenMultiplier", 0.3);
      FAKE_MP_REGEN_MULTIPLIER = phantom.getProperty("FakeMpRegenMultiplier", 0.3);
      FAKE_CP_REGEN_MULTIPLIER = phantom.getProperty("FakeCpRegenMultiplier", 0.3);
      FAKE_VIRTUAL_POTION_HP_MULTIPLIER = phantom.getProperty("FakeVirtualPotionHpMultiplier", 0.3);
      FAKE_VIRTUAL_POTION_MP_MULTIPLIER = phantom.getProperty("FakeVirtualPotionMpMultiplier", 0.3);
      FAKE_VIRTUAL_POTION_CP_MULTIPLIER = phantom.getProperty("FakeVirtualPotionCpMultiplier", 0.3);
      FAKE_PLAYER_COLOR_NAME = phantom.getProperty("FakePlayerColorName", "");
      FAKE_PLAYER_COLOR_TITLE = phantom.getProperty("FakePlayerColorTitle", "");
      FAKE_PLAYER_FIXED_TITLE = phantom.getProperty("FakePlayerTitle", "");
      FAKE_PLAYER_CLAN_FIXED_TITLE = phantom.getProperty("FakePlayerClanTitle", "");
      CLAN_ID = phantom.getProperty("FakeClanIDList", "");
      FAKE_PLAYER_CLAN_FIXED_TITLE = getMergedString(phantomClan, phantom, "FakePlayerClanTitle", FAKE_PLAYER_CLAN_FIXED_TITLE);
      CLAN_ID = getMergedString(phantomClan, phantom, "FakeClanIDList", CLAN_ID);
      LIST_CLAN_ID = new ArrayList<>();

      for (String itemId : CLAN_ID.split(",")) {
         itemId = itemId.trim();
         if (!itemId.isEmpty()) {
            LIST_CLAN_ID.add(Integer.parseInt(itemId));
         }
      }

      MIN_ENCHANT_ARMOR = phantom.getProperty("MinEnchantAmor", 0);
      MAX_ENCHANT_ARMOR = phantom.getProperty("MaxEnchantAmor", 0);
      MIN_ENCHANT_WEAPON = phantom.getProperty("MinEnchantWeapon", 0);
      MAX_ENCHANT_WEAPON = phantom.getProperty("MaxEnchantWeapon", 0);
      MIN_ENCHANT_JEWEL = phantom.getProperty("MinEnchantJewel", 0);
      MAX_ENCHANT_JEWEL = phantom.getProperty("MaxEnchantJewel", 0);
      FAKE_ENCHANT_PROFILE_ENABLED = phantom.getProperty("FakeEnchantProfileEnabled", true);
      FAKE_ENCHANT_LOW_CHANCE = phantom.getProperty("FakeEnchantLowChance", 30);
      FAKE_ENCHANT_MID_CHANCE = phantom.getProperty("FakeEnchantMidChance", 50);
      FAKE_ENCHANT_HIGH_CHANCE = phantom.getProperty("FakeEnchantHighChance", 20);
      FAKE_OLYMPIAD_ARMOR_GRADE = parseCrystalType(phantom.getProperty("FakeOlympiadArmorGrade", "S"), CrystalType.S);
      FAKE_OLYMPIAD_MAX_ENCHANT = phantom.getProperty("FakeOlympiadMaxEnchant", 6);
      PHANTOM_ROB_ARMOR_1 = phantom.getProperty("ListRobeArmor1", "0");
      LIST_PHANTOM_ROB_ARMOR_1 = parseIntList(PHANTOM_ROB_ARMOR_1);
      PHANTOM_ROB_ARMOR_2 = phantom.getProperty("ListRobeArmor2", "0");
      LIST_PHANTOM_ROB_ARMOR_2 = parseIntList(PHANTOM_ROB_ARMOR_2);
      PHANTOM_ROB_ARMOR_3 = phantom.getProperty("ListRobeArmor3", "0");
      LIST_PHANTOM_ROB_ARMOR_3 = parseIntList(PHANTOM_ROB_ARMOR_3);
      PHANTOM_ROB_ARMOR_4 = phantom.getProperty("ListRobeArmor4", "0");
      LIST_PHANTOM_ROB_ARMOR_4 = parseIntList(PHANTOM_ROB_ARMOR_4);
      PHANTOM_HEAVY_ARMOR_1 = phantom.getProperty("ListHeavyArmor1", "0");
      LIST_PHANTOM_HEAVY_ARMOR_1 = parseIntList(PHANTOM_HEAVY_ARMOR_1);
      PHANTOM_HEAVY_ARMOR_2 = phantom.getProperty("ListHeavyArmor2", "0");
      LIST_PHANTOM_HEAVY_ARMOR_2 = parseIntList(PHANTOM_HEAVY_ARMOR_2);
      PHANTOM_HEAVY_ARMOR_3 = phantom.getProperty("ListHeavyArmor3", "0");
      LIST_PHANTOM_HEAVY_ARMOR_3 = parseIntList(PHANTOM_HEAVY_ARMOR_3);
      PHANTOM_HEAVY_ARMOR_4 = phantom.getProperty("ListHeavyArmor4", "0");
      LIST_PHANTOM_HEAVY_ARMOR_4 = parseIntList(PHANTOM_HEAVY_ARMOR_4);
      PHANTOM_LIGHT_ARMOR_1 = phantom.getProperty("ListLightArmor1", "0");
      LIST_PHANTOM_LIGHT_ARMOR_1 = parseIntList(PHANTOM_LIGHT_ARMOR_1);
      PHANTOM_LIGHT_ARMOR_2 = phantom.getProperty("ListLightArmor2", "0");
      LIST_PHANTOM_LIGHT_ARMOR_2 = parseIntList(PHANTOM_LIGHT_ARMOR_2);
      PHANTOM_LIGHT_ARMOR_3 = phantom.getProperty("ListLightArmor3", "0");
      LIST_PHANTOM_LIGHT_ARMOR_3 = parseIntList(PHANTOM_LIGHT_ARMOR_3);
      PHANTOM_LIGHT_ARMOR_4 = phantom.getProperty("ListLightArmor4", "0");
      LIST_PHANTOM_LIGHT_ARMOR_4 = parseIntList(PHANTOM_LIGHT_ARMOR_4);
      FAKE_OLYMPIAD_ROBE_ARMOR_1 = phantom.getProperty("FakeOlympiadRobeArmor1", PHANTOM_ROB_ARMOR_1);
      LIST_FAKE_OLYMPIAD_ROBE_ARMOR_1 = parseIntList(FAKE_OLYMPIAD_ROBE_ARMOR_1);
      FAKE_OLYMPIAD_ROBE_ARMOR_2 = phantom.getProperty("FakeOlympiadRobeArmor2", PHANTOM_ROB_ARMOR_2);
      LIST_FAKE_OLYMPIAD_ROBE_ARMOR_2 = parseIntList(FAKE_OLYMPIAD_ROBE_ARMOR_2);
      FAKE_OLYMPIAD_ROBE_ARMOR_3 = phantom.getProperty("FakeOlympiadRobeArmor3", PHANTOM_ROB_ARMOR_3);
      LIST_FAKE_OLYMPIAD_ROBE_ARMOR_3 = parseIntList(FAKE_OLYMPIAD_ROBE_ARMOR_3);
      FAKE_OLYMPIAD_ROBE_ARMOR_4 = phantom.getProperty("FakeOlympiadRobeArmor4", PHANTOM_ROB_ARMOR_4);
      LIST_FAKE_OLYMPIAD_ROBE_ARMOR_4 = parseIntList(FAKE_OLYMPIAD_ROBE_ARMOR_4);
      FAKE_OLYMPIAD_HEAVY_ARMOR_1 = phantom.getProperty("FakeOlympiadHeavyArmor1", PHANTOM_HEAVY_ARMOR_1);
      LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_1 = parseIntList(FAKE_OLYMPIAD_HEAVY_ARMOR_1);
      FAKE_OLYMPIAD_HEAVY_ARMOR_2 = phantom.getProperty("FakeOlympiadHeavyArmor2", PHANTOM_HEAVY_ARMOR_2);
      LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_2 = parseIntList(FAKE_OLYMPIAD_HEAVY_ARMOR_2);
      FAKE_OLYMPIAD_HEAVY_ARMOR_3 = phantom.getProperty("FakeOlympiadHeavyArmor3", PHANTOM_HEAVY_ARMOR_3);
      LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_3 = parseIntList(FAKE_OLYMPIAD_HEAVY_ARMOR_3);
      FAKE_OLYMPIAD_HEAVY_ARMOR_4 = phantom.getProperty("FakeOlympiadHeavyArmor4", PHANTOM_HEAVY_ARMOR_4);
      LIST_FAKE_OLYMPIAD_HEAVY_ARMOR_4 = parseIntList(FAKE_OLYMPIAD_HEAVY_ARMOR_4);
      FAKE_OLYMPIAD_LIGHT_ARMOR_1 = phantom.getProperty("FakeOlympiadLightArmor1", PHANTOM_LIGHT_ARMOR_1);
      LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_1 = parseIntList(FAKE_OLYMPIAD_LIGHT_ARMOR_1);
      FAKE_OLYMPIAD_LIGHT_ARMOR_2 = phantom.getProperty("FakeOlympiadLightArmor2", PHANTOM_LIGHT_ARMOR_2);
      LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_2 = parseIntList(FAKE_OLYMPIAD_LIGHT_ARMOR_2);
      FAKE_OLYMPIAD_LIGHT_ARMOR_3 = phantom.getProperty("FakeOlympiadLightArmor3", PHANTOM_LIGHT_ARMOR_3);
      LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_3 = parseIntList(FAKE_OLYMPIAD_LIGHT_ARMOR_3);
      FAKE_OLYMPIAD_LIGHT_ARMOR_4 = phantom.getProperty("FakeOlympiadLightArmor4", PHANTOM_LIGHT_ARMOR_4);
      LIST_FAKE_OLYMPIAD_LIGHT_ARMOR_4 = parseIntList(FAKE_OLYMPIAD_LIGHT_ARMOR_4);

      FAKE_BOW_ID = phantom.getProperty("FakeBowIDList", "");
      LIST_FAKE_BOW = parseIntList(FAKE_BOW_ID);
      FAKE_DAGGER_ID = phantom.getProperty("FakeDaggerIDList", "");
      LIST_FAKE_DAGGER = parseIntList(FAKE_DAGGER_ID);
      FAKE_SWORD_ID = phantom.getProperty("FakeSwordIDList", "");
      LIST_FAKE_SWORD = parseIntList(FAKE_SWORD_ID);
      FAKE_SPEAR_ID = phantom.getProperty("FakeSpearIDList", "");
      LIST_FAKE_SPEAR = parseIntList(FAKE_SPEAR_ID);
      FAKE_DUAL_ID = phantom.getProperty("FakeDualIDList", "");
      LIST_FAKE_DUAL = parseIntList(FAKE_DUAL_ID);
      FAKE_FIST_ID = phantom.getProperty("FakeFistIDList", "");
      LIST_FAKE_FIST = parseIntList(FAKE_FIST_ID);
      FAKE_BIGSWORD_ID = phantom.getProperty("FakeBigSwordIDList", "");
      LIST_FAKE_BIG_SWORD = parseIntList(FAKE_BIGSWORD_ID);
      FAKE_MAGIC_ID = phantom.getProperty("FakeMagicWeaponIDList", "");
      LIST_FAKE_MAGIC = parseIntList(FAKE_MAGIC_ID);
      FAKE_SHIELD_ID = phantom.getProperty("FakeShieldIDList", "");
      LIST_FAKE_SHIELD = parseIntList(FAKE_SHIELD_ID);
      FAKE_OLYMPIAD_BOW_ID = phantom.getProperty("FakeOlympiadBowIDList", FAKE_BOW_ID);
      LIST_FAKE_OLYMPIAD_BOW = parseIntList(FAKE_OLYMPIAD_BOW_ID);
      FAKE_OLYMPIAD_DAGGER_ID = phantom.getProperty("FakeOlympiadDaggerIDList", FAKE_DAGGER_ID);
      LIST_FAKE_OLYMPIAD_DAGGER = parseIntList(FAKE_OLYMPIAD_DAGGER_ID);
      FAKE_OLYMPIAD_SWORD_ID = phantom.getProperty("FakeOlympiadSwordIDList", FAKE_SWORD_ID);
      LIST_FAKE_OLYMPIAD_SWORD = parseIntList(FAKE_OLYMPIAD_SWORD_ID);
      FAKE_OLYMPIAD_SPEAR_ID = phantom.getProperty("FakeOlympiadSpearIDList", FAKE_SPEAR_ID);
      LIST_FAKE_OLYMPIAD_SPEAR = parseIntList(FAKE_OLYMPIAD_SPEAR_ID);
      FAKE_OLYMPIAD_DUAL_ID = phantom.getProperty("FakeOlympiadDualIDList", FAKE_DUAL_ID);
      LIST_FAKE_OLYMPIAD_DUAL = parseIntList(FAKE_OLYMPIAD_DUAL_ID);
      FAKE_OLYMPIAD_FIST_ID = phantom.getProperty("FakeOlympiadFistIDList", FAKE_FIST_ID);
      LIST_FAKE_OLYMPIAD_FIST = parseIntList(FAKE_OLYMPIAD_FIST_ID);
      FAKE_OLYMPIAD_BIGSWORD_ID = phantom.getProperty("FakeOlympiadBigSwordIDList", FAKE_BIGSWORD_ID);
      LIST_FAKE_OLYMPIAD_BIG_SWORD = parseIntList(FAKE_OLYMPIAD_BIGSWORD_ID);
      FAKE_OLYMPIAD_MAGIC_ID = phantom.getProperty("FakeOlympiadMagicWeaponIDList", FAKE_MAGIC_ID);
      LIST_FAKE_OLYMPIAD_MAGIC = parseIntList(FAKE_OLYMPIAD_MAGIC_ID);
      FAKE_OLYMPIAD_SHIELD_ID = phantom.getProperty("FakeOlympiadShieldIDList", FAKE_SHIELD_ID);
      LIST_FAKE_OLYMPIAD_SHIELD = parseIntList(FAKE_OLYMPIAD_SHIELD_ID);

      String[] propertySplit = phantom.getProperty("JewelSetList1", "0,0").split(";");
      LIST_PHANTOM_JEWELS_1.clear();

      for (String reward : propertySplit) {
         String[] rewardSplit = reward.split(",");
         if (rewardSplit.length != 2) {
            _log.warning("JewelSetList1[FakePlayerConfig.load()]: invalid config property -> JewelSetList1 \"" + reward + "\"");
         } else {
            try {
               LIST_PHANTOM_JEWELS_1.add(new int[]{Integer.parseInt(rewardSplit[0]), Integer.parseInt(rewardSplit[1])});
            } catch (NumberFormatException var21) {
               if (!reward.isEmpty()) {
                  _log.warning("JewelSetList1[FakePlayerConfig.load()]: invalid config property -> JewelSetList1 \"" + reward + "\"");
               }
            }
         }
      }

      propertySplit = phantom.getProperty("JewelSetList2", "0,0").split(";");
      LIST_PHANTOM_JEWELS_2.clear();

      for (String rewardx : propertySplit) {
         String[] rewardSplit = rewardx.split(",");
         if (rewardSplit.length != 2) {
            _log.warning("JewelSetList2[FakePlayerConfig.load()]: invalid config property -> JewelSetList2 \"" + rewardx + "\"");
         } else {
            try {
               LIST_PHANTOM_JEWELS_2.add(new int[]{Integer.parseInt(rewardSplit[0]), Integer.parseInt(rewardSplit[1])});
            } catch (NumberFormatException var20) {
               if (!rewardx.isEmpty()) {
                  _log.warning("JewelSetList2[FakePlayerConfig.load()]: invalid config property -> JewelSetList2 \"" + rewardx + "\"");
               }
            }
         }
      }

      ALLOW_FAKE_PLAYERS_ACCESSORY = phantom.getProperty("AllowFakePlayerAccesory", false);
      FAKE_ACCESSORY_ID = phantom.getProperty("FakeAccessoryIDList", "");
      LIST_FAKE_ACCESSORY = parseIntList(FAKE_ACCESSORY_ID);
      FAKE_OLYMPIAD_ACCESSORY_ID = phantom.getProperty("FakeOlympiadAccessoryIDList", FAKE_ACCESSORY_ID);
      LIST_FAKE_OLYMPIAD_ACCESSORY = parseIntList(FAKE_OLYMPIAD_ACCESSORY_ID);
      ALLOW_FAKE_PLAYERS_ACCESSORY_BY_CLASS = phantom.getProperty("AllowFakePlayerAccesoryByClass", false);
      NUKER_BUFFER = phantom.getProperty("NukerBufferList", "0");
      NUKER_BUFFER_LIST = parseIntList(NUKER_BUFFER);
      ARCHER_BUFFER = phantom.getProperty("ArcherBufferList", "0");
      ARCHER_BUFFER_LIST = parseIntList(ARCHER_BUFFER);
      DAGGER_BUFFER = phantom.getProperty("DaggerBufferList", "0");
      DAGGER_BUFFER_LIST = parseIntList(DAGGER_BUFFER);
      WARRIOR_BUFFER = phantom.getProperty("WarriorBufferList", "0");
      WARRIOR_BUFFER_LIST = parseIntList(WARRIOR_BUFFER);
      TANKER_BUFFER = phantom.getProperty("TankerBufferList", "0");
      TANKER_BUFFER_LIST = parseIntList(TANKER_BUFFER);
      RANDOM_BUFFER = phantom.getProperty("RandomBufferList", "0");
      RANDOM_BUFFER_LIST = parseIntList(RANDOM_BUFFER);
      ALLOW_FAKE_PLAYERS_HENNA = phantom.getProperty("AllowFakePlayerHenna", false);
      NUKER_HENNA = phantom.getProperty("NukerHennaList", "0");
      NUKER_HENNA_LIST = parseIntList(NUKER_HENNA);
      ARCHER_HENNA = phantom.getProperty("ArcherHennaList", "0");
      ARCHER_HENNA_LIST = parseIntList(ARCHER_HENNA);
      DAGGER_HENNA = phantom.getProperty("DaggerHennaList", "0");
      DAGGER_HENNA_LIST = parseIntList(DAGGER_HENNA);
      TANKER_HENNA = phantom.getProperty("TankerHennaList", "0");
      TANKER_HENNA_LIST = parseIntList(TANKER_HENNA);
      WARRIOR_HENNA = phantom.getProperty("WarriorHennaList", "0");
      WARRIOR_HENNA_LIST = parseIntList(WARRIOR_HENNA);
      FAKE_POTIONS = phantom.getProperty("PotionSkills", "0");
      FAKE_POTIONS_SKILLS = new ArrayList<>(parseIntList(FAKE_POTIONS));

      ALLOW_FAKE_PLAYER_TVT = phantom.getProperty("TvTAllowFakePlayer", false);
      TVT_FAKE_PLAYER_COUNT_MIN = phantom.getProperty("TvTFakePlayerCountMin", 5);
      TVT_FAKE_PLAYER_COUNT_MAX = phantom.getProperty("TvTFakePlayerCountMax", 5);
      TVT_FAKE_PLAYER_LIST_LOCS = parseLocationList(phantom.getProperty("TvTFakePlayerSpawnLocs", "82698,148638,-3473"));
      TVT_FAKE_JOIN_CHANCE = phantom.getProperty("TvTFakeJoinChance", 100);

      ALLOW_FAKE_PLAYER_CTF = phantom.getProperty("CTFAllowFakePlayer", false);
      CTF_FAKE_PLAYER_COUNT_MIN = phantom.getProperty("CTFFakePlayerCountMin", 5);
      CTF_FAKE_PLAYER_COUNT_MAX = phantom.getProperty("CTFFakePlayerCountMax", 5);
      CTF_FAKE_PLAYER_LIST_LOCS = parseLocationList(phantom.getProperty("CTFFakePlayerSpawnLocs", "82698,148638,-3473"));
      CTF_FAKE_JOIN_CHANCE = phantom.getProperty("CTFFakeJoinChance", 100);

      ALLOW_FAKE_PLAYER_KTB = phantom.getProperty("KTBAllowFakePlayer", false);
      KTB_FAKE_PLAYER_COUNT_MIN = phantom.getProperty("KTBFakePlayerCountMin", 5);
      KTB_FAKE_PLAYER_COUNT_MAX = phantom.getProperty("KTBFakePlayerCountMax", 5);
      KTB_FAKE_JOIN_CHANCE = phantom.getProperty("KTBFakeJoinChance", 100);
      KTB_FAKE_PLAYER_LIST_LOCS = parseLocationList(phantom.getProperty("KTBFakePlayerSpawnLocs", "82698,148638,-3473"));

      ALLOW_FAKE_PLAYER_TOURNAMENT = phantom.getProperty("TournamentAllowFakePlayer", false);
      TOURNAMENT_FAKE_COUNT_MIN = phantom.getProperty("TournamentFakesCountMin", 5);
      TOURNAMENT_FAKE_COUNT_MAX = phantom.getProperty("TournamentFakesCountMax", 5);
      TOURNAMENT_FAKE_JOIN_CHANCE = phantom.getProperty("TournamentFakeJoinChance", 100);
      FAKE_TOURNAMENT_LIST_LOCS = parseLocationList(phantom.getProperty("SpawnLocationsTour", "82698,148638,-3473"));

      ALLOW_FAKE_PLAYER_ANONYMOUS_PVP = phantom.getProperty("AnonymousPvPAllowFakePlayer", true);
      ANONYMOUS_PVP_FAKE_PLAYER_COUNT_MIN = phantom.getProperty("AnonymousPvPFakePlayerCountMin", 6);
      ANONYMOUS_PVP_FAKE_PLAYER_COUNT_MAX = phantom.getProperty("AnonymousPvPFakePlayerCountMax", 14);
      ANONYMOUS_PVP_FAKE_JOIN_CHANCE = phantom.getProperty("AnonymousPvPFakeJoinChance", 100);

      ALLOW_FAKE_PLAYER_ULTIMATE_ZONE = phantom.getProperty("UltimateZoneAllowFakePlayer", true);
      ULTIMATE_ZONE_FAKE_PLAYER_COUNT_MIN = phantom.getProperty("UltimateZoneFakePlayerCountMin", 8);
      ULTIMATE_ZONE_FAKE_PLAYER_COUNT_MAX = phantom.getProperty("UltimateZoneFakePlayerCountMax", 18);
      ULTIMATE_ZONE_FAKE_JOIN_CHANCE = phantom.getProperty("UltimateZoneFakeJoinChance", 100);

      ALLOW_FAKE_PLAYER_AUTO_SPAWN = phantom.getProperty("AutoSpawnAllowFakePlayer", false);
      AUTO_SPAWN_FAKE_COUNT_MIN = phantom.getProperty("AutoSpawnFakesCountMin", 5);
      AUTO_SPAWN_FAKE_COUNT_MAX = phantom.getProperty("AutoSpawnFakesCountMax", 5);
      AUTO_SPAWN_DELAY_TIME = phantom.getProperty("AutoSpawnDelayTime", 15);
      FAKE_AUTO_SPAWN_LIST_LOCS = parseLocationList(phantom.getProperty("AutoSpawnLocations", "82698,148638,-3473"));

      AUTO_SPAWN_ARCHER_PERCENT = phantom.getProperty("AutoSpawnArcherPvP", 20);
      AUTO_SPAWN_NUKER_PERCENT = phantom.getProperty("AutoSpawnNukerPvP", 20);
      AUTO_SPAWN_WARRIOR_PERCENT = phantom.getProperty("AutoSpawnWarriorPvP", 20);
      AUTO_SPAWN_DAGGER_PERCENT = phantom.getProperty("AutoSpawnDaggerPvP", 20);
      AUTO_SPAWN_TANKER_PERCENT = phantom.getProperty("AutoSpawnTankerPvP", 20);
      TOWN_ALLOW_FAKE_PLAYER_AUTO_SPAWN = phantom.getProperty("TownAutoSpawnAllowFakePlayer", false);
      TOWN_AUTO_SPAWN_FAKE_COUNT_MIN = phantom.getProperty("TownAutoSpawnFakesCountMin", 2);
      TOWN_AUTO_SPAWN_FAKE_COUNT_MAX = phantom.getProperty("TownAutoSpawnFakesCountMax", 5);
      TOWN_AUTO_SPAWN_DELAY_TIME = phantom.getProperty("TownAutoSpawnDelayTime", 15);
      TOWN_AUTO_SPAWN_LIST_LOCS = parseLocationList(phantom.getProperty("TownAutoSpawnLocations", ""));
   }

   public static List<FakePrivateBuyHolder> parseBuyList(ExProperties propertie, String configName) {
      List<FakePrivateBuyHolder> auxReturn = new ArrayList<>();
      String aux = propertie.getProperty(configName).trim();

      for (String randomReward : aux.split(";")) {
         String[] infos = randomReward.split(",");
         if (infos.length > 5) {
            auxReturn.add(
               new FakePrivateBuyHolder(
                  Integer.valueOf(infos[0]),
                  Integer.valueOf(infos[1]),
                  Integer.valueOf(infos[2]),
                  Integer.valueOf(infos[3]),
                  Integer.valueOf(infos[4]),
                  Integer.valueOf(infos[5])
               )
            );
         } else {
            auxReturn.add(
               new FakePrivateBuyHolder(
                  Integer.valueOf(infos[0]), Integer.valueOf(infos[1]), Integer.valueOf(infos[2]), Integer.valueOf(infos[3]), Integer.valueOf(infos[4])
               )
            );
         }
      }

      return auxReturn;
   }

   public static List<FakePrivateSellHolder> parseSellList(ExProperties propertie, String configName) {
      List<FakePrivateSellHolder> auxReturn = new ArrayList<>();
      String aux = propertie.getProperty(configName).trim();

      for (String randomReward : aux.split(";")) {
         String[] infos = randomReward.split(",");
         if (infos.length > 3) {
            auxReturn.add(new FakePrivateSellHolder(Integer.valueOf(infos[0]), Integer.valueOf(infos[1]), Integer.valueOf(infos[2]), Integer.valueOf(infos[3])));
         } else {
            auxReturn.add(new FakePrivateSellHolder(Integer.valueOf(infos[0]), Integer.valueOf(infos[1]), Integer.valueOf(infos[2])));
         }
      }

      return auxReturn;
   }

   private static List<Integer> parseIntList(String raw) {
      List<Integer> list = new ArrayList<>();
      if (raw == null || raw.isEmpty()) {
         return list;
      }
      for (String s : raw.split(",")) {
         s = s.trim();
         if (!s.isEmpty()) {
            try {
               list.add(Integer.parseInt(s));
            } catch (NumberFormatException e) {
               _log.warning("FakePlayerConfig: invalid integer '" + s + "', skipping.");
            }
         }
      }
      return list;
   }

   private static CrystalType parseCrystalType(String raw, CrystalType defaultValue) {
      if (raw == null || raw.trim().isEmpty()) {
         return defaultValue;
      }

      try {
         return CrystalType.valueOf(raw.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
         _log.warning("FakePlayerConfig: invalid crystal type '" + raw + "', using " + defaultValue + ".");
         return defaultValue;
      }
   }

   private static List<Location> parseLocationList(String raw) {
      List<Location> list = new ArrayList<>();
      if (raw == null || raw.isEmpty()) {
         return list;
      }
      for (String loc : raw.split(";")) {
         String[] coords = loc.trim().split(",");
         if (coords.length == 3) {
            try {
               list.add(new Location(Integer.parseInt(coords[0].trim()), Integer.parseInt(coords[1].trim()), Integer.parseInt(coords[2].trim())));
            } catch (NumberFormatException e) {
               _log.warning("FakePlayerConfig: invalid location '" + loc + "', skipping.");
            }
         }
      }
      return list;
   }

   private static Location parseLocation(String raw) {
      if (raw == null || raw.trim().isEmpty()) {
         return null;
      }

      String[] coords = raw.trim().split(",");
      if (coords.length != 3) {
         _log.warning("FakePlayerConfig: invalid location '" + raw + "', expected X,Y,Z.");
         return null;
      }

      try {
         return new Location(Integer.parseInt(coords[0].trim()), Integer.parseInt(coords[1].trim()), Integer.parseInt(coords[2].trim()));
      } catch (NumberFormatException e) {
         _log.warning("FakePlayerConfig: invalid location '" + raw + "', skipping.");
         return null;
      }
   }

   public static ExProperties load(String filename) {
      return load(new File(filename));
   }

   public static ExProperties load(File file) {
      ExProperties result = new ExProperties();

      try {
         result.load(file);
      } catch (IOException var3) {
         _log.warning("Error loading config : " + file.getName() + "!");
      }

      return result;
   }

   private static boolean getMergedBoolean(ExProperties primary, ExProperties fallback, String key, boolean defaultValue) {
      return primary.getProperty(key, fallback.getProperty(key, defaultValue));
   }

   private static int getMergedInt(ExProperties primary, ExProperties fallback, String key, int defaultValue) {
      return primary.getProperty(key, fallback.getProperty(key, defaultValue));
   }

   private static String getMergedString(ExProperties primary, ExProperties fallback, String key, String defaultValue) {
      return primary.getProperty(key, fallback.getProperty(key, defaultValue));
   }

   private static String locationToProperty(Location location) {
      if (location == null) {
         return "";
      }

      return location.getX() + "," + location.getY() + "," + location.getZ();
   }
}
