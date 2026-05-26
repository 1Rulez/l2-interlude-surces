/*
 * Copyright (c) 2024 DenArt Designs
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.l2jmega.hopzone.eu.global;

import com.l2jmega.gameserver.data.ItemTable;
import com.l2jmega.gameserver.model.World;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.kind.Item;
import com.l2jmega.hopzone.eu.Configurations;
import com.l2jmega.hopzone.eu.gui.Gui;
import com.l2jmega.hopzone.eu.model.GlobalResponse;
import com.l2jmega.hopzone.eu.util.Logs;
import com.l2jmega.hopzone.eu.util.Random;
import com.l2jmega.hopzone.eu.util.Rewards;
import com.l2jmega.hopzone.eu.util.Url;
import com.l2jmega.hopzone.eu.util.Utilities;
import com.l2jmega.hopzone.eu.util.VDSThreadPool;
import com.l2jmega.hopzone.eu.vote.VDSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * @Author Nightwolf iToPz Discord: https://discord.gg/KkPms6B5aE
 * @Author Rationale Base structure credits goes on Rationale Discord: Rationale#7773
 *         <p>
 *         VDS Stands for: Vote Donation System Script website: https://itopz.com/ Partner website: https://hopzone.eu/ Script version: 1.8 Pack Support: aCis 401
 *         <p>
 *         Freemium Donate Panel V4: https://www.denart-designs.com/ Download: https://mega.nz/folder/6oxUyaIJ#qQDUXeoXlPvBjbPMDYzu-g Buy: https://shop.denart-designs.com/product/auto-donate-panel-v4/ Quick Guide: https://github.com/nightw0lv/VDSystem/tree/master/Guide
 */
public class Global
{
	// logger
	private static final Logs _log = new Logs(Global.class.getSimpleName());
	
	// global server vars
	private static int storedVotes, serverVotes, serverRank, serverNeededVotes, serverNextRank;
	private static int responseCode;
	
	// ip array list
	private final List<String> FINGERPRINT = new ArrayList<>();
	
