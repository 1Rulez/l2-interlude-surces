package phantom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.data.MapRegionTable;
import com.l2jmega.gameserver.model.pledge.Clan;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.base.ClassId;
import com.l2jmega.gameserver.model.zone.ZoneId;
import com.l2jmega.gameserver.network.L2GameClient;
import com.l2jmega.gameserver.network.SystemMessageId;
import com.l2jmega.gameserver.network.serverpackets.PledgeShowMemberListUpdate;
import com.l2jmega.gameserver.network.serverpackets.SystemMessage;
import phantom.ai.FakePlayerUtilsAI;
import phantom.ai.autospawn.AutoSpawnAI;
import phantom.ai.autospawn.TownAutoSpawnAI;
import phantom.ai.check.CheckFakeManager;
import phantom.helpers.FakeHelpers;
import com.l2jmega.gameserver.communitybbs.PlayerStatsUpdateTask;


public enum FakePlayerManager {
   INSTANCE;

   private static final Logger _log = Logger.getLogger(FakePlayerManager.class.getName());
   private static final int STARTUP_FAKE_INIT_MIN_DELAY_SEC = 10;
   private static final int ADMIN_FAKE_STARTUP_BATCH_SIZE = 25;
   private static final long ADMIN_FAKE_STARTUP_BATCH_INTERVAL_MS = 1500L;

   private static final Map<Integer, FakePlayer> _fakePlayers = new ConcurrentHashMap<>();
   private static volatile List<FakePlayer> _fakePlayersSnapshot = Collections.emptyList();

   public static boolean isClanFakeSystemEnabled() {
      return FakePlayerConfig.ALLOW_FAKE_CLAN_PLAYERS;
   }

   // --- Clan city fakes ---
   public static FakePlayer spawnClanArcherCity(int x, int y, int z) {
      if (!isClanFakeSystemEnabled()) {
         return null;
      }

      FakePlayer fake = spawnClanArcher(x, y, z);
      if (fake == null) {
         return null;
      }

      fake.setFakeAi(new phantom.ai.walker.CitizenAI(fake));
      return fake;
   }

   public static FakePlayer spawnClanNukerCity(int x, int y, int z) {
      if (!isClanFakeSystemEnabled()) {
         return null;
      }

      FakePlayer fake = spawnClanNuker(x, y, z);
      if (fake == null) {
         return null;
      }

      fake.setFakeAi(new phantom.ai.walker.CitizenAI(fake));
      return fake;
   }

   public static FakePlayer spawnClanWarriorCity(int x, int y, int z) {
      if (!isClanFakeSystemEnabled()) {
         return null;
      }

      FakePlayer fake = spawnClanWarrior(x, y, z);
      if (fake == null) {
         return null;
      }

      fake.setFakeAi(new phantom.ai.walker.CitizenAI(fake));
      return fake;
   }

   public static void initialise() {
      FakePlayerNameManager.INSTANCE.initialise();
      FakePlayerTaskManager.INSTANCE.initialise();
      CheckFakeManager.getInstance();

      final int startupDelaySec = Math.max(STARTUP_FAKE_INIT_MIN_DELAY_SEC, FakePlayerConfig.AUTO_SPAWN_DELAY_TIME);
      _log.info("[Fake Players] Delaying heavy startup spawn by " + startupDelaySec + "s to reduce GS startup load.");

      ThreadPool.schedule(() ->
      {
         if (!FakePlayerConfig.ALLOW_FAKE_PLAYER_AUTO_SPAWN) {
            final int despawned = despawnAllRegularFakes();
            _log.info("[Fake Players] AutoSpawnAllowFakePlayer is disabled; skipped startup fake spawns and despawned " + despawned + " active fake(s).");
            PlayerStatsUpdateTask.forceUpdateNow();
            return;
         }

         AutoSpawnAI.getInstance().scheduleSpawnIfEnabled();
         TownAutoSpawnAI.getInstance().scheduleSpawnIfEnabled();
         AdminSpawnedFakesStorage.loadAndSpawnGradual(ADMIN_FAKE_STARTUP_BATCH_SIZE, ADMIN_FAKE_STARTUP_BATCH_INTERVAL_MS);
         // Refresh CommunityBoard stats after fake systems start.
         PlayerStatsUpdateTask.forceUpdateNow();
      }, startupDelaySec * 1000L);
   }

