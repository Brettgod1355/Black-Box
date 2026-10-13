/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import net.runelite.api.ChatMessageType;
import net.runelite.api.MenuAction;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Recordings keep game text and leave other players out. */
public class PrivacyTest
{
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
		assertEquals("<you> has a full cargo hold.", Privacy.scrub("Air Marshal has a full cargo hold.", "Air Marshal"));
		assertEquals("Well done, <you>!", Privacy.scrub("Well done, air marshal!", "Air Marshal"));
		assertEquals("<you> and <you>", Privacy.scrub("Air_Marshal and AIR-MARSHAL", "Air Marshal"));
		assertEquals("Airship Marshal stays", Privacy.scrub("Airship Marshal stays", "Air Marshal"));
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
}
