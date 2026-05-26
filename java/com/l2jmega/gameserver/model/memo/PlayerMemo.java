package com.l2jmega.gameserver.model.memo;

import com.l2jmega.L2DatabaseFactory;
import com.l2jmega.gameserver.model.actor.instance.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.l2jmega.commons.util.Mysql;

public class PlayerMemo extends AbstractMemo
{
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = Logger.getLogger(PlayerMemo.class.getName());

    // USE dungeon table because all your other methods use it
    private static final String SELECT_QUERY = "SELECT * FROM character_memo_dungeon WHERE obj_id = ?";
    private static final String DELETE_QUERY = "DELETE FROM character_memo_dungeon WHERE obj_id = ?";
    private static final String INSERT_QUERY = "INSERT INTO character_memo_dungeon (obj_id, name, value, expire_time) VALUES (?, ?, ?, ?)";

    // legacy table (used only for migration)
    private static final String LEGACY_SELECT = "SELECT * FROM character_memo WHERE charId = ?";

    private final int _objectId;

    public PlayerMemo(int objectId)
    {
        _objectId = objectId;
        restoreMe();
    }

    // ====== FIXED getVarObject ======
    public static PlayerVar getVarObject(Player player, String name)
    {
        if (player.getMemos() == null)
            return null;

        Object obj = player.getMemos().get(name);

        if (obj == null)
            return null;

        // If already PlayerVar
        if (obj instanceof PlayerVar)
            return (PlayerVar) obj;

        // If old data stored as String -> convert it
        if (obj instanceof String)
        {
            PlayerVar pv = new PlayerVar(player, name, (String) obj, 0);
            player.getMemos().put(name, pv);
            return pv;
        }

        return null;
    }

    public static void changeValue(Player player, String name, String value)
    {
        if (!player.getMemos().containsKey(name))
        {
            player.sendMessage("Variable does not exist...");
            return;
        }

        PlayerVar pv = getVarObject(player, name);
        if (pv == null)
            return;

        pv.setValue(value);

        Mysql.set("UPDATE character_memo_dungeon SET value=? WHERE obj_id=? AND name=?", value, player.getObjectId(), name);
    }

    public static void setVar(Player player, String name, String value, long expirationTime)
    {
        if (player.getMemos().containsKey(name))
            getVarObject(player, name).stopExpireTask();

        player.getMemos().put(name, new PlayerVar(player, name, value, expirationTime));

        Mysql.set("REPLACE INTO character_memo_dungeon (obj_id, name, value, expire_time) VALUES (?,?,?,?)",
                player.getObjectId(), name, value, expirationTime);
    }

    public static void setVar(Player player, String name, int value, long expirationTime)
    {
        setVar(player, name, String.valueOf(value), expirationTime);
    }

    public static void setVar(Player player, String name, long value, long expirationTime)
    {
        setVar(player, name, String.valueOf(value), expirationTime);
    }

    public static long getVarTimeToExpire(Player player, String name)
    {
        try
        {
            return getVarObject(player, name).getTimeToExpire();
        }
        catch (NullPointerException npe)
        {
        }

        return 0;
    }

    public static void unsetVar(Player player, String name)
    {
        if (name == null)
            return;

        if (player == null)
            return;

        PlayerVar pv = getVarObject(player, name);

        if (pv != null)
        {
            if (name.contains("delete_temp_item"))
                pv.getOwner().deleteTempItem(Integer.parseInt(pv.getValue()));
            else if (name.contains("solo_hero"))
            {
                pv.getOwner().broadcastCharInfo();
                pv.getOwner().broadcastUserInfo();
            }

            Mysql.set("DELETE FROM character_memo_dungeon WHERE obj_id=? AND name=? LIMIT 1", pv.getOwner().getObjectId(), name);

            pv.stopExpireTask();
        }
    }

