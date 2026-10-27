/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;

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
	 * The bank PIN keypad, which also opens when a PIN is set or changed. While it shows, clicks, game values and
	 * screenshots are not recorded: the clicked button and a picture of the shuffled keypad would give the PIN
	 * away.
	 */
	static boolean isPinKeypad(int groupId)
	{
		return groupId == InterfaceID.BANKPIN_KEYPAD;
	}

	/**
	 * The welcome screen after logging in. No screenshot is saved while it shows; everything else is recorded as
	 * usual.
	 */
	static boolean isWelcomeScreen(int groupId)
	{
		return groupId == InterfaceID.WELCOME_SCREEN;
	}

	/**
	 * Whether a frame drawn in this game state may be saved as a screenshot: only while logged in or loading
	 * the next area. A screenshot asked for just before logging out can be drawn on the login screen, which
	 * shows the character's name; that frame, and any other drawn on the login screen, while logging in,
	 * hopping or with the connection lost, is never saved.
	 */
	static boolean mayKeepFrame(GameState state)
	{
		return state == GameState.LOGGED_IN || state == GameState.LOADING;
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
		return ownPattern(ownName).matcher(text).replaceAll(Matcher.quoteReplacement(YOU));
	}

	/** The last own name and its pattern: the name rarely changes, and scrub runs on every line. */
	private static volatile Object[] ownCache = {null, null};

	/**
	 * The own name as a whole name: "Bob" is not replaced inside "Bobby", which would leave half of another player's
	 * name behind and stop it being recognized.
	 */
	private static Pattern ownPattern(String ownName)
	{
		Object[] cached = ownCache;
		if (ownName.equals(cached[0]))
		{
			return (Pattern) cached[1];
		}
		Pattern pattern = Pattern.compile("(?<![\\w])" + namePattern(ownName) + "(?![\\w])",
			Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		ownCache = new Object[]{ownName, pattern};
		return pattern;
	}

	/** What another player's name becomes in text that may hold one (the Debug page's interface contents). */
	static final String PLAYER = "<player>";

	/** A name as a pattern, its words joined by any of the game's separators. */
	private static String namePattern(String name)
	{
		String[] words = name.trim().split("[\\s\\u00A0_-]+");
		StringBuilder pattern = new StringBuilder();
		for (int i = 0; i < words.length; i++)
		{
			if (i > 0)
			{
				pattern.append("[\\s\\u00A0_-]+");
			}
			pattern.append(Pattern.quote(words[i]));
		}
		return pattern.toString();
	}

	/**
	 * One pattern for all these names (friends, ignores, chat channel and clan members, players in view), longest
	 * first so a name inside another is not cut short; null when there are none. Built once and reused, since it
	 * runs over every line of text on screen.
	 */
	static Pattern namesPattern(Collection<String> names)
	{
		List<String> sorted = new ArrayList<>();
		for (String name : names)
		{
			if (name != null && !name.trim().isEmpty())
			{
				sorted.add(name);
			}
		}
		if (sorted.isEmpty())
		{
			return null;
		}
		sorted.sort((a, b) -> b.length() - a.length());
		StringBuilder pattern = new StringBuilder("(?<![\\w])(?:");
		for (int i = 0; i < sorted.size(); i++)
		{
			if (i > 0)
			{
				pattern.append('|');
			}
			pattern.append(namePattern(sorted.get(i)));
		}
		pattern.append(")(?![\\w])");
		return Pattern.compile(pattern.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	/** The text with every name the pattern knows replaced by {@link #PLAYER}. */
	static String withoutNames(String text, Pattern names)
	{
		if (text == null || names == null)
		{
			return text;
		}
		return names.matcher(text).replaceAll(Matcher.quoteReplacement(PLAYER));
	}

	/**
	 * Account names and ids other software logs about the player, as in RuneLite's own "Discord RPC service is ready
	 * with user ..." or a plugin's "user=31654": the value after {@code user}, {@code username} or {@code account}
	 * followed by {@code =} or {@code :}, or after "with user" / "as user", becomes {@link #WITHHELD}.
	 */
	private static final Pattern ACCOUNT = Pattern.compile(
		"(?i)\\b((?:user(?:name)?|account)\\s*[=:]\\s*|(?:with|as)\\s+user\\s+)([^\\s,;)\\]}]*[^\\s,;)\\]}.])");
	static final String WITHHELD = "<withheld>";

	static String scrubAccounts(String text)
	{
		return text == null ? null : ACCOUNT.matcher(text).replaceAll("$1" + Matcher.quoteReplacement(WITHHELD));
	}

	/** Whether a menu entry's target is another player, whose name must not be recorded. */
	static boolean targetsPlayer(MenuAction action)
	{
		return action != null && action.name().toUpperCase(Locale.ROOT).contains("PLAYER");
	}

	/**
	 * Whether a menu entry's target may be written. Never for an entry on a player; and for an entry on an interface
	 * (chat box, friends and clan lists) only when it is about an item, since there the target is often a player's
	 * name: right-clicking a chat line or a friend names them.
	 */
	static boolean keepsTarget(MenuAction action, int itemId)
	{
		if (action == null || targetsPlayer(action))
		{
			return false;
		}
		String name = action.name();
		// RUNELITE entries are plugins' own, often on a friend or a chat line (Friend Notes' "Add Note").
		boolean onInterface = name.startsWith("CC_OP") || name.startsWith("WIDGET_") || name.startsWith("RUNELITE");
		return !onInterface || itemId > 0;
	}
}
