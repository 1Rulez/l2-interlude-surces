package phantom;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import com.l2jmega.L2DatabaseFactory;

/**
 * Loads hero character IDs from the database for use by the fake player system.
 * 
 * Sources:
 * <ul>
 *   <li>{@code heroes} table - Olympiad heroes (field: {@code char_id})</li>
 *   <li>{@code character_hero} table - Donation-based heroes (field: {@code objectId})</li>
 *   <li>{@code characters_hero_eterno} table - Eternal heroes (field: {@code obj_Id})</li>
 * </ul>
 */
public class FakeHeroLoader
{
	private static final Logger _log = Logger.getLogger(FakeHeroLoader.class.getName());
	
	private static final String SELECT_HEROES = "SELECT char_id FROM heroes WHERE played = 1";
	private static final String SELECT_CHARACTER_HERO = "SELECT objectId FROM character_hero";
	private static final String SELECT_ETERNAL_HERO = "SELECT obj_Id FROM characters_hero_eterno WHERE hero = 1";
	
	private static volatile List<Integer> _heroIds = Collections.emptyList();
	
	/**
	 * Load all hero character IDs from the database.
	 * Should be called once at server startup.
	 */
	public static void load()
	{
		final List<Integer> ids = new ArrayList<>();
		
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			// Load from heroes table (Olympiad heroes)
			try (PreparedStatement ps = con.prepareStatement(SELECT_HEROES);
				ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
					ids.add(rs.getInt("char_id"));
			}
			
			// Load from character_hero table (donation heroes)
			try (PreparedStatement ps = con.prepareStatement(SELECT_CHARACTER_HERO);
				ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final int id = rs.getInt("objectId");
					if (!ids.contains(id))
						ids.add(id);
				}
			}
			
			// Load from characters_hero_eterno table (eternal heroes)
			try (PreparedStatement ps = con.prepareStatement(SELECT_ETERNAL_HERO);
				ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final int id = rs.getInt("obj_Id");
					if (!ids.contains(id))
						ids.add(id);
				}
			}
			
			_heroIds = Collections.unmodifiableList(ids);
			_log.info("FakeHeroLoader: Loaded " + ids.size() + " hero IDs from database.");
		}
		catch (Exception e)
		{
			_log.warning("FakeHeroLoader: Failed to load hero IDs: " + e.getMessage());
		}
	}
	
	/**
	 * Reload hero IDs from the database (e.g. after a new hero is granted).
	 */
	public static void reload()
	{
		load();
	}
	
	/**
	 * Check if a character ID belongs to a hero.
	 * @param objectId the character's object ID
	 * @return {@code true} if the character is a hero
	 */
	public static boolean isHero(int objectId)
	{
		return _heroIds.contains(objectId);
	}
	
	/**
	 * Get the list of all hero character IDs.
	 * @return unmodifiable list of hero object IDs
	 */
	public static List<Integer> getHeroIds()
	{
		return _heroIds;
	}
	
	/**
	 * Check if there are any hero IDs loaded.
	 * @return {@code true} if at least one hero ID is available
	 */
	public static boolean hasHeroes()
	{
		return !_heroIds.isEmpty();
	}
}