	/**
	 * Global reward main function
	 */
	public Global()
	{
		// check if allowed the HOPZONE reward to start
		if (Configurations.HOPZONE_EU_GLOBAL_REWARD)
		{
			VDSThreadPool.scheduleAtFixedRate(() -> execute("HOPZONE"), 100, Configurations.HOPZONE_EU_VOTE_CHECK_DELAY * 1000);
			_log.info(Global.class.getSimpleName() + ": HOPZONE reward started.");
		}
		
		// check if allowed the ITOPZ reward to start
		if (Configurations.ITOPZ_GLOBAL_REWARD)
		{
			VDSThreadPool.scheduleAtFixedRate(() -> execute("ITOPZ"), 100, Configurations.ITOPZ_VOTE_CHECK_DELAY * 1000);
			_log.info(Global.class.getSimpleName() + ": ITOPZ reward started.");
		}
		
		// check if allowed the HOPZONENET reward to start
		if (Configurations.HOPZONE_NET_GLOBAL_REWARD)
		{
			VDSThreadPool.scheduleAtFixedRate(() -> execute("HOPZONENET"), 100, Configurations.HOPZONE_NET_VOTE_CHECK_DELAY * 1000);
			_log.info(Global.class.getSimpleName() + ": HOPZONENET reward started.");
		}
		
		// check if allowed the L2TOPGAMESERVER reward to start
		if (Configurations.L2TOPGAMESERVER_GLOBAL_REWARD)
		{
			VDSThreadPool.scheduleAtFixedRate(() -> execute("L2TOPGAMESERVER"), 100, Configurations.L2TOPGAMESERVER_VOTE_CHECK_DELAY * 1000);
			_log.info(Global.class.getSimpleName() + ": L2TOPGAMESERVER reward started.");
		}
		
		// check if allowed the L2JBRASIL reward to start
		if (Configurations.L2JBRASIL_GLOBAL_REWARD)
		{
			VDSThreadPool.scheduleAtFixedRate(() -> execute("L2JBRASIL"), 100, Configurations.L2JBRASIL_VOTE_CHECK_DELAY * 1000);
			_log.info(Global.class.getSimpleName() + ": L2JBRASIL reward started.");
		}
		
		// check if allowed the L2NETWORK reward to start
		if (Configurations.L2NETWORK_GLOBAL_REWARD)
		{
			VDSThreadPool.scheduleAtFixedRate(() -> execute("L2NETWORK"), 100, Configurations.L2NETWORK_VOTE_CHECK_DELAY * 1000);
			_log.info(Global.class.getSimpleName() + ": L2NETWORK reward started.");
		}
		
		// check if allowed the HOTSERVERS reward to start
		if (Configurations.HOTSERVERS_GLOBAL_REWARD)
		{
			VDSThreadPool.scheduleAtFixedRate(() -> execute("HOTSERVERS"), 100, Configurations.HOTSERVERS_VOTE_CHECK_DELAY * 1000);
			_log.info(Global.class.getSimpleName() + ": HOTSERVERS reward started.");
		}
		
		// check if allowed the L2VOTES reward to start
		if (Configurations.L2VOTES_GLOBAL_REWARD)
		{
			VDSThreadPool.scheduleAtFixedRate(() -> execute("L2VOTES"), 100, Configurations.L2VOTES_VOTE_CHECK_DELAY * 1000);
			_log.info(Global.class.getSimpleName() + ": L2VOTES reward started.");
		}
		
		// check if allowed the L2RANKZONE reward to start
		if (Configurations.L2RANKZONE_GLOBAL_REWARD)
		{
			VDSThreadPool.scheduleAtFixedRate(() -> execute("L2RANKZONE"), 100, Configurations.L2RANKZONE_VOTE_CHECK_DELAY * 1000);
			_log.info(Global.class.getSimpleName() + ": L2RANKZONE reward started.");
		}
		
		// check if allowed the TOP4TEAMBR reward to start
		if (Configurations.TOP4TEAMBR_GLOBAL_REWARD)
		{
			VDSThreadPool.scheduleAtFixedRate(() -> execute("TOP4TEAMBR"), 100, Configurations.TOP4TEAMBR_VOTE_CHECK_DELAY * 1000);
			_log.info(Global.class.getSimpleName() + ": TOP4TEAMBR reward started.");
		}
	}
	
