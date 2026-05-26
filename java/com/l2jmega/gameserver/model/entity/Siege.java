package com.l2jmega.gameserver.model.entity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.commons.util.ArraysUtil;

import com.l2jmega.Config;
import com.l2jmega.L2DatabaseFactory;
import com.l2jmega.gameserver.data.NpcTable;
import com.l2jmega.gameserver.data.sql.ClanTable;
import com.l2jmega.gameserver.instancemanager.CastleManager;
import com.l2jmega.gameserver.model.L2Spawn;
import com.l2jmega.gameserver.model.WorldObject;
import com.l2jmega.gameserver.model.actor.Npc;
import com.l2jmega.gameserver.model.actor.instance.ControlTower;
import com.l2jmega.gameserver.model.actor.instance.FlameTower;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.location.TowerSpawnLocation;
import com.l2jmega.gameserver.model.pledge.Clan;
import com.l2jmega.gameserver.model.pledge.ClanMember;
import com.l2jmega.gameserver.network.SystemMessageId;
import com.l2jmega.gameserver.network.serverpackets.ExShowScreenMessage;
import com.l2jmega.gameserver.network.serverpackets.ItemList;
import com.l2jmega.gameserver.network.serverpackets.PlaySound;
import com.l2jmega.gameserver.network.serverpackets.StatusUpdate;
import com.l2jmega.gameserver.network.serverpackets.SystemMessage;
import com.l2jmega.gameserver.network.serverpackets.UserInfo;
import com.l2jmega.gameserver.scripting.Quest;
import com.l2jmega.gameserver.util.Broadcast;
import phantom.FakePlayerConfig;

public class Siege implements Siegable
{
    public enum SiegeSide { OWNER, DEFENDER, ATTACKER, PENDING }
    public enum SiegeStatus { REGISTRATION_OPENED, REGISTRATION_OVER, IN_PROGRESS }

    protected static final Logger _log = Logger.getLogger(Siege.class.getName());

    private static final String LOAD_SIEGE_CLAN = "SELECT clan_id,type FROM siege_clans WHERE castle_id=?";
    private static final String CLEAR_SIEGE_CLANS = "DELETE FROM siege_clans WHERE castle_id=?";
    private static final String CLEAR_PENDING_CLANS = "DELETE FROM siege_clans WHERE castle_id=? AND type='PENDING'";
    private static final String CLEAR_ACTIVE_SIEGE_REGISTRATIONS = "DELETE FROM siege_clans WHERE castle_id=? AND type IN ('ATTACKER','DEFENDER','PENDING')";
    private static final String CLEAR_SIEGE_CLAN = "DELETE FROM siege_clans WHERE castle_id=? AND clan_id=?";
    private static final String UPDATE_SIEGE_INFOS = "UPDATE castle SET siegeDate=?, regTimeOver=? WHERE id=?";
    private static final String ADD_OR_UPDATE_SIEGE_CLAN = "INSERT INTO siege_clans (clan_id,castle_id,type) VALUES (?,?,?) ON DUPLICATE KEY UPDATE type=?";

    private final Map<Clan, SiegeSide> _registeredClans = new ConcurrentHashMap<>();
    private final Castle _castle;
    private final List<ControlTower> _controlTowers = new ArrayList<>();
    private final List<FlameTower> _flameTowers = new ArrayList<>();
    private final List<Npc> _destroyedTowers = new ArrayList<>();

    protected Calendar _siegeEndDate;
    protected ScheduledFuture<?> _siegeTask;
    private boolean _startupSiegeDateSynced;
    private boolean _registrationOneMinuteAnnounced;
    private boolean _autoRegistrationRetryScheduled;

    private SiegeStatus _siegeStatus = SiegeStatus.REGISTRATION_OPENED;
    private List<Quest> _questEvents = Collections.emptyList();

    public Siege(Castle castle)
    {
        _castle = castle;

        if (_castle.getOwnerId() > 0)
        {
            final Clan clan = ClanTable.getInstance().getClan(getCastle().getOwnerId());
            if (clan != null)
                _registeredClans.put(clan, SiegeSide.OWNER);
        }

        try (Connection con = L2DatabaseFactory.getInstance().getConnection())
        {
            PreparedStatement ps = con.prepareStatement(LOAD_SIEGE_CLAN);
            ps.setInt(1, _castle.getCastleId());

            ResultSet rs = ps.executeQuery();
            while (rs.next())
            {
                final Clan clan = ClanTable.getInstance().getClan(rs.getInt("clan_id"));
                if (clan != null)
                    _registeredClans.put(clan, Enum.valueOf(SiegeSide.class, rs.getString("type")));
            }
            rs.close();
            ps.close();
        }
        catch (Exception e)
        {
            _log.log(Level.WARNING, "Exception: loadSiegeClan(): " + e.getMessage(), e);
        }

        startAutoTask();
    }

    @Override
    public void startSiege()
    {
        if (!isCastleSiegeEnabled())
            return;

        if (isInProgress())
            return;

        if (getAttackerClans().isEmpty())
        {
            final SystemMessage sm = SystemMessage.getSystemMessage((getCastle().getOwnerId() <= 0) ? SystemMessageId.SIEGE_OF_S1_HAS_BEEN_CANCELED_DUE_TO_LACK_OF_INTEREST : SystemMessageId.S1_SIEGE_WAS_CANCELED_BECAUSE_NO_CLANS_PARTICIPATED);
            sm.addString(getCastle().getName());

            Broadcast.toAllOnlinePlayers(sm);
            saveCastleSiege(true);
            return;
        }

        changeStatus(SiegeStatus.IN_PROGRESS);

        updatePlayerSiegeStateFlags(false);
        getCastle().getSiegeZone().banishForeigners(getCastle().getOwnerId());

        spawnControlTowers();
        spawnFlameTowers();
        getCastle().closeDoors();
        getCastle().spawnSiegeGuardsOrMercenaries();

        getCastle().getSiegeZone().setIsActive(true);
        getCastle().getSiegeZone().updateZoneStatusForCharactersInside();

        _siegeEndDate = Calendar.getInstance();
        _siegeEndDate.add(Calendar.MINUTE, Config.SIEGE_LENGTH);

        ThreadPool.schedule(new EndSiegeTask(getCastle()), 1000);

        Broadcast.toAllOnlinePlayers(SystemMessage.getSystemMessage(SystemMessageId.SIEGE_OF_S1_HAS_STARTED).addString(getCastle().getName()));
        Broadcast.toAllOnlinePlayers(new PlaySound("systemmsg_e.17"));
        _log.info("Siege started for " + getCastle().getName() + ". Scheduled end at " + _siegeEndDate.getTime() + ".");
        
        // Deploy fake players from registered clans
        phantom.ai.event.SiegeFakeSystem.getInstance().onSiegeStart(getCastle());
    }

