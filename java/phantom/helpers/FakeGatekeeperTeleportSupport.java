package phantom.helpers;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.l2jmega.Config;
import com.l2jmega.commons.random.Rnd;
import com.l2jmega.gameserver.data.cache.HtmCache;
import com.l2jmega.gameserver.data.xml.TeleportLocationData;
import com.l2jmega.gameserver.model.actor.instance.Gatekeeper;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.location.TeleportLocation;

public final class FakeGatekeeperTeleportSupport
{
	private static final String[] GLOBAL_GK_PAGES =
	{
		"data/html/teleporter/10006.htm",
		"data/html/teleporter/custom/Towns.htm",
		"data/html/teleporter/half/30848.htm",
		"data/html/teleporter/half/30059.htm",
		"data/html/teleporter/half/30320.htm",
		"data/html/teleporter/half/30256.htm",
		"data/html/teleporter/half/30080.htm",
		"data/html/teleporter/half/31275.htm",
		"data/html/teleporter/half/30899.htm",
		"data/html/teleporter/half/30233.htm",
		"data/html/teleporter/half/30177.htm",
		"data/html/teleporter/half/31320.htm",
		"data/html/teleporter/half/31964.htm",
		"data/html/teleporter/10006-3.htm",
		"data/html/teleporter/10006-6.htm",
		"data/html/teleporter/10006-7.htm",
		"data/html/teleporter/10006-8.htm",
		"data/html/teleporter/10006-9.htm",
		"data/html/teleporter/10006-10.htm",
		"data/html/teleporter/10006-11.htm",
		"data/html/teleporter/10006-12.htm",
		"data/html/teleporter/10006-13.htm",
		"data/html/teleporter/10006-14.htm",
		"data/html/teleporter/10006-15.htm",
		"data/html/teleporter/10006-16.htm",
		"data/html/teleporter/10006-17.htm",
		"data/html/teleporter/10006-18.htm",
		"data/html/teleporter/10006-19.htm"
	};

	private static final Pattern GOTO_PATTERN = Pattern.compile("goto\\s+(\\d+)");
	private static final Pattern SPECIAL_PATTERN = Pattern.compile("\\b(random_farmzone1|vote_zone|random_ultimatezone|anonymous_event_join|tele_tournament)\\b");

	private static volatile int[] _globalGotoIds = new int[0];
	private static volatile String[] _specialCommands = new String[0];

	private FakeGatekeeperTeleportSupport()
	{
	}

	public static boolean useRandomGlobalTeleport(Player player, Gatekeeper gatekeeper)
	{
		if (player == null || gatekeeper == null || player.isAlikeDead())
			return false;

		ensureCatalogLoaded();

		if (_specialCommands.length > 0 && Rnd.get(100) < 12)
		{
			final String command = _specialCommands[Rnd.get(_specialCommands.length)];
			if (useSpecialCommand(player, gatekeeper, command))
				return true;
		}

		if (_globalGotoIds.length > 0)
			return teleportToGatekeeperPoint(player, _globalGotoIds[Rnd.get(_globalGotoIds.length)]);

		if (_specialCommands.length > 0)
			return useSpecialCommand(player, gatekeeper, _specialCommands[Rnd.get(_specialCommands.length)]);

		return false;
	}

	private static boolean teleportToGatekeeperPoint(Player player, int locationId)
	{
		final TeleportLocation location = TeleportLocationData.getInstance().getTeleportLocation(locationId);
		if (location == null)
			return false;

		player.teleToLocation(location, 0);
		return true;
	}

	private static boolean useSpecialCommand(Player player, Gatekeeper gatekeeper, String command)
	{
		if ("tele_tournament".equals(command))
		{
			if (player.isOlympiadProtection())
				return false;

			final int targetX = Config.Tournament_locx + Rnd.get(-100, 100);
			final int targetY = Config.Tournament_locy + Rnd.get(-100, 100);

			player.teleToLocation(targetX, targetY, Config.Tournament_locz, 0);
			player.setTournamentTeleport(true);
			return true;
		}

		gatekeeper.onBypassFeedback(player, command);
		return true;
	}

	private static void ensureCatalogLoaded()
	{
		if (_globalGotoIds.length > 0)
			return;

		synchronized (FakeGatekeeperTeleportSupport.class)
		{
			if (_globalGotoIds.length > 0)
				return;

			final Set<Integer> gotoIds = new LinkedHashSet<>();
			final Set<String> specialCommands = new LinkedHashSet<>();

			for (String page : GLOBAL_GK_PAGES)
			{
				final String html = HtmCache.getInstance().getHtmForce(page);
				if (html == null || html.isEmpty())
					continue;

				final Matcher gotoMatcher = GOTO_PATTERN.matcher(html);
				while (gotoMatcher.find())
					gotoIds.add(Integer.parseInt(gotoMatcher.group(1)));

				final Matcher specialMatcher = SPECIAL_PATTERN.matcher(html);
				while (specialMatcher.find())
					specialCommands.add(specialMatcher.group(1));
			}

			_globalGotoIds = new int[gotoIds.size()];

			int index = 0;
			for (Integer gotoId : gotoIds)
				_globalGotoIds[index++] = gotoId;

			_specialCommands = specialCommands.toArray(new String[specialCommands.size()]);
		}
	}
}
