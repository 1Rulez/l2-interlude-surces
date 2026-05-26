package phantom;

import java.util.logging.Level;
import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.gameserver.model.actor.ai.CtrlIntention;
import com.l2jmega.gameserver.data.SkillTable;
import com.l2jmega.gameserver.geoengine.GeoEngine;
import com.l2jmega.gameserver.instancemanager.CastleManager;
import com.l2jmega.gameserver.instancemanager.CursedWeaponsManager;
import com.l2jmega.gameserver.model.pledge.ClanMember;
import com.l2jmega.gameserver.model.L2Effect;
import com.l2jmega.gameserver.model.WorldObject;
import com.l2jmega.gameserver.model.L2Party;
import com.l2jmega.gameserver.model.L2Skill;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.gameserver.model.actor.Creature;
import com.l2jmega.gameserver.model.actor.Playable;
import com.l2jmega.gameserver.model.actor.Summon;
import com.l2jmega.gameserver.model.actor.appearance.PcAppearance;
import com.l2jmega.gameserver.model.actor.instance.Door;
import com.l2jmega.gameserver.model.actor.instance.Monster;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.actor.template.PlayerTemplate;
import com.l2jmega.events.ArenaTask;
import com.l2jmega.events.Arena1x1;
import com.l2jmega.events.BossEvent;
import com.l2jmega.gameserver.model.olympiad.OlympiadManager;
import com.l2jmega.gameserver.network.SystemMessageId;
import com.l2jmega.gameserver.network.serverpackets.ActionFailed;
import com.l2jmega.gameserver.network.serverpackets.CreatureSay;
import com.l2jmega.gameserver.network.serverpackets.PledgeShowMemberListUpdate;
import com.l2jmega.gameserver.skills.l2skills.L2SkillSiegeFlag;
import com.l2jmega.gameserver.templates.skills.L2SkillType;
import com.l2jmega.gameserver.util.Broadcast;
import com.l2jmega.gameserver.util.SiegeParticipationUtil;
import Base.custom.event.AnonymousPvPEvent;
import phantom.ai.FakePlayerAI;
import phantom.ai.FakePlayerUtilsAI;
import phantom.ai.event.KillTheBossAI;
import phantom.ai.event.OlympiadFakeSystem;
import phantom.ai.event.TournamentAI;
import phantom.ai.party.PartyFollowerAI;
import phantom.ai.party.PartyMode;
import phantom.helpers.FakeHelpers;
import phantom.model.FakeEmotion;

public class FakePlayer extends Player {
   private FakePlayerAI _fakeAi;
   private FakeEmotion _emotion;
   private boolean _underControl = false;
   private boolean _isFakePvp;
   private boolean _isFakeFarm;
   private boolean _isFakeKTBEvent;
   private boolean _isFakeEvent;
   private boolean _isFakeTeleport;
   private boolean _isTour;
   private boolean _olympiadParticipant;
   private PartyMode _partyMode = PartyMode.FOLLOW;
   protected String _mood = "";

   @Override
   public boolean isUnderControl() {
      return this._underControl;
   }

   @Override
   public void setUnderControl(boolean underControl) {
      this._underControl = underControl;
   }

   protected FakePlayer(int objectId) {
      super(objectId);
      setIsPhantom(true);
      _emotion = new FakeEmotion();
   }

   public FakePlayer(int objectId, PlayerTemplate template, String accountName, PcAppearance app) {
      super(objectId, template, accountName, app);
      setIsPhantom(true);
      _emotion = new FakeEmotion();
   }

   public FakePlayerAI getFakeAi() {
      return this._fakeAi;
   }

   public void setFakeAi(FakePlayerAI _fakeAi) {
      this._fakeAi = _fakeAi;
   }

   @Override
   public void reviveRequest(Player Reviver, L2Skill skill, boolean Pet) {
      super.reviveRequest(Reviver, skill, Pet);

      if (Pet || !isDead()) {
         return;
      }
      if (!isReviveRequested()) {
         return;
      }
      
      ThreadPool.schedule(() -> {
         if (!isDead() || !isReviveRequested()) {
            return;
         }

         int answer = 1;
         if (getFakeAi() instanceof PartyFollowerAI) {
            answer = ((PartyFollowerAI) getFakeAi()).shouldAutoAcceptResurrection() ? 1 : 0;
         }

         reviveAnswer(answer);
      }, 250L);
   }

