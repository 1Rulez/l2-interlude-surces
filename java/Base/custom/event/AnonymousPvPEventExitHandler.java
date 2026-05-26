package Base.custom.event;

import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.network.serverpackets.TutorialCloseHtml;
import com.l2jmega.gameserver.network.serverpackets.TutorialShowHtml;
import com.l2jmega.gameserver.network.serverpackets.TutorialShowQuestionMark;


public class AnonymousPvPEventExitHandler
{
	/** ID unico para identificar o clique no "?" do Anonymous PvP Event */
	public static final int ANONYMOUS_PVP_QUESTION_MARK_ID = 55503;
	
	/** Link para sair do Anonymous PvP Event */
	public static final String EXIT_LINK = "anon_pvp_exit";
	
	/** Link para fechar a janela e continuar no evento */
	public static final String CLOSE_LINK = "anon_pvp_close";
	
	private AnonymousPvPEventExitHandler()
	{
	}
	
	/**
	 * Verifica se o jogador esta participando do Anonymous PvP Event
	 * @param player Jogador
	 * @return true se estiver participando
	 */
	public static boolean isInAnonymousPvPEvent(Player player)
	{
		if (player == null)
			return false;
		
		return AnonymousPvPEvent.isPlayerInEvent(player);
	}
	
	/**
	 * Mostra o icone "?" na tela do jogador quando ele se registra no evento.
	 * @param player Jogador que se registrou
	 */
	public static void showExitButton(Player player)
	{
		if (player == null || !player.isOnline())
			return;
		
		// Mostra o icone "?" que pisca na tela
		player.sendPacket(new TutorialShowQuestionMark(ANONYMOUS_PVP_QUESTION_MARK_ID));
	}
	
	/**
	 * Remove o icone "?" e fecha qualquer janela tutorial aberta.
	 * Chamado quando o jogador sai do evento.
	 * @param player Jogador
	 */
	public static void hideExitButton(Player player)
	{
		if (player == null)
			return;
		
		// Fecha a janela HTML do tutorial
		player.sendPacket(TutorialCloseHtml.STATIC_PACKET);
	}
	
	/**
	 * Chamado quando o jogador clica no icone "?".
	 * Mostra a janela HTML com informacoes do evento e opcao de sair.
	 * @param player Jogador
	 * @param markId ID do question mark clicado
	 * @return true se foi tratado por este handler
	 */
	public static boolean onQuestionMarkClicked(Player player, int markId)
	{
		if (markId != ANONYMOUS_PVP_QUESTION_MARK_ID)
			return false;
		
		if (player == null || !player.isOnline())
			return true;
		
		// Se nao estiver registrado no evento, fecha e ignora
		if (!isInAnonymousPvPEvent(player))
		{
			hideExitButton(player);
			return true;
		}
		
		// Obtem informacoes do evento
		final int timerSeconds = AnonymousPvPEvent.getRemainingTimeSeconds();
		final int kills = AnonymousPvPEvent.getPlayerKillCount(player);
		
		// Monta o HTML da janela
		final StringBuilder html = new StringBuilder();
		html.append("<html><body>");
		html.append("<center>");
		html.append("<br>");
		html.append("<font color=\"LEVEL\">Anonymous PvP Event</font><br>");
		html.append("<br>");
		html.append("<table width=250 bgcolor=000000>");
		html.append("<tr><td align=center>");
		html.append("<font color=\"FFFFFF\">Time remaining:</font> <font color=\"FFFF00\">").append(formatTime(timerSeconds)).append("</font><br>");
		html.append("<font color=\"FFFFFF\">Your Eliminations:</font> <font color=\"FF0000\">").append(kills).append("</font><br>");
		html.append("</td></tr>");
		html.append("</table>");
		html.append("<br><br>");
		html.append("<table width=200>");
		html.append("<tr><td align=center>");
		html.append("<button value=\"Continue in the Event\" action=\"link ").append(CLOSE_LINK).append("\" width=180 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
		html.append("</td></tr>");
		html.append("<tr><td align=center>");
		html.append("<button value=\"Leave the Event\" action=\"link ").append(EXIT_LINK).append("\" width=180 height=25 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
		html.append("</td></tr>");
		html.append("</table>");
		html.append("<br>");
		html.append("<font color=\"999999\">Upon leaving, you will be teleported to Giran..</font><br>");
		html.append("<font color=\"999999\">You may register again if you wish.</font>");
		html.append("</center>");
		html.append("</body></html>");
		
		player.sendPacket(new TutorialShowHtml(html.toString()));
		return true;
	}
	
	/**
	 * Chamado quando o jogador clica em um link na janela do tutorial.
	 * Trata os links "anon_pvp_exit" (sair) e "anon_pvp_close" (fechar e continuar).
	 * @param player Jogador
	 * @param link Link clicado
	 * @return true se foi tratado por este handler
	 */
	public static boolean onTutorialLinkClicked(Player player, String link)
	{
		if (link == null)
			return false;
		
		// Verifica se e um dos links do Anonymous PvP Event
		final boolean isExitLink = link.equalsIgnoreCase(EXIT_LINK);
		final boolean isCloseLink = link.equalsIgnoreCase(CLOSE_LINK);
		
		if (!isExitLink && !isCloseLink)
			return false;
		
		if (player == null || !player.isOnline())
			return true;
		
		// Fecha a janela do tutorial
		player.sendPacket(TutorialCloseHtml.STATIC_PACKET);
		
		// Se clicou em "Continuar no Evento", apenas fecha a janela e mostra o "?" novamente
		if (isCloseLink)
		{
			if (isInAnonymousPvPEvent(player))
				showExitButton(player);
			return true;
		}
		
		// Clicou em "Sair do Evento"
		// Verifica se ainda esta no evento
		if (!isInAnonymousPvPEvent(player))
		{
			player.sendMessage("You are no longer in the Anonymous PvP Event.");
			return true;
		}
		
		// Chama o metodo de leave do evento (simula .pvpleave)
		AnonymousPvPEvent.unregister(player);
		
		return true;
	}
	
	/**
	 * Formata segundos em MM:SS
	 * @param seconds Segundos totais
	 * @return String formatada
	 */
	private static String formatTime(int seconds)
	{
		if (seconds <= 0)
			return "00:00";
		
		int mins = seconds / 60;
		int secs = seconds % 60;
		
		return String.format("%02d:%02d", mins, secs);
	}
}