    @Override
    public void endSiege()
    {
        if (!isInProgress())
            return;

        Broadcast.toAllOnlinePlayers(SystemMessage.getSystemMessage(SystemMessageId.SIEGE_OF_S1_HAS_ENDED).addString(getCastle().getName()));
        Broadcast.toAllOnlinePlayers(new PlaySound("systemmsg_e.18"));
        _log.info("Siege ended for " + getCastle().getName() + ".");

        if (getCastle().getOwnerId() > 0)
        {
            Clan clan = ClanTable.getInstance().getClan(getCastle().getOwnerId());
            Broadcast.toAllOnlinePlayers(SystemMessage.getSystemMessage(SystemMessageId.CLAN_S1_VICTORIOUS_OVER_S2_S_SIEGE).addString(clan.getName()).addString(getCastle().getName()));

            final Clan formerClan = getCastle().getInitialCastleOwner();
            if (formerClan != null && formerClan.getClanId() != clan.getClanId())
            {
                // Retail crown removal
                getCastle().checkItemsForClan(formerClan);

                // Remove siege rewards ONLY if enabled
                if (Config.REMOVE_SIEGE_REWARD_ON_LOSE)
                {
                    // Remove from online players
                    removeSiegeRewardItems(formerClan);

                    // Remove from offline players (inventory, warehouse, freight, paperdoll)
                    removeSiegeRewardItemsOffline(formerClan);

                    // Remove from clan warehouse
                    removeSiegeRewardItemsFromClanWarehouse(formerClan);
                }

                for (ClanMember member : clan.getMembers())
                {
                    final Player player = member.getPlayerInstance();
                    if (player != null && player.isNoble())
                        Hero.getInstance().setCastleTaken(player.getObjectId(), getCastle().getCastleId());
                }
            }

            
            if (Config.ENABLE_REWARD_WINNER_CLAN && !isCastleAlreadyRewarded())
            {
                rewardClanWinner(clan);
                markCastleRewarded();
                endSiege();
            }
        }
        else
            Broadcast.toAllOnlinePlayers(SystemMessage.getSystemMessage(SystemMessageId.SIEGE_S1_DRAW).addString(getCastle().getName()));

        for (Clan clan : _registeredClans.keySet())
        {
            clan.setSiegeKills(0);
            clan.setSiegeDeaths(0);
            clan.setFlag(null);
        }

        getCastle().updateClansReputation();
        getCastle().getSiegeZone().banishForeigners(getCastle().getOwnerId());
        updatePlayerSiegeStateFlags(true);
        saveCastleSiege(true);
        clearAllClans();
        removeTowers();
        getCastle().despawnSiegeGuardsOrMercenaries();
        getCastle().spawnDoors(false);

        getCastle().getSiegeZone().setIsActive(false);
        getCastle().getSiegeZone().updateZoneStatusForCharactersInside();
        
        // Cleanup fake players from siege
        phantom.ai.event.SiegeFakeSystem.getInstance().onSiegeEnd(getCastle());
    }

    @Override
    public final List<Clan> getAttackerClans()
    {
        return _registeredClans.entrySet().stream().filter(e -> e.getValue() == SiegeSide.ATTACKER).map(Map.Entry::getKey).collect(Collectors.toList());
    }

    @Override
    public final List<Clan> getDefenderClans()
    {
        return _registeredClans.entrySet().stream().filter(e -> e.getValue() == SiegeSide.DEFENDER || e.getValue() == SiegeSide.OWNER).map(Map.Entry::getKey).collect(Collectors.toList());
    }

    @Override
    public boolean checkSide(Clan clan, SiegeSide type)
    {
        return clan != null && _registeredClans.get(clan) == type;
    }

    @Override
    public boolean checkSides(Clan clan, SiegeSide... types)
    {
        return clan != null && ArraysUtil.contains(types, _registeredClans.get(clan));
    }

    @Override
    public boolean checkSides(Clan clan)
    {
        return clan != null && _registeredClans.containsKey(clan);
    }

    @Override
    public Npc getFlag(Clan clan)
    {
        return (checkSide(clan, SiegeSide.ATTACKER)) ? clan.getFlag() : null;
    }

    @Override
    public final Calendar getSiegeDate()
    {
        return getCastle().getSiegeDate();
    }

    public Map<Clan, SiegeSide> getRegisteredClans()
    {
        return _registeredClans;
    }

    public final List<Clan> getPendingClans()
    {
        return _registeredClans.entrySet().stream().filter(e -> e.getValue() == SiegeSide.PENDING).map(Map.Entry::getKey).collect(Collectors.toList());
    }

    private void switchSide(Clan clan, SiegeSide newState)
    {
        _registeredClans.put(clan, newState);
    }

    private void switchSides(SiegeSide newState, SiegeSide... previousStates)
    {
        for (Map.Entry<Clan, SiegeSide> entry : _registeredClans.entrySet())
        {
            if (ArraysUtil.contains(previousStates, entry.getValue()))
                entry.setValue(newState);
        }
    }

    public SiegeSide getSide(Clan clan)
    {
        return _registeredClans.get(clan);
    }

    public boolean isOnOppositeSide(Clan formerClan, Clan targetClan)
    {
        final SiegeSide formerSide = _registeredClans.get(formerClan);
        final SiegeSide targetSide = _registeredClans.get(targetClan);

        if (formerSide == null || targetSide == null)
            return false;

        return (targetSide == SiegeSide.ATTACKER && (formerSide == SiegeSide.OWNER || formerSide == SiegeSide.DEFENDER || formerSide == SiegeSide.PENDING))
                || (formerSide == SiegeSide.ATTACKER && (targetSide == SiegeSide.OWNER || targetSide == SiegeSide.DEFENDER || targetSide == SiegeSide.PENDING));
    }