    public static void deleteExpiredVar(Player player, String name, String value)
    {
        if (name == null)
            return;

        if (name.contains("delete_temp_item"))
            player.deleteTempItem(Integer.parseInt(value));

        Mysql.set("DELETE FROM character_memo_dungeon WHERE obj_id=? AND name=? LIMIT 1", player.getObjectId(), name);
    }

    public static String getVar(Player player, String name)
    {
        PlayerVar pv = getVarObject(player, name);

        if (pv == null)
            return null;

        return pv.getValue();
    }

    public static boolean getVarB(Player player, String name, boolean defaultVal)
    {
        PlayerVar pv = getVarObject(player, name);

        if (pv == null)
            return defaultVal;

        return pv.getValueBoolean();
    }

    public static boolean getVarB(Player player, String name)
    {
        return getVarB(player, name, false);
    }

    public long getVarLong(Player player, String name)
    {
        return getVarLong(player, name, 0L);
    }

    public long getVarLong(Player player, String name, long defaultVal)
    {
        long result = defaultVal;
        String var = getVar(player, name);
        if (var != null)
            result = Long.parseLong(var);

        return result;
    }

    public static int getVarInt(Player player, String name)
    {
        return getVarInt(player, name, 0);
    }

    public static int getVarInt(Player player, String name, int defaultVal)
    {
        int result = defaultVal;
        String var = getVar(player, name);
        if (var != null)
        {
            if (var.equalsIgnoreCase("true"))
                result = 1;
            else if (var.equalsIgnoreCase("false"))
                result = 0;
            else
                result = Integer.parseInt(var);
        }
        return result;
    }

    // ========= IMPORTANT: FIXED restoreMe ==========
    @Override
    public boolean restoreMe()
    {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection())
        {
            PreparedStatement st = con.prepareStatement(SELECT_QUERY);
            st.setInt(1, _objectId);

            ResultSet rset = st.executeQuery();

            while (rset.next())
            {
                // IMPORTANT: store as String to avoid ClassCastException
                set(rset.getString("name"), rset.getString("value"));
            }

            rset.close();
            st.close();
        }
        catch (SQLException e)
        {
            LOG.log(Level.SEVERE, "Couldn't restore variables for player id: " + _objectId, e);
            return false;
        }
        finally
        {
            compareAndSetChanges(true, false);
        }

        // Legacy migration (if you still have old memo table)
        migrateLegacy();

        return true;
    }

    private void migrateLegacy()
    {
        try (Connection con = L2DatabaseFactory.getInstance().getConnection())
        {
            PreparedStatement st = con.prepareStatement(LEGACY_SELECT);
            st.setInt(1, _objectId);

            ResultSet rset = st.executeQuery();

            while (rset.next())
            {
                // Only migrate if not already exists
                String var = rset.getString("var");
                if (!containsKey(var))
                {
                    set(var, rset.getString("val"));
                }
            }

            rset.close();
            st.close();
        }
        catch (Exception e)
        {
            // ignore
        }
    }

    @Override
    public boolean storeMe()
    {
        if (!hasChanges())
            return false;

        try (Connection con = L2DatabaseFactory.getInstance().getConnection())
        {
            PreparedStatement st = con.prepareStatement(DELETE_QUERY);
            st.setInt(1, _objectId);
            st.execute();
            st.close();

            st = con.prepareStatement(INSERT_QUERY);
            st.setInt(1, _objectId);

            for (Entry<String, Object> entry : entrySet())
            {
                st.setString(2, entry.getKey());
                st.setString(3, String.valueOf(entry.getValue()));
                st.setLong(4, 0); // expiration time not stored here
                st.addBatch();
            }

            st.executeBatch();
            st.close();
        }
        catch (SQLException e)
        {
            LOG.log(Level.SEVERE, "Couldn't update variables for player id: " + _objectId, e);
            return false;
        }
        finally
        {
            compareAndSetChanges(true, false);
        }
        return true;
    }
}
