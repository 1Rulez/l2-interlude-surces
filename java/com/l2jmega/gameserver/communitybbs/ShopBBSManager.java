package com.l2jmega.gameserver.communitybbs;

import com.l2jmega.gameserver.communitybbs.Manager.BaseBBSManager;
import com.l2jmega.gameserver.data.xml.MultisellData;
import com.l2jmega.gameserver.model.actor.Npc;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.item.instance.ItemInstance;
import com.l2jmega.gameserver.model.zone.ZoneId;
import com.l2jmega.gameserver.network.serverpackets.ActionFailed;
import com.l2jmega.gameserver.network.serverpackets.SellList;

import java.util.List;
import java.util.StringTokenizer;

public class ShopBBSManager extends BaseBBSManager 
{
   protected ShopBBSManager()
   {
   }

   @Override
public void parseCmd(String command, Player player) 
   {
      if (player.getPvpFlag() > 0) 
      {
         separateAndSend("<html><body><br><br><center>You can't use Community Board when you are pvp flagged.</center></body></html>", player);
         player.sendMessage("You can't use Shop when you are pvp flagged.");
         player.sendPacket(ActionFailed.STATIC_PACKET);
      } 
      else if (player.isInCombat()) 
      {
         separateAndSend("<html><body><br><br><center>You can't use Community Board when you are in combat.</center></body></html>", player);
         player.sendMessage("You can't use Shop when you are in combat.");
         player.sendPacket(ActionFailed.STATIC_PACKET);
      } 
      else if (player.isDead()) 
      {
         separateAndSend("<html><body><br><br><center>You're dead. You can't use Community Board.</center></body></html>", player);
         player.sendMessage("You're dead. You can't use Shop.");
         player.sendPacket(ActionFailed.STATIC_PACKET);
      } 
      else if (!player.isInsideZone(ZoneId.PEACE)) 
      {
         separateAndSend("<html><body><br><br><center>You're not in Peace Zone. You can't use Community Board.</center></body></html>", player);
         player.sendMessage("You're not in Peace Zone. You can't use Shop.");
         player.sendPacket(ActionFailed.STATIC_PACKET);
      } 
      else 
      {
         StringTokenizer st;
         if (command.startsWith("_bbsshop;")) 
         {
            player.setIsUsingCMultisell(true);
            st = new StringTokenizer(command, ";");
            st.nextToken();
            MultisellData.getInstance().separateAndSend(st.nextToken().concat(st.nextToken()), player, (Npc)null, false);
         } 
         else if (command.startsWith("_bbsshopsell;")) 
         {
            st = new StringTokenizer(command, ";");
            st.nextToken();
            List<ItemInstance> items = player.getInventory().getSellableItems();
            player.sendPacket((new SellList(player.getAdena(), items)));
         } else {
            super.parseCmd(command, player);
         }

      }
   }

   @Override
protected String getFolder() 
   {
      return "top/";
   }

   public static ShopBBSManager getInstance() 
   {
      return ShopBBSManager.SingletonHolder.INSTANCE;
   }

   private static class SingletonHolder {
      protected static final ShopBBSManager INSTANCE = new ShopBBSManager();
   }
}