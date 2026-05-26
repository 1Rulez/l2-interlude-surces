package com.l2jmega.gameserver.handler.admincommandhandlers;

import com.l2jmega.gameserver.handler.IAdminCommandHandler;
import com.l2jmega.gameserver.instancemanager.PerformanceMetricsManager;
import com.l2jmega.gameserver.instancemanager.ZonePlayerManager;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * Admin command handler for performance monitoring and metrics
 * Provides real-time performance statistics and optimization tools
 * 
 * @author Optimized by AI Assistant
 */
public class AdminPerformance implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = {
		"admin_performance",
		"admin_perf",
		"admin_metrics",
		"admin_reset_metrics"
	};
	
	@Override
	public boolean useAdminCommand(String command, Player activeChar)
	{
		if (command.equals("admin_performance") || command.equals("admin_perf") || command.equals("admin_metrics"))
		{
			showPerformanceReport(activeChar);
		}
		else if (command.equals("admin_reset_metrics"))
		{
			PerformanceMetricsManager.getInstance().resetMetrics();
			activeChar.sendMessage("Performance metrics have been reset.");
			showPerformanceReport(activeChar);
		}
		
		return true;
	}
	
	private static void showPerformanceReport(Player activeChar)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/admin/performance_report.htm");
		
		// Get performance data
		PerformanceMetricsManager perfManager = PerformanceMetricsManager.getInstance();
		String performanceReport = perfManager.getPerformanceReport();
		String performanceSummary = perfManager.getPerformanceSummary();
		boolean isAcceptable = perfManager.isPerformanceAcceptable();
		
		// Replace placeholders
		html.replace("%performance_report%", formatHtmlReport(performanceReport));
		html.replace("%performance_summary%", performanceSummary);
		html.replace("%performance_status%", isAcceptable ? 
			"<font color=\"00FF00\">GOOD</font>" : 
			"<font color=\"FF0000\">NEEDS ATTENTION</font>");
		html.replace("%zone_metrics%", ZonePlayerManager.getInstance().getPerformanceMetrics());
		
		activeChar.sendPacket(html);
	}
	
	private static String formatHtmlReport(String report)
	{
		// Convert plain text report to HTML format
		return report.replace("\n", "<br>")
					.replace("=== ", "<h2>")
					.replace(" ===", "</h2>")
					.replace("--- ", "<h3>")
					.replace(" ---", "</h3>")
					.replace("World.getPlayers() calls:", "<b>World.getPlayers() calls:</b>")
					.replace("Zone Cache Hit Rate:", "<b>Zone Cache Hit Rate:</b>")
					.replace("HWID Index lookups:", "<b>HWID Index lookups:</b>")
					.replace("Method Performance", "<b>Method Performance</b>")
					.replace("Memory Usage", "<b>Memory Usage</b>")
					.replace("ZonePlayerManager", "<b>ZonePlayerManager</b>");
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
