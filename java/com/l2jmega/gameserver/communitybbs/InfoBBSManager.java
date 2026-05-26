   package com.l2jmega.gameserver.communitybbs;

import com.l2jmega.gameserver.communitybbs.Manager.BaseBBSManager;
import com.l2jmega.gameserver.data.cache.HtmCache;
import com.l2jmega.gameserver.model.actor.instance.Player;

public class InfoBBSManager extends BaseBBSManager 
{
   protected InfoBBSManager() 
   {
   }

   @Override
public void parseCmd(String command, Player activeChar) 
   {
      if (!command.startsWith("_bbsInfo") && !command.startsWith("_bbsloc")) {
         super.parseCmd(command, activeChar);
      } else {
         String html = HtmCache.getInstance().getHtm("data/html/CommunityBoard/" + this.getFolder() + "info.htm");
         separateAndSend(html, activeChar);
      }

   }

   @Override
protected String getFolder()
   {
      return "top/";
   }

   public static InfoBBSManager getInstance()
   {
      return InfoBBSManager.SingletonHolder.INSTANCE;
   }

   private static class SingletonHolder {
      protected static final InfoBBSManager INSTANCE = new InfoBBSManager();
   }
}