   public FakeEmotion getEmotion() {
      return this._emotion;
   }

   public void setEmotion(FakeEmotion emotion) {
      this._emotion = emotion;
   }

   public void assignDefaultAI() {
      try {
         Class<? extends FakePlayerAI> aiClass = FakeHelpers.getAIbyClassId(this.getClassId());
         if (aiClass == null) {
            _log.warning("FakePlayer: No AI class found for classId " + this.getClassId() + " (player: " + this.getName() + ")");
            return;
         }

         this.setFakeAi(aiClass.getConstructor(FakePlayer.class).newInstance(this));
      } catch (Exception e) {
         _log.log(Level.WARNING, "FakePlayer: Failed to assign AI for " + this.getName() + " classId=" + this.getClassId(), e);
      }
   }

   public boolean checkUseMagicConditions(L2Skill skill, boolean forceUse, boolean dontMove) {
      if (skill == null)
         return false;

      if (this.isDead() || this.isOutOfControl()) {
         this.sendPacket(ActionFailed.STATIC_PACKET);
         return false;
      }

      if (this.isSkillDisabled(skill))
         return false;

      L2SkillType sklType = skill.getSkillType();

      if (this.isFishing() && sklType != L2SkillType.PUMPING && sklType != L2SkillType.REELING && sklType != L2SkillType.FISHING)
         return false;

      if (this.isInObserverMode()) {
         this.abortCast();
         return false;
      }

      if (this.isSitting()) {
         if (skill.isToggle()) {
            L2Effect effect = this.getFirstEffect(skill.getId());
            if (effect != null) {
               effect.exit();
               return false;
            }
         }
         return false;
      }

      if (skill.isToggle()) {
         L2Effect effect = this.getFirstEffect(skill.getId());
         if (effect != null) {
            if (skill.getId() != 60)
               effect.exit();

            this.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
         }
      }

      if (this.isFakeDeath()) {
         this.sendPacket(ActionFailed.STATIC_PACKET);
         return false;
      }

      WorldObject target = null;
      L2Skill.SkillTargetType sklTargetType = skill.getTargetType();
      Location worldPosition = this.getCurrentSkillWorldPosition();

      if (sklTargetType == L2Skill.SkillTargetType.TARGET_GROUND && worldPosition == null) {
         _log.info("WorldPosition is null for skill: " + skill.getName() + ", player: " + this.getName() + ".");
         this.sendPacket(ActionFailed.STATIC_PACKET);
         return false;
      }

      switch (sklTargetType) {
         case TARGET_AURA:
         case TARGET_FRONT_AURA:
         case TARGET_BEHIND_AURA:
         case TARGET_AURA_UNDEAD:
         case TARGET_PARTY:
         case TARGET_ALLY:
         case TARGET_CLAN:
         case TARGET_GROUND:
         case TARGET_SELF:
         case TARGET_CORPSE_ALLY:
         case TARGET_AREA_SUMMON:
            target = this;
            break;
         case TARGET_PET:
         case TARGET_SUMMON:
            target = this.getPet();
            break;
         default:
            target = this.getTarget();
      }

      if (target == null) {
         this.sendPacket(ActionFailed.STATIC_PACKET);
         return false;
      }

      if (target instanceof Door && !(((Door) target).isAutoAttackable(this)
         && (!((Door) target).isUnlockable() || skill.getSkillType() == L2SkillType.UNLOCK))) {
         this.sendPacket(SystemMessageId.INCORRECT_TARGET);
         this.sendPacket(ActionFailed.STATIC_PACKET);
         return false;
      }

      if (this.isInDuel() && target instanceof Playable) {
         Player cha = target.getActingPlayer();
         if (cha.getDuelId() != this.getDuelId()) {
            this.sendPacket(SystemMessageId.INCORRECT_TARGET);
            this.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
         }
      }

      if (!skill.checkCondition(this, target, false)) {
         this.sendPacket(ActionFailed.STATIC_PACKET);
         return false;
      }

      if (skill.isOffensive()) {
         if (isInsidePeaceZone(this, target)) {
            this.sendPacket(SystemMessageId.TARGET_IN_PEACEZONE);
            this.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
         }

         if (this.isInOlympiadMode() && !this.isOlympiadStart()) {
            this.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
         }

         if (!target.isAttackable() && !this.getAccessLevel().allowPeaceAttack()) {
            this.sendPacket(ActionFailed.STATIC_PACKET);
            return false;
         }

         if (!target.isAutoAttackable(this) && !forceUse) {
            switch (sklTargetType) {
               case TARGET_AURA:
               case TARGET_FRONT_AURA:
               case TARGET_BEHIND_AURA:
               case TARGET_AURA_UNDEAD:
               case TARGET_PARTY:
               case TARGET_ALLY:
               case TARGET_CLAN:
               case TARGET_GROUND:
               case TARGET_SELF:
               case TARGET_CORPSE_ALLY:
               case TARGET_AREA_SUMMON:
                  break;
               default:
                  this.sendPacket(ActionFailed.STATIC_PACKET);
                  return false;
            }
         }

         if (dontMove) {
            if (sklTargetType == L2Skill.SkillTargetType.TARGET_GROUND) {
               if (!this.isInsideRadius(
                  worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(),
                  (int) (skill.getCastRange() + this.getTemplate().getCollisionRadius()), false, false)) {
                  this.sendPacket(SystemMessageId.TARGET_TOO_FAR);
                  this.sendPacket(ActionFailed.STATIC_PACKET);
                  return false;
               }
            } else if (skill.getCastRange() > 0
               && !this.isInsideRadius(target, (int) (skill.getCastRange() + this.getTemplate().getCollisionRadius()), false, false)) {
               this.sendPacket(SystemMessageId.TARGET_TOO_FAR);
               this.sendPacket(ActionFailed.STATIC_PACKET);
               return false;
            }
         }
      }

      if (!skill.isOffensive() && target instanceof Monster && !forceUse) {
         switch (sklTargetType) {
            case TARGET_AURA:
            case TARGET_FRONT_AURA:
            case TARGET_BEHIND_AURA:
            case TARGET_AURA_UNDEAD:
            case TARGET_PARTY:
            case TARGET_ALLY:
            case TARGET_CLAN:
            case TARGET_GROUND:
            case TARGET_SELF:
            case TARGET_CORPSE_ALLY:
            case TARGET_PET:
            case TARGET_SUMMON:
            case TARGET_CORPSE_MOB:
            case TARGET_AREA_CORPSE_MOB:
               break;
            case TARGET_AREA_SUMMON:
            default:
               switch (sklType) {
                  case BEAST_FEED:
                  case DELUXE_KEY_UNLOCK:
                  case UNLOCK:
                     break;
                  default:
                     this.sendPacket(ActionFailed.STATIC_PACKET);
                     return false;
               }
         }
      }

      if (sklType == L2SkillType.SPOIL && !(target instanceof Monster)) {
         this.sendPacket(SystemMessageId.INCORRECT_TARGET);
         this.sendPacket(ActionFailed.STATIC_PACKET);
         return false;
      }

      if (sklType == L2SkillType.DRAIN_SOUL && !(target instanceof Monster)) {
         this.sendPacket(SystemMessageId.INCORRECT_TARGET);
         this.sendPacket(ActionFailed.STATIC_PACKET);
         return false;
      }

      switch (sklTargetType) {
         case TARGET_AURA:
         case TARGET_FRONT_AURA:
         case TARGET_BEHIND_AURA:
         case TARGET_AURA_UNDEAD:
         case TARGET_PARTY:
         case TARGET_ALLY:
         case TARGET_CLAN:
         case TARGET_GROUND:
         case TARGET_SELF:
         case TARGET_CORPSE_ALLY:
         case TARGET_AREA_SUMMON:
            break;
         default:
            if (!this.checkPvpSkill(target, skill) && !this.getAccessLevel().allowPeaceAttack()) {
               this.sendPacket(SystemMessageId.TARGET_IS_INCORRECT);
               this.sendPacket(ActionFailed.STATIC_PACKET);
               return false;
            }
      }

      if ((sklTargetType != L2Skill.SkillTargetType.TARGET_HOLY
            || this.checkIfOkToCastSealOfRule(CastleManager.getInstance().getCastle(this), false, skill, target))
         && (sklType != L2SkillType.SIEGEFLAG || L2SkillSiegeFlag.checkIfOkToPlaceFlag(this, false))
         && (sklType != L2SkillType.STRSIEGEASSAULT || this.checkIfOkToUseStriderSiegeAssault(skill))
         && (sklType != L2SkillType.SUMMON_FRIEND || checkSummonerStatus(this) && checkSummonTargetStatus(target, this))) {
         return true;
      }

      this.sendPacket(ActionFailed.STATIC_PACKET);
      this.abortCast();
      return false;
   }

