package phantom.ai;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.gameserver.model.actor.ai.CtrlIntention;
import com.l2jmega.gameserver.data.MapRegionTable;
import com.l2jmega.gameserver.data.MapRegionTable.TeleportType;

import com.l2jmega.gameserver.geoengine.GeoEngine;
import com.l2jmega.gameserver.instancemanager.CastleManager;
import com.l2jmega.gameserver.model.entity.Castle;
import com.l2jmega.gameserver.model.entity.Siege;
import com.l2jmega.gameserver.model.entity.Siege.SiegeSide;
import com.l2jmega.gameserver.model.location.SpawnLocation;
import com.l2jmega.gameserver.model.WorldObject;
import com.l2jmega.gameserver.model.L2Skill;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.gameserver.model.actor.Creature;
import com.l2jmega.gameserver.model.actor.Summon;
import com.l2jmega.gameserver.model.actor.instance.Door;
import com.l2jmega.gameserver.model.actor.instance.Monster;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.actor.instance.RaidBoss;
import com.l2jmega.gameserver.model.actor.instance.Servitor;
import com.l2jmega.Config;
import com.l2jmega.gameserver.model.zone.ZoneId;
import com.l2jmega.gameserver.network.serverpackets.MoveToLocation;
import com.l2jmega.gameserver.network.serverpackets.MoveToPawn;
import com.l2jmega.gameserver.network.serverpackets.StopMove;
import com.l2jmega.gameserver.network.serverpackets.StopRotation;
import com.l2jmega.gameserver.network.serverpackets.TeleportToLocation;
import com.l2jmega.gameserver.network.serverpackets.CreatureSay;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.util.Broadcast;
import Base.custom.event.AnonymousPvPEvent;
import phantom.FakePlayer;
import phantom.FakePlayerConfig;
import phantom.ai.party.PartyMode;
import phantom.helpers.FakeHelpers;

public abstract class FakePlayerAI {
   private static final int TOURNAMENT_TARGET_RADIUS = 3000;
   public final FakePlayer _fakePlayer;
   protected volatile boolean _clientMoving;
   protected volatile boolean _clientAutoAttacking;
   private long _moveToPawnTimeout;
   protected int _clientMovingToPawnOffset;
   protected boolean _isBusyThinking = false;
   protected int iterationsOnDeath = 0;
   private long _lastBossEventResRequestAt;
   @SuppressWarnings("unused")
   private final int toVillageIterationsOnDeath = 10;
   private boolean _despawnScheduled = false;
   private boolean _siegeRespawnScheduled = false;
   private long _lastMoveDebugAt;
   private int _lastMoveTargetX;
   private int _lastMoveTargetY;
   private int _lastMoveTargetZ;
   private long _lastObstacleBypassMs = 0L;
   private static final long OBSTACLE_BYPASS_COOLDOWN_MS = 550L;
   private static final int OBSTACLE_BYPASS_MIN_STEP = 40;
   private static final int OBSTACLE_BYPASS_REUSE_DISTANCE = 60;
   private static final long OBSTACLE_BYPASS_HOLD_MS = 2500L;
   private int _activeObstacleBypassTargetId = 0;
   private int _activeObstacleBypassX = 0;
   private int _activeObstacleBypassY = 0;
   private int _activeObstacleBypassZ = 0;
   private long _activeObstacleBypassUntilMs = 0L;

   public FakePlayerAI(FakePlayer character) {
      this._fakePlayer = character;
      this.setup();
   }

   public void setup() {
      this._fakePlayer.setIsRunning(true);
      scheduleNormalChat();
   }

   protected void handleDeath() {
      if (this._fakePlayer.isInOlympiadMode() || this._fakePlayer.isOlympiadProtection() || this._fakePlayer.getOlympiadGameId() != -1) {
         this.iterationsOnDeath = 0;
         return;
      }

      if (this._fakePlayer.isDead() && isActiveSiegeParticipant()) {
         handleSiegeDeath();
         return;
      }

      _siegeRespawnScheduled = false;

      if (this._fakePlayer.isFakeKTBEvent()) {
         if (this._fakePlayer.isDead()) {
            maybeRequestBossEventRes();
         } else {
            _lastBossEventResRequestAt = 0L;
         }
      }

      if ((!this._fakePlayer.isDead() || !(this._fakePlayer._inEventTvT || this._fakePlayer._inEventCTF))
         && (!this._fakePlayer.isDead() || !this._fakePlayer.isFakeKTBEvent())) {
         if (this._fakePlayer.isDead() && !this._fakePlayer.isInsideZone(ZoneId.PVP_CUSTOM)) {
            if (this.iterationsOnDeath >= 10) {
               this.toVillageOnDeath();
               this.setBusyThinking(true);
            }

            this.iterationsOnDeath++;
         } else if (this._fakePlayer.isDead() && this._fakePlayer.isInsideZone(ZoneId.PVP_CUSTOM)) {
            if (this.iterationsOnDeath >= 10) {
               this.toPvpZoneOnDeath();
               this.setBusyThinking(true);
            }

            this.iterationsOnDeath++;
         } else {
            this.iterationsOnDeath = 0;
         }
      }
   }

   protected boolean isActiveSiegeParticipant() {
      return resolveActiveSiege() != null;
   }

