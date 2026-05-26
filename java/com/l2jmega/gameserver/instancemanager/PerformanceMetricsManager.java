package com.l2jmega.gameserver.instancemanager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

/**
 * Performance metrics collection and monitoring system
 * Tracks critical performance indicators for optimization
 * 
 * @author Optimized by AI Assistant
 */
public class PerformanceMetricsManager
{
	private static final PerformanceMetricsManager _instance = new PerformanceMetricsManager();
	
	// Performance counters
	private final AtomicLong _worldGetPlayersCalls = new AtomicLong(0);
	private final AtomicLong _worldGetPlayersTime = new AtomicLong(0);
	private final AtomicLong _zonePlayerCacheHits = new AtomicLong(0);
	private final AtomicLong _zonePlayerCacheMisses = new AtomicLong(0);
	private final AtomicLong _hwidIndexLookups = new AtomicLong(0);
	private final AtomicLong _hwidIndexTime = new AtomicLong(0);
	
	// Method execution times
	private final Map<String, AtomicLong> _methodExecutionTimes = new ConcurrentHashMap<>();
	private final Map<String, AtomicLong> _methodCallCounts = new ConcurrentHashMap<>();
	
	// Memory usage tracking
	private volatile long _lastMemoryCheck = System.currentTimeMillis();
	private volatile long _maxMemoryUsed = 0;
	private volatile long _currentMemoryUsed = 0;
	
	private PerformanceMetricsManager()
	{
		// Initialize common method tracking
		initializeMethodTracking();
	}
	
	public static PerformanceMetricsManager getInstance()
	{
		return _instance;
	}
	
	private void initializeMethodTracking()
	{
		String[] methods = {
			"checkPlayersKickTask",
			"checkPlayersKickTask_ip", 
			"checkClanAreaKickTask",
			"checkAllyAreaKickTask",
			"getPlayersInZone",
			"countHWIDInZones"
		};
		
		for (String method : methods)
		{
			_methodExecutionTimes.put(method, new AtomicLong(0));
			_methodCallCounts.put(method, new AtomicLong(0));
		}
	}
	
	/**
	 * Record World.getInstance().getPlayers() call performance
	 * @param executionTime execution time in milliseconds
	 */
	public void recordWorldGetPlayersCall(long executionTime)
	{
		_worldGetPlayersCalls.incrementAndGet();
		_worldGetPlayersTime.addAndGet(executionTime);
	}
	
	/**
	 * Record zone player cache hit
	 */
	public void recordZoneCacheHit()
	{
		_zonePlayerCacheHits.incrementAndGet();
	}
	
	/**
	 * Record zone player cache miss
	 */
	public void recordZoneCacheMiss()
	{
		_zonePlayerCacheMisses.incrementAndGet();
	}
	
	/**
	 * Record HWID index lookup performance
	 * @param executionTime execution time in milliseconds
	 */
	public void recordHWIDIndexLookup(long executionTime)
	{
		_hwidIndexLookups.incrementAndGet();
		_hwidIndexTime.addAndGet(executionTime);
	}
	
	/**
	 * Record method execution time
	 * @param methodName name of the method
	 * @param executionTime execution time in milliseconds
	 */
	public void recordMethodExecution(String methodName, long executionTime)
	{
		AtomicLong timeCounter = _methodExecutionTimes.get(methodName);
		AtomicLong callCounter = _methodCallCounts.get(methodName);
		
		if (timeCounter != null && callCounter != null)
		{
			timeCounter.addAndGet(executionTime);
			callCounter.incrementAndGet();
		}
	}
	
	/**
	 * Update memory usage statistics
	 */
	public void updateMemoryStats()
	{
		long currentTime = System.currentTimeMillis();
		if (currentTime - _lastMemoryCheck > 60000) // Update every minute
		{
			Runtime runtime = Runtime.getRuntime();
			_currentMemoryUsed = runtime.totalMemory() - runtime.freeMemory();
			_maxMemoryUsed = Math.max(_maxMemoryUsed, _currentMemoryUsed);
			_lastMemoryCheck = currentTime;
		}
	}
	