	/**
	 * set server information vars
	 * @param TOPSITE
	 */
	public void execute(String TOPSITE)
	{
		// get response from topsite about this ip address
		Optional.ofNullable(GlobalResponse.OPEN(Url.from(TOPSITE + "_GLOBAL_URL").toString()).connect(TOPSITE, VDSystem.VoteType.GLOBAL)).ifPresent(response -> {
			// set variables
			responseCode = response.getResponseCode();
			serverNeededVotes = response.getServerNeededVotes();
			serverNextRank = response.getServerNextRank();
			serverRank = response.getServerRank();
			serverVotes = response.getServerVotes();
		});
		// check topsite response
		if (responseCode != 200 || serverVotes == -2)
		{
			// write on console
			Gui.getInstance().ConsoleWrite(TOPSITE + " Not responding maybe its the end of the world.");
			if (Configurations.DEBUG)
				Gui.getInstance().ConsoleWrite(TOPSITE + " RESPONSE:" + responseCode + " VOTES:" + serverVotes);
			return;
		}
		
		// write console info from response
		switch (TOPSITE)
		{
			case "HOPZONE":
				Gui.getInstance().ConsoleWrite(TOPSITE + " Server Votes: " + serverVotes + " votes.");
				Gui.getInstance().UpdateHopzoneStats(serverVotes);
				break;
			case "ITOPZ":
				Gui.getInstance().ConsoleWrite(TOPSITE + " Server Votes:" + serverVotes + " Rank:" + serverRank + " Next Rank(" + serverNextRank + ") need: " + serverNeededVotes + "votes.");
				Gui.getInstance().UpdateItopzStats(serverVotes, serverRank, serverNextRank, serverNeededVotes);
				break;
			case "HOPZONENET":
				Gui.getInstance().ConsoleWrite(TOPSITE + " Server Votes: " + serverVotes + " votes.");
				Gui.getInstance().UpdateHopzonenetStats(serverVotes);
				break;
			case "L2JBRASIL":
				Gui.getInstance().ConsoleWrite(TOPSITE + " Server Votes: " + serverVotes + " votes.");
				Gui.getInstance().UpdateBrasilStats(serverVotes);
				break;
			case "L2NETWORK":
				Gui.getInstance().ConsoleWrite(TOPSITE + " Server Votes: " + serverVotes + " votes.");
				Gui.getInstance().UpdateNetworkStats(serverVotes);
				break;
			case "L2TOPGAMESERVER":
				Gui.getInstance().ConsoleWrite(TOPSITE + " Server Votes: " + serverVotes + " votes.");
				Gui.getInstance().UpdateTopGameServerStats(serverVotes);
				break;
			case "HOTSERVERS":
				Gui.getInstance().ConsoleWrite(TOPSITE + " Server Votes: " + serverVotes + " votes.");
				Gui.getInstance().UpdateHotServersStats(serverVotes);
				break;
			case "L2VOTES":
				Gui.getInstance().ConsoleWrite(TOPSITE + " Server Votes: " + serverVotes + " votes.");
				Gui.getInstance().UpdateVotesStats(serverVotes);
				break;
			case "L2RANKZONE":
				Gui.getInstance().ConsoleWrite(TOPSITE + " Server Votes: " + serverVotes + " votes.");
				Gui.getInstance().UpdateL2RankZoneStats(serverVotes);
				break;
			case "TOP4TEAMBR":
				Gui.getInstance().ConsoleWrite(TOPSITE + " Server Votes: " + serverVotes + " votes.");
				Gui.getInstance().UpdateTop4TeamBRStats(serverVotes);
				break;
		}
		storedVotes = Utilities.selectGlobalVar(TOPSITE, "votes");
		
		// check if default return value is -1 (failed)
		if (storedVotes == -1)
		{
			// re-set server votes
			Gui.getInstance().ConsoleWrite(TOPSITE + " recover votes.");
			// save votes
			Utilities.saveGlobalVar(TOPSITE, "votes", serverVotes);
			return;
		}
		
		// check stored votes are lower than server votes
		if (storedVotes < serverVotes)
		{
			// write on console
			Gui.getInstance().ConsoleWrite(TOPSITE + " update database");
			// save votes
			Utilities.saveGlobalVar(TOPSITE, "votes", storedVotes);
		}
		
		// monthly reset
		if (storedVotes > serverVotes)
		{
			// write on console
			Gui.getInstance().ConsoleWrite(TOPSITE + " monthly reset");
			// save votes
			Utilities.saveGlobalVar(TOPSITE, "votes", serverVotes);
		}
		
		// announce current votes
		switch (TOPSITE)
		{
			case "HOPZONE":
				if (Configurations.HOPZONE_EU_ANNOUNCE_STATISTICS)
					Gui.getInstance().UpdateHopzoneStats(serverVotes);
				if (serverVotes >= storedVotes + Configurations.HOPZONE_EU_VOTE_STEP)
				{
					reward(TOPSITE);
				}
				int nextHop = storedVotes + Configurations.HOPZONE_EU_VOTE_STEP;
				int needHop = Math.max(0, nextHop - serverVotes);
				Utilities.announce(TOPSITE,
					TOPSITE + ": " + serverVotes + " / " + nextHop +
					" votes (need " + needHop + " more)");
				break;

			case "ITOPZ":
				if (Configurations.ITOPZ_ANNOUNCE_STATISTICS)
					Utilities.announce(TOPSITE, "Server Votes:" + serverVotes + " Rank:" + serverRank + " Next Rank(" + serverNextRank + ") need:" + serverNeededVotes + "votes");
				if (serverVotes >= storedVotes + Configurations.ITOPZ_VOTE_STEP)
				{
					reward(TOPSITE);
				}
				int nextItopz = storedVotes + Configurations.ITOPZ_VOTE_STEP;
				int needItopz = Math.max(0, nextItopz - serverVotes);
				Utilities.announce(TOPSITE,
					TOPSITE + ": " + serverVotes + " / " + nextItopz +
					" votes (need " + needItopz + " more)");
				break;

			case "HOPZONENET":
				if (Configurations.HOPZONE_NET_ANNOUNCE_STATISTICS)
					Gui.getInstance().UpdateHopzonenetStats(serverVotes);
				if (serverVotes >= storedVotes + Configurations.HOPZONE_NET_VOTE_STEP)
				{
					reward(TOPSITE);
				}
				int nextHopNet = storedVotes + Configurations.HOPZONE_NET_VOTE_STEP;
				int needHopNet = Math.max(0, nextHopNet - serverVotes);
				Utilities.announce(TOPSITE,
					TOPSITE + ": " + serverVotes + " / " + nextHopNet +
					" votes (need " + needHopNet + " more)");
				break;

			case "L2JBRASIL":
			    if (Configurations.L2JBRASIL_ANNOUNCE_STATISTICS)
			        Gui.getInstance().UpdateBrasilStats(serverVotes);

			    int step = Configurations.L2JBRASIL_VOTE_STEP;

			    // calculate next reward based only on real votes
			    int nextBrasil = ((serverVotes / step) + 1) * step;
			    int needBrasil = nextBrasil - serverVotes;

			    // reward when threshold reached
			    if (needBrasil <= 0)
			    {
			        reward(TOPSITE);
			    }

			    Utilities.announce(TOPSITE,
			        TOPSITE + ": " + serverVotes + " / " + nextBrasil +
			        " votes (need " + needBrasil + " more)");
			    break;


			case "L2NETWORK":
				if (Configurations.L2NETWORK_ANNOUNCE_STATISTICS)
					Gui.getInstance().UpdateNetworkStats(serverVotes);
				if (serverVotes >= storedVotes + Configurations.L2NETWORK_VOTE_STEP)
				{
					reward(TOPSITE);
				}
				int nextNet = storedVotes + Configurations.L2NETWORK_VOTE_STEP;
				int needNet = Math.max(0, nextNet - serverVotes);
				Utilities.announce(TOPSITE,
					TOPSITE + ": " + serverVotes + " / " + nextNet +
					" votes (need " + needNet + " more)");
				break;

			case "L2TOPGAMESERVER":
				if (Configurations.L2TOPGAMESERVER_ANNOUNCE_STATISTICS)
					Gui.getInstance().UpdateTopGameServerStats(serverVotes);
				if (serverVotes >= storedVotes + Configurations.L2TOPGAMESERVER_VOTE_STEP)
				{
					reward(TOPSITE);
				}
				int nextTop = storedVotes + Configurations.L2TOPGAMESERVER_VOTE_STEP;
				int needTop = Math.max(0, nextTop - serverVotes);
				Utilities.announce(TOPSITE,
					TOPSITE + ": " + serverVotes + " / " + nextTop +
					" votes (need " + needTop + " more)");
				break;

			case "HOTSERVERS":
				if (Configurations.HOTSERVERS_ANNOUNCE_STATISTICS)
					Gui.getInstance().UpdateHotServersStats(serverVotes);
				if (serverVotes >= storedVotes + Configurations.HOTSERVERS_VOTE_STEP)
				{
					reward(TOPSITE);
				}
				int nextHot = storedVotes + Configurations.HOTSERVERS_VOTE_STEP;
				int needHot = Math.max(0, nextHot - serverVotes);
				Utilities.announce(TOPSITE,
					TOPSITE + ": " + serverVotes + " / " + nextHot +
					" votes (need " + needHot + " more)");
				break;

			case "L2VOTES":
				if (Configurations.L2VOTES_ANNOUNCE_STATISTICS)
					Gui.getInstance().UpdateVotesStats(serverVotes);
				if (serverVotes >= storedVotes + Configurations.L2VOTES_VOTE_STEP)
				{
					reward(TOPSITE);
				}
				int nextVotes = storedVotes + Configurations.L2VOTES_VOTE_STEP;
				int needVotes = Math.max(0, nextVotes - serverVotes);
				Utilities.announce(TOPSITE,
					TOPSITE + ": " + serverVotes + " / " + nextVotes +
					" votes (need " + needVotes + " more)");
				break;

			case "L2RANKZONE":
				if (Configurations.L2RANKZONE_ANNOUNCE_STATISTICS)
					Gui.getInstance().UpdateL2RankZoneStats(serverVotes);
				if (serverVotes >= storedVotes + Configurations.L2RANKZONE_VOTE_STEP)
				{
					reward(TOPSITE);
				}
				int nextRank = storedVotes + Configurations.L2RANKZONE_VOTE_STEP;
				int needRank = Math.max(0, nextRank - serverVotes);
				Utilities.announce(TOPSITE,
					TOPSITE + ": " + serverVotes + " / " + nextRank +
					" votes (need " + needRank + " more)");
				break;

			case "TOP4TEAMBR":
				if (Configurations.TOP4TEAMBR_ANNOUNCE_STATISTICS)
					Gui.getInstance().UpdateTop4TeamBRStats(serverVotes);
				if (serverVotes >= storedVotes + Configurations.TOP4TEAMBR_VOTE_STEP)
				{
					reward(TOPSITE);
				}
				int nextTop4 = storedVotes + Configurations.TOP4TEAMBR_VOTE_STEP;
				int needTop4 = Math.max(0, nextTop4 - serverVotes);
				Utilities.announce(TOPSITE,
					TOPSITE + ": " + serverVotes + " / " + nextTop4 +
					" votes (need " + needTop4 + " more)");
				break;
		}
	}
	
