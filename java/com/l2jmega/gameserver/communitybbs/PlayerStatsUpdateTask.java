package com.l2jmega.gameserver.communitybbs;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.Config;
import com.l2jmega.L2DatabaseFactory;

public class PlayerStatsUpdateTask {
    static Logger _log = Logger.getLogger(PlayerStatsUpdateTask.class.getName());

    /**
     * Raid points and adena are aggregated per character in subqueries so we do not
     * multiply rows when joining (multiple raid bosses × multiple adena stacks).
     */
    private static final String STATS_FROM =
            "FROM characters ch "
                    + "LEFT JOIN (SELECT char_id, SUM(points) AS rp FROM character_raid_points GROUP BY char_id) chr ON ch.obj_Id = chr.char_id "
                    + "LEFT JOIN (SELECT owner_id, SUM(count) AS ad FROM items WHERE item_id = 57 GROUP BY owner_id) it ON ch.obj_Id = it.owner_id ";

    private static final String STATS_SELECT =
            "SELECT COALESCE(chr.rp, 0) AS raidpoints, COALESCE(it.ad, 0) AS adena, ch.char_name, ch.pkkills, ch.pvpkills, ch.onlinetime, ch.base_class, ch.online ";

    private static StringBuilder _pvpList = new StringBuilder();
    private static StringBuilder _pkList = new StringBuilder();
    private static StringBuilder _raidList = new StringBuilder();
    private static StringBuilder _adenaList = new StringBuilder();
    private static StringBuilder _onlineList = new StringBuilder();
    private static String _lastUpdate = "Error";

    public static void updateTask() {
        _log.log(Level.INFO, "[CommunityBoard]: Started player stats update task!");

        ThreadPool.scheduleAtFixedRate(() -> {
            PlayerStatsUpdateTask.cleanUpTask();
            PlayerStatsUpdateTask.updatePvPList();
            PlayerStatsUpdateTask.updatePkList();
            PlayerStatsUpdateTask.updateRaidList();
            PlayerStatsUpdateTask.updateAdenaList();
            PlayerStatsUpdateTask.updateOnlineList();

            Date date = new Date();
            PlayerStatsUpdateTask.lastUpdate(date.toString());
        }, 60000L, 300000L);
    }

    public static void forceUpdateNow()
    {
        try
        {
            cleanUpTask();
            updatePvPList();
            updatePkList();
            updateRaidList();
            updateAdenaList();
            updateOnlineList();
            lastUpdate(new Date().toString());
        }
        catch (Exception e)
        {
            _log.log(Level.WARNING, "Failed to force update CommunityBoard stats!", e);
        }
    }

    static void cleanUpTask() {
        _pvpList.setLength(0);
        _pkList.setLength(0);
        _raidList.setLength(0);
        _adenaList.setLength(0);
        _onlineList.setLength(0);
    }

    static void updatePvPList() {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection()) {
            int pos = 0;
            PreparedStatement statement = con.prepareStatement(
                    STATS_SELECT + STATS_FROM + "ORDER BY ch.pvpkills DESC LIMIT " + Config.TOP_PLAYER_RESULTS
            );
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                pos++;
                boolean status = result.getInt("online") == 1;
                long pvpkills = result.getLong("pvpkills");
                long pkkills = result.getLong("pkkills");
                long raidpoints = result.getLong("raidpoints");
                long adena = result.getLong("adena");
                long onlineTime = result.getLong("onlinetime");

                String timeon = getPlayerRunTime(onlineTime);
                String adenas = getAdenas(adena);

                _pvpList.append("<table border=0 cellspacing=0 cellpadding=2 height=" + Config.TOP_PLAYER_ROW_HEIGHT + " width=610><tr><td FIXWIDTH=5></td>");
                _pvpList.append("<td FIXWIDTH=25>" + pos + "</td>");
                _pvpList.append("<td FIXWIDTH=180>" + result.getString("char_name") + "</td>");
                _pvpList.append("<td FIXWIDTH=175>" + className(result.getInt("base_class")) + "</td>");
                _pvpList.append("<td FIXWIDTH=60>" + pvpkills + "</td>");
                _pvpList.append("<td FIXWIDTH=60>" + pkkills + "</td>");
                _pvpList.append("<td FIXWIDTH=70>" + raidpoints + "</td>");
                _pvpList.append("<td FIXWIDTH=140>" + adenas + "</td>");
                _pvpList.append("<td FIXWIDTH=150>" + timeon + "</td>");
                _pvpList.append("<td FIXWIDTH=65>" + (status ? "<font color=99FF00>Online</font>" : "<font color=CC0000>Offline</font>") + "</td>");
                _pvpList.append("</tr></table><img src=\"L2UI.Squaregray\" width=\"610\" height=\"1\">");
            }

