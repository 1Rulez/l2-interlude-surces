package com.l2jmega.gameserver.instancemanager;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;

import com.l2jmega.commons.concurrent.ThreadPool;
import com.l2jmega.Config;
import com.l2jmega.events.BossEvent;

public class NextBossEvent
{
    private static NextBossEvent _instance = null;
    protected static final Logger _log = Logger.getLogger(NextBossEvent.class.getName());
    private Calendar nextEvent;
    private final SimpleDateFormat format = new SimpleDateFormat("HH:mm");

    private ScheduledFuture<?> _task = null;

    public static NextBossEvent getInstance()
    {
        if (_instance == null)
            _instance = new NextBossEvent();
        return _instance;
    }

    public String getNextTime()
    {
        if (nextEvent != null)
            return format.format(nextEvent.getTime());
        return "Error";
    }

    public void startCalculationOfNextEventTime()
    {
        // Cancel previous task to avoid double scheduling
        if (_task != null && !_task.isCancelled())
            _task.cancel(false);

        try
        {
            Calendar currentTime = Calendar.getInstance();
            Calendar nearestEventTime = null;
            long shortestDelay = Long.MAX_VALUE;

            for (String timeOfDay : Config.BOSS_EVENT_BY_TIME_OF_DAY)
            {
                Calendar eventTime = Calendar.getInstance();
                eventTime.setLenient(true);

                String[] splitTime = timeOfDay.split(":");
                eventTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(splitTime[0]));
                eventTime.set(Calendar.MINUTE, Integer.parseInt(splitTime[1]));
                eventTime.set(Calendar.SECOND, 0);
                eventTime.set(Calendar.MILLISECOND, 0);

                if (eventTime.before(currentTime))
                    eventTime.add(Calendar.DATE, 1);

                long delay = eventTime.getTimeInMillis() - currentTime.getTimeInMillis();

                if (delay < shortestDelay)
                {
                    shortestDelay = delay;
                    nearestEventTime = eventTime;
                }
            }

            nextEvent = nearestEventTime;

            if (nextEvent != null)
            {
                _log.info("[Hero Boss Event]: Next Event Time -> " + format.format(nextEvent.getTime()));
                _task = ThreadPool.schedule(new StartEventTask(), shortestDelay);
            }
            else
            {
                _log.warning("[Hero Boss Event]: Could not calculate next event time.");
            }
        }
        catch (Exception e)
        {
            _log.severe("[Boss Event] Error calculating next event time: " + e);
        }
    }

    private class StartEventTask implements Runnable
    {
    	@Override
    	public void run()
    	{
    	    if (BossEvent.getInstance().getState() != BossEvent.EventState.INACTIVE)
    	        return;

    	    _log.info("----------------------------------------------------------------------------");
    	    _log.info("[Hero Boss Event]: Event Started.");
    	    _log.info("----------------------------------------------------------------------------");

    	    BossEvent.getInstance().startRegistration();

    	    startCalculationOfNextEventTime();
    	
        }
    }
}
