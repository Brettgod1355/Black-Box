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
 * A plugin's current settings as one JSON object, with the withholdings that keep a recording safe to share:
 * an item the plugin marks {@code secret} is written as {@code <secret>}, a hidden item is left out, and a
 * free-text value that is a webhook, a key or a token (by its key name or by what it looks like) is written as
 * {@code <withheld>}. Everything else is written as the client stores it, free text included: highlight lists,
 * block lists and names typed into a plugin are exactly what a developer needs to see (owner, 2026-10-20).
 */
final class SettingsSnapshot
{
	static final String SECRET = "<secret>";
	static final String WITHHELD = "<withheld>";
	/** Key names whose free-text value is never written, whatever it holds: where webhooks, keys and passwords live. */
	private static final Pattern RISKY_KEY = Pattern.compile("webhook|hook|token|secret|password|passphrase|apikey|api_key|api-key|bearer|auth|credential", Pattern.CASE_INSENSITIVE);
	/** Links that carry a credential in them: Discord and other webhooks, anything with a token or key in the path. */
	private static final Pattern RISKY_LINK = Pattern.compile("webhook|hook|token|key=|secret|api/|apikey", Pattern.CASE_INSENSITIVE);

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

	/** A free-text value, or {@code <withheld>} when it is a webhook, a key or a token. Ordinary links and names stay. */
	static String text(String key, String stored)
	{
		if (RISKY_KEY.matcher(key).find())
		{
			return WITHHELD;
		}
		String lower = stored.toLowerCase(Locale.ROOT);
		if (lower.contains("://") && RISKY_LINK.matcher(lower).find())
		{
			return WITHHELD;
		}
		if (looksLikeToken(stored))
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