   public void forceAutoAttack(Creature creature) {
      if (this.getTarget() != null) {
         if (this.isFriendlyArenaTarget(this.getTarget())) {
            return;
         }
         
         // Safety: phantoms should not attack real players unless explicitly in PvP/event mode.
         if (this.getTarget() instanceof Player)
         {
            final Player targetPlayer = (Player) this.getTarget();
            final boolean sameOlympiadMatch = this.isOlympiadStart() && this.isInOlympiadMode() && targetPlayer.isInOlympiadMode()
               && this.getOlympiadGameId() == targetPlayer.getOlympiadGameId();
            final boolean anonymousPvp = AnonymousPvPEvent.areRegisteredOpponents(this, targetPlayer);
            if (!sameOlympiadMatch && !anonymousPvp && !this._isFakePvp && !this._isFakeEvent && !this._isTour
               && !(this.getTarget() instanceof FakePlayer))
               return;
         }

         // Don't attack party members
         if (this.getTarget() instanceof Player)
         {
            final Player tp = (Player) this.getTarget();
            if (this.getParty() != null && tp.getParty() == this.getParty())
               return;
            if (this.isInParty() && this.getParty().getPartyMembers().contains(tp))
               return;
         }

         if (!isInsidePeaceZone(this, this.getTarget())) {
            if (this.isInOlympiadMode() && this.getTarget() != null && this.getTarget() instanceof Playable) {
               Player target = this.getTarget().getActingPlayer();
               if (target == null || target.isInOlympiadMode() && (!this.isOlympiadStart() || this.getOlympiadGameId() != target.getOlympiadGameId())) {
                  return;
               }
            }

            if (this.getTarget() == null || this.getTarget().isAttackable() || this.getAccessLevel().allowPeaceAttack()) {
               if (!this.isConfused()) {
                  if (GeoEngine.getInstance().canSeeTarget(this, this.getTarget())) {
                     if (this.getAI().getIntention() == CtrlIntention.ATTACK && this.getAI().getTarget() == this.getTarget()) {
                        return;
                     }
                     this.getAI().setIntention(CtrlIntention.ATTACK, this.getTarget());
                  }
               }
            }
         }
      }
   }

