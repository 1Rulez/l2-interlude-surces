package phantom.ai.event;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Logger;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.events.ArenaTask;
import com.l2jmega.gameserver.model.L2Party;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.commons.random.Rnd;
import java.util.concurrent.ScheduledFuture;
import com.l2jmega.gameserver.model.actor.instance.Player;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.FakePlayerManager;
import com.l2jmega.gameserver.network.serverpackets.CreatureSay;
import com.l2jmega.gameserver.util.Broadcast;

public class TournamentAI {
   public static final Logger _log = Logger.getLogger(TournamentAI.class.getName());
   public static List<Location> locs = new ArrayList<>();
   public static List<FakePlayer> tourFakes = new ArrayList<>();

   private static ScheduledFuture<?> chatTask = null;
   private static final String[] phantomChat = {
      "lf 1x1", "gogo 5x5 arena", "anyone 2x2?", "need partner 2x2", 
      "ready for 5x5", "arena 1x1 go", "who is going 2x2?", 
      "looking for 5x5 team", "fast 1x1 anyone?"
   };

   private static boolean isTournamentRunning() {
      return ArenaTask.is_started();
   }

   private static boolean canUseTournamentFake(FakePlayer fake) {
      return fake != null && fake.isOnline() && !fake.isDead() && fake.isTour() && isTournamentRunning();
   }

   private static boolean skipIfClanInActiveSiege(FakePlayer fake) {
      if (fake == null) {
         return true;
      }
      if (SiegeFakeSystem.getInstance().isClanInActiveSiege(fake.getClanId())) {
         fake.setTour(false);
         fake.despawnPlayer();
         return true;
      }
      return false;
   }

   public static boolean unspawnPhantoms() {
      if (chatTask != null) {
          chatTask.cancel(false);
          chatTask = null;
      }
      try {
         LinkedHashSet<FakePlayer> toDespawn = new LinkedHashSet<>(tourFakes);
         for (Player player : World.getInstance().getPlayers()) {
            if (!(player instanceof FakePlayer)) {
               continue;
            }

            FakePlayer fake = (FakePlayer) player;
            // Include fakes still in a match (inArenaEvent / arenaAttack); old logic skipped them and they could remain.
            if (fake.isTour()
               || fake.isInArenaEvent()
               || fake.isArenaAttack()
               || fake.isArenaProtection()
               || fake.isArena1x1()
               || fake.isArena2x2()
               || fake.isArena5x5()) {
               toDespawn.add(fake);
            }
         }

         for (FakePlayer f : toDespawn) {
            if (f == null) {
               continue;
            }
            ArenaTask.clearPlayerTournamentState(f);
            f.setTour(false);
            f.despawnPlayer();
         }
         tourFakes.clear();

         return true;
      } catch (Exception var2) {
         var2.printStackTrace();
         return false;
      }
   }

   private static int pickJoinMode(int slotsLeft) {
      int weight1x1 = FakePlayerConfig.TOURNAMENT_FAKE_JOIN_1X1 && slotsLeft >= 1 ? Math.max(0, FakePlayerConfig.TOURNAMENT_FAKE_JOIN_WEIGHT_1X1) : 0;
      int weight2x2 = FakePlayerConfig.TOURNAMENT_FAKE_JOIN_2X2 && slotsLeft >= 2 ? Math.max(0, FakePlayerConfig.TOURNAMENT_FAKE_JOIN_WEIGHT_2X2) : 0;
      int weight5x5 = FakePlayerConfig.TOURNAMENT_FAKE_JOIN_5X5 && slotsLeft >= 5 ? Math.max(0, FakePlayerConfig.TOURNAMENT_FAKE_JOIN_WEIGHT_5X5) : 0;
      int totalWeight = weight1x1 + weight2x2 + weight5x5;

      if (totalWeight <= 0) {
         List<Integer> modes = new ArrayList<>();
         if (FakePlayerConfig.TOURNAMENT_FAKE_JOIN_1X1 && slotsLeft >= 1)
            modes.add(1);
         if (FakePlayerConfig.TOURNAMENT_FAKE_JOIN_2X2 && slotsLeft >= 2)
            modes.add(2);
         if (FakePlayerConfig.TOURNAMENT_FAKE_JOIN_5X5 && slotsLeft >= 5)
            modes.add(5);
         if (modes.isEmpty())
            return 1;
         return modes.get(Rnd.get(modes.size()));
      }

      int roll = Rnd.get(totalWeight);
      if (roll < weight1x1)
         return 1;
      roll -= weight1x1;
      if (roll < weight2x2)
         return 2;
      return 5;
   }