	/**
	 * reward players
	 * @param TOPSITE
	 */
	private void reward(String TOPSITE)
	{
		// iterate through all players (skip fake players - they have no real connection)
		for (Player player : World.getInstance().getPlayers().stream().filter(Objects::nonNull).collect(Collectors.toList()))
		{
			if (player.isPhantom())
				continue;

			// set player signature key (IP; fake players have null client/connection)
			String key = "";
			try
			{
				if (player.getClient() == null || player.getClient().getConnection() == null)
					key = player.getName();
				else
					key = Objects.requireNonNullElse(player.getClient().getConnection().getInetAddress().getHostAddress(), player.getName());
			}
			catch (Exception e)
			{
				e.printStackTrace();
				continue;
			}
			
			// if key exists ignore player
			if (FINGERPRINT.contains(key))
			{
				continue;
			}
			// add the key on ignore list
			FINGERPRINT.add(key);
			
			for (final int itemId : Rewards.from(TOPSITE + "_GLOBAL_REWARDS").keys())
			{
				// check if the item id exists
				final Item item = ItemTable.getInstance().getTemplate(itemId);
				if (Objects.nonNull(item))
				{
					// get config values
					final Integer[] values = Rewards.from(TOPSITE + "_GLOBAL_REWARDS").get(itemId);
					// set min count value of received item
					int min = values[0];
					// set max count value of received item
					int max = values[1];
					// set chances of getting the item
					int chance = values[2];
					// set count of each item
					int count = Random.get(min, max);
					// chance for each item
					if (Random.get(100) > chance || chance >= 100)
					{
						// reward item
						player.addItem(TOPSITE, itemId, count, player, true);
						// write info on console
						Gui.getInstance().ConsoleWrite(TOPSITE + ": player " + player.getName() + " received x" + count + " " + item.getName());
					}
				}
			}
		}
		
		FINGERPRINT.clear();
		
		// announce the reward
		Utilities.announce(TOPSITE, "Thanks for voting! Players rewarded!");
		// save votes
		Utilities.saveGlobalVar(TOPSITE, "votes", serverVotes);
		// write on console
		Gui.getInstance().ConsoleWrite(TOPSITE + ": Players rewarded!");
	}
	
	public static Global getInstance()
	{
		return Global.SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final Global _instance = new Global();
	}
}