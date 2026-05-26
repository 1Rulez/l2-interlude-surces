package phantom.ai.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.Config;
import com.l2jmega.events.TvT;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.FakePlayerManager;

public class TeamVsTeamAI {
   private static final Logger _log = Logger.getLogger(TeamVsTeamAI.class.getName());
   public static List<FakePlayer> _tvtFakes = new CopyOnWriteArrayList<>();

   public static boolean spawnPhantoms() {
      List<Location> locs = FakePlayerConfig.TVT_FAKE_PLAYER_LIST_LOCS;
      if (locs == null || locs.isEmpty() || TvT._teams == null || TvT._teams.size() < 2) {
         return false;
      }

      int count = Rnd.get(FakePlayerConfig.TVT_FAKE_PLAYER_COUNT_MIN, FakePlayerConfig.TVT_FAKE_PLAYER_COUNT_MAX);
      final int joinChance = Math.max(0, Math.min(100, FakePlayerConfig.TVT_FAKE_JOIN_CHANCE));
      int attempts = 0;
      final int maxAttempts = Math.max(count * 5, count);

      try {
         while (_tvtFakes.size() < count && attempts++ < maxAttempts) {
            if (joinChance < 100 && Rnd.get(100) >= joinChance) {
               continue;
            }

            String teamName = resolveTeamNameForJoin();
            if (teamName == null) {
               break;
            }

            Location loc = locs.get(Rnd.get(locs.size()));
            FakePlayer fp = FakePlayerManager.spawnEventPlayer(loc.getX() + Rnd.get(-210, 210), loc.getY() + Rnd.get(-210, 210), loc.getZ());
            if (fp == null) {
               continue;
            }
            if (SiegeFakeSystem.getInstance().isClanInActiveSiege(fp.getClanId())) {
               fp.setFakeEvent(false);
               fp.despawnPlayer();
               continue;
            }

            TvT.addPlayer(fp, teamName);
            if (!isSuccessfullyRegistered(fp)) {
               fp.setFakeEvent(false);
               fp.despawnPlayer();
               continue;
            }

            fp.setFakeEvent(true);
            fp.assignDefaultAI();
            _tvtFakes.add(fp);
         }

         _log.info("[Fake TvT]: Spawned " + _tvtFakes.size() + " fake players for TvT.");
         return true;
      } catch (Exception e) {
         _log.log(Level.WARNING, "[Fake TvT]: Error spawning phantoms.", e);
         return false;
      }
   }

   public static boolean unspawnPhantoms() {
      try {
         for (FakePlayer fp : _tvtFakes) {
            if (fp != null) {
               fp._inEventTvT = false;
               fp._teamNameTvT = "";
               fp.setFakeEvent(false);
               fp.despawnPlayer();
            }
         }
         _tvtFakes.clear();
         return true;
      } catch (Exception e) {
         e.printStackTrace();
         return false;
      }
   }

   public static void clearPhantomList() {
      _tvtFakes.clear();
   }

   private static boolean isSuccessfullyRegistered(FakePlayer fake) {
      if (fake == null || !fake._inEventTvT) {
         return false;
      }

      if (Config.TVT_EVEN_TEAMS.equals("SHUFFLE")) {
         return true;
      }

      return fake._teamNameTvT != null && !fake._teamNameTvT.isEmpty();
   }

   private static String resolveTeamNameForJoin() {
      if (TvT._teams == null || TvT._teams.isEmpty()) {
         return null;
      }

      if (Config.TVT_EVEN_TEAMS.equals("BALANCE")) {
         int lowestCount = Integer.MAX_VALUE;

         for (String team : TvT._teams) {
            final int playersCount = TvT.teamPlayersCount(team);
            if (playersCount >= 0 && playersCount < lowestCount) {
               lowestCount = playersCount;
            }
         }

         if (lowestCount != Integer.MAX_VALUE) {
            final List<String> joinableTeams = new CopyOnWriteArrayList<>();
            for (String team : TvT._teams) {
               if (TvT.teamPlayersCount(team) == lowestCount) {
                  joinableTeams.add(team);
               }
            }

            if (!joinableTeams.isEmpty()) {
               return joinableTeams.get(Rnd.get(joinableTeams.size()));
            }
         }
      }

      return TvT._teams.get(_tvtFakes.size() % TvT._teams.size());
   }
}