   protected Siege resolveActiveSiege() {
      Siege siege = CastleManager.getInstance().getSiege(this._fakePlayer);
      if (siege != null && siege.isInProgress()) {
         return siege;
      }

      if (this._fakePlayer.getSiegeState() <= 0 && !this._fakePlayer.isInSiege()) {
         return null;
      }

      if (this._fakePlayer.getClan() != null) {
         for (Castle castle : CastleManager.getInstance().getCastles()) {
            final Siege activeSiege = castle.getSiege();
            if (activeSiege != null && activeSiege.isInProgress() && activeSiege.checkSides(this._fakePlayer.getClan())) {
               return activeSiege;
            }
         }
      }

      // Fallback: keep siege behavior active for already flagged participants standing in siege-related zones.
      for (Castle castle : CastleManager.getInstance().getCastles()) {
         final Siege activeSiege = castle.getSiege();
         if (activeSiege == null || !activeSiege.isInProgress()) {
            continue;
         }

         if (castle.getSiegeZone() != null && castle.getSiegeZone().isCharacterInZone(this._fakePlayer)) {
            return activeSiege;
         }

         if (castle.getCastleZone() != null && castle.getCastleZone().isCharacterInZone(this._fakePlayer)) {
            return activeSiege;
         }
      }

      return null;
   }

   private void handleSiegeDeath() {
      this.setBusyThinking(true);
      this.iterationsOnDeath = 0;

      if (_siegeRespawnScheduled) {
         return;
      }

      final Siege siege = resolveActiveSiege();
      if (siege == null) {
         return;
      }

      _siegeRespawnScheduled = true;
      final int respawnDelay = getSiegeRespawnDelay(siege);
      ThreadPool.schedule(() -> {
         _siegeRespawnScheduled = false;

         if (!_fakePlayer.isOnline()) {
            return;
         }

         final Siege activeSiege = resolveActiveSiege();
         if (activeSiege == null || !activeSiege.isInProgress()) {
            _fakePlayer.setSiegeState((byte) 0);
            _fakePlayer.setIsInSiege(false);
            setBusyThinking(false);
            return;
         }

         final Location respawn = resolveSiegeRespawnLocation(activeSiege);
         if (_fakePlayer.isDead()) {
            _fakePlayer.doRevive();
         }

         final SiegeSide side = activeSiege.getSide(_fakePlayer.getClan());
         if (side == SiegeSide.ATTACKER) {
            _fakePlayer.setSiegeState((byte) 1);
         } else if (side == SiegeSide.DEFENDER || side == SiegeSide.OWNER) {
            _fakePlayer.setSiegeState((byte) 2);
         }
         _fakePlayer.setIsInSiege(true);

         if (respawn != null) {
            teleportToLocation(respawn.getX(), respawn.getY(), respawn.getZ(), 20);
         }

         setBusyThinking(false);
      }, scheduleSiegeWaveRespawnDelay(siege, respawnDelay));
   }

   private int getSiegeRespawnDelay(Siege siege) {
      if (siege == null || this._fakePlayer.getClan() == null) {
         return FakePlayerConfig.FAKE_SIEGE_DEFENDER_RESPAWN_SECONDS * 1000;
      }

      if (siege.checkSide(this._fakePlayer.getClan(), SiegeSide.ATTACKER)) {
         return FakePlayerConfig.FAKE_SIEGE_ATTACKER_RESPAWN_SECONDS >= 0 ? FakePlayerConfig.FAKE_SIEGE_ATTACKER_RESPAWN_SECONDS * 1000 : Config.ATTACKERS_RESPAWN_DELAY;
      }

      return FakePlayerConfig.FAKE_SIEGE_DEFENDER_RESPAWN_SECONDS * 1000;
   }

   protected Location resolveSiegeRespawnLocation(Siege siege) {
      if (siege == null || this._fakePlayer.getClan() == null) {
         return null;
      }

      final Location leaderRespawn = resolveClanLeaderSiegeRespawnLocation(siege);
      if (leaderRespawn != null) {
         return leaderRespawn;
      }

      final SiegeSide side = siege.getSide(this._fakePlayer.getClan());
      if (side == SiegeSide.ATTACKER) {
         final Castle castle = siege.getCastle();
         final Location configuredSpawn = castle != null ? FakePlayerConfig.getFakeSiegeAttackerSpawnForCastle(castle.getCastleId()) : null;
         if (configuredSpawn != null) {
            return applyAttackerClanOffset(configuredSpawn);
         }
         if (castle != null && castle.getCastleZone() != null) {
            final Location exteriorCastleSpawn = castle.getCastleZone().getChaoticSpawnLoc();
            if (exteriorCastleSpawn != null) {
               return applyAttackerClanOffset(exteriorCastleSpawn);
            }
         }
         return MapRegionTable.getInstance().getLocationToTeleport(this._fakePlayer, TeleportType.SIEGE_FLAG);
      }

      if (side == SiegeSide.DEFENDER || side == SiegeSide.OWNER) {
         return MapRegionTable.getInstance().getLocationToTeleport(this._fakePlayer, TeleportType.CASTLE);
      }

      return MapRegionTable.getInstance().getLocationToTeleport(this._fakePlayer, TeleportType.TOWN);
   }

