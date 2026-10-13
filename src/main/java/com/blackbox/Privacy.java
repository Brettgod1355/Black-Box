/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MenuAction;

/**
 * What a recording may hold about people. Recordings are meant to be shared with whoever helps test a
 * plugin, so they keep game text and leave out other players: no public, private, clan, friends or trade
 * chat, no broadcasts (which name players), no player menu targets. The player's own name is replaced
 * by "&lt;you&gt;" wherever the game writes it. Pure, so it is tested without a client.
 */
final class Privacy
{
	/** Chat the recording keeps: lines the game itself writes, never another player's words. */
	static final Set<ChatMessageType> RECORDED_CHAT = EnumSet.of(
		ChatMessageType.GAMEMESSAGE,
		ChatMessageType.ENGINE,
		ChatMessageType.SPAM,
		ChatMessageType.CONSOLE,
		ChatMessageType.ITEM_EXAMINE,
		ChatMessageType.NPC_EXAMINE,
		ChatMessageType.OBJECT_EXAMINE,
		ChatMessageType.WELCOME,
		ChatMessageType.DIALOG,
		ChatMessageType.MESBOX,
		ChatMessageType.NPC_SAY,
		ChatMessageType.DIDYOUKNOW,
		ChatMessageType.LEVELUPMESSAGE);

	static final String YOU = "<you>";

	private Privacy()
	{
	}

	static boolean recordsChat(ChatMessageType type)
	{
		return type != null && RECORDED_CHAT.contains(type);
	}

	/**
	 * The text with the player's own name replaced by {@link #YOU}, matched without case and with the game's
	 * non-breaking spaces, underscores and hyphens treated as the same separator.
	 */
	static String scrub(String text, String ownName)
	{
		if (text == null || ownName == null || ownName.trim().isEmpty())
		{
			return text;
		}
		String[] words = ownName.trim().split("[\\s\\u00A0_-]+");
		StringBuilder pattern = new StringBuilder();
		for (int i = 0; i < words.length; i++)
		{
			if (i > 0)
			{
				pattern.append("[\\s\\u00A0_-]+");
			}
			pattern.append(Pattern.quote(words[i]));
		}
		Matcher m = Pattern.compile(pattern.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text);
		return m.replaceAll(Matcher.quoteReplacement(YOU));
	}

	/** Whether a menu entry's target is another player, whose name must not be recorded. */
	static boolean targetsPlayer(MenuAction action)
	{
		return action != null && action.name().toUpperCase(Locale.ROOT).contains("PLAYER");
	}
}
