/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.runelite.client.config.ConfigItemDescriptor;

/**
 * A plugin's current settings as one JSON object, with the withholdings that keep a recording safe to share:
 * an item the plugin marks {@code secret} is written as {@code <secret>}, a hidden item is left out, and a
 * free-text value that is a webhook, a key or a token (by its key name or by what it looks like) is written as
 * {@code <withheld>}. Everything else is written as the client stores it, free text included: highlight lists,
 * block lists and names typed into a plugin are exactly what a developer needs to see.
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
	 * The keys of a plugin's free-text settings (format 11): typed text, where a webhook, a key or a name can sit
	 * under any key name. Recorded as they are, and left out of an export unless the player picks them. Hidden and
	 * secret items are not listed: their values never reach a recording as text.
	 */
	static JsonArray textKeys(Collection<ConfigItemDescriptor> items)
	{
		JsonArray keys = new JsonArray();
		for (ConfigItemDescriptor item : items)
		{
			if (item.getType() == String.class && !item.getItem().hidden() && !item.getItem().secret())
			{
				keys.add(item.key());
			}
		}
		return keys;
	}

	/** What a text setting the player did not pick reads as in an export. */
	static final String NOT_INCLUDED = "<text, not included>";

	/** A color as RuneLite stores it ({@code #AARRGGBB}): a plain value, safe in an old recording's export. */
	private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{6,8}");

	/**
	 * One events line as an export holds it: on a {@code settings} or {@code setting} line, every text setting's
	 * value the player did not pick ({@code group.key} in {@code include}) is {@link #NOT_INCLUDED}. From format 11 a
	 * line says which keys are text ({@code text}); an older line (no {@code text} field; {@code marked} false)
	 * is judged by its values, and any value that is not a plain number, flag, enum, point or color counts as text.
	 * Values already withheld stay as they are. Returns the same object, changed in place, or untouched.
	 */
	static JsonObject forExport(JsonObject line, boolean marked, Set<String> include)
	{
		String kind = line.has("e") ? line.get("e").getAsString() : "";
		String group = line.has("group") ? line.get("group").getAsString() : "";
		if ("settings".equals(kind) && line.has("values") && line.get("values").isJsonObject())
		{
			JsonObject values = line.getAsJsonObject("values");
			for (Map.Entry<String, JsonElement> entry : values.entrySet())
			{
				boolean text = marked ? listed(line, entry.getKey()) : looksLikeText(entry.getValue());
				if (text && !withheld(entry.getValue()) && !include.contains(group + "." + entry.getKey()))
				{
					entry.setValue(new JsonPrimitive(NOT_INCLUDED));
				}
			}
		}
		else if ("setting".equals(kind) && line.has("value"))
		{
			boolean text = marked
				? line.has("text") && line.get("text").getAsBoolean()
				: looksLikeText(line.get("value"));
			String key = line.has("key") ? line.get("key").getAsString() : "";
			if (text && !withheld(line.get("value")) && !include.contains(group + "." + key))
			{
				line.addProperty("value", NOT_INCLUDED);
			}
		}
		return line;
	}

	private static boolean listed(JsonObject line, String key)
	{
		if (!line.has("text") || !line.get("text").isJsonArray())
		{
			return false;
		}
		for (JsonElement k : line.getAsJsonArray("text"))
		{
			if (key.equals(k.getAsString()))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean looksLikeText(JsonElement value)
	{
		if (value == null || !value.isJsonPrimitive())
		{
			return false;
		}
		String s = value.getAsString();
		return !s.isEmpty() && !PLAIN_VALUE.matcher(s).matches() && !COLOR.matcher(s).matches();
	}

	/** A value Black Box already wrote as withheld, secret or hashed. */
	private static boolean withheld(JsonElement value)
	{
		if (value == null || !value.isJsonPrimitive())
		{
			return true;
		}
		String s = value.getAsString();
		return s.equals(SECRET) || s.equals(WITHHELD) || s.equals(NOT_INCLUDED) || (s.startsWith("<") && s.contains(" chars, sha256 "));
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

	/**
	 * A value whose shape cannot hold a name or an account: a short number, true or false, an enum constant, a point
	 * ({@code 12:34}) or a size ({@code 765x503}). Ten digits at most, since an account hash is a longer number.
	 */
	private static final Pattern PLAIN_VALUE = Pattern.compile(
		"-?\\d{1,10}(\\.\\d+)?|true|false|[A-Z][A-Z0-9_]*|-?\\d{1,6}:-?\\d{1,6}|\\d{1,6}x\\d{1,6}");

	/**
	 * A value the plugin keeps outside its visible settings (a hidden item, or a key no item declares, format 9).
	 * These hold the saved login name, a party passphrase, recent friends chats, so only a plain number, flag, enum,
	 * point or size is written as it is; anything else is {@code <N chars, sha256 abc123def456>}, which still shows
	 * that it changed and compares between recordings without saying what it is.
	 */
	static String other(String key, String stored)
	{
		if (RISKY_KEY.matcher(key).find())
		{
			return WITHHELD;
		}
		if (PLAIN_VALUE.matcher(stored).matches())
		{
			return stored;
		}
		return hashed(stored);
	}

	/** {@code <N chars, sha256 abc123def456>}: the length and a short hash, never the text. */
	static String hashed(String text)
	{
		return "<" + text.length() + " chars, sha256 " + Hashes.short12(text) + ">";
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
