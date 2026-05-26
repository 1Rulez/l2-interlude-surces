package com.l2jmega.gameserver.handler.admincommandhandlers;

import com.l2jmega.gameserver.data.MapRegionTable;
import com.l2jmega.gameserver.handler.IAdminCommandHandler;
import com.l2jmega.gameserver.model.L2Party;
import com.l2jmega.gameserver.model.actor.instance.Player;
import com.l2jmega.gameserver.model.location.Location;
import com.l2jmega.gameserver.model.zone.ZoneId;
import com.l2jmega.gameserver.network.serverpackets.NpcHtmlMessage;
import phantom.FakePlayer;
import phantom.FakePlayerManager;
import phantom.FakePlayerTaskManager;
import phantom.FakeAccountService;
import phantom.ai.autospawn.AutoSpawnAI;
import phantom.ai.autospawn.TownAutoSpawnAI;
import phantom.ai.party.PartyFollowerAI;
import phantom.ai.party.PartyMode;
import phantom.ai.shop.PrivateStoreBuyAI;
import phantom.AdminSpawnedFakesStorage;
import phantom.ai.shop.PrivateStoreSellAI;
import phantom.ai.walker.CitizenAI;
import phantom.helpers.FakeHelpers;
import com.l2jmega.gameserver.model.base.ClassId;

