package Base.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.logging.Logger;

import com.l2jmega.L2DatabaseFactory;
import Base.auction.AuctionItem;

/**
 * @author L2Fol
 *
 */
public class AuctionTable
{
    private static Logger log = Logger.getLogger(AuctionTable.class.getName());

    private ArrayList<AuctionItem> items;
    private int maxId;

    public static AuctionTable getInstance()
    {
        return SingletonHolder._instance;
    }

    protected AuctionTable()
    {
        items = new ArrayList<>();
        maxId = 0;

        load();
    }

    private void load()
    {
        Connection con = null;
        try
        {
            con = L2DatabaseFactory.getInstance().getConnection();
            PreparedStatement stm = con.prepareStatement("SELECT * FROM auction_table");
            ResultSet rset = stm.executeQuery();

            while (rset.next())
            {
                int auctionId = rset.getInt("auctionid");
                int ownerId = rset.getInt("ownerid");
                int itemId = rset.getInt("itemid");
                int count = rset.getInt("count");
                int enchant = rset.getInt("enchant");
                int costId = rset.getInt("costid");
                int costCount = rset.getInt("costcount");

                items.add(new AuctionItem(auctionId, ownerId, itemId, count, enchant, costId, costCount));

                if (auctionId > maxId)
                    maxId = auctionId;
            }

            rset.close();
            stm.close();
        }
        catch (Exception e)
        {
            log.warning("AuctionTable: Error loading auction_table: " + e.getMessage());
            e.printStackTrace();
        }
        finally
        {
            try
            {
                if (con != null)
                    con.close();
            }
            catch (Exception e)
            {
                log.warning("AuctionTable: Error closing DB connection: " + e.getMessage());
                e.printStackTrace();
            }
        }

        log.info("AuctionTable: Loaded "+items.size()+" auction items. MaxId=" + maxId);
    }

    public void addItem(AuctionItem item)
    {
        items.add(item);
        log.info("AuctionTable: Adding auction item ID=" + item.getAuctionId());

        Connection con = null;
        try
        {
            con = L2DatabaseFactory.getInstance().getConnection();
            PreparedStatement stm = con.prepareStatement("INSERT INTO auction_table VALUES (?,?,?,?,?,?,?)");
            stm.setInt(1, item.getAuctionId());
            stm.setInt(2, item.getOwnerId());
            stm.setInt(3, item.getItemId());
            stm.setInt(4, item.getCount());
            stm.setInt(5, item.getEnchant());
            stm.setInt(6, item.getCostId());
            stm.setInt(7, item.getCostCount());

            stm.execute();
            stm.close();

            log.info("AuctionTable: Auction item ID=" + item.getAuctionId() + " inserted into database.");
        }
        catch (Exception e)
        {
            log.warning("AuctionTable: Error inserting auction item ID=" + item.getAuctionId() + ": " + e.getMessage());
            e.printStackTrace();
        }
        finally
        {
            try
            {
                if (con != null)
                    con.close();
            }
            catch (Exception e)
            {
                log.warning("AuctionTable: Error closing DB connection: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void deleteItem(AuctionItem item)
    {
        items.remove(item);
        log.info("AuctionTable: Deleting auction item ID=" + item.getAuctionId());

        Connection con = null;
        try
        {
            con = L2DatabaseFactory.getInstance().getConnection();
            PreparedStatement stm = con.prepareStatement("DELETE FROM auction_table WHERE auctionid=?");
            stm.setInt(1, item.getAuctionId());

            stm.execute();
            stm.close();

            log.info("AuctionTable: Auction item ID=" + item.getAuctionId() + " deleted from database.");
        }
        catch (Exception e)
        {
            log.warning("AuctionTable: Error deleting auction item ID=" + item.getAuctionId() + ": " + e.getMessage());
            e.printStackTrace();
        }
        finally
        {
            try
            {
                if (con != null)
                    con.close();
            }
            catch (Exception e)
            {
                log.warning("AuctionTable: Error closing DB connection: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public AuctionItem getItem(int auctionId)
    {
        AuctionItem ret = null;

        for (AuctionItem item : items)
        {
            if (item.getAuctionId() == auctionId)
            {
                ret = item;
                break;
            }
        }

        return ret;
    }

    public ArrayList<AuctionItem> getItems()
    {
        return items;
    }

    public int getNextAuctionId()
    {
        maxId++;
        return maxId;
    }

    private static class SingletonHolder
    {
        protected static final AuctionTable _instance = new AuctionTable();
    }
}