    public void midVictory()
    {
        if (!isInProgress())
            return;

        getCastle().despawnSiegeGuardsOrMercenaries();

        if (getCastle().getOwnerId() <= 0)
            return;

        final List<Clan> attackers = getAttackerClans();
        final List<Clan> defenders = getDefenderClans();

        final Clan castleOwner = ClanTable.getInstance().getClan(getCastle().getOwnerId());

        if (defenders.isEmpty() && attackers.size() == 1)
        {
            switchSide(castleOwner, SiegeSide.OWNER);
            endSiege();
            return;
        }

        final int allyId = castleOwner.getAllyId();

        if (defenders.isEmpty() && allyId != 0)
        {
            boolean allInSameAlliance = true;
            for (Clan clan : attackers)
            {
                if (clan.getAllyId() != allyId)
                {
                    allInSameAlliance = false;
                    break;
                }
            }

            if (allInSameAlliance)
            {
                switchSide(castleOwner, SiegeSide.OWNER);
                endSiege();
                return;
            }
        }

        switchSides(SiegeSide.ATTACKER, SiegeSide.DEFENDER, SiegeSide.OWNER);
        switchSide(castleOwner, SiegeSide.OWNER);

        if (allyId != 0)
        {
            for (Clan clan : attackers)
            {
                if (clan.getAllyId() == allyId)
                    switchSide(clan, SiegeSide.DEFENDER);
            }
        }

        getCastle().getSiegeZone().banishForeigners(getCastle().getOwnerId());

        for (Clan clan : defenders)
            clan.setFlag(null);

        getCastle().removeDoorUpgrade();
        getCastle().removeTrapUpgrade();
        getCastle().spawnDoors(true);

        removeTowers();
        spawnControlTowers();
        spawnFlameTowers();
        updatePlayerSiegeStateFlags(false);
    }

    public void announceToPlayer(SystemMessage message, boolean bothSides)
    {
        for (Clan clan : getDefenderClans())
            clan.broadcastToOnlineMembers(message);

        if (bothSides)
        {
            for (Clan clan : getAttackerClans())
                clan.broadcastToOnlineMembers(message);
        }
    }

    public void updatePlayerSiegeStateFlags(boolean clear)
    {
        for (Clan clan : getAttackerClans())
        {
            for (Player member : clan.getOnlineMembers())
            {
                if (clear)
                {
                    member.setSiegeState((byte) 0);
                    member.setIsInSiege(false);
                }
                else
                {
                    member.setSiegeState((byte) 1);
                    if (checkIfInZone(member))
                        member.setIsInSiege(true);
                }
                member.sendPacket(new UserInfo(member));
                member.broadcastRelationsChanges();
            }
        }

        for (Clan clan : getDefenderClans())
        {
            for (Player member : clan.getOnlineMembers())
            {
                if (clear)
                {
                    member.setSiegeState((byte) 0);
                    member.setIsInSiege(false);
                }
                else
                {
                    member.setSiegeState((byte) 2);
                    if (checkIfInZone(member))
                        member.setIsInSiege(true);
                }
                member.sendPacket(new UserInfo(member));
                member.broadcastRelationsChanges();
            }
        }
    }

    public boolean checkIfInZone(WorldObject object)
    {
        return checkIfInZone(object.getX(), object.getY(), object.getZ());
    }

    public boolean checkIfInZone(int x, int y, int z)
    {
        return isInProgress() && getCastle().checkIfInZone(x, y, z);
    }

    public void clearAllClans()
    {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(CLEAR_SIEGE_CLANS))
        {
            ps.setInt(1, getCastle().getCastleId());
            ps.executeUpdate();
        }
        catch (Exception e)
        {
            _log.log(Level.SEVERE, "Error clearing registered clans.", e);
        }

        _registeredClans.clear();