   public static boolean spawnPhantoms() {
      if (!FakePlayerConfig.ALLOW_FAKE_PLAYER_TOURNAMENT || !isTournamentRunning()) {
         return false;
      }

      locs = FakePlayerConfig.FAKE_TOURNAMENT_LIST_LOCS;
      tourFakes.clear();
      if (locs == null || locs.isEmpty()) {
         return false;
      }

      try {
         Location loc = null;
         final int fakesCount = Rnd.get(FakePlayerConfig.TOURNAMENT_FAKE_COUNT_MIN, FakePlayerConfig.TOURNAMENT_FAKE_COUNT_MAX);
         final int joinChance = Math.max(0, Math.min(100, FakePlayerConfig.TOURNAMENT_FAKE_JOIN_CHANCE));
         int attempts = 0;
         final int maxAttempts = Math.max(fakesCount * 5, fakesCount);
         int spawned = 0;

         while (spawned < fakesCount && attempts++ < maxAttempts) {
            if (!isTournamentRunning()) {
               break;
            }
            if (joinChance < 100 && Rnd.get(100) >= joinChance) {
               continue;
            }

            int mode = pickJoinMode(fakesCount - spawned);

            if (mode == 1) {
               // === 1x1 solo ===
               loc = locs.get(Rnd.get(locs.size()));
               FakePlayer fakeSoloPlayer = FakePlayerManager.spawnEventPlayer(loc.getX() + Rnd.get(210), loc.getY() + Rnd.get(210), loc.getZ());
               if (skipIfClanInActiveSiege(fakeSoloPlayer)) {
                  continue;
               }
               fakeSoloPlayer.assignDefaultAI();
               fakeSoloPlayer.setTour(true);
               tourFakes.add(fakeSoloPlayer);
               spawned++;

               ThreadPool.schedule(new MoveToNpc(fakeSoloPlayer, () -> fakeSoloPlayer.registerTournament()), Rnd.get(2000, 10000));

            } else if (mode == 2) {
               // === 2x2 party ===
               loc = locs.get(Rnd.get(locs.size()));
               FakePlayer leader = FakePlayerManager.spawnEventPlayer(loc.getX() + Rnd.get(210), loc.getY() + Rnd.get(210), loc.getZ());
               FakePlayer partner = FakePlayerManager.spawnEventPlayer(loc.getX() + Rnd.get(210), loc.getY() + Rnd.get(210), loc.getZ());
               if (skipIfClanInActiveSiege(leader) || skipIfClanInActiveSiege(partner)) {
                  continue;
               }
               leader.assignDefaultAI();
               partner.assignDefaultAI();
               leader.setTour(true);
               partner.setTour(true);

               // Create party
               leader.setParty(new L2Party(leader, 0));
               partner.joinParty(leader.getParty());

               tourFakes.add(leader);
               tourFakes.add(partner);
               spawned += 2;

               ThreadPool.schedule(new MoveToNpc(leader, () -> leader.registerTournament2x2(partner)), Rnd.get(2000, 10000));
               ThreadPool.schedule(new MoveToNpc(partner, null), Rnd.get(2000, 10000));

            } else if (mode == 5) {
               // === 5x5 party ===
               loc = locs.get(Rnd.get(locs.size()));
               FakePlayer p1 = FakePlayerManager.spawnEventPlayer(loc.getX() + Rnd.get(210), loc.getY() + Rnd.get(210), loc.getZ());
               FakePlayer p2 = FakePlayerManager.spawnEventPlayer(loc.getX() + Rnd.get(210), loc.getY() + Rnd.get(210), loc.getZ());
               FakePlayer p3 = FakePlayerManager.spawnEventPlayer(loc.getX() + Rnd.get(210), loc.getY() + Rnd.get(210), loc.getZ());
               FakePlayer p4 = FakePlayerManager.spawnEventPlayer(loc.getX() + Rnd.get(210), loc.getY() + Rnd.get(210), loc.getZ());
               FakePlayer p5 = FakePlayerManager.spawnEventPlayer(loc.getX() + Rnd.get(210), loc.getY() + Rnd.get(210), loc.getZ());
               if (skipIfClanInActiveSiege(p1) || skipIfClanInActiveSiege(p2) || skipIfClanInActiveSiege(p3) || skipIfClanInActiveSiege(p4) || skipIfClanInActiveSiege(p5)) {
                  continue;
               }

               p1.assignDefaultAI();
               p2.assignDefaultAI();
               p3.assignDefaultAI();
               p4.assignDefaultAI();
               p5.assignDefaultAI();

               p1.setTour(true);
               p2.setTour(true);
               p3.setTour(true);
               p4.setTour(true);
               p5.setTour(true);

               // Create party
               p1.setParty(new L2Party(p1, 0));
               p2.joinParty(p1.getParty());
               p3.joinParty(p1.getParty());
               p4.joinParty(p1.getParty());
               p5.joinParty(p1.getParty());

               tourFakes.add(p1);
               tourFakes.add(p2);
               tourFakes.add(p3);
               tourFakes.add(p4);
               tourFakes.add(p5);
               spawned += 5;

               ThreadPool.schedule(new MoveToNpc(p1, () -> p1.registerTournament5x5(p2, p3, p4, p5)), Rnd.get(2000, 10000));
               ThreadPool.schedule(new MoveToNpc(p2, null), Rnd.get(2000, 10000));
               ThreadPool.schedule(new MoveToNpc(p3, null), Rnd.get(2000, 10000));
               ThreadPool.schedule(new MoveToNpc(p4, null), Rnd.get(2000, 10000));
               ThreadPool.schedule(new MoveToNpc(p5, null), Rnd.get(2000, 10000));
            }
         }

         if (chatTask == null) {
            chatTask = ThreadPool.scheduleAtFixedRate(() -> repeatChat(), FakePlayerConfig.TOURNAMENT_FAKE_CHAT_INITIAL_DELAY_MS, FakePlayerConfig.TOURNAMENT_FAKE_CHAT_INTERVAL_MS);
         }

         return true;
      } catch (Exception var3) {
         var3.printStackTrace();
         return false;
      }
   }

