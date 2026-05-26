package phantom.ai.event;

import java.util.List;

import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.pledge.Clan;

import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.FakePlayerManager;

/**
 * System that makes fake players engage in PvP against enemy clan members
 * when their clan is at war.
 */

public class ClanWarFakeSystem {

   private static volatile ClanWarFakeSystem _instance;

   private ClanWarFakeSystem() {
      ThreadPool.scheduleAtFixedRate(this::processClanWarPvP, 30000, 10000);
   }

   public static ClanWarFakeSystem getInstance() {
      if (_instance == null) {
         synchronized (ClanWarFakeSystem.class) {
            if (_instance == null) {
               _instance = new ClanWarFakeSystem();
            }
         }
      }
      return _instance;
   }

   /**
    * Periodically check if any online fake player has a clan at war,
    * and if an enemy is nearby, engage in PvP.
    */
   private void processClanWarPvP() {
      if (!FakePlayerConfig.FAKE_CLAN_WAR_ENABLED) {
         return;
      }

      for (FakePlayer fake : FakePlayerManager.getFakePlayers()) {
         if (fake == null || fake.isDead() || !fake.isOnline()) {
            continue;
         }

         // Skip busy fakes
         if (fake.isInOlympiadMode() || fake.isFakeEvent() || fake.isTour()
            || fake.isFakeKTBEvent() || fake.isInCombat() || fake.isInSiege()) {
            continue;
         }

         final Clan clan = fake.getClan();
         if (clan == null || !clan.isAtWar()) {
            continue;
         }

         // Chance check per tick
         if (Rnd.get(100) >= FakePlayerConfig.FAKE_CLAN_WAR_ATTACK_CHANCE) {
            continue;
         }

         // Find nearby enemy clan member
         final Player enemy = findNearbyEnemy(fake, clan);
         if (enemy == null) {
            continue;
         }

         // Engage the enemy - set target and AI will handle combat
         fake.setTarget(enemy);

         if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
            fake.say("[WAR] Engaging " + enemy.getName());
         }
      }
   }

   /**
    * Find a nearby player whose clan is at war with the fake's clan.
 * @param fake 
 * @param fakeClan 
 * @return 
    */
   private static Player findNearbyEnemy(FakePlayer fake, Clan fakeClan) {
      final List<Integer> enemies = fakeClan.getWarList();
      if (enemies == null || enemies.isEmpty()) {
         return null;
      }

      Player closest = null;
      final int searchRadius = Math.max(300, FakePlayerConfig.FAKE_CLAN_WAR_SEARCH_RADIUS);
      double closestDist = (long) searchRadius * searchRadius;

      for (Player player : fake.getKnownTypeInRadius(Player.class, searchRadius)) {
         if (player == null || player == fake || player.isDead() || !player.isOnline()) {
            continue;
         }

         // Skip other fakes if configured
         if (!FakePlayerConfig.FAKE_CLAN_WAR_ATTACK_FAKES && player instanceof FakePlayer) {
            continue;
         }

         final Clan targetClan = player.getClan();
         if (targetClan == null) {
            continue;
         }

         // Check mutual war
         if (!enemies.contains(targetClan.getClanId())) {
            continue;
         }

         if (!targetClan.isAtWarWith(fakeClan.getClanId())) {
            continue;
         }

         // Skip academy members
         if (player.getPledgeType() == Clan.SUBUNIT_ACADEMY) {
            continue;
         }

         final double dist = fake.getDistanceSq(player);
         if (dist < closestDist) {
            closestDist = dist;
            closest = player;
         }
      }

      return closest;
   }
}
