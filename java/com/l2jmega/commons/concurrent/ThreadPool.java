package com.l2jmega.commons.concurrent;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import com.l2jmega.commons.logging.CLogger;

import com.l2jmega.Config;

/**
 * This class handles thread pooling system. It relies on two ThreadPoolExecutor arrays, which poolers number is generated using config.
 * <p>
 * Those arrays hold following pools :
 * </p>
 * <ul>
 * <li>Scheduled pool keeps a track about incoming, future events.</li>
 * <li>Instant pool handles short-life events.</li>
 * </ul>
 */
public final class ThreadPool
{
	protected static final CLogger LOGGER = new CLogger(ThreadPool.class.getName());
	
	private static final long MAX_DELAY = TimeUnit.NANOSECONDS.toMillis(Long.MAX_VALUE - System.nanoTime()) / 2;
	
	private static int _threadPoolRandomizer;
	
	protected static ScheduledThreadPoolExecutor[] _scheduledPools;
	protected static ThreadPoolExecutor[] _instantPools;
	
	/**
	 * Init the different pools, based on Config. It is launched only once, on Gameserver instance.
	 */
	public static void init()
	{
		// Feed scheduled pool.
		int poolCount = Config.SCHEDULED_THREAD_POOL_COUNT;
		if (poolCount == -1)
			poolCount = Runtime.getRuntime().availableProcessors();
		
		_scheduledPools = new ScheduledThreadPoolExecutor[poolCount];
		for (int i = 0; i < poolCount; i++)
			_scheduledPools[i] = new ScheduledThreadPoolExecutor(Config.THREADS_PER_SCHEDULED_THREAD_POOL);
		
		// Feed instant pool.
		poolCount = Config.INSTANT_THREAD_POOL_COUNT;
		if (poolCount == -1)
			poolCount = Runtime.getRuntime().availableProcessors();
		
		_instantPools = new ThreadPoolExecutor[poolCount];
		for (int i = 0; i < poolCount; i++)
			_instantPools[i] = new ThreadPoolExecutor(Config.THREADS_PER_INSTANT_THREAD_POOL, Config.THREADS_PER_INSTANT_THREAD_POOL, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100000));
		
		// Prestart core threads.
		for (ScheduledThreadPoolExecutor threadPool : _scheduledPools)
			threadPool.prestartAllCoreThreads();
		
		for (ThreadPoolExecutor threadPool : _instantPools)
			threadPool.prestartAllCoreThreads();
		
		// Launch purge task.
		scheduleAtFixedRate(new Runnable()
		{
			@Override
			public void run()
			{
				for (ScheduledThreadPoolExecutor threadPool : _scheduledPools)
					threadPool.purge();
				
				for (ThreadPoolExecutor threadPool : _instantPools)
					threadPool.purge();
			}
		}, 600000, 600000);
		