   private static void repeatChat() {
      try {
         if (!isTournamentRunning() || tourFakes.isEmpty()) return;
         FakePlayer f = tourFakes.get(Rnd.get(tourFakes.size()));
         if (canUseTournamentFake(f) && !f.isInArenaEvent() && !f.isArenaAttack()) {
             Broadcast.toKnownPlayersInRadius(f, new CreatureSay(f.getObjectId(), 0, f.getName(), phantomChat[Rnd.get(phantomChat.length)]), 1200);
         }
      } catch (Exception e) {
          _log.warning("TournamentAI Chat error: " + e);
      }
   }

   /**
    * Schedules a fake to move to tournament NPC and re-register for 1x1 after a random delay.
    * @param f fake player
    */
   public static void scheduleFakeReRegister1x1(FakePlayer f) {
      if (f == null || !FakePlayerConfig.ALLOW_FAKE_PLAYER_TOURNAMENT || !isTournamentRunning()) return;
      int delayMs = Rnd.get(10000, 25000);
      ThreadPool.schedule(() -> {
         if (!canUseTournamentFake(f)) return;
         new MoveToNpc(f, () -> f.registerTournament()).run();
      }, delayMs);
   }

   /**
    * Schedules a 2x2 pair of fakes to move to NPC and re-register after a random delay.
    * Recreates party (disbanded on arena entry) before re-registration.
    * @param leader leader fake
    * @param partner partner fake
    */
   public static void scheduleFakeReRegister2x2(FakePlayer leader, FakePlayer partner) {
      if (leader == null || partner == null || !FakePlayerConfig.ALLOW_FAKE_PLAYER_TOURNAMENT || !isTournamentRunning()) return;
      int delayMs = Rnd.get(10000, 25000);
      ThreadPool.schedule(() -> {
         if (!isTournamentRunning() || !canUseTournamentFake(leader) || !canUseTournamentFake(partner)) return;
         if (leader.isInParty()) leader.leaveParty();
         if (partner.isInParty()) partner.leaveParty();
         leader.setParty(new L2Party(leader, 0));
         partner.joinParty(leader.getParty());
         ThreadPool.schedule(new MoveToNpc(leader, () -> leader.registerTournament2x2(partner)), Rnd.get(0, 2000));
         ThreadPool.schedule(new MoveToNpc(partner, null), Rnd.get(0, 2000));
      }, delayMs);
   }

