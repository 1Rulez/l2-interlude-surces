package Base.BonusXp;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.l2jmega.commons.data.xml.XMLDocument;
import com.l2jmega.gameserver.templates.StatsSet;

/**
 * @author l2fol
 */
public class RuneData extends XMLDocument
{

	private Map<Integer, Rune> runeData = new HashMap<>();

	public static RuneData getInstance()
	{
		return SingleTonHolder._instance;
	}

	private static class SingleTonHolder
	{
		protected static final RuneData _instance = new RuneData();
	}

	protected RuneData()
	{
		load();
	}

	public void reload()
	{
		runeData.clear();
		load();
	}

	@Override
	protected void load()
	{
		loadDocument("./data/xml/RuneData.xml");
	    LOGGER.info("Loaded " + runeData.size() + " Runes.");

	}

	@Override
	protected void parseDocument(Document doc, File f)
	{
		// StatsSet used to feed informations. Cleaned on every entry.
		final StatsSet set = new StatsSet();

		// First element is never read.
		final Node n = doc.getFirstChild();

		for (Node o = n.getFirstChild(); o != null; o = o.getNextSibling())
		{
			// Parse and feed access levels.
			if ("rune".equalsIgnoreCase(o.getNodeName()))
			{
				// Parse and feed content.
				parseAndFeed(o.getAttributes(), set);

				Rune rune = new Rune(set);

				runeData.put(set.getInteger("id"), rune);

				set.clear();
			}
		}
	}

	/**
	 * @param id 
	 * @return the {@link RuneData} based on its level.
	 */
	public Rune getRune(int id)
	{

		return runeData.get(id);
	}

	public List<Rune> getAllRunes()
	{
		return new ArrayList<>(runeData.values());

	}

	public Map<Integer, Rune> getRunes()
	{
		return runeData;
	}

	public void setRunes(Map<Integer, Rune> runeData)
	{
		this.runeData = runeData;
	}

}