public class AdminFakePlayers implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS = {
		"admin_robot",
		"admin_robot_spawn",
		"admin_robot_spawn_healers",
		"admin_spawnhealer",
		"admin_deleterobot",
		"admin_deleteAllrobots",
		"admin_spawncitizen",
		"admin_spawnbuyer",
		"admin_spawnseller",
		"admin_spawnclass",
		"admin_fake_invite",
		"admin_fake_teleport",
		"admin_fake_setai",
		"admin_fake_heal",
		"admin_fake_rebuff",
		"admin_fake_party_follow",
		"admin_fake_party_assist",
		"admin_fake_party_defend",
		"admin_fake_party_farm",
		"admin_fake_party_stand",
		"admin_fake_party_dismiss",
		"admin_fake_respawnall",
		"admin_deleteAllPvp",
		"admin_deleteAllFarm",
		"admin_deleteAllCity",
		"admin_deletePvpSingle",
		"admin_deleteFarmSingle",
		"admin_deleteCitySingle"
	};

	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}

	private static void showSpawnPage(Player activeChar)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/admin/fakeplayer_spawn.htm");
		activeChar.sendPacket(html);
	}

	private static void showFakeDashboard(Player activeChar)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/admin/fakeplayer.htm");
		html.replace("%fakecount%", String.valueOf(FakePlayerManager.getFakePlayersCount()));
		html.replace("%taskcount%", String.valueOf(FakePlayerTaskManager.INSTANCE.getTaskCount()));

		String targetInfo = "No target";
		String targetAI = "-";
		String targetMode = "-";
		String soloPvpStatus = "-";
		if (activeChar.getTarget() instanceof FakePlayer)
		{
			FakePlayer target = (FakePlayer) activeChar.getTarget();
			targetInfo = target.getName() + " [" + target.getClassId() + "] Lv." + target.getLevel();
			targetAI = target.getFakeAi() != null ? target.getFakeAi().getClass().getSimpleName() : "None";
			targetMode = target.getPartyMode().name();
			soloPvpStatus = target.isFakePvp() ? "ON" : "OFF";
		}
		html.replace("%targetinfo%", targetInfo);
		html.replace("%targetai%", targetAI);
		html.replace("%targetmode%", targetMode);
		html.replace("%solopvp%", soloPvpStatus);

		int partyFakes = 0;
		int partyPvp = 0;
		if (activeChar.isInParty())
		{
			for (Player member : activeChar.getParty().getPartyMembers())
			{
				if (member instanceof FakePlayer)
				{
					partyFakes++;
					if (((FakePlayer) member).isFakePvp())
						partyPvp++;
				}
			}
		}
		html.replace("%partyfakes%", String.valueOf(partyFakes));
		html.replace("%partypvp%", partyFakes > 0 ? partyPvp + "/" + partyFakes : "-");

		activeChar.sendPacket(html);
	}

	@Override
	public boolean useAdminCommand(String command, Player activeChar)
	{
		if (command.startsWith("admin_fake_respawnall"))
		{
			AutoSpawnAI.getInstance().loadData();
			TownAutoSpawnAI.getInstance().loadData();
			activeChar.sendMessage("All fake players respawned.");
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_robot_spawn_healers"))
		{
			showHealerSpawnPage(activeChar);
			return true;
		}

		if (command.startsWith("admin_spawnhealer") && command.contains(" "))
		{
			handleSpawnHealerByClass(activeChar, command.split(" ")[1]);
			showHealerSpawnPage(activeChar);
			return true;
		}

		if (command.startsWith("admin_robot_spawn"))
		{
			showSpawnPage(activeChar);
			return true;
		}

		if (command.startsWith("admin_robot"))
		{
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_fake_invite"))
		{
			handleInvite(activeChar);
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_fake_teleport"))
		{
			handleTeleport(activeChar);
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_fake_heal"))
		{
			handleHeal(activeChar);
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_fake_rebuff"))
		{
			handleRebuff(activeChar);
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_fake_setai "))
		{
			String mode = command.substring("admin_fake_setai ".length()).trim().toLowerCase();
			handleSetAI(activeChar, mode);
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_fake_party_dismiss"))
		{
			handlePartyDismiss(activeChar);
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_fake_party_"))
		{
			String modeStr = command.substring("admin_fake_party_".length()).trim().toUpperCase();
			handlePartyCommand(activeChar, modeStr);
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_deletePvpSingle"))
		{
			if (activeChar.getTarget() instanceof FakePlayer)
			{
				FakePlayer fake = (FakePlayer) activeChar.getTarget();
				   if (fake.isFakePvp())
				   {
					   fake.despawnPlayer();
					   AdminSpawnedFakesStorage.remove("pvp", fake.getX(), fake.getY(), fake.getZ());
					   activeChar.sendMessage("PvP phantom deleted.");
				   }
				else
					activeChar.sendMessage("Target is not a PvP phantom.");
			}
			else
				activeChar.sendMessage("Target a fake player first.");
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_deleteFarmSingle"))
		{
			if (activeChar.getTarget() instanceof FakePlayer)
			{
				FakePlayer fake = (FakePlayer) activeChar.getTarget();
				   if (fake.isFakeFarm())
				   {
					   fake.despawnPlayer();
					   AdminSpawnedFakesStorage.remove("farm", fake.getX(), fake.getY(), fake.getZ());
					   activeChar.sendMessage("Farm phantom deleted.");
				   }
				else
					activeChar.sendMessage("Target is not a Farm phantom.");
			}
			else
				activeChar.sendMessage("Target a fake player first.");
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_deleteCitySingle"))
		{
			if (activeChar.getTarget() instanceof FakePlayer)
			{
				FakePlayer fake = (FakePlayer) activeChar.getTarget();
				   if (!fake.isFakePvp() && !fake.isFakeFarm() && !fake.isTour() && !fake.isFakeEvent() && !fake.isFakeKTBEvent())
				   {
					   fake.despawnPlayer();
					   AdminSpawnedFakesStorage.remove("citizen", fake.getX(), fake.getY(), fake.getZ());
					   activeChar.sendMessage("City phantom deleted.");
				   }
				else
					activeChar.sendMessage("Target is not a City phantom.");
			}
			else
				activeChar.sendMessage("Target a fake player first.");
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_deleterobot"))
		{
			if (activeChar.getTarget() instanceof FakePlayer)
			{
				FakePlayer fake = (FakePlayer) activeChar.getTarget();
				final String storageType = resolveStorageType(fake);
				final int curX = fake.getX();
				final int curY = fake.getY();
				final int curZ = fake.getZ();
				final int lastX = fake.getLastX();
				final int lastY = fake.getLastY();
				final int lastZ = fake.getLastZ();
				fake.despawnPlayer();
				
				// Hard remove from startup storage: exact current/last coords + nearest fallback.
				AdminSpawnedFakesStorage.remove(storageType, curX, curY, curZ);
				AdminSpawnedFakesStorage.remove(storageType, lastX, lastY, lastZ);
				boolean removedNear = AdminSpawnedFakesStorage.removeNear(storageType, curX, curY, curZ, 1200, true);
				if (!removedNear)
					AdminSpawnedFakesStorage.removeNear(storageType, lastX, lastY, lastZ, 1200, true);
				
				// Force delete from DB as requested, including persistent clan fakes.
				FakeAccountService.deletePersistentFakeCharacter(fake.getObjectId());
				
				activeChar.sendMessage("Deleted fake permanently. Type: " + storageType + ".");
			}
			else
				activeChar.sendMessage("Target a fake player first.");

			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_deleteAllPvp"))
		{
			int counter = 0;
			for (FakePlayer fakePlayer : FakePlayerManager.getFakePlayers())
			{
				if (fakePlayer.isFakePvp())
				{
					counter++;
					fakePlayer.despawnPlayer();
				}
			}
			activeChar.sendMessage("Deleted " + counter + " PvP phantom(s).");
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_deleteAllFarm"))
		{
			int counter = 0;
			for (FakePlayer fakePlayer : FakePlayerManager.getFakePlayers())
			{
				if (fakePlayer.isFakeFarm())
				{
					counter++;
					fakePlayer.despawnPlayer();
				}
			}
			activeChar.sendMessage("Deleted " + counter + " Farm phantom(s).");
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_deleteAllCity"))
		{
			int counter = 0;
			for (FakePlayer fakePlayer : FakePlayerManager.getFakePlayers())
			{
				if (!fakePlayer.isFakePvp() && !fakePlayer.isFakeFarm() && !fakePlayer.isTour() && !fakePlayer.isFakeEvent() && !fakePlayer.isFakeKTBEvent())
				{
					counter++;
					fakePlayer.despawnPlayer();
				}
			}
			activeChar.sendMessage("Deleted " + counter + " City phantom(s).");
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_deleteAllrobots"))
		{
			int counter = 0;
			for (FakePlayer fakePlayer : FakePlayerManager.getFakePlayers())
			{
				counter++;
				fakePlayer.despawnPlayer();
			}
			activeChar.sendMessage("A total of " + counter + " fake players have been kicked.");
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_spawnbuyer") && command.contains(" "))
		{
			String loc = command.split(" ")[1];
			int x = activeChar.getX(), y = activeChar.getY(), z = activeChar.getZ();
			FakePlayer fake = "clan".equals(loc)
				? FakePlayerManager.spawnClanPlayer(x, y, z)
				: FakePlayerManager.spawnPlayer(x, y, z);
			if (fake == null)
			{
				activeChar.sendMessage("Clan fake players are disabled.");
				showFakeDashboard(activeChar);
				return true;
			}
			fake.setFakeAi(new PrivateStoreBuyAI(fake));
			AdminSpawnedFakesStorage.save("clan".equals(loc) ? "buyer_clan" : "buyer", x, y, z);
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_spawnseller") && command.contains(" "))
		{
			String loc = command.split(" ")[1];
			int x = activeChar.getX(), y = activeChar.getY(), z = activeChar.getZ();
			FakePlayer fake = "clan".equals(loc)
				? FakePlayerManager.spawnClanPlayer(x, y, z)
				: FakePlayerManager.spawnPlayer(x, y, z);
			if (fake == null)
			{
				activeChar.sendMessage("Clan fake players are disabled.");
				showFakeDashboard(activeChar);
				return true;
			}
			fake.setFakeAi(new PrivateStoreSellAI(fake));
			AdminSpawnedFakesStorage.save("clan".equals(loc) ? "seller_clan" : "seller", x, y, z);
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_spawncitizen") && command.contains(" "))
		{
			String loc = command.split(" ")[1];
			int x = activeChar.getX(), y = activeChar.getY(), z = activeChar.getZ();
			FakePlayer fake = "clan".equals(loc)
				? FakePlayerManager.spawnClanPlayer(x, y, z)
				: FakePlayerManager.spawnPlayer(x, y, z);
			if (fake == null)
			{
				activeChar.sendMessage("Clan fake players are disabled.");
				showFakeDashboard(activeChar);
				return true;
			}
			fake.setFakeAi(new CitizenAI(fake));
			AdminSpawnedFakesStorage.save("clan".equals(loc) ? "citizen_clan" : "citizen", x, y, z);
			showFakeDashboard(activeChar);
			return true;
		}

		if (command.startsWith("admin_spawnclass") && command.contains(" "))
		{
			handleSpawnClass(activeChar, command.split(" ")[1]);
			showFakeDashboard(activeChar);
			return true;
		}

		return true;
	}

	private static void handleInvite(Player activeChar)
	{
		if (!(activeChar.getTarget() instanceof FakePlayer))
		{
			activeChar.sendMessage("Target a fake player first.");
			return;
		}

		FakePlayer fake = (FakePlayer) activeChar.getTarget();
		if (fake.isInParty())
		{
			activeChar.sendMessage(fake.getName() + " is already in a party.");
			return;
		}

		if (!activeChar.isInParty())
			activeChar.setParty(new L2Party(activeChar, 0));

		L2Party party = activeChar.getParty();
		if (party.getMemberCount() >= 9)
		{
			activeChar.sendMessage("Party is full.");
			return;
		}

		fake.joinParty(party);
		fake.setFakeAi(new PartyFollowerAI(fake));
		PartyMode currentMode = resolveCurrentPartyMode(activeChar);
		fake.setPartyMode(currentMode);
		activeChar.sendMessage(fake.getName() + " joined your party in " + currentMode.name() + " mode.");
	}

	private static PartyMode resolveCurrentPartyMode(Player activeChar)
	{
		if (activeChar == null || !activeChar.isInParty())
			return PartyMode.FOLLOW;

		for (Player member : activeChar.getParty().getPartyMembers())
		{
			if (member instanceof FakePlayer)
				return ((FakePlayer) member).getPartyMode();
		}
		return PartyMode.FOLLOW;
	}

	private static void handleTeleport(Player activeChar)
	{
		if (!(activeChar.getTarget() instanceof FakePlayer))
		{
			activeChar.sendMessage("Target a fake player first.");
			return;
		}

		FakePlayer fake = (FakePlayer) activeChar.getTarget();
		fake.setXYZ(activeChar.getX(), activeChar.getY(), activeChar.getZ());
		fake.onTeleported();
		fake.broadcastUserInfo();
		activeChar.sendMessage(fake.getName() + " teleported to you.");
	}

	private static void handleHeal(Player activeChar)
	{
		if (!(activeChar.getTarget() instanceof FakePlayer))
		{
			activeChar.sendMessage("Target a fake player first.");
			return;
		}

		FakePlayer fake = (FakePlayer) activeChar.getTarget();
		fake.heal();
		activeChar.sendMessage(fake.getName() + " healed.");
	}

	private static void handleRebuff(Player activeChar)
	{
		if (!(activeChar.getTarget() instanceof FakePlayer))
		{
			activeChar.sendMessage("Target a fake player first.");
			return;
		}

		FakePlayer fake = (FakePlayer) activeChar.getTarget();
		FakeHelpers.giveBuffsByClass(fake);
		fake.heal();
		activeChar.sendMessage(fake.getName() + " rebuffed.");
	}

	private static void handleSetAI(Player activeChar, String mode)
	{
		if (!(activeChar.getTarget() instanceof FakePlayer))
		{
			activeChar.sendMessage("Target a fake player first.");
			return;
		}

		FakePlayer fake = (FakePlayer) activeChar.getTarget();

		switch (mode)
		{
			case "follow":
				fake.setFakeAi(new PartyFollowerAI(fake));
				fake.setPartyMode(PartyMode.FOLLOW);
				activeChar.sendMessage(fake.getName() + " -> FOLLOW");
				break;
			case "assist":
				fake.setFakeAi(new PartyFollowerAI(fake));
				fake.setPartyMode(PartyMode.ASSIST);
				activeChar.sendMessage(fake.getName() + " -> ASSIST");
				break;
			case "defend":
				fake.setFakeAi(new PartyFollowerAI(fake));
				fake.setPartyMode(PartyMode.DEFEND);
				activeChar.sendMessage(fake.getName() + " -> DEFEND");
				break;
			case "farm":
				if (fake.isInParty())
				{
					fake.setFakeAi(new PartyFollowerAI(fake));
					fake.setPartyMode(PartyMode.FARM);
					activeChar.sendMessage(fake.getName() + " -> Party FARM");
				}
				else
				{
					fake.setFakePvp(false);
					fake.setFakeFarm(true);
					fake.assignDefaultAI();
					activeChar.sendMessage(fake.getName() + " -> Standalone FARM");
				}
				break;
			case "stand":
				fake.setFakeAi(new PartyFollowerAI(fake));
				fake.setPartyMode(PartyMode.STAND);
				activeChar.sendMessage(fake.getName() + " -> STAND");
				break;
			case "citizen":
				if (!fake.isInsideZone(ZoneId.TOWN))
				{
					Location townLoc = MapRegionTable.getInstance().getLocationToTeleport(fake, MapRegionTable.TeleportType.TOWN);
					fake.setXYZ(townLoc.getX(), townLoc.getY(), townLoc.getZ());
					fake.onTeleported();
					fake.broadcastUserInfo();
					activeChar.sendMessage(fake.getName() + " teleported to nearest town.");
				}
				fake.setFakeAi(new CitizenAI(fake));
				activeChar.sendMessage(fake.getName() + " -> CitizenAI");
				break;
			case "combat":
			case "pvp":
				fake.setFakeFarm(false);
				fake.setFakePvp(true);
				fake.assignDefaultAI();
				activeChar.sendMessage(fake.getName() + " -> Standalone PvP (CombatAI)");
				break;
			default:
				activeChar.sendMessage("Unknown AI mode: " + mode);
		}
	}

	private static void handlePartyCommand(Player activeChar, String modeStr)
	{
		if (!activeChar.isInParty())
		{
			activeChar.sendMessage("You are not in a party.");
			return;
		}

		PartyMode mode;
		try { mode = PartyMode.valueOf(modeStr); }
		catch (IllegalArgumentException e) { activeChar.sendMessage("Unknown party mode: " + modeStr); return; }

		int count = 0;
		for (Player member : activeChar.getParty().getPartyMembers())
		{
			if (member instanceof FakePlayer)
			{
				FakePlayer fake = (FakePlayer) member;
				fake.setPartyMode(mode);
				if (!(fake.getFakeAi() instanceof PartyFollowerAI))
					fake.setFakeAi(new PartyFollowerAI(fake));
				count++;
			}
		}
		activeChar.sendMessage(count + " fake(s) set to " + mode.name() + " mode.");
	}

	private static void handlePartyDismiss(Player activeChar)
	{
		if (!activeChar.isInParty())
		{
			activeChar.sendMessage("You are not in a party.");
			return;
		}

		java.util.List<FakePlayer> fakesToRemove = new java.util.ArrayList<>();
		for (Player member : activeChar.getParty().getPartyMembers())
		{
			if (member instanceof FakePlayer)
				fakesToRemove.add((FakePlayer) member);
		}

		for (FakePlayer fake : fakesToRemove)
		{
			fake.leaveParty();
			fake.setFakeAi(new CitizenAI(fake));
		}
		activeChar.sendMessage(fakesToRemove.size() + " fake(s) dismissed from party.");
	}

	private static void handleSpawnClass(Player activeChar, String className)
	{
		int x = activeChar.getX(), y = activeChar.getY(), z = activeChar.getZ();
		FakePlayer fake = null;

		switch (className)
		{
			case "archer":       fake = FakePlayerManager.spawnArcher(x, y, z); fake.setFakePvp(true); break;
			case "nuker":        fake = FakePlayerManager.spawnNuker(x, y, z); fake.setFakePvp(true); break;
			case "warrior":      fake = FakePlayerManager.spawnWarrior(x, y, z); fake.setFakePvp(true); break;
			case "dagger":       fake = FakePlayerManager.spawnDagger(x, y, z); fake.setFakePvp(true); break;
			case "tanker":       fake = FakePlayerManager.spawnTanker(x, y, z); fake.setFakePvp(true); break;
			case "healer":       fake = FakePlayerManager.spawnHealer(x, y, z); fake.setFakePvp(true); break;
			case "archer_farm":  fake = FakePlayerManager.spawnArcher(x, y, z); fake.setFakeFarm(true); break;
			case "nuker_farm":   fake = FakePlayerManager.spawnNuker(x, y, z); fake.setFakeFarm(true); break;
			case "warrior_farm": fake = FakePlayerManager.spawnWarrior(x, y, z); fake.setFakeFarm(true); break;
			case "dagger_farm":  fake = FakePlayerManager.spawnDagger(x, y, z); fake.setFakeFarm(true); break;
			case "tanker_farm":  fake = FakePlayerManager.spawnTanker(x, y, z); fake.setFakeFarm(true); break;
			case "healer_farm":  fake = FakePlayerManager.spawnHealer(x, y, z); fake.setFakeFarm(true); break;
			// Clan PvP
			case "archer_clan":   fake = FakePlayerManager.spawnClanArcher(x, y, z); if (fake != null) fake.setFakePvp(true); break;
			case "nuker_clan":    fake = FakePlayerManager.spawnClanNuker(x, y, z); if (fake != null) fake.setFakePvp(true); break;
			case "warrior_clan":  fake = FakePlayerManager.spawnClanWarrior(x, y, z); if (fake != null) fake.setFakePvp(true); break;
			// Clan Farm
			case "archer_farm_clan":  fake = FakePlayerManager.spawnClanArcher(x, y, z); fake.setFakeFarm(true); break;
			case "nuker_farm_clan":   fake = FakePlayerManager.spawnClanNuker(x, y, z); fake.setFakeFarm(true); break;
			case "warrior_farm_clan": fake = FakePlayerManager.spawnClanWarrior(x, y, z); fake.setFakeFarm(true); break;
			// Clan City
			case "archer_city_clan":  fake = FakePlayerManager.spawnClanArcherCity(x, y, z); break;
			case "nuker_city_clan":   fake = FakePlayerManager.spawnClanNukerCity(x, y, z); break;
			case "warrior_city_clan": fake = FakePlayerManager.spawnClanWarriorCity(x, y, z); break;
			case "healer_city":      fake = FakePlayerManager.spawnHealer(x, y, z); break;
			default:
				   activeChar.sendMessage("Unknown class type: " + className);
				   return;
		   }

		   if (fake == null) {
			   activeChar.sendMessage("Failed to spawn fake player for class: " + className);
			   return;
		   }

		   fake.setLastCords(fake.getX(), fake.getY(), fake.getZ());
		   // For city fakes, use CitizenAI instead of default combat AI
		   if (className.endsWith("city_clan") || className.equals("healer_city")) {
			   fake.setFakeAi(new CitizenAI(fake));
		   } else {
			   fake.assignDefaultAI();
		   }

		   // If admin is already in party, auto-join and inherit current party command.
		   if (activeChar.isInParty() && activeChar.getParty().getMemberCount() < 9)
		   {
			   PartyMode currentMode = resolveCurrentPartyMode(activeChar);
			   fake.joinParty(activeChar.getParty());
			   fake.setFakeAi(new PartyFollowerAI(fake));
			   fake.setPartyMode(currentMode);
			   activeChar.sendMessage(fake.getName() + " auto-joined party in " + currentMode.name() + " mode.");
		   }
		   AdminSpawnedFakesStorage.save(className, x, y, z);
	   }

	private static void showHealerSpawnPage(Player activeChar)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/admin/fakeplayer_spawn_healers.htm");
		activeChar.sendPacket(html);
	}

	private static void handleSpawnHealerByClass(Player activeChar, String healerType)
	{
		int x = activeChar.getX(), y = activeChar.getY(), z = activeChar.getZ();
		ClassId classId;
		switch (healerType)
		{
			case "cardinal":       classId = ClassId.CARDINAL; break;
			case "evas_saint":     classId = ClassId.EVAS_SAINT; break;
			case "shillien_saint": classId = ClassId.SHILLIEN_SAINT; break;
			case "hierophant":     classId = ClassId.HIEROPHANT; break;
			case "doomcryer":      classId = ClassId.DOOMCRYER; break;
			default:
				activeChar.sendMessage("Unknown healer type: " + healerType);
				return;
		}

		FakePlayer fake = FakePlayerManager.spawnHealerByClass(x, y, z, classId);
		if (fake == null)
		{
			activeChar.sendMessage("Failed to spawn healer: " + healerType);
			return;
		}

		fake.setFakePvp(true);
		fake.setLastCords(fake.getX(), fake.getY(), fake.getZ());
		fake.assignDefaultAI();

		if (activeChar.isInParty() && activeChar.getParty().getMemberCount() < 9)
		{
			PartyMode currentMode = resolveCurrentPartyMode(activeChar);
			fake.joinParty(activeChar.getParty());
			fake.setFakeAi(new PartyFollowerAI(fake));
			fake.setPartyMode(currentMode);
			activeChar.sendMessage(fake.getName() + " (" + classId.name() + ") auto-joined party in " + currentMode.name() + " mode.");
		}
		else
		{
			activeChar.sendMessage("Spawned healer: " + classId.name());
		}
		AdminSpawnedFakesStorage.save("healer_" + healerType, x, y, z);
	}
	
	private static String resolveStorageType(FakePlayer fake)
	{
		if (fake.getFakeAi() instanceof PrivateStoreBuyAI)
			return "buyer";
		
		if (fake.getFakeAi() instanceof PrivateStoreSellAI)
			return "seller";
		
		if (fake.getFakeAi() instanceof CitizenAI)
		{
			if (isHealerClass(fake.getClassId()))
				return "healer_city";
			
			return "citizen";
		}
		
		if (isHealerClass(fake.getClassId()))
		{
			final String specificHealer = resolveSpecificHealerType(fake.getClassId());
			if (specificHealer != null)
				return specificHealer;
		}
		
		String baseType = resolveCombatArchetype(fake.getClassId());
		if (baseType == null)
			baseType = "citizen";
		
		if (fake.isFakeFarm())
			return baseType + "_farm";
		
		return baseType;
	}
	
	private static String resolveSpecificHealerType(ClassId classId)
	{
		switch (classId)
		{
			case CARDINAL:
				return "healer_cardinal";
			case EVAS_SAINT:
				return "healer_evas_saint";
			case SHILLIEN_SAINT:
				return "healer_shillien_saint";
			case HIEROPHANT:
				return "healer_hierophant";
			case DOOMCRYER:
				return "healer_doomcryer";
			default:
				return null;
		}
	}
	
	private static boolean isHealerClass(ClassId classId)
	{
		return classId == ClassId.CARDINAL
			|| classId == ClassId.EVAS_SAINT
			|| classId == ClassId.SHILLIEN_SAINT
			|| classId == ClassId.HIEROPHANT
			|| classId == ClassId.DOOMCRYER;
	}
	
	private static String resolveCombatArchetype(ClassId classId)
	{
		switch (classId)
		{
			case SAGGITARIUS:
			case MOONLIGHT_SENTINEL:
			case GHOST_SENTINEL:
				return "archer";
			case STORM_SCREAMER:
			case MYSTIC_MUSE:
			case ARCHMAGE:
			case SOULTAKER:
				return "nuker";
			case DUELIST:
			case GRAND_KHAVATARI:
			case DREADNOUGHT:
				return "warrior";
			case ADVENTURER:
			case WIND_RIDER:
			case GHOST_HUNTER:
				return "dagger";
			case PHOENIX_KNIGHT:
				return "tanker";
			case CARDINAL:
			case EVAS_SAINT:
			case SHILLIEN_SAINT:
			case HIEROPHANT:
			case DOOMCRYER:
				return "healer";
			default:
				return null;
		}
	}
}