        if (getCastle().getOwnerId() > 0)
        {
            final Clan clan = ClanTable.getInstance().getClan(getCastle().getOwnerId());
            if (clan != null)
                _registeredClans.put(clan, SiegeSide.OWNER);
        }
    }

    protected void clearPendingClans()
    {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(CLEAR_PENDING_CLANS))
        {
            ps.setInt(1, getCastle().getCastleId());
            ps.executeUpdate();
        }
        catch (Exception e)
        {
            _log.log(Level.SEVERE, "Error clearing pending clans.", e);
        }

        _registeredClans.entrySet().removeIf(e -> e.getValue() == SiegeSide.PENDING);
    }

    public void registerAttacker(Player player)
    {
        if (player.getClan() == null)
            return;

        int allyId = 0;
        if (getCastle().getOwnerId() != 0)
            allyId = ClanTable.getInstance().getClan(getCastle().getOwnerId()).getAllyId();

        if (allyId != 0)
        {
            if (player.getClan().getAllyId() == allyId)
            {
                player.sendPacket(SystemMessageId.CANNOT_ATTACK_ALLIANCE_CASTLE);
                return;
            }
        }

        if (allyIsRegisteredOnOppositeSide(player.getClan(), true))
            player.sendPacket(SystemMessageId.CANT_ACCEPT_ALLY_ENEMY_FOR_SIEGE);
        else if (checkIfCanRegister(player, SiegeSide.ATTACKER))
            registerClan(player.getClan(), SiegeSide.ATTACKER);
    }

    public void registerDefender(Player player)
    {
        if (player.getClan() == null)
            return;

        if (getCastle().getOwnerId() <= 0)
            player.sendPacket(SystemMessageId.DEFENDER_SIDE_FULL);
        else if (allyIsRegisteredOnOppositeSide(player.getClan(), false))
            player.sendPacket(SystemMessageId.CANT_ACCEPT_ALLY_ENEMY_FOR_SIEGE);
        else if (checkIfCanRegister(player, SiegeSide.PENDING))
            registerClan(player.getClan(), SiegeSide.PENDING);
    }

    private boolean allyIsRegisteredOnOppositeSide(Clan clan, boolean attacker)
    {
        final int allyId = clan.getAllyId();
        if (allyId != 0)
        {
            for (Clan alliedClan : ClanTable.getInstance().getClans())
            {
                if (alliedClan.getAllyId() == allyId)
                {
                    if (alliedClan.getClanId() == clan.getClanId())
                        continue;

                    if (attacker)
                    {
                        if (checkSides(alliedClan, SiegeSide.DEFENDER, SiegeSide.OWNER, SiegeSide.PENDING))
                            return true;
                    }
                    else
                    {
                        if (checkSides(alliedClan, SiegeSide.ATTACKER))
                            return true;
                    }
                }
            }
        }
        return false;
    }

    public void unregisterClan(Clan clan)
    {
        if (clan == null || clan.getCastleId() == getCastle().getCastleId() || _registeredClans.remove(clan) == null)
            return;

        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(CLEAR_SIEGE_CLAN))
        {
            ps.setInt(1, getCastle().getCastleId());
            ps.setInt(2, clan.getClanId());
            ps.executeUpdate();
        }
        catch (Exception e)
        {
            _log.log(Level.SEVERE, "Error unregistering clan.", e);
        }
    }

    private void startAutoTask()
    {
        if (!isCastleSiegeEnabled())
        {
            if (Config.CLEAR_SIEGE_REGISTRATIONS_WHEN_DISABLED)
                clearDisabledCastleRegistrations();
            changeStatus(SiegeStatus.REGISTRATION_OVER);
            getCastle().setTimeRegistrationOver(true);
            if (_siegeTask != null)
                _siegeTask.cancel(false);
            return;
        }

        if (!_startupSiegeDateSynced)
        {
            _startupSiegeDateSynced = true;
            if (Config.FORCE_RESYNC_SIEGE_DATE_ON_STARTUP && shouldResyncSiegeDateFromConfig())
            {
                getCastle().getSiegeDate().setTimeInMillis(System.currentTimeMillis());
                saveCastleSiege(false);
            }
        }

        if (getCastle().getSiegeDate().getTimeInMillis() < Calendar.getInstance().getTimeInMillis())
            saveCastleSiege(false);
        else
        {
            if (_siegeTask != null)
                _siegeTask.cancel(false);

            autoRegisterConfiguredClans();
            _siegeTask = ThreadPool.schedule(new SiegeTask(getCastle()), 1000);
        }
    }

    private boolean checkIfCanRegister(Player player, SiegeSide type)
    {
        SystemMessage sm;

        if (!isCastleSiegeEnabled())
            sm = SystemMessage.sendString("Siege is disabled for " + getCastle().getName() + ".");
        else if (isRegistrationOver())
            sm = SystemMessage.getSystemMessage(SystemMessageId.DEADLINE_FOR_SIEGE_S1_PASSED).addString(getCastle().getName());
        else if (isInProgress())
            sm = SystemMessage.getSystemMessage(SystemMessageId.NOT_SIEGE_REGISTRATION_TIME2);
        else if (player.getClan() == null || player.getClan().getLevel() < Config.MINIMUM_CLAN_LEVEL)
            sm = SystemMessage.getSystemMessage(SystemMessageId.ONLY_CLAN_LEVEL_4_ABOVE_MAY_SIEGE);
        else if (player.getClan().hasCastle())
            sm = (player.getClan().getClanId() == getCastle().getOwnerId()) ? SystemMessage.getSystemMessage(SystemMessageId.CLAN_THAT_OWNS_CASTLE_IS_AUTOMATICALLY_REGISTERED_DEFENDING) : SystemMessage.getSystemMessage(SystemMessageId.CLAN_THAT_OWNS_CASTLE_CANNOT_PARTICIPATE_OTHER_SIEGE);
        else if (player.getClan().isRegisteredOnSiege())
            sm = SystemMessage.getSystemMessage(SystemMessageId.ALREADY_REQUESTED_SIEGE_BATTLE);
        else if (checkIfAlreadyRegisteredForSameDay(player.getClan()))
            sm = SystemMessage.getSystemMessage(SystemMessageId.APPLICATION_DENIED_BECAUSE_ALREADY_SUBMITTED_A_REQUEST_FOR_ANOTHER_SIEGE_BATTLE);
        else if (type == SiegeSide.ATTACKER && getAttackerClans().size() >= Config.MAX_ATTACKERS_NUMBER)
            sm = SystemMessage.getSystemMessage(SystemMessageId.ATTACKER_SIDE_FULL);
        else if ((type == SiegeSide.DEFENDER || type == SiegeSide.PENDING || type == SiegeSide.OWNER) && (getDefenderClans().size() + getPendingClans().size() >= Config.MAX_DEFENDERS_NUMBER))
            sm = SystemMessage.getSystemMessage(SystemMessageId.DEFENDER_SIDE_FULL);
        else
            return true;

        player.sendPacket(sm);
        return false;
    }

    public boolean checkIfAlreadyRegisteredForSameDay(Clan clan)
    {
        for (Castle castle : CastleManager.getInstance().getCastles())
        {
            final Siege siege = castle.getSiege();
            if (siege == this)
                continue;

            if (siege.getSiegeDate().get(Calendar.DAY_OF_WEEK) == getSiegeDate().get(Calendar.DAY_OF_WEEK) && siege.checkSides(clan))
                return true;
        }
        return false;
    }

    private void removeTowers()
    {
        for (FlameTower ct : _flameTowers)
            ct.deleteMe();

        for (ControlTower ct : _controlTowers)
            ct.deleteMe();

        for (Npc ct : _destroyedTowers)
            ct.deleteMe();

        _flameTowers.clear();
        _controlTowers.clear();
        _destroyedTowers.clear();
    }

    private void saveCastleSiege(boolean launchTask)
    {
        if (!isCastleSiegeEnabled())
        {
            if (Config.CLEAR_SIEGE_REGISTRATIONS_WHEN_DISABLED)
                clearDisabledCastleRegistrations();
            getCastle().setTimeRegistrationOver(true);
            saveSiegeDate();

            if (_siegeTask != null)
                _siegeTask.cancel(false);
            return;
        }

        setNextSiegeDate();
        getCastle().setTimeRegistrationOver(false);
        saveSiegeDate();

        if (launchTask)
            startAutoTask();

        _log.info("New date for " + getCastle().getName() + " siege: " + getCastle().getSiegeDate().getTime());
    }

    private void saveSiegeDate()
    {
        if (_siegeTask != null)
        {
            _siegeTask.cancel(true);
            _siegeTask = ThreadPool.schedule(new SiegeTask(getCastle()), 1000);
        }

        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(UPDATE_SIEGE_INFOS))
        {
            ps.setLong(1, getSiegeDate().getTimeInMillis());
            ps.setString(2, String.valueOf(isTimeRegistrationOver()));
            ps.setInt(3, getCastle().getCastleId());
            ps.executeUpdate();
        }
        catch (Exception e)
        {
            _log.log(Level.SEVERE, "Error saving siege date.", e);
        }
    }

    public void registerClan(Clan clan, SiegeSide type)
    {
        if (clan.hasCastle())
            return;

        switch (type)
        {
            case DEFENDER:
            case PENDING:
            case OWNER:
                if (getDefenderClans().size() + getPendingClans().size() >= Config.MAX_DEFENDERS_NUMBER)
                    return;
                break;

            default:
                if (getAttackerClans().size() >= Config.MAX_ATTACKERS_NUMBER)
                    return;
                break;
        }

        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(ADD_OR_UPDATE_SIEGE_CLAN))
        {
            ps.setInt(1, clan.getClanId());
            ps.setInt(2, getCastle().getCastleId());
            ps.setString(3, type.toString());
            ps.setString(4, type.toString());
            ps.executeUpdate();
        }
        catch (Exception e)
        {
            _log.log(Level.SEVERE, "Error registering clan on siege.", e);
        }

        _registeredClans.put(clan, type);
    }

    private void setNextSiegeDate()
    {
        if (!isCastleSiegeEnabled())
            return;

        _registrationOneMinuteAnnounced = false;
        final Calendar siegeDate = getCastle().getSiegeDate();
        if (siegeDate.getTimeInMillis() < System.currentTimeMillis())
            siegeDate.setTimeInMillis(System.currentTimeMillis());

        switch (getCastle().getCastleId())
        {
            case 1: siegeDate.set(Calendar.DAY_OF_WEEK, Config.SIEGE_DAY_GLUDIO); break;
            case 2: siegeDate.set(Calendar.DAY_OF_WEEK, Config.SIEGE_DAY_DION); break;
            case 3: siegeDate.set(Calendar.DAY_OF_WEEK, Config.SIEGE_DAY_GIRAN); break;
            case 4: siegeDate.set(Calendar.DAY_OF_WEEK, Config.SIEGE_DAY_OREN); break;
            case 5: siegeDate.set(Calendar.DAY_OF_WEEK, Config.SIEGE_DAY_ADEN); break;
            case 6: siegeDate.set(Calendar.DAY_OF_WEEK, Config.SIEGE_DAY_INNADRIL); break;
            case 7: siegeDate.set(Calendar.DAY_OF_WEEK, Config.SIEGE_DAY_GODDARD); break;
            case 8: siegeDate.set(Calendar.DAY_OF_WEEK, Config.SIEGE_DAY_RUNE); break;
            case 9: siegeDate.set(Calendar.DAY_OF_WEEK, Config.SIEGE_DAY_SCHUT); break;
        }

        siegeDate.add(Calendar.WEEK_OF_YEAR, Config.DAY_TO_SIEGE);

        switch (getCastle().getCastleId())
        {
            case 1: siegeDate.set(Calendar.HOUR_OF_DAY, Config.SIEGE_HOUR_GLUDIO); break;
            case 2: siegeDate.set(Calendar.HOUR_OF_DAY, Config.SIEGE_HOUR_DION); break;
            case 3: siegeDate.set(Calendar.HOUR_OF_DAY, Config.SIEGE_HOUR_GIRAN); break;
            case 4: siegeDate.set(Calendar.HOUR_OF_DAY, Config.SIEGE_HOUR_OREN); break;
            case 5: siegeDate.set(Calendar.HOUR_OF_DAY, Config.SIEGE_HOUR_ADEN); break;
            case 6: siegeDate.set(Calendar.HOUR_OF_DAY, Config.SIEGE_HOUR_INNADRIL); break;
            case 7: siegeDate.set(Calendar.HOUR_OF_DAY, Config.SIEGE_HOUR_GODDARD); break;
            case 8: siegeDate.set(Calendar.HOUR_OF_DAY, Config.SIEGE_HOUR_RUNE); break;
            case 9: siegeDate.set(Calendar.HOUR_OF_DAY, Config.SIEGE_HOUR_SCHUT); break;
        }

        siegeDate.set(Calendar.MINUTE, getConfiguredSiegeMinute());
        siegeDate.set(Calendar.SECOND, 0);
        siegeDate.set(Calendar.MILLISECOND, 0);

        // If the configured slot is still in the past (e.g. same siege day but hour already passed,
        // or WeeksToNextSiege=0), advance by whole week steps until the next siege is in the future.
        // Otherwise saveCastleSiege/startAutoTask/SiegeTask loop on the same past timestamp and spam
        // registration open/close messages.
        final int weekStep = Math.max(1, Config.DAY_TO_SIEGE);
        while (siegeDate.getTimeInMillis() <= System.currentTimeMillis())
            siegeDate.add(Calendar.WEEK_OF_YEAR, weekStep);

        Broadcast.toAllOnlinePlayers(SystemMessage.getSystemMessage(SystemMessageId.S1_ANNOUNCED_SIEGE_TIME).addString(getCastle().getName()));
        changeStatus(SiegeStatus.REGISTRATION_OPENED);
        Broadcast.toAllOnlinePlayers(SystemMessage.sendString("Siege registration for " + getCastle().getName() + " is now open."));
        autoRegisterConfiguredClans();
        _log.info("Siege registration opened for " + getCastle().getName() + ". Next siege at " + getSiegeDate().getTime() + ", registration closes at " + new java.util.Date(getSiegeRegistrationEndDate()) + ".");
    }

    public boolean isCastleSiegeEnabled()
    {
        switch (getCastle().getCastleId())
        {
            case 1: return Config.SIEGE_GLUDIO;
            case 2: return Config.SIEGE_DION;
            case 3: return Config.SIEGE_GIRAN;
            case 4: return Config.SIEGE_OREN;
            case 5: return Config.SIEGE_ADEN;
            case 6: return Config.SIEGE_INNADRIL;
            case 7: return Config.SIEGE_GODDARD;
            case 8: return Config.SIEGE_RUNE;
            case 9: return Config.SIEGE_SCHUT;
            default: return true;
        }
    }

    private boolean shouldResyncSiegeDateFromConfig()
    {
        final Calendar siegeDate = getCastle().getSiegeDate();
        return siegeDate.get(Calendar.DAY_OF_WEEK) != getConfiguredSiegeDay()
            || siegeDate.get(Calendar.HOUR_OF_DAY) != getConfiguredSiegeHour()
            || siegeDate.get(Calendar.MINUTE) != getConfiguredSiegeMinute();
    }

    private int getConfiguredSiegeDay()
    {
        switch (getCastle().getCastleId())
        {
            case 1: return Config.SIEGE_DAY_GLUDIO;
            case 2: return Config.SIEGE_DAY_DION;
            case 3: return Config.SIEGE_DAY_GIRAN;
            case 4: return Config.SIEGE_DAY_OREN;
            case 5: return Config.SIEGE_DAY_ADEN;
            case 6: return Config.SIEGE_DAY_INNADRIL;
            case 7: return Config.SIEGE_DAY_GODDARD;
            case 8: return Config.SIEGE_DAY_RUNE;
            case 9: return Config.SIEGE_DAY_SCHUT;
            default: return getCastle().getSiegeDate().get(Calendar.DAY_OF_WEEK);
        }
    }

    private int getConfiguredSiegeHour()
    {
        switch (getCastle().getCastleId())
        {
            case 1: return Config.SIEGE_HOUR_GLUDIO;
            case 2: return Config.SIEGE_HOUR_DION;
            case 3: return Config.SIEGE_HOUR_GIRAN;
            case 4: return Config.SIEGE_HOUR_OREN;
            case 5: return Config.SIEGE_HOUR_ADEN;
            case 6: return Config.SIEGE_HOUR_INNADRIL;
            case 7: return Config.SIEGE_HOUR_GODDARD;
            case 8: return Config.SIEGE_HOUR_RUNE;
            case 9: return Config.SIEGE_HOUR_SCHUT;
            default: return getCastle().getSiegeDate().get(Calendar.HOUR_OF_DAY);
        }
    }

    private int getConfiguredSiegeMinute()
    {
        final int minute;
        switch (getCastle().getCastleId())
        {
            case 1: minute = Config.SIEGE_MINUTE_GLUDIO; break;
            case 2: minute = Config.SIEGE_MINUTE_DION; break;
            case 3: minute = Config.SIEGE_MINUTE_GIRAN; break;
            case 4: minute = Config.SIEGE_MINUTE_OREN; break;
            case 5: minute = Config.SIEGE_MINUTE_ADEN; break;
            case 6: minute = Config.SIEGE_MINUTE_INNADRIL; break;
            case 7: minute = Config.SIEGE_MINUTE_GODDARD; break;
            case 8: minute = Config.SIEGE_MINUTE_RUNE; break;
            case 9: minute = Config.SIEGE_MINUTE_SCHUT; break;
            default: minute = getCastle().getSiegeDate().get(Calendar.MINUTE);
        }
        return Math.max(0, Math.min(59, minute));
    }

    private void clearDisabledCastleRegistrations()
    {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(CLEAR_ACTIVE_SIEGE_REGISTRATIONS))
        {
            ps.setInt(1, getCastle().getCastleId());
            ps.executeUpdate();
        }
        catch (Exception e)
        {
            _log.log(Level.SEVERE, "Error clearing disabled castle registrations.", e);
        }

        _registeredClans.entrySet().removeIf(e -> e.getValue() != SiegeSide.OWNER);
        _log.info("Cleared attacker/defender/pending siege registrations for disabled castle " + getCastle().getName() + ".");
    }

    private void autoRegisterConfiguredClans()
    {
        if (!CastleManager.isReady())
        {
            if (!_autoRegistrationRetryScheduled)
            {
                _autoRegistrationRetryScheduled = true;
                ThreadPool.schedule(() ->
                {
                    _autoRegistrationRetryScheduled = false;
                    autoRegisterConfiguredClans();
                }, 1000);
            }
            return;
        }

        if (!isCastleSiegeEnabled() || isInProgress() || isRegistrationOver() || isTimeRegistrationOver() || getSiegeRegistrationEndDate() <= System.currentTimeMillis())
            return;

        syncConfiguredAutoClans();
        final List<Integer> attackerIds = Config.getAutoSiegeClanIds(getCastle().getCastleId(), true);
        final List<Integer> defenderIds = Config.getAutoSiegeClanIds(getCastle().getCastleId(), false);
        if (attackerIds != null)
            autoRegisterConfiguredSide(attackerIds, SiegeSide.ATTACKER);
        if (defenderIds != null)
            autoRegisterConfiguredSide(defenderIds, SiegeSide.DEFENDER);
    }

    private void syncConfiguredAutoClans()
    {
        if (!Config.SYNC_AUTO_SIEGE_CLANS_TO_CONFIG)
            return;

        final List<Integer> rawAttackers = Config.getAutoSiegeClanIds(getCastle().getCastleId(), true);
        final List<Integer> rawDefenders = Config.getAutoSiegeClanIds(getCastle().getCastleId(), false);
        final Set<Integer> configuredAttackers = rawAttackers != null ? new HashSet<>(rawAttackers) : new HashSet<>();
        final Set<Integer> configuredDefenders = rawDefenders != null ? new HashSet<>(rawDefenders) : new HashSet<>();

        for (Map.Entry<Clan, SiegeSide> entry : new ArrayList<>(_registeredClans.entrySet()))
        {
            final Clan clan = entry.getKey();
            final SiegeSide side = entry.getValue();
            if (clan == null || side == SiegeSide.OWNER || !FakePlayerConfig.LIST_CLAN_ID.contains(clan.getClanId()))
                continue;

            final boolean shouldRemain;
            switch (side)
            {
                case ATTACKER:
                    shouldRemain = configuredAttackers.contains(clan.getClanId());
                    break;

                case DEFENDER:
                case PENDING:
                    shouldRemain = configuredDefenders.contains(clan.getClanId());
                    break;

                default:
                    shouldRemain = true;
                    break;
            }

            if (shouldRemain)
                continue;

            unregisterClan(clan);
            _log.info("Auto siege registration sync: clan " + clan.getName() + " removed from " + side + " for " + getCastle().getName() + ".");
        }
    }

    private void autoRegisterConfiguredSide(List<Integer> clanIds, SiegeSide side)
    {
        for (int clanId : clanIds)
        {
            final Clan clan = ClanTable.getInstance().getClan(clanId);
            if (clan == null)
            {
                _log.warning("Auto siege registration skipped: clan " + clanId + " not found for " + getCastle().getName() + ".");
                continue;
            }

            if (checkSides(clan))
                continue;

            if (clan.hasCastle() && clan.getClanId() != getCastle().getOwnerId())
            {
                _log.info("Auto siege registration skipped: clan " + clan.getName() + " already owns another castle.");
                continue;
            }

            if (clan.isRegisteredOnSiege() || checkIfAlreadyRegisteredForSameDay(clan))
            {
                _log.info("Auto siege registration skipped: clan " + clan.getName() + " is already registered on another siege.");
                continue;
            }

            if (clan.getLevel() < Config.MINIMUM_CLAN_LEVEL)
            {
                _log.info("Auto siege registration skipped: clan " + clan.getName() + " does not meet minimum level.");
                continue;
            }

            if (side == SiegeSide.ATTACKER && getAttackerClans().size() >= Config.MAX_ATTACKERS_NUMBER)
                break;

            if (side == SiegeSide.DEFENDER && getDefenderClans().size() + getPendingClans().size() >= Config.MAX_DEFENDERS_NUMBER)
                break;

            registerClan(clan, side);
            _log.info("Auto siege registration: clan " + clan.getName() + " registered as " + side + " for " + getCastle().getName() + ".");
        }
    }

    private void spawnControlTowers()
    {
        for (TowerSpawnLocation ts : getCastle().getControlTowers())
        {
            try
            {
                final L2Spawn spawn = new L2Spawn(NpcTable.getInstance().getTemplate(ts.getId()));
                spawn.setLoc(ts);

                final ControlTower tower = (ControlTower) spawn.doSpawn(false);
                tower.setCastle(getCastle());

                _controlTowers.add(tower);
            }
            catch (Exception e)
            {
                _log.warning(getClass().getName() + ": Cannot spawn control tower! " + e);
            }
        }
    }

    private void spawnFlameTowers()
    {
        for (TowerSpawnLocation ts : getCastle().getFlameTowers())
        {
            try
            {
                final L2Spawn spawn = new L2Spawn(NpcTable.getInstance().getTemplate(ts.getId()));
                spawn.setLoc(ts);

                final FlameTower tower = (FlameTower) spawn.doSpawn(false);
                tower.setCastle(getCastle());
                tower.setUpgradeLevel(ts.getUpgradeLevel());
                tower.setZoneList(ts.getZoneList());

                _flameTowers.add(tower);
            }
            catch (Exception e)
            {
                _log.warning(getClass().getName() + ": Cannot spawn flame tower! " + e);
            }
        }
    }

    public final Castle getCastle()
    {
        return _castle;
    }

    public final boolean isInProgress()
    {
        return _siegeStatus == SiegeStatus.IN_PROGRESS;
    }

    public final boolean isRegistrationOver()
    {
        return _siegeStatus != SiegeStatus.REGISTRATION_OPENED;
    }

    public final boolean isTimeRegistrationOver()
    {
        return getCastle().isTimeRegistrationOver();
    }

    public final long getSiegeRegistrationEndDate()
    {
        return getCastle().getSiegeDate().getTimeInMillis() - (Config.SIEGE_REGISTRATION_TIME * 60000L);
    }

    public void endTimeRegistration(boolean automatic)
    {
        getCastle().setTimeRegistrationOver(true);
        if (!automatic)
            saveSiegeDate();
    }

    public int getControlTowerCount()
    {
        return (int) _controlTowers.stream().filter(lc -> lc.isActive()).count();
    }

    public List<ControlTower> getControlTowers()
    {
        return _controlTowers;
    }

    public List<FlameTower> getFlameTowers()
    {
        return _flameTowers;
    }

    public List<Npc> getDestroyedTowers()
    {
        return _destroyedTowers;
    }

    public class EndSiegeTask implements Runnable
    {
        private final Castle _castle;

        public EndSiegeTask(Castle castle)
        {
            _castle = castle;
        }

        @Override
        public void run()
        {
            if (!isInProgress())
                return;

            final long timeRemaining = _siegeEndDate.getTimeInMillis() - Calendar.getInstance().getTimeInMillis();
            if (timeRemaining > 3600000)
            {
                announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.S1_HOURS_UNTIL_SIEGE_CONCLUSION).addNumber(2), true);
                ThreadPool.schedule(new EndSiegeTask(_castle), timeRemaining - 3600000);
            }
            else if (timeRemaining <= 3600000 && timeRemaining > 600000)
            {
                announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.S1_MINUTES_UNTIL_SIEGE_CONCLUSION).addNumber(Math.round(timeRemaining / 60000)), true);
                ThreadPool.schedule(new EndSiegeTask(_castle), timeRemaining - 600000);
            }
            else if (timeRemaining <= 600000 && timeRemaining > 300000)
            {
                announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.S1_MINUTES_UNTIL_SIEGE_CONCLUSION).addNumber(Math.round(timeRemaining / 60000)), true);
                ThreadPool.schedule(new EndSiegeTask(_castle), timeRemaining - 300000);
            }
            else if (timeRemaining <= 300000 && timeRemaining > 10000)
            {
                announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.S1_MINUTES_UNTIL_SIEGE_CONCLUSION).addNumber(Math.round(timeRemaining / 60000)), true);
                ThreadPool.schedule(new EndSiegeTask(_castle), timeRemaining - 10000);
            }
            else if (timeRemaining <= 10000 && timeRemaining > 0)
            {
                announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.CASTLE_SIEGE_S1_SECONDS_LEFT).addNumber(Math.round(timeRemaining / 1000)), true);
                ThreadPool.schedule(new EndSiegeTask(_castle), timeRemaining);
            }
            else
                _castle.getSiege().endSiege();
        }
    }

    private class SiegeTask implements Runnable
    {
        private final Castle _castle;

        public SiegeTask(Castle castle)
        {
            _castle = castle;
        }

        @Override
        public void run()
        {
            if (isInProgress())
                return;

            if (!isTimeRegistrationOver())
            {
                final long regTimeRemaining = getSiegeRegistrationEndDate() - Calendar.getInstance().getTimeInMillis();
                if (regTimeRemaining > 0)
                {
                    if (regTimeRemaining > 60000)
                    {
                        _siegeTask = ThreadPool.schedule(new SiegeTask(_castle), regTimeRemaining - 60000);
                    }
                    else
                    {
                        if (!_registrationOneMinuteAnnounced)
                        {
                            _registrationOneMinuteAnnounced = true;
                            Broadcast.toAllOnlinePlayers(SystemMessage.sendString("Siege registration for " + getCastle().getName() + " closes in 1 minute."));
                        }
                        _siegeTask = ThreadPool.schedule(new SiegeTask(_castle), regTimeRemaining);
                    }
                    return;
                }

                endTimeRegistration(true);
                Broadcast.toAllOnlinePlayers(SystemMessage.getSystemMessage(SystemMessageId.REGISTRATION_TERM_FOR_S1_ENDED).addString(getCastle().getName()));
                changeStatus(SiegeStatus.REGISTRATION_OVER);
                clearPendingClans();
                _log.info("Siege registration closed for " + getCastle().getName() + ". Siege starts at " + getSiegeDate().getTime() + ".");
            }

            final long timeRemaining = getSiegeDate().getTimeInMillis() - Calendar.getInstance().getTimeInMillis();

            if (timeRemaining > 600000)
                _siegeTask = ThreadPool.schedule(new SiegeTask(_castle), timeRemaining - 600000);
            else if (timeRemaining > 300000)
                _siegeTask = ThreadPool.schedule(new SiegeTask(_castle), timeRemaining - 300000);
            else if (timeRemaining > 10000)
                _siegeTask = ThreadPool.schedule(new SiegeTask(_castle), timeRemaining - 10000);
            else if (timeRemaining > 0)
                _siegeTask = ThreadPool.schedule(new SiegeTask(_castle), timeRemaining);
            else
                _castle.getSiege().startSiege();
        }
    }

    public void addQuestEvent(Quest quest)
    {
        if (_questEvents.isEmpty())
            _questEvents = new ArrayList<>(3);

        _questEvents.add(quest);
    }

    public List<Quest> getQuestEvents()
    {
        return _questEvents;
    }

    public SiegeStatus getStatus()
    {
        return _siegeStatus;
    }

    protected void changeStatus(SiegeStatus status)
    {
        _siegeStatus = status;

        for (Quest quest : _questEvents)
            quest.onSiegeEvent();
    }

    public void rewardClanWinner(Clan clanWinner)
    {
        for (ClanMember member : clanWinner.getMembers())
        {
            Player player = member.getPlayerInstance();
            if (player == null)
                continue;

            if (player.getClan().getLeaderId() == player.getObjectId())
            {
                for (int[] item : Config.LEADER_REWARD_WINNER_SIEGE_CLAN)
                    player.addItem("SiegeReward", item[0], item[1], player, true);
            }
            else
            {
                for (int[] item : Config.REWARD_WINNER_SIEGE_CLAN)
                    player.addItem("SiegeReward", item[0], item[1], player, true);
            }

            player.sendPacket(new ExShowScreenMessage("Congratulations! You've been rewarded for the " + getCastle().getName() + " siege victory!", 8000));

            player.sendPacket(new ItemList(player, true));
            player.sendPacket(new StatusUpdate(player));
        }
    }

    public void removeSiegeRewardItems(Clan clan)
    {
        if (clan == null)
            return;

        for (Player player : clan.getOnlineMembers())
            removeSiegeRewardItems(player);
    }

    private static void removeSiegeRewardItems(Player player)
    {
        if (player == null)
            return;

        for (int[] item : Config.LEADER_REWARD_WINNER_SIEGE_CLAN)
            player.destroyItemByItemId("SiegeLoginCheck", item[0], -1, player, true);

        for (int[] item : Config.REWARD_WINNER_SIEGE_CLAN)
            player.destroyItemByItemId("SiegeLoginCheck", item[0], -1, player, true);

        player.sendPacket(new ItemList(player, true));
        player.sendPacket(new StatusUpdate(player));
    }

    private static void removeSiegeRewardItemsOffline(Clan clan)
    {
        if (clan == null)
            return;

        for (ClanMember member : clan.getMembers())
        {
            final int ownerId = member.getObjectId();

            for (int[] item : Config.LEADER_REWARD_WINNER_SIEGE_CLAN)
                deleteItemFromDB(ownerId, item[0]);

            for (int[] item : Config.REWARD_WINNER_SIEGE_CLAN)
                deleteItemFromDB(ownerId, item[0]);
        }

        for (int[] item : Config.LEADER_REWARD_WINNER_SIEGE_CLAN)
            deleteItemFromClanWarehouse(clan.getClanId(), item[0]);

        for (int[] item : Config.REWARD_WINNER_SIEGE_CLAN)
            deleteItemFromClanWarehouse(clan.getClanId(), item[0]);
    }

    private static void deleteItemFromDB(int ownerId, int itemId)
    {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "DELETE FROM items WHERE owner_id=? AND item_id=? AND loc IN ('INVENTORY','WAREHOUSE','FREIGHT','PAPERDOLL')"))
        {
            ps.setInt(1, ownerId);
            ps.setInt(2, itemId);
            ps.executeUpdate();
        }
        catch (Exception e)
        {
            _log.warning("Offline siege item removal failed: " + e.getMessage());
        }
    }

    private static void deleteItemFromClanWarehouse(int clanId, int itemId)
    {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM items WHERE owner_id=? AND item_id=? AND loc='CLANWH'"))
        {
            ps.setInt(1, clanId);
            ps.setInt(2, itemId);
            ps.executeUpdate();
        }
        catch (Exception e)
        {
            _log.warning("Clan warehouse siege item removal failed: " + e.getMessage());
        }
    }

    private static void removeSiegeRewardItemsFromClanWarehouse(Clan clan)
    {
        if (clan == null)
            return;

        final int clanId = clan.getClanId();

        for (int[] item : Config.LEADER_REWARD_WINNER_SIEGE_CLAN)
            deleteItemFromClanWarehouse(clanId, item[0]);

        for (int[] item : Config.REWARD_WINNER_SIEGE_CLAN)
            deleteItemFromClanWarehouse(clanId, item[0]);
    }
    
    private boolean isCastleAlreadyRewarded()
    {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT rewarded FROM siege_rewards WHERE castle_id=?"))
        {
            ps.setInt(1, _castle.getCastleId());

            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                    return rs.getInt("rewarded") == 1;
            }

            // not exists -> insert
            try (PreparedStatement ps2 = con.prepareStatement("INSERT INTO siege_rewards (castle_id, rewarded) VALUES (?,0)"))
            {
                ps2.setInt(1, _castle.getCastleId());
                ps2.execute();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return false;
    }


    private void markCastleRewarded()
    {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE siege_rewards SET rewarded=1 WHERE castle_id=?"))
        {
            ps.setInt(1, _castle.getCastleId());
            ps.execute();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void resetCastleReward()
    {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE siege_rewards SET rewarded=0 WHERE castle_id=?"))
        {
            ps.setInt(1, _castle.getCastleId());
            ps.execute();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}