   private Location resolveClanLeaderSiegeRespawnLocation(Siege siege) {
      if (siege == null || this._fakePlayer.getClan() == null) {
         return null;
      }

      final com.l2jmega.gameserver.model.pledge.ClanMember leaderMember = this._fakePlayer.getClan().getLeader();
      if (leaderMember == null) {
         return null;
      }

      final Player leader = leaderMember.getPlayerInstance();
      if (leader == null || !leader.isOnline() || leader.isDead() || leader instanceof FakePlayer) {
         return null;
      }

      if (leader.getClanId() != this._fakePlayer.getClanId()) {
         return null;
      }

      final Siege leaderSiege = CastleManager.getInstance().getSiege(leader);
      if (leaderSiege != siege && leader.getSiegeState() <= 0) {
         return null;
      }

      final int seed = Math.abs(this._fakePlayer.getObjectId());
      final int slot = seed % 6;
      final int ring = (seed / 6) % 3;
      final int radius = 80 + (ring * 60);
      final double angle = Math.toRadians(slot * 60.0);

      final int x = leader.getX() + (int) Math.round(Math.cos(angle) * radius);
      final int y = leader.getY() + (int) Math.round(Math.sin(angle) * radius);
      final int z = leader.getZ();
      return new Location(x, y, z);
   }

   private Location applyAttackerClanOffset(Location baseSpawn) {
      if (baseSpawn == null || this._fakePlayer.getClanId() <= 0) {
         return baseSpawn;
      }

      final int radius = Math.max(0, FakePlayerConfig.FAKE_SIEGE_ATTACKER_CLAN_SPREAD_RADIUS);
      if (radius == 0) {
         return baseSpawn;
      }

      final double angle = Math.toRadians(Math.abs(this._fakePlayer.getClanId() % 360));
      final int offsetX = (int) Math.round(Math.cos(angle) * radius);
      final int offsetY = (int) Math.round(Math.sin(angle) * radius);
      return new Location(baseSpawn.getX() + offsetX, baseSpawn.getY() + offsetY, baseSpawn.getZ());
   }

   private static int scheduleSiegeWaveRespawnDelay(Siege siege, int baseDelayMs) {
      if (siege == null) {
         return baseDelayMs;
      }

      final int jitter = Rnd.get(FakePlayerConfig.FAKE_SIEGE_RESPAWN_WAVE_JITTER_MS + 1);
      final long finalDelay = Math.max(0, baseDelayMs) + jitter;
      return (int) Math.min(Integer.MAX_VALUE, finalDelay);
   }

   private void maybeRequestBossEventRes() {
      final long now = System.currentTimeMillis();
      if (_lastBossEventResRequestAt > 0L && now - _lastBossEventResRequestAt < 60000L) {
         return;
      }

      if (Rnd.get(100) >= 38) {
         _lastBossEventResRequestAt = now;
         return;
      }

      Broadcast.toSelfAndKnownPlayers(_fakePlayer, new CreatureSay(_fakePlayer.getObjectId(), 0, _fakePlayer.getName(), "res"));
      _lastBossEventResRequestAt = now;
   }

   public void setBusyThinking(boolean thinking) {
      this._isBusyThinking = thinking;
   }

   public boolean isBusyThinking() {
      return this._isBusyThinking;
   }

   protected void teleportToLocation(int x, int y, int z, int randomOffset) {
      if (this._fakePlayer.isInOlympiadMode() || this._fakePlayer.isOlympiadProtection() || this._fakePlayer.getOlympiadGameId() != -1) {
         return;
      }

      this._fakePlayer.stopMove(null);
      this._fakePlayer.abortAttack();
      this._fakePlayer.abortCast();
      this._fakePlayer.setIsTeleporting(true);
      this._fakePlayer.setTarget(null);
      this._fakePlayer.getAI().setIntention(CtrlIntention.ACTIVE);
      if (randomOffset > 0) {
         x += Rnd.get(-randomOffset, randomOffset);
         y += Rnd.get(-randomOffset, randomOffset);
      }

      z += 5;
      this._fakePlayer.broadcastPacket(new TeleportToLocation(this._fakePlayer, x, y, z));
      this._fakePlayer.decayMe();
      this._fakePlayer.setXYZ(x, y, z);
      this._fakePlayer.onTeleported();
      this._fakePlayer.revalidateZone(true);
   }

   protected void tryTargetRandomCreatureByTypeInRadius(Class<? extends Creature> creatureClass, int radius) {
      if (this._fakePlayer.isInOlympiadMode()) {
         return;
      }

      if (this._fakePlayer.isInArenaEvent()) {
         radius = Math.max(radius, TOURNAMENT_TARGET_RADIUS);
      }
      // Boss event: boss often farther than farm radius; checkTarget allows 5000 for KTB.
      if (this._fakePlayer.isFakeKTBEvent()) {
         radius = Math.max(radius, 5000);
      }

      if (this._fakePlayer.getTarget() == null) {
         if (this._fakePlayer.isInArenaEvent()) {
            Creature tournamentTarget = findTournamentTarget(radius);
            if (tournamentTarget != null) {
               this._fakePlayer.setTarget(tournamentTarget);
               return;
            }
         }

         // KTB + Creature.class: every fake must be able to lock the same boss (getTestTargetClass() uses Creature).
         final boolean avoidSharedTargets = !this._fakePlayer.isInArenaEvent()
            && !(this._fakePlayer.isFakeKTBEvent() && RaidBoss.class.isAssignableFrom(creatureClass))
            && !(this._fakePlayer.isFakeKTBEvent() && creatureClass == Creature.class);
         Set<Integer> takenByOtherFakes = new HashSet<>();
         if (avoidSharedTargets) {
            for (FakePlayer other : this._fakePlayer.getKnownTypeInRadius(FakePlayer.class, radius)) {
               if (other != this._fakePlayer && other.getPartyMode() != PartyMode.ASSIST
                  && other.getTarget() != null && other.getTarget() instanceof Creature) {
                  takenByOtherFakes.add(other.getTarget().getObjectId());
               }
            }
         }
         List<Creature> targets = this._fakePlayer
            .getKnownTypeInRadius(creatureClass, radius)
            .stream()
            .filter(x -> this.checkTarget(x) && GeoEngine.getInstance().canSeeTarget(this._fakePlayer, x)
               && (!avoidSharedTargets || !takenByOtherFakes.contains(x.getObjectId())))
            .sorted(Comparator.comparingDouble(c -> this._fakePlayer.getDistanceSq(c)))
            .collect(Collectors.toList());
         if (!targets.isEmpty()) {
            Creature target = targets.get(0);
            this._fakePlayer.setTarget(target);
         }
      } else if (((Creature)this._fakePlayer.getTarget()).isDead()) {
         this._fakePlayer.setTarget(null);
      }
   }

