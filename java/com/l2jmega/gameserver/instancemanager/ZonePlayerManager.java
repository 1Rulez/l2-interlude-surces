package com.l2jmega.gameserver.instancemanager;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.zone.ZoneId;

/**
 * Optimized zone player management for performance-critical operations
 * Replaces expensive World.getInstance().getPlayers() loops with cached zone collections
 * 
 * @author Optimized by AI Assistant
 */
public class ZonePlayerManager
{
	private static final ZonePlayerManager _instance = new ZonePlayerManager();
	
	// Cache for players in specific zones
	private final Map<ZoneId, Set<Player>> _zonePlayerCache = new ConcurrentHashMap<>();
	
	// HWID index for fast lookups
	private final Map<String, Set<Player>> _hwidIndex = new ConcurrentHashMap<>();
	
	// Performance metrics
	private volatile int _cacheHits = 0;
	private volatile int _cacheMisses = 0;
	
	private ZonePlayerManager()
	{
		// Initialize zone collections
		for (ZoneId zoneId : ZoneId.values())
		{
			_zonePlayerCache.put(zoneId, ConcurrentHashMap.newKeySet());
		}
	}
	
	public static ZonePlayerManager getInstance()
	{
		return _instance;
	}
	
	/**
	 * Get players in specific zone (cached)
	 * Much faster than World.getInstance().getPlayers() filtering
	 * @param zoneId the zone ID to get players from
	 * @return Set of players in the zone
	 */
	public Set<Player> getPlayersInZone(ZoneId zoneId)
	{
		long startTime = System.nanoTime();
		
		Set<Player> players = _zonePlayerCache.get(zoneId);
		if (players == null)
		{
			_cacheMisses++;
			PerformanceMetricsManager.getInstance().recordZoneCacheMiss();
			return Collections.emptySet();
		}
		
		_cacheHits++;
		PerformanceMetricsManager.getInstance().recordZoneCacheHit();
		
		long executionTime = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
		PerformanceMetricsManager.getInstance().recordMethodExecution("getPlayersInZone", executionTime);
		
		return Collections.unmodifiableSet(players);
	}
	
	/**
	 * Get players in multiple zones
	 * @param zoneIds the zone IDs to get players from
	 * @return Set of players in the zones
	 */
	public Set<Player> getPlayersInZones(ZoneId... zoneIds)
	{
		Set<Player> result = ConcurrentHashMap.newKeySet();
		
		for (ZoneId zoneId : zoneIds)
		{
			Set<Player> zonePlayers = _zonePlayerCache.get(zoneId);
			if (zonePlayers != null)
			{
				result.addAll(zonePlayers);
			}
		}
		
		return result;
	}
	
	/**
	 * Get players by HWID (indexed lookup)
	 * @param hwid the HWID to search for
	 * @return Set of players with the HWID
	 */
	public Set<Player> getPlayersByHWID(String hwid)
	{
		Set<Player> players = _hwidIndex.get(hwid);
		return players != null ? Collections.unmodifiableSet(players) : Collections.emptySet();
	}
	
