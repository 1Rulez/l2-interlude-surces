package Base.auction;

/**
 * @author L2Fol
 *
 */
public class AuctionItem
{
	private int auctionId;
	private int ownerId;
	private int itemId;
	private int count;
	private int enchant;
	private int costId;
	private int costCount;
	
	public AuctionItem(int auctionId, int ownerId, int itemId, int count, int enchant, int costId, int costCount)
	{
		this.auctionId = auctionId;
		this.ownerId = ownerId;
		this.itemId = itemId;
		this.count = count;
		this.enchant = enchant;
		this.costId = costId;
		this.costCount = costCount;
	}
	
	public int getAuctionId()
	{
		return auctionId;
	}
	
	public int getOwnerId()
	{
		return ownerId;
	}
	
	public int getItemId()
	{
		return itemId;
	}
	
	public int getCount()
	{
		return count;
	}
	
	public int getEnchant()
	{
		return enchant;
	}
	
	public int getCostId()
	{
		return costId;
	}
	
	public int getCostCount()
	{
		return costCount;
	}
}
