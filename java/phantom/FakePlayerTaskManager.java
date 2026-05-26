package phantom;

import java.util.List;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import phantom.ai.RaidPartyHelper;
import phantom.ai.event.ClanFakePresenceSystem;
import phantom.ai.event.SiegeFakeSystem;
import phantom.ai.event.ClanWarFakeSystem;
import phantom.task.AITaskRunner;

public enum FakePlayerTaskManager {
   INSTANCE;

   @SuppressWarnings("unused")
   private final int aiTaskRunnerInterval = 500;
   @SuppressWarnings("unused")
   private final int _playerCountPerTask = 2000;
   private volatile int _taskCount;

   public void initialise() {
      try {
         final RaidPartyHelper raidPartyHelper = RaidPartyHelper.getInstance();
         final Object olympiadFakeSystem = resolveOlympiadSystem();
         
         // Initialize siege and clan war systems
         if (FakePlayerConfig.ALLOW_FAKE_CLAN_PLAYERS && FakePlayerConfig.FAKE_SIEGE_ENABLED)
            SiegeFakeSystem.getInstance();
         if (FakePlayerConfig.ALLOW_FAKE_CLAN_PLAYERS)
            ClanFakePresenceSystem.getInstance();
         if (FakePlayerConfig.ALLOW_FAKE_CLAN_PLAYERS && FakePlayerConfig.FAKE_CLAN_WAR_ENABLED)
            ClanWarFakeSystem.getInstance();
         
         ThreadPool.scheduleAtFixedRate(new AITaskRunner(), aiTaskRunnerInterval, aiTaskRunnerInterval);
         ThreadPool.scheduleAtFixedRate(() -> phantom.ai.FakePlayerChatManager.INSTANCE.processFakeInitiatedMessages(), 20000L, 20000L);
         // Persist fake online time in DB for CommunityBoard statistics.
         ThreadPool.scheduleAtFixedRate(() -> {
            final List<FakePlayer> fakePlayers = FakePlayerManager.getFakePlayers();
            for (Player p : fakePlayers) {
               if (p != null && p.isOnline())
                  p.updateOnlineTimeInDb();
            }
         }, 60000L, 60000L);
         ThreadPool.scheduleAtFixedRate(() -> {
            final List<FakePlayer> fakePlayers = FakePlayerManager.getFakePlayers();
            for (FakePlayer fake : fakePlayers) {
               if (fake == null || !fake.isOnline())
                  continue;

               raidPartyHelper.addAvailableHelper(fake);
               if (FakePlayerConfig.isAutomatedFakePopulationEnabled() && FakePlayerConfig.FAKE_OLYMPIAD_ENABLED && olympiadFakeSystem != null)
                  invokeOlympiad(olympiadFakeSystem, "registerForOlympiad", new Class<?>[]{FakePlayer.class}, new Object[]{fake});

               if (fake.isInOlympiadMode() && olympiadFakeSystem != null)
                  invokeOlympiad(olympiadFakeSystem, "updateOlympiadAI", new Class<?>[]{FakePlayer.class, phantom.model.FakeEmotion.class}, new Object[]{fake, fake.getEmotion()});
            }

            if (FakePlayerConfig.isAutomatedFakePopulationEnabled() && FakePlayerConfig.FAKE_RAID_HELPER_ENABLED) {
               for (Player player : World.getInstance().getPlayers()) {
                  if (player != null && player.isOnline() && !(player instanceof FakePlayer))
                     raidPartyHelper.checkRaidHelpRequest(player);
               }
            }
         }, 15000L, 30000L);
         this._taskCount = 0;
      } catch (Throwable var2) {
         var2.printStackTrace();
      }
   }

   public void runAITick() {
      final List<FakePlayer> fakePlayers = FakePlayerManager.getFakePlayers();
      final int fakePlayerCount = fakePlayers.size();
      final int tasksNeeded = calculateTasksNeeded(fakePlayerCount);
      _taskCount = tasksNeeded;

      for (int i = 0; i < tasksNeeded; i++) {
         final int from = i * 2000;
         final int to = (i + 1) * 2000;
         ThreadPool.execute(new phantom.task.AITask(fakePlayers, from, to));
      }
   }

   private static int calculateTasksNeeded(int fakePlayerCount) {
      return fakePlayerCount <= 0 ? 0 : ((fakePlayerCount - 1) / 2000) + 1;
   }

   public static int getPlayerCountPerTask() {
      return 2000;
   }

   public int getTaskCount() {
      return _taskCount;
   }

   private static Object resolveOlympiadSystem() {
      try {
         final Class<?> clazz = Class.forName("phantom.ai.event.OlympiadFakeSystem");
         return clazz.getMethod("getInstance").invoke(null);
      } catch (Exception e) {
         return null;
      }
   }

   private static void invokeOlympiad(Object instance, String method, Class<?>[] parameterTypes, Object[] args) {
      try {
         instance.getClass().getMethod(method, parameterTypes).invoke(instance, args);
      } catch (Exception ignored) {
      }
   }
}