	/**
	 * Get comprehensive performance report
	 * @return performance report string
	 */
	public String getPerformanceReport()
	{
		updateMemoryStats();
		
		StringBuilder report = new StringBuilder();
		report.append("=== PERFORMANCE METRICS REPORT ===\n");
		
		// World.getPlayers() statistics
		long worldCalls = _worldGetPlayersCalls.get();
		long worldTime = _worldGetPlayersTime.get();
		if (worldCalls > 0)
		{
			report.append(String.format("World.getPlayers() calls: %d, Avg time: %.2f ms\n", 
				worldCalls, (double) worldTime / worldCalls));
		}
		
		// Zone cache statistics
		long cacheHits = _zonePlayerCacheHits.get();
		long cacheMisses = _zonePlayerCacheMisses.get();
		long totalCacheRequests = cacheHits + cacheMisses;
		if (totalCacheRequests > 0)
		{
			report.append(String.format("Zone Cache Hit Rate: %.2f%% (%d/%d)\n", 
				(double) cacheHits / totalCacheRequests * 100, cacheHits, totalCacheRequests));
		}
		
		// HWID index statistics
		long hwidLookups = _hwidIndexLookups.get();
		long hwidTime = _hwidIndexTime.get();
		if (hwidLookups > 0)
		{
			report.append(String.format("HWID Index lookups: %d, Avg time: %.2f ms\n", 
				hwidLookups, (double) hwidTime / hwidLookups));
		}
		
		// Method execution statistics
		report.append("\n--- Method Performance ---\n");
		for (Map.Entry<String, AtomicLong> entry : _methodCallCounts.entrySet())
		{
			String methodName = entry.getKey();
			long callCount = entry.getValue().get();
			long totalTime = _methodExecutionTimes.get(methodName).get();
			
			if (callCount > 0)
			{
				report.append(String.format("%s: %d calls, Avg time: %.2f ms\n", 
					methodName, callCount, (double) totalTime / callCount));
			}
		}
		
		// Memory statistics
		report.append(String.format("\n--- Memory Usage ---\n"));
		report.append(String.format("Current Memory: %.2f MB\n", _currentMemoryUsed / 1024.0 / 1024.0));
		report.append(String.format("Max Memory Used: %.2f MB\n", _maxMemoryUsed / 1024.0 / 1024.0));
		
		// ZonePlayerManager metrics
		report.append("\n--- ZonePlayerManager ---\n");
		report.append(ZonePlayerManager.getInstance().getPerformanceMetrics());
		
		return report.toString();
	}
	
	/**
	 * Get performance summary for monitoring
	 * @return performance summary string
	 */
	public String getPerformanceSummary()
	{
		long worldCalls = _worldGetPlayersCalls.get();
		long cacheHits = _zonePlayerCacheHits.get();
		long cacheMisses = _zonePlayerCacheMisses.get();
		long totalCacheRequests = cacheHits + cacheMisses;
		
		double cacheHitRate = totalCacheRequests > 0 ? (double) cacheHits / totalCacheRequests * 100 : 0;
		
		return String.format("Perf: WorldCalls=%d, CacheHitRate=%.1f%%, Memory=%.1fMB", 
			worldCalls, cacheHitRate, _currentMemoryUsed / 1024.0 / 1024.0);
	}
	
	/**
	 * Reset all metrics
	 */
	public void resetMetrics()
	{
		_worldGetPlayersCalls.set(0);
		_worldGetPlayersTime.set(0);
		_zonePlayerCacheHits.set(0);
		_zonePlayerCacheMisses.set(0);
		_hwidIndexLookups.set(0);
		_hwidIndexTime.set(0);
		
		for (AtomicLong counter : _methodExecutionTimes.values())
		{
			counter.set(0);
		}
		
		for (AtomicLong counter : _methodCallCounts.values())
		{
			counter.set(0);
		}
		
		ZonePlayerManager.getInstance().resetMetrics();
	}
	
	/**
	 * Check if performance is within acceptable limits
	 * @return true if performance is acceptable
	 */
	public boolean isPerformanceAcceptable()
	{
		updateMemoryStats();
		
		// Check memory usage (should be under 80% of max)
		Runtime runtime = Runtime.getRuntime();
		long maxMemory = runtime.maxMemory();
		double memoryUsagePercent = (double) _currentMemoryUsed / maxMemory * 100;
		
		// Check cache hit rate (should be above 70%)
		long cacheHits = _zonePlayerCacheHits.get();
		long cacheMisses = _zonePlayerCacheMisses.get();
		long totalCacheRequests = cacheHits + cacheMisses;
		double cacheHitRate = totalCacheRequests > 0 ? (double) cacheHits / totalCacheRequests * 100 : 100;
		
		return memoryUsagePercent < 80.0 && cacheHitRate > 70.0;
	}
}
