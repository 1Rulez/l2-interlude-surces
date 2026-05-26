package Base.BonusXp;

import com.l2jmega.gameserver.templates.StatsSet;

public class Rune
{
	private final String _name;
	private final int _id;
	private final double _xpRate;
	private final double _spRate;

	private final double _itemDropRate;

	private final int _time;

	public Rune(StatsSet set)
	{
		_id = set.getInteger("id", 0);
		_name = set.getString("name");
		_xpRate = set.getDouble("xpRate", 1.0);
		_spRate = set.getDouble("spRate", 1.0);
		_itemDropRate = set.getDouble("itemDropRate", 1.0);
		_time = set.getInteger("time", 1);
	}

	public String getName()
	{
		return _name;
	}

	public int getId()
	{
		return _id;
	}

	public double getXPRate()
	{
		return _xpRate;
	}

	public double getSPRate()
	{
		return _spRate;
	}

	public double getItemDropRate()
	{
		return _itemDropRate;
	}

	public int getTime()
	{
		return _time;
	}

}