            result.close();
            statement.close();
        } catch (Exception e) {
            _log.log(Level.WARNING, "Failed to update pvp list!");
            e.printStackTrace();
        }
    }

    static void updatePkList() {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection()) {
            int pos = 0;
            PreparedStatement statement = con.prepareStatement(
                    STATS_SELECT + STATS_FROM + "ORDER BY ch.pkkills DESC LIMIT " + Config.TOP_PLAYER_RESULTS
            );
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                pos++;
                boolean status = result.getInt("online") == 1;
                long pvpkills = result.getLong("pvpkills");
                long pkkills = result.getLong("pkkills");
                long raidpoints = result.getLong("raidpoints");
                long adena = result.getLong("adena");
                long onlineTime = result.getLong("onlinetime");

                String timeon = getPlayerRunTime(onlineTime);
                String adenas = getAdenas(adena);

                _pkList.append("<table border=0 cellspacing=0 cellpadding=2 height=" + Config.TOP_PLAYER_ROW_HEIGHT + " width=610><tr><td FIXWIDTH=5></td>");
                _pkList.append("<td FIXWIDTH=25>" + pos + "</td>");
                _pkList.append("<td FIXWIDTH=180>" + result.getString("char_name") + "</td>");
                _pkList.append("<td FIXWIDTH=175>" + className(result.getInt("base_class")) + "</td>");
                _pkList.append("<td FIXWIDTH=60>" + pvpkills + "</td>");
                _pkList.append("<td FIXWIDTH=60>" + pkkills + "</td>");
                _pkList.append("<td FIXWIDTH=70>" + raidpoints + "</td>");
                _pkList.append("<td FIXWIDTH=140>" + adenas + "</td>");
                _pkList.append("<td FIXWIDTH=150>" + timeon + "</td>");
                _pkList.append("<td FIXWIDTH=65>" + (status ? "<font color=99FF00>Online</font>" : "<font color=CC0000>Offline</font>") + "</td>");
                _pkList.append("</tr></table><img src=\"L2UI.Squaregray\" width=\"610\" height=\"1\">");
            }

            result.close();
            statement.close();
        } catch (Exception e) {
            _log.log(Level.WARNING, "Failed to update pk list!");
            e.printStackTrace();
        }
    }

    static void updateRaidList() {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection()) {
            int pos = 0;
            PreparedStatement statement = con.prepareStatement(
                    STATS_SELECT + STATS_FROM + "ORDER BY raidpoints DESC LIMIT " + Config.TOP_PLAYER_RESULTS
            );
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                pos++;
                boolean status = result.getInt("online") == 1;
                long pvpkills = result.getLong("pvpkills");
                long pkkills = result.getLong("pkkills");
                long raidpoints = result.getLong("raidpoints");
                long adena = result.getLong("adena");
                long onlineTime = result.getLong("onlinetime");

                String timeon = getPlayerRunTime(onlineTime);
                String adenas = getAdenas(adena);

                _raidList.append("<table border=0 cellspacing=0 cellpadding=2 height=" + Config.TOP_PLAYER_ROW_HEIGHT + " width=610><tr><td FIXWIDTH=5></td>");
                _raidList.append("<td FIXWIDTH=25>" + pos + "</td>");
                _raidList.append("<td FIXWIDTH=180>" + result.getString("char_name") + "</td>");
                _raidList.append("<td FIXWIDTH=175>" + className(result.getInt("base_class")) + "</td>");
                _raidList.append("<td FIXWIDTH=60>" + pvpkills + "</td>");
                _raidList.append("<td FIXWIDTH=60>" + pkkills + "</td>");
                _raidList.append("<td FIXWIDTH=70>" + raidpoints + "</td>");
                _raidList.append("<td FIXWIDTH=140>" + adenas + "</td>");
                _raidList.append("<td FIXWIDTH=150>" + timeon + "</td>");
                _raidList.append("<td FIXWIDTH=65>" + (status ? "<font color=99FF00>Online</font>" : "<font color=CC0000>Offline</font>") + "</td>");
                _raidList.append("</tr></table><img src=\"L2UI.Squaregray\" width=\"610\" height=\"1\">");
            }

            result.close();
            statement.close();
        } catch (Exception e) {
            _log.log(Level.WARNING, "Failed to update raid list!");
            e.printStackTrace();
        }
    }

    static void updateAdenaList() {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection()) {
            int pos = 0;
            PreparedStatement statement = con.prepareStatement(
                    STATS_SELECT + STATS_FROM + "ORDER BY adena DESC LIMIT " + Config.TOP_PLAYER_RESULTS
            );
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                pos++;
                boolean status = result.getInt("online") == 1;
                long pvpkills = result.getLong("pvpkills");
                long pkkills = result.getLong("pkkills");
                long raidpoints = result.getLong("raidpoints");
                long adena = result.getLong("adena");
                long onlineTime = result.getLong("onlinetime");

                String timeon = getPlayerRunTime(onlineTime);
                String adenas = getAdenas(adena);

                _adenaList.append("<table border=0 cellspacing=0 cellpadding=2 height=" + Config.TOP_PLAYER_ROW_HEIGHT + " width=610><tr><td FIXWIDTH=5></td>");
                _adenaList.append("<td FIXWIDTH=25>" + pos + "</td>");
                _adenaList.append("<td FIXWIDTH=180>" + result.getString("char_name") + "</td>");
                _adenaList.append("<td FIXWIDTH=175>" + className(result.getInt("base_class")) + "</td>");
                _adenaList.append("<td FIXWIDTH=60>" + pvpkills + "</td>");
                _adenaList.append("<td FIXWIDTH=60>" + pkkills + "</td>");
                _adenaList.append("<td FIXWIDTH=70>" + raidpoints + "</td>");
                _adenaList.append("<td FIXWIDTH=140>" + adenas + "</td>");
                _adenaList.append("<td FIXWIDTH=150>" + timeon + "</td>");
                _adenaList.append("<td FIXWIDTH=65>" + (status ? "<font color=99FF00>Online</font>" : "<font color=CC0000>Offline</font>") + "</td>");
                _adenaList.append("</tr></table><img src=\"L2UI.Squaregray\" width=\"610\" height=\"1\">");
            }

            result.close();
            statement.close();
        } catch (Exception e) {
            _log.log(Level.WARNING, "Failed to update adena list!");
            e.printStackTrace();
        }
    }

    static void updateOnlineList() {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection()) {
            int pos = 0;
            PreparedStatement statement = con.prepareStatement(
                    STATS_SELECT + STATS_FROM + "ORDER BY ch.onlinetime DESC LIMIT " + Config.TOP_PLAYER_RESULTS
            );
            ResultSet result = statement.executeQuery();

            while (result.next()) {
                pos++;
                boolean status = result.getInt("online") == 1;
                long pvpkills = result.getLong("pvpkills");
                long pkkills = result.getLong("pkkills");
                long raidpoints = result.getLong("raidpoints");
                long adena = result.getLong("adena");
                long onlineTime = result.getLong("onlinetime");

                String timeon = getPlayerRunTime(onlineTime);
                String adenas = getAdenas(adena);

                _onlineList.append("<table border=0 cellspacing=0 cellpadding=2 height=" + Config.TOP_PLAYER_ROW_HEIGHT + " width=610><tr><td FIXWIDTH=5></td>");
                _onlineList.append("<td FIXWIDTH=25>" + pos + "</td>");
                _onlineList.append("<td FIXWIDTH=180>" + result.getString("char_name") + "</td>");
                _onlineList.append("<td FIXWIDTH=175>" + className(result.getInt("base_class")) + "</td>");
                _onlineList.append("<td FIXWIDTH=60>" + pvpkills + "</td>");
                _onlineList.append("<td FIXWIDTH=60>" + pkkills + "</td>");
                _onlineList.append("<td FIXWIDTH=70>" + raidpoints + "</td>");
                _onlineList.append("<td FIXWIDTH=140>" + adenas + "</td>");
                _onlineList.append("<td FIXWIDTH=150>" + timeon + "</td>");
                _onlineList.append("<td FIXWIDTH=65>" + (status ? "<font color=99FF00>Online</font>" : "<font color=CC0000>Offline</font>") + "</td>");
                _onlineList.append("</tr></table><img src=\"L2UI.Squaregray\" width=\"610\" height=\"1\">");
            }

            result.close();
            statement.close();
        } catch (Exception e) {
            _log.log(Level.WARNING, "Failed to update online time list!");
            e.printStackTrace();
        }
    }

    public static String pvpList() {
        return _pvpList.toString();
    }

    public static String pkList() {
        return _pkList.toString();
    }

    public static String raidList() {
        return _raidList.toString();
    }

    public static String adenaList() {
        return _adenaList.toString();
    }

    public static String onlineList() {
        return _onlineList.toString();
    }

    static void lastUpdate(String format) {
        _lastUpdate = format;
    }

    public static String getLastUpdate() {
        return _lastUpdate;
    }

    public static final String className(int classid) {
        Map<Integer, String> classList = new HashMap<>();

        classList.put(0, "Fighter");
        classList.put(1, "Warrior");
        classList.put(2, "Gladiator");
        classList.put(3, "Warlord");
        classList.put(4, "Knight");
        classList.put(5, "Paladin");
        classList.put(6, "Dark Avenger");
        classList.put(7, "Rogue");
        classList.put(8, "Treasure Hunter");
        classList.put(9, "Hawkeye");
        classList.put(10, "Mage");
        classList.put(11, "Wizard");
        classList.put(12, "Sorcerer");
        classList.put(13, "Necromancer");
        classList.put(14, "Warlock");
        classList.put(15, "Cleric");
        classList.put(16, "Bishop");
        classList.put(17, "Prophet");
        classList.put(18, "Elven Fighter");
        classList.put(19, "Elven Knight");
        classList.put(20, "Temple Knight");
        classList.put(21, "Swordsinger");
        classList.put(22, "Elven Scout");
        classList.put(23, "Plains Walker");
        classList.put(24, "Silver Ranger");
        classList.put(25, "Elven Mage");
        classList.put(26, "Elven Wizard");
        classList.put(27, "Spellsinger");
        classList.put(28, "Elemental Summoner");
        classList.put(29, "Oracle");
        classList.put(30, "Elder");
        classList.put(31, "Dark Fighter");
        classList.put(32, "Palus Knight");
        classList.put(33, "Shillien Knight");
        classList.put(34, "Bladedancer");
        classList.put(35, "Assassin");
        classList.put(36, "Abyss Walker");
        classList.put(37, "Phantom Ranger");
        classList.put(38, "Dark Mage");
        classList.put(39, "Dark Wizard");
        classList.put(40, "Spellhowler");
        classList.put(41, "Phantom Summoner");
        classList.put(42, "Shillien Oracle");
        classList.put(43, "Shilien Elder");
        classList.put(44, "Orc Fighter");
        classList.put(45, "Orc Raider");
        classList.put(46, "Destroyer");
        classList.put(47, "Orc Monk");
        classList.put(48, "Tyrant");
        classList.put(49, "Orc Mage");
        classList.put(50, "Orc Shaman");
        classList.put(51, "Overlord");
        classList.put(52, "Warcryer");
        classList.put(53, "Dwarven Fighter");
        classList.put(54, "Scavenger");
        classList.put(55, "Bounty Hunter");
        classList.put(56, "Artisan");
        classList.put(57, "Warsmith");
        classList.put(88, "Duelist");
        classList.put(89, "Dreadnought");
        classList.put(90, "Phoenix Knight");
        classList.put(91, "Hell Knight");
        classList.put(92, "Sagittarius");
        classList.put(93, "Adventurer");
        classList.put(94, "Archmage");
        classList.put(95, "Soultaker");
        classList.put(96, "Arcana Lord");
        classList.put(97, "Cardinal");
        classList.put(98, "Hierophant");
        classList.put(99, "Evas Templar");
        classList.put(100, "Sword Muse");
        classList.put(101, "Wind Rider");
        classList.put(102, "Moonlight Sentinel");
        classList.put(103, "Mystic Muse");
        classList.put(104, "Elemental Master");
        classList.put(105, "Evas Saint");
        classList.put(106, "Shillien Templar");
        classList.put(107, "Spectral Dancer");
        classList.put(108, "Ghost Hunter");
        classList.put(109, "Ghost Sentinel");
        classList.put(110, "Storm Screamer");
        classList.put(111, "Spectral Master");
        classList.put(112, "Shillien Saint");
        classList.put(113, "Titan");
        classList.put(114, "Grand Khavatari");
        classList.put(115, "Dominator");
        classList.put(116, "Doomcryer");
        classList.put(117, "Fortune Seeker");
        classList.put(118, "Maestro");

        final String name = classList.get(classid);
        return name != null ? name : "Unknown";
    }

    static String getPlayerRunTime(long secs) {
        if (secs >= 86400) {
            return (secs / 86400) + " Days " + ((secs % 86400) / 3600) + " Hours";
        }
		return (secs / 3600) + " Hours " + ((secs % 3600) / 60) + " Mins";
    }

    static String getAdenas(long adena) {
        if (adena >= 1_000_000_000) {
            return (adena / 1_000_000_000) + " Billion " + ((adena % 1_000_000_000) / 1_000_000) + " Million";
        }
		return (adena / 1_000_000) + " Million " + ((adena % 1_000_000) / 1_000) + " K";
    }
}