   private boolean isFriendlyArenaTarget(WorldObject target) {
      if (!this.isInArenaEvent() || target == null) {
         return false;
      }

      if (target instanceof Player) {
         Player player = (Player) target;
         if (!player.isInArenaEvent()) {
            return false;
         }

         int selfTeam = this.getTeamTour();
         int targetTeam = player.getTeamTour();
         return selfTeam > 0 && targetTeam > 0 && selfTeam == targetTeam;
      }

      if (target instanceof Summon) {
         Player owner = ((Summon) target).getOwner();
         if (owner == null || !owner.isInArenaEvent()) {
            return false;
         }

         int selfTeam = this.getTeamTour();
         int ownerTeam = owner.getTeamTour();
         return selfTeam > 0 && ownerTeam > 0 && selfTeam == ownerTeam;
      }

      return false;
   }

   public synchronized void despawnPlayer() {
      try {
         final boolean preservePersistentClanFake = this.getClan() != null;

         if (this.isInOlympiadMode() || this.isOlympiadProtection() || this.getOlympiadGameId() != -1 || OlympiadManager.getInstance().isRegistered(this)) {
            return;
         }

         // Protect olympiad participants from despawn if config is enabled
         if (FakePlayerConfig.FAKE_OLYMPIAD_NO_DESPAWN && this._olympiadParticipant) {
            return;
         }

         // Clear event links first so delayed tasks cannot re-teleport or reuse this fake after despawn.
         this.setFakeEvent(false);
         this.setFakeKTBEvent(false);
         this.setTour(false);
         BossEvent.getInstance().removePlayer(this);
         KillTheBossAI._ktbFakes.remove(this);
         TournamentAI.tourFakes.remove(this);

         phantom.ai.FakePlayerChatManager.INSTANCE.onFakeDespawned(this);
         FakePlayerUtilsAI.removeNormalChatCooldown(getObjectId());
         OlympiadFakeSystem.getInstance().forgetFake(this);
         this.setOnlineStatus(false, true);
         this.abortAttack();
         this.abortCast();
         this.stopMove(null);
         this.setTarget(null);
         if (this.isFlying()) {
            this.removeSkill(SkillTable.FrequentSkill.WYVERN_BREATH.getSkill().getId(), false);
         }

         this.stopAllTimers();

         for (L2Effect effect : this.getAllEffects()) {
            if (effect.getSkill().isToggle()) {
               effect.exit();
            } else {
               switch (effect.getEffectType()) {
                  case SIGNET_GROUND:
                  case SIGNET_EFFECT:
                     effect.exit();
                     break;
                  default:
                     break;
               }
            }
         }

         this.decayMe();
         if (this.getParty() != null) {
            this.getParty().removePartyMember(this, L2Party.MessageType.Disconnected);
         }

         if (this.getPet() != null) {
            this.getPet().unSummon(this);
         }

         if (OlympiadManager.getInstance().isRegistered(this) || this.getOlympiadGameId() != -1) {
            OlympiadManager.getInstance().removeDisconnectedCompetitor(this);
         }

         if (this.getClan() != null) {
            ClanMember clanMember = this.getClan().getClanMember(this.getObjectId());
            if (clanMember != null) {
               if (preservePersistentClanFake) {
                  clanMember.setPlayerInstance(null);
               } else {
                  this.getClan().removeClanMember(this.getObjectId(), 0);
               }
            }
         }

         if (this.getActiveRequester() != null) {
            this.setActiveRequester(null);
            this.cancelActiveTrade();
         }

         if (this.getVehicle() != null) {
            this.getVehicle().oustPlayer(this, true, Location.DUMMY_LOC);
         }

         this.getInventory().deleteMe();
         this.clearWarehouse();
         this.clearFreight();
         this.clearDepositedFreight();
         if (this.isCursedWeaponEquipped()) {
            CursedWeaponsManager.getInstance().getCursedWeapon(this.getCursedWeaponEquippedId()).setPlayer(null);
         }

         if (this.getClanId() > 0) {
            this.getClan().broadcastToOtherOnlineMembers(new PledgeShowMemberListUpdate(this), this);
         }

         World.getInstance().removePlayer(this);
         this.notifyFriends(false);
         this.getBlockList().playerLogout();
         this.deleteMe();

         // Keep fake characters persisted in DB so CommunityBoard rankings
         // can include both fake and regular players consistently.
      } catch (Exception var5) {
         _log.log(Level.WARNING, "Exception on deleteMe()" + var5.getMessage(), var5);
      } finally {
         FakePlayerManager.unregisterFakePlayer(this);
      }
   }

