/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Recordings keep game text and leave other players out. */
public class PrivacyTest
{
	@Test
	public void namesToHideSkipAMissingName()
	{
		// A friend with no previous name threw on every tick with Interface contents on.
		java.util.Set<String> names = new java.util.TreeSet<>();
		DebugProbes.addName(names, "Cow31337Killer");
		DebugProbes.addName(names, null);
		DebugProbes.addName(names, "");
		assertEquals(java.util.Collections.singleton("Cow31337Killer"), names);
	}

	@Test
	public void onlyGameWrittenChatIsKept()
	{
		assertTrue(Privacy.recordsChat(ChatMessageType.GAMEMESSAGE));
		assertTrue(Privacy.recordsChat(ChatMessageType.SPAM));
		assertTrue(Privacy.recordsChat(ChatMessageType.NPC_SAY));
		for (ChatMessageType people : new ChatMessageType[]{ChatMessageType.PUBLICCHAT, ChatMessageType.PRIVATECHAT,
			ChatMessageType.PRIVATECHATOUT, ChatMessageType.FRIENDSCHAT, ChatMessageType.CLAN_CHAT,
			ChatMessageType.CLAN_GUEST_CHAT, ChatMessageType.CLAN_GIM_CHAT, ChatMessageType.MODCHAT,
			ChatMessageType.TRADE, ChatMessageType.TRADEREQ, ChatMessageType.BROADCAST, ChatMessageType.AUTOTYPER,
			ChatMessageType.LOGINLOGOUTNOTIFICATION, ChatMessageType.FRIENDNOTIFICATION})
		{
			assertFalse(people.name(), Privacy.recordsChat(people));
		}
		assertFalse(Privacy.recordsChat(null));
	}

	@Test
	public void theOwnNameIsReplacedHoweverTheGameSpellsTheGap()
	{
		// Made-up names only: the plugin scrubs whoever is logged in. Cow31337Killer is the game's own joke NPC.
		assertEquals("<you> has a full cargo hold.", Privacy.scrub("Cow31337Killer has a full cargo hold.", "Cow31337Killer"));
		assertEquals("Well done, <you>!", Privacy.scrub("Well done, cow31337killer!", "Cow31337Killer"));
		// A name with a gap, which the game writes as a space, a non-breaking space (second line), an underscore or a
		// hyphen; a near miss stays.
		assertEquals("<you> has a full cargo hold.", Privacy.scrub("Salty Pete has a full cargo hold.", "Salty Pete"));
		assertEquals("Well done, <you>!", Privacy.scrub("Well done, salty pete!", "Salty Pete"));
		assertEquals("<you> and <you>", Privacy.scrub("Salty_Pete and SALTY-PETE", "Salty Pete"));
		assertEquals("Saltwater Pete stays", Privacy.scrub("Saltwater Pete stays", "Salty Pete"));
		assertEquals("no name known", Privacy.scrub("no name known", null));
		assertEquals("a $ and \\ survive", Privacy.scrub("a $ and \\ survive", "Bob"));
	}

	@Test
	public void clicksOnPlayersLoseTheirTarget()
	{
		assertTrue(Privacy.targetsPlayer(MenuAction.PLAYER_FIRST_OPTION));
		assertTrue(Privacy.targetsPlayer(MenuAction.WIDGET_TARGET_ON_PLAYER));
		assertFalse(Privacy.targetsPlayer(MenuAction.GAME_OBJECT_FIRST_OPTION));
		assertFalse(Privacy.targetsPlayer(MenuAction.NPC_FIRST_OPTION));
		assertFalse(Privacy.targetsPlayer(null));
	}

	@Test
	public void theBankPinKeypadPausesRecording()
	{
		assertTrue(Privacy.isPinKeypad(InterfaceID.BANKPIN_KEYPAD));
		assertTrue(Privacy.isPinKeypad(213));
		assertFalse(Privacy.isPinKeypad(InterfaceID.BANKMAIN));
	}

	@Test
	public void theWelcomeScreenIsKnown()
	{
		assertTrue(Privacy.isWelcomeScreen(InterfaceID.WELCOME_SCREEN));
		assertTrue(Privacy.isWelcomeScreen(378));
		assertFalse(Privacy.isWelcomeScreen(InterfaceID.BANKPIN_KEYPAD));
	}

	@Test
	public void framesAreKeptOnlyInTheGameWorld()
	{
		assertTrue(Privacy.mayKeepFrame(GameState.LOGGED_IN));
		assertTrue(Privacy.mayKeepFrame(GameState.LOADING));
		for (GameState state : GameState.values())
		{
			if (state != GameState.LOGGED_IN && state != GameState.LOADING)
			{
				assertFalse(state.name(), Privacy.mayKeepFrame(state));
			}
		}
		assertFalse(Privacy.mayKeepFrame(null));
	}

	@Test
	public void menuTargetsOnPlayersAndOnInterfacesWithoutAnItemAreLeftOut()
	{
		assertFalse(Privacy.keepsTarget(net.runelite.api.MenuAction.PLAYER_FIRST_OPTION, -1));
		assertFalse(Privacy.keepsTarget(net.runelite.api.MenuAction.CC_OP, -1));
		assertTrue(Privacy.keepsTarget(net.runelite.api.MenuAction.CC_OP, 995));
		assertTrue(Privacy.keepsTarget(net.runelite.api.MenuAction.NPC_FIRST_OPTION, -1));
		assertTrue(Privacy.keepsTarget(net.runelite.api.MenuAction.GAME_OBJECT_FIRST_OPTION, -1));
		assertFalse(Privacy.keepsTarget(null, 995));
		// A plugin's own entry (Friend Notes' "Add Note" on a friend) loses its target unless it is about an item.
		assertFalse(Privacy.keepsTarget(net.runelite.api.MenuAction.RUNELITE, -1));
		assertTrue(Privacy.keepsTarget(net.runelite.api.MenuAction.RUNELITE, 995));
	}
}
