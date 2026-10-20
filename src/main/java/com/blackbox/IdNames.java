/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;

/**
 * The names behind the numbers: RuneLite's constant classes (varbits, varps, interfaces, sounds, animations,
 * spot animations, NPCs, objects, items, inventories) as a table, {@code names.tsv.gz}, generated from the
 * client's API at build time (never read by reflection at run time). An export gets a {@code names.txt} with
 * the names of every id the exported lines mention, so a reader sees "19252 SAILING_SIDEPANEL_BOAT_FETIDWATER_RESISTANT"
 * and not just 19252.
 */
final class IdNames
{
	static final String RESOURCE = "names.tsv.gz";
	static final String FILE = "names.txt";

	private final Map<String, Map<Integer, String>> byKind = new HashMap<>();

	IdNames()
	{
	}

	/** Loads the bundled table; a few megabytes of text, so done once and off the client thread. */
	static IdNames load() throws IOException
	{
		try (InputStream in = IdNames.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				throw new IOException(RESOURCE + " is missing from the jar");
			}
			return read(new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8)));
		}
	}

	static IdNames read(BufferedReader in) throws IOException
	{
		IdNames names = new IdNames();
		String line;
		while ((line = in.readLine()) != null)
		{
			int a = line.indexOf('\t');
			int b = a < 0 ? -1 : line.indexOf('\t', a + 1);
			if (b < 0)
			{
				continue;
			}
			try
			{
				names.byKind.computeIfAbsent(line.substring(0, a), k -> new HashMap<>())
					.putIfAbsent(Integer.parseInt(line.substring(a + 1, b)), line.substring(b + 1));
			}
			catch (NumberFormatException e)
			{
				// a header or a bad line; skip
			}
		}
		return names;
	}

	/** The name of an id of a kind (varbit, varp, interface, sound, anim, spotanim, npc, object, item, inventory), or null. */
	String lookup(String kind, int id)
	{
		Map<Integer, String> table = byKind.get(kind);
		return table == null ? null : table.get(id);
	}

	int size()
	{
		int n = 0;
		for (Map<Integer, String> table : byKind.values())
		{
			n += table.size();
		}
		return n;
	}

	/**
	 * The names of every id the given event lines mention, one per line as {@code kind id NAME}, grouped by kind
	 * and sorted by id. Ids the table does not know are listed with a question mark, so the reader knows they
	 * were looked for.
	 */
	List<String> tableFor(Gson gson, List<String> eventLines)
	{
		Map<String, TreeSet<Integer>> wanted = new TreeMap<>();
		for (String text : eventLines)
		{
			JsonObject o;
			try
			{
				o = gson.fromJson(text, JsonObject.class);
			}
			catch (JsonSyntaxException e)
			{
				continue;
			}
			if (o == null || !o.has("e"))
			{
				continue;
			}
			String kind = o.get("e").getAsString();
			switch (kind)
			{
				case "var":
					want(wanted, "varbit", o, "varbit");
					want(wanted, "varp", o, "varp");
					break;
				case "open":
				case "close":
					want(wanted, "interface", o, "interface");
					break;
				case "sound":
					want(wanted, "sound", o, "id");
					want(wanted, "npc", o, "npc");
					break;
				case "npcAdd":
				case "npcGone":
				case "npcSay":
				case "npcTarget":
				case "hit":
					want(wanted, "npc", o, "npc");
					break;
				case "npcAnim":
					want(wanted, "npc", o, "npc");
					want(wanted, "anim", o, "anim");
					break;
				case "tick":
					want(wanted, "anim", o, "anim");
					break;
				case "objectAdd":
				case "objectGone":
					want(wanted, "object", o, "id");
					break;
				case "items":
					want(wanted, "inventory", o, "container");
					if (o.has("items") && o.get("items").isJsonArray())
					{
						for (JsonElement pair : o.getAsJsonArray("items"))
						{
							if (pair.isJsonArray() && pair.getAsJsonArray().size() > 0)
							{
								int id = pair.getAsJsonArray().get(0).getAsInt();
								if (id >= 0)
								{
									wanted.computeIfAbsent("item", k -> new TreeSet<>()).add(id);
								}
							}
						}
					}
					break;
				case "graphic":
					if (o.has("ids") && o.get("ids").isJsonArray())
					{
						for (JsonElement id : o.getAsJsonArray("ids"))
						{
							wanted.computeIfAbsent("spotanim", k -> new TreeSet<>()).add(id.getAsInt());
						}
					}
					break;
				default:
					break;
			}
		}
		List<String> out = new ArrayList<>();
		for (Map.Entry<String, TreeSet<Integer>> entry : wanted.entrySet())
		{
			for (int id : entry.getValue())
			{
				String name = lookup(entry.getKey(), id);
				out.add(entry.getKey() + " " + id + " " + (name == null ? "?" : name));
			}
		}
		return out;
	}

	private static void want(Map<String, TreeSet<Integer>> wanted, String kind, JsonObject o, String field)
	{
		if (o.has(field) && !o.get(field).isJsonNull())
		{
			int id = o.get(field).getAsInt();
			if (id >= 0)
			{
				wanted.computeIfAbsent(kind, k -> new TreeSet<>()).add(id);
			}
		}
	}

	/** For tests: a table from a few lines. */
	static IdNames of(String tsv) throws IOException
	{
		return read(new BufferedReader(new java.io.StringReader(tsv)));
	}

	/** Unused-array guard for Gson's JsonArray import in some JDKs. */
	static int count(JsonArray array)
	{
		return array == null ? 0 : array.size();
	}
}