   public void registerTournament() {
      if (!canRegisterTournament()) {
         return;
      }

      if (Arena1x1.getInstance().register(this)) {
         this.setArena1x1(true);
         this.setArenaProtection(true);
      }
   }

   public void heal() {
      this.setCurrentCp(this.getMaxCp());
      this.setCurrentHp(this.getMaxHp());
      this.setCurrentMp(this.getMaxMp());
   }

   public boolean isFakePvp() {
      return this._isFakePvp;
   }

   public void setFakePvp(boolean isFakePvp) {
      this._isFakePvp = isFakePvp;
   }

   public boolean isFakeFarm() {
      return this._isFakeFarm;
   }

   public void setFakeFarm(boolean isFakeFarm) {
      this._isFakeFarm = isFakeFarm;
   }

   public boolean isFakeKTBEvent() {
      return this._isFakeKTBEvent;
   }

   public void setFakeKTBEvent(boolean isFakeKTBEvent) {
      this._isFakeKTBEvent = isFakeKTBEvent;
   }

   public boolean isFakeEvent() {
      return this._isFakeEvent;
   }

   public void setFakeEvent(boolean isFakeEvent) {
      this._isFakeEvent = isFakeEvent;
   }

   public boolean isFakeTeleport() {
      return this._isFakeTeleport;
   }

   public void setFakeTeleport(boolean isFakeTeleport) {
      this._isFakeTeleport = isFakeTeleport;
   }

   public boolean isTour() {
      return this._isTour;
   }