   private Creature findTournamentTarget(int radius) {
      Creature bestTarget = null;
      double bestDistance = Double.MAX_VALUE;

      for (Player player : this._fakePlayer.getKnownTypeInRadius(Player.class, radius)) {
         if (player == null || player == this._fakePlayer || !player.isOnline() || player.isDead() || player.isInObserverMode()) {
            continue;
         }

         if (!player.isInArenaEvent() || !player.isArenaAttack()) {
            continue;
         }

         if (!this.checkTarget(player)) {
            continue;
         }

         final double distance = this._fakePlayer.getDistanceSq(player);
         if (distance > (long) radius * radius) {
            continue;
         }

         if (distance < bestDistance) {
            bestDistance = distance;
            bestTarget = player;
         }
      }

      return bestTarget;
   }

   protected boolean checkTarget(Creature target) {
      if (target == null) {
         return false;
      } else if (target.isDead() || target.isGM() || target.isInvul()) {
         return false;
      }
      
      if (target instanceof Player) {
         // Hard block: never target same party members (covers edge cases where party list sync is off).
         if (_fakePlayer.getParty() != null && ((Player) target).getParty() == _fakePlayer.getParty())
            return false;
         if (AnonymousPvPEvent.areRegisteredOpponents(_fakePlayer, (Player) target))
            return true;
      }
      
      if (this._fakePlayer.isInArenaEvent()) {
         if (target instanceof Player) {
            Player eventPlayer = (Player) target;
            if (!eventPlayer.isInArenaEvent()) {
               return false;
            }

            int selfTeam = this._fakePlayer.getTeamTour();
            int targetTeam = eventPlayer.getTeamTour();
            if (selfTeam > 0 && targetTeam > 0) {
               return selfTeam != targetTeam;
            }

            if (this._fakePlayer.isInParty() && this._fakePlayer.getParty().getPartyMembers().contains(eventPlayer)) {
               return false;
            }

            return true;
         } else if (target instanceof Summon) {
            Player owner = ((Summon) target).getOwner();
            if (owner == null || !owner.isInArenaEvent()) {
               return false;
            }

            int selfTeam = this._fakePlayer.getTeamTour();
            int ownerTeam = owner.getTeamTour();
            if (selfTeam > 0 && ownerTeam > 0) {
               return selfTeam != ownerTeam;
            }

            if (this._fakePlayer.isInParty() && this._fakePlayer.getParty().getPartyMembers().contains(owner)) {
               return false;
            }

            return true;
         }

         return false;
      } else if (this._fakePlayer.isInParty() && target instanceof Player
         && this._fakePlayer.getParty().getPartyMembers().contains(target)) {
         return false;
      } else if (target.isInsideZone(ZoneId.PEACE) && target.getInstanceId() == 0 && !this._fakePlayer.isFakeKTBEvent()) {
         return false;
      } else {
         if (!this._fakePlayer.isFakePvp() && !this._fakePlayer.isFakeEvent() && !this._fakePlayer.isTour()) {
            if (target instanceof FakePlayer) {
               return false;
            }
            // Boss event before farm: GrandBoss is Monster, not RaidBoss; farm uses small radius and would block KTB.
            if (this._fakePlayer.isFakeKTBEvent()) {
               if (target instanceof RaidBoss) {
                  RaidBoss raidEvent = (RaidBoss)target;
                  if (this._fakePlayer.isInsideRadius(raidEvent, 5000, false, false)) {
                     return true;
                  }
               } else if (target instanceof Monster) {
                  Monster monster = (Monster)target;
                  if (this._fakePlayer.isInsideRadius(monster, 5000, false, false)) {
                     return true;
                  }
               }
            } else if (this._fakePlayer.isFakeFarm()) {
               if (target instanceof Player) {
                  Player player = (Player)target;
                  if (this._fakePlayer.getClanId() > 0 && player.getClanId() > 0 && this._fakePlayer.getClanId() == player.getClanId()
                     || this._fakePlayer.getAllyId() > 0 && player.getAllyId() > 0 && this._fakePlayer.getAllyId() == player.getAllyId()) {
                     return false;
                  }

                  if (player.getClan() != null) {
                     return true;
                  }

                  if (player.getKarma() > 0 || player.getPvpFlag() > 0) {
                     return true;
                  }

                  if (player.isInsideZone(ZoneId.PVP) && player.getActiveWeaponInstance() != null
                     || player.isInsideZone(ZoneId.SIEGE)
                     || player.isInsideZone(ZoneId.PVP_CUSTOM)) {
                     return true;
                  }

                  if (player.isInObserverMode()) {
                     return false;
                  }
               } else if (target instanceof Servitor) {
                  Summon summon = (Summon)target;
                  if (summon.getKarma() > 0 || summon.getPvpFlag() > 0) {
                     return true;
                  }

                  if (summon.isInsideZone(ZoneId.PVP) || summon.isInsideZone(ZoneId.SIEGE) || summon.isInsideZone(ZoneId.PVP_CUSTOM)) {
                     return true;
                  }
               } else if (target instanceof Monster) {
                  Monster monster = (Monster)target;
                  if (this._fakePlayer.isInsideRadius(monster, FakePlayerConfig.FAKE_FARM_RADIUS, false, false)) {
                     return true;
                  }
               }
            }
         } else {
            if (Config.TVT_EVENT_ENABLED && this._fakePlayer._inEventTvT && target instanceof Player) {
               Player tp = (Player) target;
               if (!tp._inEventTvT) {
                  return false;
               }
               if (this._fakePlayer._teamNameTvT != null && tp._teamNameTvT != null
                  && this._fakePlayer._teamNameTvT.equals(tp._teamNameTvT)) {
                  return false;
               }
            }

            if (Config.CTF_EVENT_ENABLED && this._fakePlayer._inEventCTF && target instanceof Player) {
               Player tp = (Player) target;
               if (!tp._inEventCTF) {
                  return false;
               }
               if (this._fakePlayer._teamNameCTF != null && tp._teamNameCTF != null
                  && this._fakePlayer._teamNameCTF.equals(tp._teamNameCTF)) {
                  return false;
               }
            }

            if (target instanceof Player
                && Base.RandomFightEvent.RandomFight.state == Base.RandomFightEvent.RandomFight.State.FIGHT
                && Base.RandomFightEvent.RandomFight.isFighting(this._fakePlayer)
                && Base.RandomFightEvent.RandomFight.isFighting((Player) target)) {
               return true;
            }

            if (this._fakePlayer.isFakePvp() && target instanceof FakePlayer && ((FakePlayer) target).isFakePvp()) {
               return true;
            }

            if (target instanceof Player) {
               Player playerx = (Player)target;
               if (!(this._fakePlayer.isFakePvp() && target instanceof FakePlayer && ((FakePlayer) target).isFakePvp())) {
                  if (this._fakePlayer.getClanId() > 0 && playerx.getClanId() > 0 && this._fakePlayer.getClanId() == playerx.getClanId()
                     || this._fakePlayer.getAllyId() > 0 && playerx.getAllyId() > 0 && this._fakePlayer.getAllyId() == playerx.getAllyId()) {
                     return false;
                  }
               }

               if (playerx.getKarma() > 0 || playerx.getPvpFlag() > 0) {
                  return true;
               }

               if ((this._fakePlayer._inEventTvT && playerx._inEventTvT)
                  || (this._fakePlayer._inEventCTF && playerx._inEventCTF)) {
                  return true;
               }

               if (playerx.isInsideZone(ZoneId.PVP) || playerx.isInsideZone(ZoneId.SIEGE) || playerx.isInsideZone(ZoneId.PVP_CUSTOM)) {
                  return true;
               }

               if (playerx.isInObserverMode()) {
                  return false;
               }
            } else if (target instanceof Servitor) {
               Summon summonx = (Summon)target;
               if (summonx.getKarma() > 0 || summonx.getPvpFlag() > 0) {
                  return true;
               }

               if (summonx.isInsideZone(ZoneId.PVP) || summonx.isInsideZone(ZoneId.SIEGE) || summonx.isInsideZone(ZoneId.PVP_CUSTOM)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   public void castSpell(L2Skill skill) {
      if (skill == null || this._fakePlayer.isCastingNow()) {
         return;
      }

      if (skill.getTargetType() == L2Skill.SkillTargetType.TARGET_GROUND) {
         if (this.maybeMoveToPosition(this._fakePlayer.getCurrentSkillWorldPosition(), skill.getCastRange())) {
            this._fakePlayer.setIsCastingNow(false);
            return;
         }
      } else {
         if (this.checkTargetLost(this._fakePlayer.getTarget())) {
            if (skill.isOffensive() && this._fakePlayer.getTarget() != null) {
               this._fakePlayer.setTarget(null);
            }

            this._fakePlayer.setIsCastingNow(false);
            return;
         }

         if (this._fakePlayer.getTarget() != null && this.maybeMoveToPawn(this._fakePlayer.getTarget(), skill.getCastRange())) {
            return;
         }

         if (this._fakePlayer.isSkillDisabled(skill)) {
            return;
         }
      }

      if (skill.getHitTime() > 50 && !skill.isSimultaneousCast()) {
         this.clientStopMoving(null);
      }

      this._fakePlayer.getAI().setIntention(CtrlIntention.CAST, skill, this._fakePlayer.getTarget());
   }

   protected void castSelfSpell(L2Skill skill) {
      if (!this._fakePlayer.isCastingNow() && !this._fakePlayer.isSkillDisabled(skill)) {
         if (skill.getHitTime() > 50 && !skill.isSimultaneousCast()) {
            this.clientStopMoving(null);
         }

         this._fakePlayer.doCast(skill);
      }
   }

   protected void toVillageOnDeath() {
      if (this._fakePlayer.isInOlympiadMode() || this._fakePlayer.isOlympiadProtection() || this._fakePlayer.getOlympiadGameId() != -1) {
         return;
      }

      Location location = MapRegionTable.getInstance().getLocationToTeleport(this._fakePlayer, MapRegionTable.TeleportType.TOWN);
      if (this._fakePlayer.isDead()) {
         this._fakePlayer.doRevive();
      }

      this._fakePlayer.getFakeAi().teleportToLocation(location.getX(), location.getY(), location.getZ(), 10);
   }

   protected void toPvpZoneOnDeath() {
      if (this._fakePlayer.isInOlympiadMode() || this._fakePlayer.isOlympiadProtection() || this._fakePlayer.getOlympiadGameId() != -1) {
         return;
      }

      final WorldObject previousTarget = this._fakePlayer.getTarget();
      final Location location = MapRegionTable.getInstance().getLocationToTeleport(this._fakePlayer, MapRegionTable.TeleportType.TOWN);
      if (this._fakePlayer.isDead()) {
         this._fakePlayer.doRevive();
      }
      this.teleportToLocation(location.getX(), location.getY(), location.getZ(), 10);

      this._fakePlayer.setTarget(null);
      this._fakePlayer.getAI().setIntention(CtrlIntention.ACTIVE);
      this.tryTargetRandomCreatureByTypeInRadius(FakeHelpers.getTestTargetClass(), FakeHelpers.getTestTargetRange());
      if (this._fakePlayer.getTarget() instanceof Creature) {
         final Creature newTarget = (Creature)this._fakePlayer.getTarget();
         if (previousTarget == null || newTarget.getObjectId() != previousTarget.getObjectId()) {
            this._fakePlayer.forceAutoAttack(newTarget);
         }
      }

      this.setBusyThinking(false);
   }

   protected void clientStopMoving(SpawnLocation loc) {
      if (this._fakePlayer.isMoving()) {
         this._fakePlayer.stopMove(loc);
      }

      this._clientMovingToPawnOffset = 0;
      if (this._clientMoving || loc != null) {
         this._clientMoving = false;
         this._fakePlayer.broadcastPacket(new StopMove(this._fakePlayer));
         if (loc != null) {
            this._fakePlayer.broadcastPacket(new StopRotation(this._fakePlayer.getObjectId(), loc.getHeading(), 0));
         }
      }
   }

   protected boolean checkTargetLost(WorldObject target) {
      if (target instanceof Player) {
         Player victim = (Player)target;
         if (victim.isFakeDeath()) {
            victim.stopFakeDeath(true);
            return false;
         }
      }

      if (target == null) {
         this._fakePlayer.getAI().setIntention(CtrlIntention.ACTIVE);
         return true;
      }
      return false;
   }

   /**
    * Если до цели упираемся в стену / нет LOS — шаг вбок или по дуге, чтобы обойти (геодата).
    * @param target цель движения
    * @return true если начали движение к обходной точке
    */
   protected boolean tryBypassObstacleForTarget(WorldObject target) {
      if (target == null || this._fakePlayer.isMovementDisabled()) {
         return false;
      }

      final GeoEngine geo = GeoEngine.getInstance();
      final long now = System.currentTimeMillis();
      if (this.tryResumeObstacleBypass(target, now)) {
         return true;
      }
      if (this._fakePlayer.isMoving() && this._fakePlayer.isOnGeodataPath()) {
         return false;
      }
      if (now - this._lastObstacleBypassMs < OBSTACLE_BYPASS_COOLDOWN_MS) {
         return false;
      }

      final int ox = this._fakePlayer.getX();
      final int oy = this._fakePlayer.getY();
      final int oz = this._fakePlayer.getZ();
      final int tx = target.getX();
      final int ty = target.getY();
      final int tz = target.getZ();

      double dx = tx - ox;
      double dy = ty - oy;
      final double len = Math.hypot(dx, dy);
      if (len < 48.0) {
         return false;
      }
      dx /= len;
      dy /= len;
      final double px = -dy;
      final double py = dx;

      final int[] mags = { 100, 150, 210, 290 };
      final double[] blends = { 0.0, 0.25, -0.25, 0.55, -0.55, 0.9, -0.9 };
      Location bestStep = null;
      int bestScore = Integer.MIN_VALUE;

      for (int mag : mags) {
         for (int sign : new int[] { 1, -1 }) {
            for (double blend : blends) {
               final int cx = ox + (int) (px * sign * mag + dx * mag * blend);
               final int cy = oy + (int) (py * sign * mag + dy * mag * blend);
               final int cz = tz;
               final Location step = geo.canMoveToTargetLoc(ox, oy, oz, cx, cy, cz);
               if (step == null) {
                  continue;
               }
               final long movedSq = (long) (step.getX() - ox) * (step.getX() - ox) + (long) (step.getY() - oy) * (step.getY() - oy);
               if (movedSq < (long) OBSTACLE_BYPASS_MIN_STEP * OBSTACLE_BYPASS_MIN_STEP) {
                  continue;
               }

               int score = (int) movedSq;
               final Location forwardProbe = geo.canMoveToTargetLoc(step.getX(), step.getY(), step.getZ(), tx, ty, tz);
               if (forwardProbe != null) {
                  final long progressSq = (long) (forwardProbe.getX() - step.getX()) * (forwardProbe.getX() - step.getX())
                     + (long) (forwardProbe.getY() - step.getY()) * (forwardProbe.getY() - step.getY());
                  score += (int) progressSq;
                  if (geo.canMoveToTarget(step.getX(), step.getY(), step.getZ(), tx, ty, tz)) {
                     score += 1000000;
                  }
               }

               if (score > bestScore) {
                  bestScore = score;
                  bestStep = step;
               }
            }
         }
      }

      if (bestStep != null) {
         this._lastObstacleBypassMs = now;
         this._activeObstacleBypassTargetId = target.getObjectId();
         this._activeObstacleBypassX = bestStep.getX();
         this._activeObstacleBypassY = bestStep.getY();
         this._activeObstacleBypassZ = bestStep.getZ();
         this._activeObstacleBypassUntilMs = now + OBSTACLE_BYPASS_HOLD_MS;
         this._fakePlayer.setTarget(target);
         this.moveTo(bestStep.getX(), bestStep.getY(), bestStep.getZ());
         return true;
      }

      return false;
   }

   private boolean tryResumeObstacleBypass(WorldObject target, long now) {
      if (target == null || this._activeObstacleBypassTargetId != target.getObjectId()) {
         clearObstacleBypassState();
         return false;
      }

      if (now > this._activeObstacleBypassUntilMs) {
         clearObstacleBypassState();
         return false;
      }

      final long distSq = (long) (this._fakePlayer.getX() - this._activeObstacleBypassX) * (this._fakePlayer.getX() - this._activeObstacleBypassX)
         + (long) (this._fakePlayer.getY() - this._activeObstacleBypassY) * (this._fakePlayer.getY() - this._activeObstacleBypassY);
      if (distSq <= (long) OBSTACLE_BYPASS_REUSE_DISTANCE * OBSTACLE_BYPASS_REUSE_DISTANCE) {
         clearObstacleBypassState();
         return false;
      }

      this._fakePlayer.setTarget(target);
      this.moveTo(this._activeObstacleBypassX, this._activeObstacleBypassY, this._activeObstacleBypassZ);
      return true;
   }

   private void clearObstacleBypassState() {
      this._activeObstacleBypassTargetId = 0;
      this._activeObstacleBypassX = 0;
      this._activeObstacleBypassY = 0;
      this._activeObstacleBypassZ = 0;
      this._activeObstacleBypassUntilMs = 0L;
   }

   protected boolean maybeMoveToPosition(Location worldPosition, int offset) {
      if (worldPosition == null) {
         return false;
      }
      if (offset < 0) {
         return false;
      }
      if (!this._fakePlayer
         .isInsideRadius(worldPosition.getX(), worldPosition.getY(), (int)(offset + this._fakePlayer.getTemplate().getCollisionRadius()), false)) {
         if (this._fakePlayer.isMovementDisabled()) {
            return true;
         }
         int x = this._fakePlayer.getX();
         int y = this._fakePlayer.getY();
         double dx = worldPosition.getX() - x;
         double dy = worldPosition.getY() - y;
         double dist = Math.sqrt(dx * dx + dy * dy);
         double sin = dy / dist;
         double cos = dx / dist;
         dist -= offset - 5;
         x += (int)(dist * cos);
         y += (int)(dist * sin);
         this.moveTo(x, y, worldPosition.getZ());
         return true;
      }
      return false;
   }

   protected void moveToPawn(WorldObject pawn, int offset) {
      if (!this._fakePlayer.isMovementDisabled()) {
         if (offset < 10) {
            offset = 10;
         }

         if (pawn == null) {
            return;
         }

         final long now = System.currentTimeMillis();
         final int pawnX = pawn.getX();
         final int pawnY = pawn.getY();
         final int pawnZ = pawn.getZ();
         final int dxTarget = pawnX - this._lastMoveTargetX;
         final int dyTarget = pawnY - this._lastMoveTargetY;
         final int dzTarget = pawnZ - this._lastMoveTargetZ;
         final long targetShiftSq = (long) dxTarget * dxTarget + (long) dyTarget * dyTarget + (long) dzTarget * dzTarget;
         final long minRecalcInterval = this._fakePlayer.isOnGeodataPath() ? 450L : 250L;

         boolean sendPacket = true;
         if (this._clientMoving && this._fakePlayer.getTarget() == pawn) {
            if (this._clientMovingToPawnOffset == offset) {
               if (now < this._moveToPawnTimeout && targetShiftSq < 2500L) {
                  return;
               }

               if (targetShiftSq < 2500L) {
                  sendPacket = false;
               }
            } else if (this._fakePlayer.isOnGeodataPath() && now < this._moveToPawnTimeout + 350L) {
               return;
            }
         }

         this._clientMoving = true;
         this._clientMovingToPawnOffset = offset;
         this._fakePlayer.setTarget(pawn);
         this._moveToPawnTimeout = now + minRecalcInterval;
         this._lastMoveTargetX = pawnX;
         this._lastMoveTargetY = pawnY;
         this._lastMoveTargetZ = pawnZ;

         this._fakePlayer.moveToLocation(pawn.getX(), pawn.getY(), pawn.getZ(), offset);
         if (!this._fakePlayer.isMoving()) {
            return;
         }

         if (FakePlayerConfig.FAKE_PLAYERS_DEBUG && now - this._lastMoveDebugAt >= 1200L) {
            this._lastMoveDebugAt = now;
            final double dist2d = Math.sqrt((this._fakePlayer.getX() - pawnX) * (long) (this._fakePlayer.getX() - pawnX) + (this._fakePlayer.getY() - pawnY) * (long) (this._fakePlayer.getY() - pawnY));
            this._fakePlayer.say("[DEBUG][MOVE] toPawn dist=" + (int) dist2d + " offset=" + offset + " moving=" + this._fakePlayer.isMoving() + " timeoutMs=" + (this._moveToPawnTimeout - now) + " shiftSq=" + targetShiftSq);
         }

         if (pawn instanceof Creature) {
            if (this._fakePlayer.isOnGeodataPath()) {
               this._fakePlayer.broadcastPacket(new MoveToLocation(this._fakePlayer));
               this._clientMovingToPawnOffset = 0;
            } else if (sendPacket) {
               this._fakePlayer.broadcastPacket(new MoveToPawn(this._fakePlayer, pawn, offset));
            }
         } else {
            this._fakePlayer.broadcastPacket(new MoveToLocation(this._fakePlayer));
         }
      }
   }

   public void moveTo(int x, int y, int z) {
      if (!this._fakePlayer.isMovementDisabled()) {
         this._clientMoving = true;
         this._clientMovingToPawnOffset = 0;
         this._fakePlayer.moveToLocation(x, y, z, 0);
         this._fakePlayer.broadcastPacket(new MoveToLocation(this._fakePlayer));
      }
   }

   protected boolean maybeMoveToPawn(WorldObject target, int offset) {
      if (target != null && offset >= 0) {
         offset += this._fakePlayer.getTemplate().getCollisionRadius();
         if (target instanceof Creature) {
            offset += ((Creature)target).getTemplate().getCollisionRadius();
         }

         final int dx = this._fakePlayer.getX() - target.getX();
         final int dy = this._fakePlayer.getY() - target.getY();
         final int dz = this._fakePlayer.getZ() - target.getZ();
         final double dist2d = Math.sqrt((long) dx * dx + (long) dy * dy);
         final double dist3d = Math.sqrt((long) dx * dx + (long) dy * dy + (long) dz * dz);
         final boolean inRange = this._fakePlayer.isInsideRadius(target, offset, false, false);

         if (inRange) {
            if (!GeoEngine.getInstance().canSeeTarget(this._fakePlayer, target)) {
               this._fakePlayer.setIsCastingNow(false);
               if (this.tryBypassObstacleForTarget(target)) {
                  return true;
               }
               // На олимпиаде не ломимся в стену - пытаемся найти обходной путь или стоим
               if (this._fakePlayer.isInOlympiadMode()) {
                  return false;
               }
               this.moveToPawn(target, 50);
               return true;
            }

            if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
               final long now = System.currentTimeMillis();
               if (now - this._lastMoveDebugAt >= 1200L) {
                  this._lastMoveDebugAt = now;
                  this._fakePlayer.say("[DEBUG][MOVE] inRange dist2d=" + (int) dist2d + " dist3d=" + (int) dist3d + " offset=" + offset + " moving=" + this._fakePlayer.isMoving());
               }
            }
            return false;
         } else if (this._fakePlayer.isMovementDisabled()) {
            if (this._fakePlayer.getAI().getIntention() == CtrlIntention.ATTACK) {
               this._fakePlayer.getAI().setIntention(CtrlIntention.IDLE);
            }

            return true;
         } else {
            if (target instanceof Creature && !(target instanceof Door)) {
               if (((Creature)target).isMoving()) {
                  offset -= 30;
               }

               if (offset < 5) {
                  offset = 5;
               }
            }

            final GeoEngine geo = GeoEngine.getInstance();
            final int ox = this._fakePlayer.getX();
            final int oy = this._fakePlayer.getY();
            final int oz = this._fakePlayer.getZ();
            final int tx = target.getX();
            final int ty = target.getY();
            final int tz = target.getZ();
            if (!geo.canMoveToTarget(ox, oy, oz, tx, ty, tz) && this.tryBypassObstacleForTarget(target)) {
               return true;
            }

            this.moveToPawn(target, offset);
            if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
               final long now = System.currentTimeMillis();
               if (now - this._lastMoveDebugAt >= 1200L) {
                  this._lastMoveDebugAt = now;
                  this._fakePlayer.say("[DEBUG][MOVE] approach dist2d=" + (int) dist2d + " dist3d=" + (int) dist3d + " offset=" + offset + " moving=" + this._fakePlayer.isMoving() + " intent=" + this._fakePlayer.getAI().getIntention());
               }
            }
            return true;
         }
      }
      return false;
   }

   protected void scheduleDespawnOnce(int minDelayMs, int maxDelayMs) {
      if (isProtectedFromDespawn()) {
         return;
      }

      if (!_despawnScheduled) {
         _despawnScheduled = true;
         ThreadPool.schedule(() -> {
            _despawnScheduled = false;
            if (isProtectedFromDespawn()) {
               return;
            }
            _fakePlayer.despawnPlayer();
         }, Rnd.get(minDelayMs, maxDelayMs));
      }
   }

   private boolean isProtectedFromDespawn() {
      return _fakePlayer.isInOlympiadMode()
         || _fakePlayer.isOlympiadProtection()
         || _fakePlayer.getOlympiadGameId() != -1
         || com.l2jmega.gameserver.model.olympiad.OlympiadManager.getInstance().isRegistered(_fakePlayer)
         || _fakePlayer.isFakeEvent()
         || _fakePlayer.isTour()
         || _fakePlayer.isFakeKTBEvent()
         || (FakePlayerConfig.FAKE_OLYMPIAD_NO_DESPAWN && _fakePlayer.isOlympiadParticipant())
         || _fakePlayer.isInSiege()
         || _fakePlayer.getSiegeState() > 0
         || resolveActiveSiege() != null;
   }

   private void scheduleNormalChat() {
      if (!FakePlayerConfig.FAKE_NORMAL_CHAT_ENABLED) {
         return;
      }

      final int delayMs = Rnd.get(
         FakePlayerConfig.FAKE_NORMAL_CHAT_MIN_INTERVAL * 1000,
         FakePlayerConfig.FAKE_NORMAL_CHAT_MAX_INTERVAL * 1000);
      ThreadPool.schedule(() -> {
         if (!_fakePlayer.isOnline()) {
            return;
         }

         FakePlayerUtilsAI.maybeAnnounceNormalChat(_fakePlayer);
         scheduleNormalChat();
      }, delayMs);
   }

   public abstract void thinkAndAct();
}