   /**
    * Schedules a 5x5 team of fakes to move to NPC and re-register after a random delay.
    * Recreates party (disbanded on arena entry) before re-registration.
    * @param p1 leader
    * @param p2 second member
    * @param p3 third member
    * @param p4 fourth member
    * @param p5 fifth member
    */
   public static void scheduleFakeReRegister5x5(FakePlayer p1, FakePlayer p2, FakePlayer p3, FakePlayer p4, FakePlayer p5) {
      if (p1 == null || p2 == null || p3 == null || p4 == null || p5 == null || !FakePlayerConfig.ALLOW_FAKE_PLAYER_TOURNAMENT || !isTournamentRunning()) return;
      int delayMs = Rnd.get(10000, 25000);
      ThreadPool.schedule(() -> {
         FakePlayer[] team = { p1, p2, p3, p4, p5 };
         for (FakePlayer fp : team)
            if (!canUseTournamentFake(fp)) return;
         for (FakePlayer fp : team)
            if (fp.isInParty()) fp.leaveParty();
         p1.setParty(new L2Party(p1, 0));
         p2.joinParty(p1.getParty());
         p3.joinParty(p1.getParty());
         p4.joinParty(p1.getParty());
         p5.joinParty(p1.getParty());
         ThreadPool.schedule(new MoveToNpc(p1, () -> p1.registerTournament5x5(p2, p3, p4, p5)), Rnd.get(0, 2000));
         ThreadPool.schedule(new MoveToNpc(p2, null), Rnd.get(0, 2000));
         ThreadPool.schedule(new MoveToNpc(p3, null), Rnd.get(0, 2000));
         ThreadPool.schedule(new MoveToNpc(p4, null), Rnd.get(0, 2000));
         ThreadPool.schedule(new MoveToNpc(p5, null), Rnd.get(0, 2000));
      }, delayMs);
   }

   public static class MoveToNpc implements Runnable {
      FakePlayer f;
      int radius = Rnd.get(-100, 100);
      Runnable afterMove;

      public MoveToNpc(FakePlayer f, Runnable afterMove) {
         this.f = f;
         this.afterMove = afterMove;
      }

      @Override
      public void run() {
         if (!canUseTournamentFake(this.f)) {
            return;
         }
         this.f.setRunning();
         this.f.getFakeAi().moveTo(-21469 + this.radius, -21000 + this.radius, -3026);
         if (afterMove != null) {
            ThreadPool.schedule(() -> {
               if (canUseTournamentFake(this.f)) {
                  afterMove.run();
               }
            }, Rnd.get(5000, 10000));
         }
      }
   }
}