		LOGGER.info("Initializing ThreadPool.");
	}
	
	/**
	 * Schedules a one-shot action that becomes enabled after a delay. The pool is chosen based on pools activity.
	 * @param r : the task to execute.
	 * @param delay : the time from now to delay execution.
	 * @return a ScheduledFuture representing pending completion of the task and whose get() method will return null upon completion.
	 */
	@SuppressWarnings("resource")
	public static ScheduledFuture<?> schedule(Runnable r, long delay)
	{
		try
		{
			ScheduledFuture<?> future = getPool(_scheduledPools).schedule(new TaskWrapper(r), validate(delay), TimeUnit.MILLISECONDS);
			return future;
		}
		catch (Exception e)
		{
			return null;
		}
	}
	
	/**
	 * Schedules a periodic action that becomes enabled after a delay. The pool is chosen based on pools activity.
	 * @param r : the task to execute.
	 * @param delay : the time from now to delay execution.
	 * @param period : the period between successive executions.
	 * @return a ScheduledFuture representing pending completion of the task and whose get() method will throw an exception upon cancellation.
	 */
	@SuppressWarnings("resource")
	public static ScheduledFuture<?> scheduleAtFixedRate(Runnable r, long delay, long period)
	{
		try
		{
			ScheduledFuture<?> future = getPool(_scheduledPools).scheduleAtFixedRate(new TaskWrapper(r), validate(delay), validate(period), TimeUnit.MILLISECONDS);
			return future;
		}
		catch (Exception e)
		{
			return null;
		}
	}
	
	/**
	 * Executes the given task sometime in the future.
	 * @param r : the task to execute.
	 */
	@SuppressWarnings("resource")
	public static void execute(Runnable r)
	{
		try
		{
			ThreadPoolExecutor pool = getPool(_instantPools);
			pool.execute(new TaskWrapper(r));
		}
		catch (Exception e)
		{
			// Ignore exceptions in task execution
		}
	}
	
	/**
	 * Retrieve stats of current running thread pools.
	 */
	@SuppressWarnings("resource")
	public static void getStats()
	{
		for (int i = 0; i < _scheduledPools.length; i++)
		{
			final ScheduledThreadPoolExecutor scheduledPool = _scheduledPools[i];
			
			LOGGER.info("=================================================");
			LOGGER.info("Scheduled pool #" + i + ":");
			LOGGER.info("\tgetActiveCount: ...... " + scheduledPool.getActiveCount());
			LOGGER.info("\tgetCorePoolSize: ..... " + scheduledPool.getCorePoolSize());
			LOGGER.info("\tgetPoolSize: ......... " + scheduledPool.getPoolSize());
			LOGGER.info("\tgetLargestPoolSize: .. " + scheduledPool.getLargestPoolSize());
			LOGGER.info("\tgetMaximumPoolSize: .. " + scheduledPool.getMaximumPoolSize());
			LOGGER.info("\tgetCompletedTaskCount: " + scheduledPool.getCompletedTaskCount());
			LOGGER.info("\tgetQueuedTaskCount: .. " + scheduledPool.getQueue().size());
			LOGGER.info("\tgetTaskCount: ........ " + scheduledPool.getTaskCount());
		}
		
		for (int i = 0; i < _instantPools.length; i++)
		{
			final ThreadPoolExecutor instantPool = _instantPools[i];
			
			LOGGER.info("=================================================");
			LOGGER.info("Instant pool #" + i + ":");
			LOGGER.info("\tgetActiveCount: ...... " + instantPool.getActiveCount());
			LOGGER.info("\tgetCorePoolSize: ..... " + instantPool.getCorePoolSize());
			LOGGER.info("\tgetPoolSize: ......... " + instantPool.getPoolSize());
			LOGGER.info("\tgetLargestPoolSize: .. " + instantPool.getLargestPoolSize());
			LOGGER.info("\tgetMaximumPoolSize: .. " + instantPool.getMaximumPoolSize());
			LOGGER.info("\tgetCompletedTaskCount: " + instantPool.getCompletedTaskCount());
			LOGGER.info("\tgetQueuedTaskCount: .. " + instantPool.getQueue().size());
			LOGGER.info("\tgetTaskCount: ........ " + instantPool.getTaskCount());
		}
	}
	
	/**
	 * Shutdown thread pooling system correctly. Send different informations.
	 */
	public static void shutdown()
	{
		try
		{
			System.out.println("ThreadPool: Shutting down.");
			
			for (ScheduledThreadPoolExecutor threadPool : _scheduledPools)
				threadPool.shutdownNow();
			
			for (ThreadPoolExecutor threadPool : _instantPools)
				threadPool.shutdownNow();
		}
		catch (Throwable t)
		{
			t.printStackTrace();
		}
	}
	
	/**
	 * @param <T> : The pool type.
	 * @param threadPools : The pool array to check.
	 * @return the less fed pool.
	 */
	private static <T> T getPool(T[] threadPools)
	{
		return threadPools[_threadPoolRandomizer++ % threadPools.length];
	}
	
	/**
	 * @param delay : The delay to validate.
	 * @return a secured value, from 0 to MAX_DELAY.
	 */
	private static long validate(long delay)
	{
		return Math.max(0, Math.min(MAX_DELAY, delay));
	}
	
	public static final class TaskWrapper implements Runnable
	{
		private final Runnable _runnable;
		
		public TaskWrapper(Runnable runnable)
		{
			_runnable = runnable;
		}
		
		@Override
		public void run()
		{
			try
			{
				_runnable.run();
			}
			catch (RuntimeException e)
			{
				LOGGER.error("Exception in a ThreadPool task execution.", e);
			}
		}
	}
}