package Base.dailyreward;

import com.l2jmega.Config;
import com.l2jmega.L2DatabaseFactory;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.commons.concurrent.ThreadPool;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class DailyRewardManager
{
    private static final Logger LOGGER = Logger.getLogger(DailyRewardManager.class.getName());
    
    private static final String UPDATE_FULL =
    	"UPDATE daily_reward_history SET last_claim_date=?, current_day=?, online_seconds=?, last_login=NOW(), hwid=?, ip=? WHERE account=?";

    private static final String UPDATE_NO_HWID =
    	"UPDATE daily_reward_history SET last_claim_date=?, current_day=?, online_seconds=?, last_login=NOW() WHERE account=?";

    	private static final String SELECT =
    	"SELECT last_claim_date, current_day, online_seconds, hwid, ip FROM daily_reward_history WHERE account=?";

        private static final String SELECT_LATEST_HWID =
        "SELECT hwid, MAX(last_claim_date) AS last_claim_date FROM daily_reward_history WHERE hwid IS NOT NULL AND hwid <> '' GROUP BY hwid";

        private static final String SELECT_LATEST_IP =
        "SELECT ip, MAX(last_claim_date) AS last_claim_date FROM daily_reward_history WHERE ip IS NOT NULL AND ip <> '' GROUP BY ip";

    	private static final String INSERT =
    	"INSERT INTO daily_reward_history (account,last_claim_date,current_day,online_seconds,last_login,hwid,ip) VALUES (?,?,?,?,NOW(),?,?)";

    

    private final Map<Integer, Map<Integer, Integer>> rewards = new TreeMap<>();
    private final List<Integer> dayOrder = new ArrayList<>();

    private final Map<String, LocalDate> lastClaim = new ConcurrentHashMap<>();
    private final Map<String, Integer> currentDay = new ConcurrentHashMap<>();
    private final Map<String, Long> onlineSeconds = new ConcurrentHashMap<>();
    private final Map<String, Long> lastOnlineTick = new ConcurrentHashMap<>();
    private final Set<String> loadedAccounts = ConcurrentHashMap.newKeySet();

    private final Map<String, LocalDate> hwidLastClaim = new ConcurrentHashMap<>();
    private final Map<String, LocalDate> ipLastClaim   = new ConcurrentHashMap<>();

    private static final DailyRewardManager INSTANCE = new DailyRewardManager();
    public static DailyRewardManager getInstance() { return INSTANCE; }

    private DailyRewardManager()
    {
        if (!Config.ENABLE_REWARD_DAILY)
            return;

        loadRewards();
        loadClaimRestrictions();

        ThreadPool.scheduleAtFixedRate(this::autosaveAll, 300000, 300000);

        LOGGER.info("Loaded " + rewards.size() + " reward days.");
        LOGGER.info("Loaded " + hwidLastClaim.size() + " HWID reward records.");
        LOGGER.info("Loaded " + ipLastClaim.size() + " IP reward records.");
        LOGGER.info("Daily Reward autosave enabled: every 5 minutes.");
        LOGGER.info("Daily Reward required online time: " + Config.DailyRewardOnlineMinutes + " minutes.");
    }

    private static String getIdentifier(Player player)
    {
        return player.getAccountName();
    }

    // ================= XML =================

    private void loadRewards()
    {
        try
        {
            File file = new File("data/xml/custom/DailyRewards.xml");
            if (!file.exists())
                return;

            Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(file);

            NodeList days = doc.getElementsByTagName("reward");
            for (int i = 0; i < days.getLength(); i++)
            {
                Element day = (Element) days.item(i);
                int dayId = Integer.parseInt(day.getAttribute("Day"));

                Map<Integer, Integer> items = new HashMap<>();
                NodeList itemsNode = day.getElementsByTagName("item");
                for (int j = 0; j < itemsNode.getLength(); j++)
                {
                    Element item = (Element) itemsNode.item(j);
                    items.put(
                        Integer.parseInt(item.getAttribute("itemId")),
                        Integer.parseInt(item.getAttribute("amount"))
                    );
                }
                rewards.put(dayId, items);
            }

            dayOrder.addAll(rewards.keySet());
            Collections.sort(dayOrder);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    // ================= DATABASE =================

    private void loadClaimRestrictions()
    {
        loadLatestRestrictionMap(SELECT_LATEST_HWID, "hwid", hwidLastClaim);
        loadLatestRestrictionMap(SELECT_LATEST_IP, "ip", ipLastClaim);
    }

    private static void loadLatestRestrictionMap(String query, String column, Map<String, LocalDate> target)
    {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(query);
             ResultSet rs = ps.executeQuery())
        {
            while (rs.next())
            {
                final String identifier = rs.getString(column);
                final String date = rs.getString("last_claim_date");
                if (identifier != null && !identifier.isEmpty() && date != null)
                    target.put(identifier, LocalDate.parse(date));
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void ensureAccountStateLoaded(String id)
    {
        if (id == null || id.isEmpty() || loadedAccounts.contains(id))
            return;

        synchronized (loadedAccounts)
        {
            if (loadedAccounts.contains(id))
                return;

            try (Connection con = L2DatabaseFactory.getInstance().getConnection();
                 PreparedStatement ps = con.prepareStatement(SELECT))
            {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery())
                {
                    if (rs.next())
                    {
                        final String date = rs.getString("last_claim_date");
                        if (date != null)
                            lastClaim.put(id, LocalDate.parse(date));

                        currentDay.put(id, rs.getInt("current_day"));
                        onlineSeconds.put(id, rs.getLong("online_seconds"));
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }

            loadedAccounts.add(id);
        }
    }

    private void releaseAccountStateIfOffline(String id, Player player)
    {
        if (id == null || id.isEmpty())
            return;

        for (Player worldPlayer : World.getInstance().getPlayers())
        {
            if (worldPlayer == null || worldPlayer == player || !worldPlayer.isOnline())
                continue;

            final String accountName = worldPlayer.getAccountName();
            if (accountName != null && accountName.equals(id))
                return;
        }

        loadedAccounts.remove(id);
        lastClaim.remove(id);
        currentDay.remove(id);
        onlineSeconds.remove(id);
    }

    // ================= ONLINE =================

    public void onPlayerTick(Player player)
    {
        if (player == null || player.isInStoreMode() || player.isDead())
            return;

        if (player.isPhantom())
            return;

        String id = getIdentifier(player);
        if (id == null || id.isEmpty())
            return;

        ensureAccountStateLoaded(id);

        long now = System.currentTimeMillis();
        long last = lastOnlineTick.getOrDefault(id, now);

        long diff = (now - last) / 1000;
        if (diff > 0 && diff < 120)
            onlineSeconds.put(id, onlineSeconds.getOrDefault(id, 0L) + diff);

        lastOnlineTick.put(id, now);
    }

    public boolean hasEnoughOnline(Player player)
    {
        ensureAccountStateLoaded(getIdentifier(player));
        return onlineSeconds.getOrDefault(getIdentifier(player), 0L)
                >= (Config.DailyRewardOnlineMinutes * 60);
    }

    public long getOnlineSeconds(Player player)
    {
        ensureAccountStateLoaded(getIdentifier(player));
        return onlineSeconds.getOrDefault(getIdentifier(player), 0L);
    }

    // ================= RESET =================

    private static boolean canClaimByDate(LocalDate last)
    {
        int resetHour = Integer.parseInt(Config.DAILY_REWARD_RESET_TIME[0]);
        int resetMin  = Integer.parseInt(Config.DAILY_REWARD_RESET_TIME[1]);

        Calendar now = Calendar.getInstance();
        Calendar reset = Calendar.getInstance();
        reset.set(Calendar.HOUR_OF_DAY, resetHour);
        reset.set(Calendar.MINUTE, resetMin);
        reset.set(Calendar.SECOND, 0);

        if (now.before(reset))
            reset.add(Calendar.DAY_OF_MONTH, -1);

        return last.isBefore(
            LocalDate.of(
                reset.get(Calendar.YEAR),
                reset.get(Calendar.MONTH) + 1,
                reset.get(Calendar.DAY_OF_MONTH)
            )
        );
    }

    public boolean canClaim(Player player)
    {
        final String id = getIdentifier(player);
        ensureAccountStateLoaded(id);
        LocalDate last = lastClaim.get(id);
        if (last == null)
            return true;

        return canClaimByDate(last);
    }

    // ================= IP + HWID =================

    public boolean isHwidBlocked(Player player)
    {
        String hwid = player.getClient().getHWID();
        if (hwid == null || hwid.isEmpty())
            return false;

        LocalDate last = hwidLastClaim.get(hwid);
        return last != null && !canClaimByDate(last);
    }

    public boolean isIpBlocked(Player player)
    {
        if (player.getClient() == null ||
            player.getClient().getConnection() == null ||
            player.getClient().getConnection().getInetAddress() == null)
            return false;

        String ip = player.getClient().getConnection()
                          .getInetAddress().getHostAddress();

        LocalDate last = ipLastClaim.get(ip);
        return last != null && !canClaimByDate(last);
    }


    // ================= MULTI WINDOW =================

    public boolean isAnotherCharOnlineAndClaimed(Player player)
    {
        if (!canClaim(player))
            return true; // already claimed on this account

        String acc = player.getAccountName();
        if (acc == null || acc.isEmpty())
            return false;

        for (Player p : World.getInstance().getPlayers())
        {
            String pAcc = p != null ? p.getAccountName() : null;
            if (p != null && p != player && pAcc != null && pAcc.equals(acc))
            {
                // If that character already claimed today
                LocalDate last = lastClaim.get(acc);
                if (last != null && !canClaimByDate(last))
                    return true;
            }
        }
        return false;
    }

    // ================= CLAIM =================

    public synchronized boolean claim(Player player)
    {
        if (player == null || player.isInStoreMode())
            return false;

        if (!canClaim(player))
            return false;

        if (!hasEnoughOnline(player))
            return false;

        if (isAnotherCharOnlineAndClaimed(player))
            return false;

        if (isHwidBlocked(player))
            return false;

        if (isIpBlocked(player))
            return false;

        String id = getIdentifier(player);
        ensureAccountStateLoaded(id);
        int day = currentDay.getOrDefault(id, dayOrder.get(0));

        Map<Integer, Integer> reward = rewards.get(day);
        if (reward == null)
            return false;

        for (Map.Entry<Integer, Integer> e : reward.entrySet())
            player.getInventory().addItem(
                "DailyReward", e.getKey(), e.getValue(), player, null);

        int index = dayOrder.indexOf(day);
        int nextDay = (index + 1 >= dayOrder.size())
                ? dayOrder.get(0)
                : dayOrder.get(index + 1);

        LocalDate today = LocalDate.now();

        lastClaim.put(id, today);
        currentDay.put(id, nextDay);
        onlineSeconds.put(id, 0L);

        String hwid = player.getClient().getHWID();
        String ip   = player.getClient().getConnection()
                          .getInetAddress().getHostAddress();

        if (hwid != null)
            hwidLastClaim.put(hwid, today);

        if (ip != null)
            ipLastClaim.put(ip, today);

        save(id, today, nextDay, 0L, hwid, ip);
        return true;
    }

    // ================= AUTOSAVE =================

    private void autosaveAll()
    {
        for (String id : new ArrayList<>(lastOnlineTick.keySet()))
        {
            save(id,
                 lastClaim.get(id),
                 currentDay.getOrDefault(id, dayOrder.get(0)),
                 onlineSeconds.getOrDefault(id, 0L),
                 null,
                 null);
        }
    }

    // ================= SAVE =================

    private static void save(String id, LocalDate date, int day, long seconds, String hwid, String ip)
    {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT))
        {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    PreparedStatement ps2;

                    if (hwid == null && ip == null)
                    {
                        ps2 = con.prepareStatement(UPDATE_NO_HWID);
                        ps2.setString(1, date != null ? date.toString() : null);
                        ps2.setInt(2, day);
                        ps2.setLong(3, seconds);
                        ps2.setString(4, id);
                    }
                    else
                    {
                        ps2 = con.prepareStatement(UPDATE_FULL);
                        ps2.setString(1, date != null ? date.toString() : null);
                        ps2.setInt(2, day);
                        ps2.setLong(3, seconds);
                        ps2.setString(4, hwid);
                        ps2.setString(5, ip);
                        ps2.setString(6, id);
                    }

                    ps2.execute();
                    ps2.close();
                }
                else
                {
                    try (PreparedStatement ps2 = con.prepareStatement(INSERT))
                    {
                        ps2.setString(1, id);
                        ps2.setString(2, date != null ? date.toString() : null);
                        ps2.setInt(3, day);
                        ps2.setLong(4, seconds);
                        ps2.setString(5, hwid);
                        ps2.setString(6, ip);
                        ps2.execute();
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public void onLogin(Player player)
    {
        if (player == null || player.getClient() == null)
            return;

        String id = getIdentifier(player);
        ensureAccountStateLoaded(id);

        // reset tick timer
        lastOnlineTick.put(id, System.currentTimeMillis());

        String hwid = player.getClient().getHWID();
        String ip = null;

        if (player.getClient().getConnection() != null &&
            player.getClient().getConnection().getInetAddress() != null)
        {
            ip = player.getClient().getConnection()
                       .getInetAddress().getHostAddress();
        }

        // update memory maps
        LocalDate last = lastClaim.get(id);

        if (last != null)
        {
            if (hwid != null && !hwid.isEmpty())
            {
                LocalDate old = hwidLastClaim.get(hwid);
                if (old == null || last.isAfter(old))
                    hwidLastClaim.put(hwid, last);
            }

            if (ip != null)
            {
                LocalDate old = ipLastClaim.get(ip);
                if (old == null || last.isAfter(old))
                    ipLastClaim.put(ip, last);
            }
        }

        // 🔥 THIS IS WHAT YOU WERE MISSING
        save(id,
             lastClaim.get(id),
             currentDay.getOrDefault(id, dayOrder.get(0)),
             onlineSeconds.getOrDefault(id, 0L),
             hwid,
             ip);
    }

    
    
    // ================= GETTERS + LOGOUT =================

    public int getCurrentDay(Player player)
    {
        ensureAccountStateLoaded(getIdentifier(player));
        return currentDay.getOrDefault(getIdentifier(player), dayOrder.get(0));
    }

    public int getTotalDays()
    {
        return dayOrder.size();
    }

    public Map<Integer, Map<Integer, Integer>> getAllRewards()
    {
        return rewards;
    }

    public void onLogout(Player player)
    {
        if (player == null)
            return;

        String id = getIdentifier(player);
        ensureAccountStateLoaded(id);
        save(id,
             lastClaim.get(id),
             currentDay.getOrDefault(id, dayOrder.get(0)),
             onlineSeconds.getOrDefault(id, 0L),
             null,
             null);

        lastOnlineTick.remove(id);
        releaseAccountStateIfOffline(id, player);
    }
}
