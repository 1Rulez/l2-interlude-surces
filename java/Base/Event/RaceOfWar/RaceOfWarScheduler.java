package Base.Event.RaceOfWar;

import com.l2jmega.Config;
import com.l2jmega.commons.concurrent.ThreadPool;

import java.util.Calendar;
import java.util.List;
import java.util.logging.Logger;

public class RaceOfWarScheduler
{
    private static final Logger _log = Logger.getLogger(RaceOfWarScheduler.class.getName());

    private static boolean _scheduled = false;

    public static void init()
    {
        if (!Config.ENABLE_RACE_OF_WAR)
        {
            _log.info("[RaceOfWar]: Event is DISABLED in config.");
            return;
        }

        if (_scheduled)
        {
            _log.info("[RaceOfWar]: Scheduler already initialized.");
            return;
        }

        _scheduled = true;

        _log.info("[RaceOfWar]: Scheduler initialized.");
        scheduleNextStart();
    }

    private static void scheduleNextStart()
    {
        long delay = calculateNextDelay();

        if (delay < 1000)
        {
            delay = 1000;
            _log.warning("[RaceOfWar]: Delay too small, forced to 1 second.");
        }

        _log.info("[RaceOfWar]: Next event starts in " + (delay / 1000 / 60) + " minute(s).");

        ThreadPool.schedule(() ->
        {
            if (RacesOnWar.getStateRaceOnWar() == StateRacesOnWar.ACTIVE)
            {
                _log.warning("[RaceOfWar]: Event already ACTIVE, skipping start.");
                scheduleNextStart();
                return;
            }

            _log.info("[RaceOfWar]: Event STARTED by scheduler.");
            RacesOnWar.startRacesOnWar();
            scheduleStop();
        }, delay);
    }

    private static long calculateNextDelay()
    {
        Calendar now = Calendar.getInstance();
        long nearest = Long.MAX_VALUE;

        List<String> times = Config.RACE_OF_WAR_START_TIMES;

        if (times == null || times.isEmpty())
        {
            _log.warning("[RaceOfWar]: StartTimes is EMPTY, retry in 1 minute.");
            return 60_000;
        }

        for (String time : times)
        {
            try
            {
                String[] split = time.split(":");
                int hour = Integer.parseInt(split[0]);
                int minute = Integer.parseInt(split[1]);

                Calendar start = (Calendar) now.clone();
                start.set(Calendar.HOUR_OF_DAY, hour);
                start.set(Calendar.MINUTE, minute);
                start.set(Calendar.SECOND, 0);

                if (now.after(start))
                    start.add(Calendar.DAY_OF_MONTH, 1);

                long diff = start.getTimeInMillis() - now.getTimeInMillis();

                if (diff > 0 && diff < nearest)
                    nearest = diff;
            }
            catch (Exception e)
            {
                _log.warning("[RaceOfWar]: Invalid StartTime -> " + time);
            }
        }

        if (nearest == Long.MAX_VALUE)
        {
            _log.warning("[RaceOfWar]: No valid StartTimes found, retry in 1 minute.");
            return 60_000;
        }

        return nearest;
    }

    public static long scheduleNextEventTime()
    {
        long now = System.currentTimeMillis();
        long next = Long.MAX_VALUE;

        List<String> times = Config.RACE_OF_WAR_START_TIMES;

        if (times == null || times.isEmpty())
            return now + 60000;

        for (String timeStr : times)
        {
            try
            {
                String[] split = timeStr.split(":");

                int hour = Integer.parseInt(split[0]);
                int minute = Integer.parseInt(split[1]);

                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(now);
                cal.set(Calendar.HOUR_OF_DAY, hour);
                cal.set(Calendar.MINUTE, minute);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);

                long candidate = cal.getTimeInMillis();

                if (candidate <= now)
                    candidate += 86400000;

                if (candidate < next)
                    next = candidate;

            }
            catch (Exception e)
            {
                // ignore bad config time
            }
        }

        return next == Long.MAX_VALUE ? now + 60000 : next;
    }
    
    
    private static void scheduleStop()
    {
        long duration = Config.RACE_OF_WAR_DURATION_MIN * 60 * 1000L;

        ThreadPool.schedule(() ->
        {
            if (RacesOnWar.getStateRaceOnWar() != StateRacesOnWar.ACTIVE)
            {
                _log.warning("[RaceOfWar]: Event already stopped.");
                scheduleNextStart();
                return;
            }

            _log.info("[RaceOfWar]: Event STOPPED by scheduler.");
            RacesOnWar.endRacesOnWar();
            scheduleNextStart();
        }, duration);
    }
}