   public static FakePlayer spawnEventPlayer(int x, int y, int z) {
      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);
      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);
      FakePlayer activeChar = FakeHelpers.createRandomTvTFakePlayer(accountName);
      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }

      activeChar.assignDefaultAI();
      activeChar.heal();
      return activeChar;
   }

   public static FakePlayer spawnPlayer(int x, int y, int z) {
      return spawnPlayerByClass(x, y, z, null);
   }

   public static FakePlayer spawnPlayerByClass(int x, int y, int z, ClassId classId) {
      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);

      // Generate or reuse fake account and ensure it exists in DB.
      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);

      // Create persistent FakePlayer (DB character + gear, skills, etc.).
      FakePlayer activeChar = (classId != null) ? FakeHelpers.createFakePlayer(accountName, classId) : FakeHelpers.createRandomFakePlayer(accountName);

      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_NAME.isEmpty()) {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_NAME));
      } else {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorNameFromWordlist()));
      }

      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE.isEmpty()) {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE));
      } else {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorTitleFromWordlist()));
      }

      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }

      activeChar.assignDefaultAI();
      applyFullBuffsAfterAdminSpawn(activeChar);
      return activeChar;
   }

   public static FakePlayer spawnClanPlayer(int x, int y, int z) {
      return spawnClanPlayer(x, y, z, null);
   }

   public static FakePlayer spawnClanPlayer(int x, int y, int z, Clan preferredClan) {
      if (!isClanFakeSystemEnabled()) {
         return null;
      }

      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);

      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);

      FakePlayer activeChar = FakeHelpers.createRandomClanFakePlayer(accountName, preferredClan);

      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      handlePlayerClanOnSpawn(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_NAME.isEmpty()) {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_NAME));
      } else {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorNameFromWordlist()));
      }

      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE.isEmpty()) {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE));
      } else {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorTitleFromWordlist()));
      }

      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }

      applyFullBuffsAfterAdminSpawn(activeChar);
      return activeChar;
   }

   public static FakePlayer spawnArcher(int x, int y, int z) {
      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);

      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);

      FakePlayer activeChar = FakeHelpers.createArcherFakePlayer(accountName);

      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_NAME.isEmpty()) {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_NAME));
      } else {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorNameFromWordlist()));
      }

      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE.isEmpty()) {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE));
      } else {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorTitleFromWordlist()));
      }

      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }

      applyFullBuffsAfterAdminSpawn(activeChar);
      return activeChar;
   }

   public static FakePlayer spawnClanArcher(int x, int y, int z) {
      if (!isClanFakeSystemEnabled()) {
         return null;
      }

      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);

      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);

      FakePlayer activeChar = FakeHelpers.createArcherClanFakePlayer(accountName);

      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      handlePlayerClanOnSpawn(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_NAME.isEmpty()) {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_NAME));
      } else {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorNameFromWordlist()));
      }

      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE.isEmpty()) {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE));
      } else {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorTitleFromWordlist()));
      }

      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }

      applyFullBuffsAfterAdminSpawn(activeChar);
      return activeChar;
   }

   public static FakePlayer spawnNuker(int x, int y, int z) {
      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);
      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);
      FakePlayer activeChar = FakeHelpers.createNukerFakePlayer(accountName);
      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_NAME.isEmpty()) {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_NAME));
      } else {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorNameFromWordlist()));
      }

      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE.isEmpty()) {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE));
      } else {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorTitleFromWordlist()));
      }

      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }

      applyFullBuffsAfterAdminSpawn(activeChar);
      return activeChar;
   }

   public static FakePlayer spawnClanNuker(int x, int y, int z) {
      if (!isClanFakeSystemEnabled()) {
         return null;
      }

      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);
      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);
      FakePlayer activeChar = FakeHelpers.createNukerClanFakePlayer(accountName);
      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      handlePlayerClanOnSpawn(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_NAME.isEmpty()) {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_NAME));
      } else {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorNameFromWordlist()));
      }

      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE.isEmpty()) {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE));
      } else {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorTitleFromWordlist()));
      }

      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }

      applyFullBuffsAfterAdminSpawn(activeChar);
      return activeChar;
   }

   public static FakePlayer spawnWarrior(int x, int y, int z) {
      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);
      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);
      FakePlayer activeChar = FakeHelpers.createWarriorFakePlayer(accountName);
      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_NAME.isEmpty()) {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_NAME));
      } else {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorNameFromWordlist()));
      }

      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE.isEmpty()) {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE));
      } else {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorTitleFromWordlist()));
      }

      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }

      applyFullBuffsAfterAdminSpawn(activeChar);
      return activeChar;
   }

   public static FakePlayer spawnClanWarrior(int x, int y, int z) {
      if (!isClanFakeSystemEnabled()) {
         return null;
      }

      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);
      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);
      FakePlayer activeChar = FakeHelpers.createWarriorClanFakePlayer(accountName);
      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      handlePlayerClanOnSpawn(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_NAME.isEmpty()) {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_NAME));
      } else {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorNameFromWordlist()));
      }

      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE.isEmpty()) {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE));
      } else {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorTitleFromWordlist()));
      }

      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }

      applyFullBuffsAfterAdminSpawn(activeChar);
      return activeChar;
   }

   public static FakePlayer spawnDagger(int x, int y, int z) {
      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);
      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);
      FakePlayer activeChar = FakeHelpers.createDaggerFakePlayer(accountName);
      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_NAME.isEmpty()) {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_NAME));
      } else {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorNameFromWordlist()));
      }

      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE.isEmpty()) {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE));
      } else {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorTitleFromWordlist()));
      }

      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }

      applyFullBuffsAfterAdminSpawn(activeChar);
      return activeChar;
   }

   public static FakePlayer spawnClanDagger(int x, int y, int z) {
      if (!isClanFakeSystemEnabled()) {
         return null;
      }

      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);
      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);
      FakePlayer activeChar = FakeHelpers.createDaggerClanFakePlayer(accountName);
      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      handlePlayerClanOnSpawn(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_NAME.isEmpty()) {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_NAME));
      } else {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorNameFromWordlist()));
      }

      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE.isEmpty()) {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE));
      } else {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorTitleFromWordlist()));
      }

      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }

      applyFullBuffsAfterAdminSpawn(activeChar);
      return activeChar;
   }

   public static FakePlayer spawnTanker(int x, int y, int z) {
      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);
      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);
      FakePlayer activeChar = FakeHelpers.createTankerFakePlayer(accountName);
      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_NAME.isEmpty()) {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_NAME));
      } else {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorNameFromWordlist()));
      }

      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE.isEmpty()) {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE));
      } else {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorTitleFromWordlist()));
      }

      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }

      applyFullBuffsAfterAdminSpawn(activeChar);
      return activeChar;
   }

   public static FakePlayer spawnClanTanker(int x, int y, int z) {
      if (!isClanFakeSystemEnabled()) {
         return null;
      }

      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);
      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);
      FakePlayer activeChar = FakeHelpers.createTankerClanFakePlayer(accountName);
      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      handlePlayerClanOnSpawn(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_NAME.isEmpty()) {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_NAME));
      } else {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorNameFromWordlist()));
      }

      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE.isEmpty()) {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE));
      } else {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorTitleFromWordlist()));
      }

      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }

      applyFullBuffsAfterAdminSpawn(activeChar);
      return activeChar;
   }

   public static FakePlayer spawnHealer(int x, int y, int z) {
      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);
      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);
      FakePlayer activeChar = FakeHelpers.createHealerFakePlayer(accountName);
      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_NAME.isEmpty()) {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_NAME));
      } else {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorNameFromWordlist()));
      }
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE.isEmpty()) {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE));
      } else {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorTitleFromWordlist()));
      }
      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }
      applyFullBuffsAfterAdminSpawn(activeChar);
      return activeChar;
   }

   public static FakePlayer spawnHealerByClass(int x, int y, int z, ClassId classId) {
      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);
      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);
      FakePlayer activeChar = FakeHelpers.createHealerFakePlayerByClass(accountName, classId);
      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_NAME.isEmpty()) {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_NAME));
      } else {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorNameFromWordlist()));
      }
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE.isEmpty()) {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE));
      } else {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorTitleFromWordlist()));
      }
      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }
      applyFullBuffsAfterAdminSpawn(activeChar);
      return activeChar;
   }

   public static FakePlayer spawnClanHealer(int x, int y, int z) {
      if (!isClanFakeSystemEnabled()) {
         return null;
      }

      L2GameClient client = new L2GameClient(null);
      client.setDetached(true);
      String accountName = FakeAccountService.generateNextFakeAccount();
      FakeAccountService.ensureFakeAccountExists(accountName);
      FakePlayer activeChar = FakeHelpers.createHealerClanFakePlayer(accountName);
      activeChar.setClient(client);
      client.setActiveChar(activeChar);
      activeChar.setOnlineStatus(true, true);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(accountName);
      World.getInstance().addPlayer(activeChar);
      registerFakePlayer(activeChar);
      handlePlayerClanOnSpawn(activeChar);
      activeChar.spawnMe(x, y, z);
      activeChar.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(activeChar);
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_NAME.isEmpty()) {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_NAME));
      } else {
         activeChar.getAppearance().setNameColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorNameFromWordlist()));
      }
      if (!FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE.isEmpty()) {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerConfig.FAKE_PLAYER_COLOR_TITLE));
      } else {
         activeChar.getAppearance().setTitleColor(Integer.decode("0x" + FakePlayerUtilsAI.getRandomColorTitleFromWordlist()));
      }
      if (!activeChar.isGM() && (!activeChar.isInSiege() || activeChar.getSiegeState() <= 0) && activeChar.isInsideZone(ZoneId.SIEGE)) {
         activeChar.teleToLocation(MapRegionTable.TeleportType.TOWN);
      }
      applyFullBuffsAfterAdminSpawn(activeChar);
      return activeChar;
   }

   public static void despawnFakePlayer(int objectId) {
      Player player = World.getInstance().getPlayer(objectId);
      if (player instanceof FakePlayer) {
         FakePlayer fakePlayer = (FakePlayer)player;
         fakePlayer.despawnPlayer();
      }
   }

   public static FakePlayer loginRestoredFakePlayer(int objectId, int x, int y, int z) {
      final Player existing = World.getInstance().getPlayer(objectId);
      if (existing instanceof FakePlayer) {
         final FakePlayer fake = (FakePlayer) existing;
         if (!getFakePlayers().contains(fake)) {
            registerFakePlayer(fake);
         }
         return fake;
      }

      final Player restored = Player.restore(objectId);
      if (!(restored instanceof FakePlayer)) {
         return null;
      }

      return spawnRestoredFakeIntoWorld((FakePlayer) restored, x, y, z);
   }

   public static FakePlayer spawnRestoredFakeIntoWorld(FakePlayer fake, int x, int y, int z) {
      final L2GameClient client = new L2GameClient(null);
      client.setDetached(true);
      client.setActiveChar(fake);
      client.setState(L2GameClient.GameClientState.IN_GAME);
      client.setAccountName(fake.getAccountName());

      fake.setClient(client);
      fake.setRunning();
      fake.standUp();
      fake.setOnlineStatus(true, true);
      World.getInstance().addPlayer(fake);
      registerFakePlayer(fake);
      handlePlayerClanOnSpawn(fake, false);
      fake.spawnMe(x, y, z);
      fake.onPlayerEnter();
      FakeHelpers.grantGrandBossAccessPasses(fake);
      fake.assignDefaultAI();
      fake.heal();
      return fake;
   }

   public static FakePlayer loginRestoredFakePlayer(int objectId) {
      final Player existing = World.getInstance().getPlayer(objectId);
      if (existing instanceof FakePlayer) {
         final FakePlayer fake = (FakePlayer) existing;
         if (!getFakePlayers().contains(fake)) {
            registerFakePlayer(fake);
         }
         return fake;
      }

      final Player restored = Player.restore(objectId);
      if (!(restored instanceof FakePlayer)) {
         return null;
      }

      final FakePlayer fake = (FakePlayer) restored;
      final int spawnX = fake.getPosition().getX();
      final int spawnY = fake.getPosition().getY();
      final int spawnZ = fake.getPosition().getZ();
      return spawnRestoredFakeIntoWorld(fake, spawnX, spawnY, spawnZ);
   }

   private static void handlePlayerClanOnSpawn(FakePlayer activeChar) {
      handlePlayerClanOnSpawn(activeChar, true);
   }

   private static void handlePlayerClanOnSpawn(FakePlayer activeChar, boolean randomizeTitle) {
      Clan clan = activeChar.getClan();
      if (clan != null) {
         // Randomly grant invite rights to make clan-fake behavior less predictable.
         // Leaders always keep full rights; regular members get invite rights by chance.
         if (activeChar.isClanLeader()) {
            activeChar.setClanPrivileges(Clan.CP_ALL);
         } else if (Rnd.get(100) < 45) {
            activeChar.setClanPrivileges(activeChar.getClanPrivileges() | Clan.CP_CL_JOIN_CLAN);
         }

         if (randomizeTitle) {
            activeChar.setTitle(FakePlayerUtilsAI.getRandomClanTitle());
         }
         activeChar.broadcastUserInfo();
         clan.getClanMember(activeChar.getObjectId()).setPlayerInstance(activeChar);
         SystemMessage msg = SystemMessage.getSystemMessage(SystemMessageId.CLAN_MEMBER_S1_LOGGED_IN).addCharName(activeChar);
         PledgeShowMemberListUpdate update = new PledgeShowMemberListUpdate(activeChar);

         for (Player member : clan.getOnlineMembers()) {
            if (member != activeChar) {
               member.sendPacket(msg);
               member.sendPacket(update);
            }
         }
      }
   }

   public static int getFakePlayersCount() {
      return _fakePlayersSnapshot.size();
   }

   public static int despawnAllRegularFakes() {
      int despawned = 0;
      for (FakePlayer fakePlayer : new ArrayList<>(getFakePlayers())) {
         if (fakePlayer == null) {
            continue;
         }

         if (fakePlayer.isInOlympiadMode() || fakePlayer.isOlympiadProtection() || fakePlayer.getOlympiadGameId() != -1) {
            continue;
         }

         if (fakePlayer.isFakeEvent() || fakePlayer.isFakeKTBEvent() || fakePlayer.isTour()) {
            continue;
         }

         fakePlayer.despawnPlayer();
         despawned++;
      }
      return despawned;
   }

   public static List<FakePlayer> getFakePlayers() {
      return _fakePlayersSnapshot;
   }

   /**
    * Админ-спавн: полный CB/Scheme Buffer (не для ивентов — см. {@link #spawnEventPlayer}).
    * @param activeChar заспавненный фейк
    */
   private static void applyFullBuffsAfterAdminSpawn(FakePlayer activeChar) {
      FakeHelpers.applySchemeBufferFullBuffs(activeChar);
   }

   public static void registerFakePlayer(FakePlayer fakePlayer) {
      if (fakePlayer == null) {
         return;
      }

      _fakePlayers.put(fakePlayer.getObjectId(), fakePlayer);
      refreshFakePlayerSnapshot();
   }

   public static void unregisterFakePlayer(FakePlayer fakePlayer) {
      if (fakePlayer == null) {
         return;
      }

      if (_fakePlayers.remove(fakePlayer.getObjectId()) != null) {
         refreshFakePlayerSnapshot();
      }
   }

   private static void refreshFakePlayerSnapshot() {
      _fakePlayersSnapshot = Collections.unmodifiableList(new ArrayList<>(_fakePlayers.values()));
   }
}
