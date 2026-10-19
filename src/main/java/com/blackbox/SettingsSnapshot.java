/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.runelite.client.config.ConfigItemDescriptor;

/**
 * The current settings of a watched plugin as one JSON object, with the withholdings that keep a recording
 * safe to share: an item the plugin marks {@code secret} is written as {@code <secret>}, a hidden item is
 * left out, and a free-text value that looks like a link or a token (by content or by key name) is written as
 * {@code <withheld>}. Numbers, booleans, enums, colors and keybinds are always written as the client stores them.
 */
final class SettingsSnapshot
{
	static final String SECRET = "<secret>";
	static final String WITHHELD = "<withheld>";
	/** Key names whose free-text value is never written, whatever it holds. */
	private static final Pattern RISKY_KEY = Pattern.compile("webhook|url|token|secret|password|passphrase|apikey|api_key|api-key|bearer|auth", Pattern.CASE_INSENSITIVE);

	private SettingsSnapshot()
	{
	}

	/**
	 * @param items the plugin's config items, from its descriptor
	 * @param value the stored value of a key as the client holds it (a string), or null when unset
	 */
	static JsonObject of(Collection<ConfigItemDescriptor> items, Function<String, String> value)
	{
		JsonObject values = new JsonObject();
		for (ConfigItemDescriptor item : items)
		{
			if (item.getItem().hidden())
			{
				continue;
			}
			String key = item.key();
			if (item.getItem().secret())
			{
				values.addProperty(key, SECRET);
				continue;
			}
			String stored = value.apply(key);
			if (stored == null)
			{
				continue;
			}
			boolean freeText = item.getType() == String.class;
			values.addProperty(key, freeText ? text(key, stored) : stored);
		}
		return values;
	}

	/** A free-text value, or {@code <withheld>} when the key or the text looks like a link or a token. */
	static String text(String key, String stored)
	{
		if (RISKY_KEY.matcher(key).find())
		{
			return WITHHELD;
		}
		String lower = stored.toLowerCase(Locale.ROOT);
		if (lower.contains("://") || lower.startsWith("www.") || looksLikeToken(stored))
		{
			return WITHHELD;
		}
		return stored;
	}

	/** One long run of letters, digits and a few symbols with no space: how keys and tokens look. */
	private static boolean looksLikeToken(String s)
	{
		if (s.length() < 24 || s.indexOf(' ') >= 0)
		{
			return false;
		}
		int letters = 0, digits = 0;
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			if (Character.isLetter(c))
			{
				letters++;
			}
			else if (Character.isDigit(c))
			{
				digits++;
			}
			else if ("-_./=+:".indexOf(c) < 0)
			{
				return false;
			}
		}
		return letters > 0 && digits > 0;
	}
}