	/**
	 * Check if HWID has players in specific zones
	 * @param hwid the HWID to check
	 * @param zoneIds the zone IDs to check
	 * @return true if HWID has players in any of the zones
	 */
	public boolean hasHWIDInZones(String hwid, ZoneId... zoneIds)
	{
		Set<Player> hwidPlayers = _hwidIndex.get(hwid);
		if (hwidPlayers == null || hwidPlayers.isEmpty())
			return false;
		
		for (Player player : hwidPlayers)
		{
			for (ZoneId zoneId : zoneIds)
			{
				if (_zonePlayerCache.get(zoneId).contains(player))
					return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Count players with same HWID in specific zones
	 * @param hwid the HWID to count
	 * @param zoneIds the zone IDs to count in
	 * @return number of players with the HWID in the zones
	 */
	public int countHWIDInZones(String hwid, ZoneId... zoneIds)
	{
		long startTime = System.nanoTime();
		
		Set<Player> hwidPlayers = _hwidIndex.get(hwid);
		if (hwidPlayers == null)
		{
			long executionTime = (System.nanoTime() - startTime) / 1_000_000;
			PerformanceMetricsManager.getInstance().recordHWIDIndexLookup(executionTime);
			return 0;
		}
		
		int count = 0;
		for (Player player : hwidPlayers)
		{
			for (ZoneId zoneId : zoneIds)
			{
				if (_zonePlayerCache.get(zoneId).contains(player))
				{
					count++;
					break; // Count each player only once
				}
			}
		}
		
		long executionTime = (System.nanoTime() - startTime) / 1_000_000;
		PerformanceMetricsManager.getInstance().recordHWIDIndexLookup(executionTime);
		PerformanceMetricsManager.getInstance().recordMethodExecution("countHWIDInZones", executionTime);
		
		return count;
	}
	
	/**
	 * Register player in zone (called when player enters zone)
	 * @param player the player entering the zone
	 * @param zoneId the zone ID the player is entering
	 */
	public void onPlayerEnterZone(Player player, ZoneId zoneId)
	{
		Set<Player> zonePlayers = _zonePlayerCache.get(zoneId);
		if (zonePlayers != null)
		{
			zonePlayers.add(player);
		}
		
		// Update HWID index
		String hwid = player.getHWID();
		if (hwid != null && !hwid.isEmpty())
		{
			_hwidIndex.computeIfAbsent(hwid, _ -> ConcurrentHashMap.newKeySet()).add(player);
		}
	}
	
	/**
	 * Unregister player from zone (called when player exits zone)
	 * @param player the player exiting the zone
	 * @param zoneId the zone ID the player is exiting
	 */
	public void onPlayerExitZone(Player player, ZoneId zoneId)
	{
		Set<Player> zonePlayers = _zonePlayerCache.get(zoneId);
		if (zonePlayers != null)
		{
			zonePlayers.remove(player);
		}
		
		// Update HWID index
		String hwid = player.getHWID();
		if (hwid != null && !hwid.isEmpty())
		{
			Set<Player> hwidPlayers = _hwidIndex.get(hwid);
			if (hwidPlayers != null)
			{
				hwidPlayers.remove(player);
				if (hwidPlayers.isEmpty())
				{
					_hwidIndex.remove(hwid);
				}
			}
		}
	}
	
	/**
	 * Handle player login - add to all current zones
	 * @param player the player logging in
	 */
	public void onPlayerLogin(Player player)
	{
		// Add to HWID index
		String hwid = player.getHWID();
		if (hwid != null && !hwid.isEmpty())
		{
			_hwidIndex.computeIfAbsent(hwid, _ -> ConcurrentHashMap.newKeySet()).add(player);
		}
		
		// Add to current zones
		for (ZoneId zoneId : ZoneId.values())
		{
			if (player.isInsideZone(zoneId))
			{
				Set<Player> zonePlayers = _zonePlayerCache.get(zoneId);
				if (zonePlayers != null)
				{
					zonePlayers.add(player);
				}
			}
		}
	}
	
	/**
	 * Handle player logout - remove from all zones and indexes
	 * @param player the player logging out
	 */
	public void onPlayerLogout(Player player)
	{
		// Remove from all zones
		for (Set<Player> zonePlayers : _zonePlayerCache.values())
		{
			zonePlayers.remove(player);
		}
		
		// Remove from HWID index
		String hwid = player.getHWID();
		if (hwid != null && !hwid.isEmpty())
		{
			Set<Player> hwidPlayers = _hwidIndex.get(hwid);
			if (hwidPlayers != null)
			{
				hwidPlayers.remove(player);
				if (hwidPlayers.isEmpty())
				{
					_hwidIndex.remove(hwid);
				}
			}
		}
	}
	
	/**
	 * Get performance metrics
	 * @return performance metrics string
	 */
	public String getPerformanceMetrics()
	{
		long totalRequests = _cacheHits + _cacheMisses;
		double hitRate = totalRequests > 0 ? (double) _cacheHits / totalRequests * 100 : 0;
		
		return String.format("ZonePlayerManager Metrics: Cache Hit Rate: %.2f%%, Total Requests: %d, HWID Index Size: %d", 
			hitRate, totalRequests, _hwidIndex.size());
	}
	
	/**
	 * Reset performance metrics
	 */
	public void resetMetrics()
	{
		_cacheHits = 0;
		_cacheMisses = 0;
	}
}
