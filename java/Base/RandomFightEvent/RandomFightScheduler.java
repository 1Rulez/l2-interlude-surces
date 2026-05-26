package Base.RandomFightEvent;

import java.util.logging.Logger;

import com.l2jmega.Config;
import com.l2jmega.commons.concurrent.ThreadPool;

public class RandomFightScheduler
{
    private static final Logger _log = Logger.getLogger(RandomFightScheduler.class.getName());

    public static void init()
    {
        if (!Config.ALLOW_RANDOM_FIGHT)
            return;

        _log.info("RandomFightScheduler: Scheduler started.");

        ThreadPool.scheduleAtFixedRate(
            () -> {
                _log.info("RandomFightScheduler: Triggering event registration.");
                RandomFight.startRegister();
            },
            60000L,
            Config.RANDOM_FIGHT_EVERY_MINUTES * 60000L
        );
    }
}
