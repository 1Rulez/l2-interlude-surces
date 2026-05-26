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
package com.l2jmega.hopzone.eu.vote;

import com.l2jmega.gameserver.handler.VoicedCommandHandler;
import com.l2jmega.hopzone.eu.Configurations;
import com.l2jmega.hopzone.eu.command.VoteCMD;
import com.l2jmega.hopzone.eu.global.Global;
import com.l2jmega.hopzone.eu.task.ItemDeliveryManager;
import com.l2jmega.hopzone.eu.util.Logs;
import com.l2jmega.hopzone.eu.util.VDSThreadPool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author Nightwolf iToPz Discord: https://discord.gg/KkPms6B5aE
 * @Author Rationale Base structure credits goes on Rationale Discord: Rationale#7773
 *         <p>
 *         VDS Stands for: Vote Donation System Script website: https://itopz.com/ Partner website: https://hopzone.eu/ Script version: 1.8 Pack Support: aCis 401
 *         <p>
 *         Freemium Donate Panel V4: https://www.denart-designs.com/ Download: https://mega.nz/folder/6oxUyaIJ#qQDUXeoXlPvBjbPMDYzu-g Buy: https://shop.denart-designs.com/product/auto-donate-panel-v4/ Quick Guide: https://github.com/nightw0lv/VDSystem/tree/master/Guide
 */
public class VDSystem
{
	public static int getL2JBrasilVotes(String serverName)
	{
	    try
	    {
	        String url = "https://top.l2jbrasil.com/index.php?a=stats&u=" + serverName;
	        String html = readUrl(url);

	        Pattern p = Pattern.compile("(Entradas|Total de Entradas).*?(\\d+)");
	        Matcher m = p.matcher(html);

	        if (m.find())
	            return Integer.parseInt(m.group(2));
	    }
	    catch (Exception e)
	    {
	        e.printStackTrace();
	    }
	    return 0;
	}


	public static String readUrl(String urlString) throws Exception
	{
		StringBuilder result = new StringBuilder();

		HttpURLConnection conn = (HttpURLConnection) URI.create(urlString).toURL().openConnection();

		conn.setRequestMethod("GET");
		conn.setRequestProperty("User-Agent",
			"Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
		conn.setConnectTimeout(8000);
		conn.setReadTimeout(8000);

		try (BufferedReader rd =
			 new BufferedReader(new InputStreamReader(conn.getInputStream())))
		{
			String line;
			while ((line = rd.readLine()) != null)
				result.append(line).append("\n");
		}
		return result.toString();
	}

	
	
	// logger
	private static final Logs _log = new Logs(VDSystem.class.getSimpleName());
	
	public enum VoteType
	{
		GLOBAL,
		INDIVIDUAL;
	}
	
	/**
	 * Constructor
	 */
	public VDSystem()
	{
		onLoad();
	}
	
	/**
	 * Vod function on load
	 */
	public void onLoad()
	{
		// check if allowed the donation system to run
		if (Configurations.ITEM_DELIVERY_MANAGER)
		{
			// start donation manager
			VDSThreadPool.scheduleAtFixedRate(new ItemDeliveryManager(), 100, 5000);
			
			// initiate Donation reward
			_log.info(ItemDeliveryManager.class.getSimpleName() + ": started.");
		}
		
		// register individual reward command
		VoicedCommandHandler.getInstance().registerHandler(new VoteCMD());
		
		// load global system rewards
		Global.getInstance();
		
		_log.info(VDSystem.class.getSimpleName() + ": System initialized.");
	}
	
	public static VDSystem getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final VDSystem _instance = new VDSystem();
	}
}