   public void setTour(boolean isTour) {
      this._isTour = isTour;
   }

   public boolean isOlympiadParticipant() {
      return this._olympiadParticipant;
   }

   public void setOlympiadParticipant(boolean value) {
      this._olympiadParticipant = value;
   }

   public PartyMode getPartyMode() {
      return this._partyMode;
   }

   public void setPartyMode(PartyMode mode) {
      this._partyMode = mode;
   }

   public void setMood(String mood) {
      if (FakePlayerConfig.FAKE_PLAYERS_DEBUG) {
         this.say("Changing my mood [" + this._mood + "] -> [" + mood + "]");
      }

      this._mood = mood;
   }

   public String getMood() {
      return this._mood;
   }

   public void say(String text) {
      Broadcast.toSelfAndKnownPlayers(this, new CreatureSay(this.getObjectId(), 0, this.getName(), text));
   }

   @Override
   public void reduceCurrentHp(double i, Creature attacker, boolean awake, boolean isDOT, com.l2jmega.gameserver.model.L2Skill skill) {
      super.reduceCurrentHp(i, attacker, awake, isDOT, skill);

      // Farm phantoms fight back when attacked by a real player
      if (FakePlayerConfig.FAKE_FARM_HIT_BACK && this._isFakeFarm && !this.isDead()
         && attacker != null && attacker instanceof Player && !(attacker instanceof FakePlayer)) {
         // Switch target to the attacker and retaliate
         this.setTarget(attacker);
         // Do not enable PvP mode automatically; otherwise phantoms can PK real players.
         if (this._isFakePvp || this._isFakeEvent || this._isTour
            || AnonymousPvPEvent.areRegisteredOpponents(this, (Player) attacker))
            this.forceAutoAttack(attacker);
      }
   }

   public void registerTournament2x2(FakePlayer partner) {
      if (!canRegisterTournament() || partner == null || !partner.canRegisterTournament()) {
         return;
      }

      if (com.l2jmega.events.Arena2x2.getInstance().register(this, partner)) {
         this.setArena2x2(true);
         this.setArenaProtection(true);
         partner.setArena2x2(true);
         partner.setArenaProtection(true);
      }
   }

   public void registerTournament5x5(FakePlayer p2, FakePlayer p3, FakePlayer p4, FakePlayer p5) {
      if (!canRegisterTournament() || p2 == null || p3 == null || p4 == null || p5 == null) {
         return;
      }

      if (!p2.canRegisterTournament() || !p3.canRegisterTournament() || !p4.canRegisterTournament() || !p5.canRegisterTournament()) {
         return;
      }

      if (com.l2jmega.events.Arena5x5.getInstance().register(this, p2, p3, p4, p5)) {
         this.setArena5x5(true);
         this.setArenaProtection(true);
         p2.setArena5x5(true);
         p2.setArenaProtection(true);
         p3.setArena5x5(true);
         p3.setArenaProtection(true);
         p4.setArena5x5(true);
         p4.setArenaProtection(true);
         p5.setArena5x5(true);
         p5.setArenaProtection(true);
      }
   }

   private boolean canRegisterTournament() {
      if (!ArenaTask.is_started()) {
         return false;
      }

      if (SiegeParticipationUtil.isPlayerOrClanInActiveSiege(this)) {
         return false;
      }

      // Defensive: clear stale arena state if we are not in any arena list (e.g. after duel end
      // when clear was missed). Ensures fake players can re-register after first match.
      boolean inAnyArena = Arena1x1.getInstance().isRegistered(this)
            || com.l2jmega.events.Arena2x2.getInstance().isRegistered(this)
            || com.l2jmega.events.Arena5x5.getInstance().isRegistered(this);
      if (!inAnyArena && (this.isArena1x1() || this.isArena2x2() || this.isArena5x5() || this.isInArenaEvent() || this.isArenaAttack())) {
         this.setArena1x1(false);
         this.setArena2x2(false);
         this.setArena5x5(false);
         this.setInArenaEvent(false);
         this.setArenaAttack(false);
         this.setArenaProtection(false);
      }

      if (this.isArena1x1() || this.isArena2x2() || this.isArena5x5() || this.isInArenaEvent() || this.isArenaAttack()) {
         return false;
      }

      if (inAnyArena) {
         return false;
      }

      return true;
   }
}
