package Base.announcerTopPlayer;

import java.util.logging.Logger;

import com.l2jmega.Config;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.commons.concurrent.ThreadPool;
import Base.Dungeon.DungeonManager;

public class AnnounceOnlinePlayers
{
    private static final Logger _log = Logger.getLogger(AnnounceOnlinePlayers.class.getName());

    public static void getInstance()
    {
        _log.info("AnnounceOnlinePlayers: Started scheduler.");

        ThreadPool.scheduleAtFixedRate(new Runnable()
        {
            @Override
            public void run()
            {
                Announce();
            }
        }, 0, Config.ANNOUNCE_ONLINE_PLAYERS_DELAY * 1000);
     }

    protected static void Announce()
    {
        int playersNotInDungeon = 0;

        for (Player player : World.getInstance().getPlayers())
        {
            if (!DungeonManager.getInstance().isInDungeon(player))
                playersNotInDungeon++;
        }

        _log.info("AnnounceOnlinePlayers: Online players (excluding dungeons) = " + playersNotInDungeon);

        if (playersNotInDungeon == 1)
        {
            for (Player player : World.getInstance().getPlayers())
            {
                if (!DungeonManager.getInstance().isInDungeon(player))
                    player.sendMessage("Record " + playersNotInDungeon + " player is online.");
            }
        }
        else
        {
            for (Player player : World.getInstance().getPlayers())
            {
                if (!DungeonManager.getInstance().isInDungeon(player))
                    player.sendMessage("Record " + playersNotInDungeon + " players are online.");
            }
        }
    }
}
