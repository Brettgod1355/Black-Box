/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.runelite.client.util.Filepath;

/**
 * What a player sends a developer: one zip beside the recording holding {@code events.jsonl} and
 * {@code client-log.txt} and nothing else (never the screenshots), with a short README saying what the files
 * hold and never hold. Optionally only the
 * last minutes, kept whole enough to read: the session line, the last {@code watch} line and the last
 * {@code settings} line per plugin before the cut, then everything after it.
 */
final class ShareBundle
{
	static final String README = "This is a Black Box recording from RuneLite, for the developer who asked for it.\n"
		+ "It holds: game messages to you, your position and animation, your items, objects and NPCs coming and going,\n"
		+ "game values, XP, interfaces opening and closing, your clicks, sound effects, frames per second, which plugins\n"
		+ "were switched and which settings changed, the settings of the plugins you chose to watch (secret ones withheld),\n"
		+ "and the ground and NPCs around you.\n"
		+ "the settings of every plugin (webhooks, keys and secrets withheld), and the ground and NPCs around you.\n"
		+ "client-log.txt holds the client's own log lines for the same time, as RuneLite's client.log would, caught\n"
		+ "inside the client with your name and home folder replaced.\n"
		+ "It never holds: other players' names or chat, your bank PIN, your character name (written as <you>), screenshots,\n"
		+ "webhooks, keys or passwords, or anything outside the game client.\n"
		+ "You can open both files in any text editor and read every line before sending them.\n";

	private ShareBundle()
	{
	}

	/**
	 * The lines to share: all of them when {@code cutoffMs} is 0, else the context lines and those at or after it.
	 * Lines that are not JSON are kept as they are when sharing everything, dropped when trimming.
	 */
	static List<String> trim(Gson gson, BufferedReader in, long cutoffMs) throws IOException
	{
		List<String> out = new ArrayList<>();
		String session = null;
		String watch = null;
		Map<String, String> settings = new LinkedHashMap<>();
		List<String> after = new ArrayList<>();
		String text;
		while ((text = in.readLine()) != null)
		{
			if (text.trim().isEmpty())
			{
				continue;
			}
			if (cutoffMs <= 0)
			{
				out.add(text);
				continue;
			}
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
			long ms = o.has("ms") ? o.get("ms").getAsLong() : 0;
			if ("session".equals(kind))
			{
				session = text;
			}
			else if (ms >= cutoffMs)
			{
				after.add(text);
			}
			else if ("watch".equals(kind))
			{
				watch = text;
			}
			else if ("settings".equals(kind) && o.has("plugin"))
			{
				settings.put(o.get("plugin").getAsString(), text);
			}
		}
		if (cutoffMs <= 0)
		{
			return out;
		}
		if (session != null)
		{
			out.add(session);
		}
		if (watch != null)
		{
			out.add(watch);
		}
		out.addAll(settings.values());
		out.addAll(after);
		return out;
	}

	/** Writes {@code <name>-share.zip} (or {@code <name>-last<n>min-share.zip}) next to the recording; returns it. */
	static Filepath write(Gson gson, Filepath recording, String name, int lastMinutes, long recordingLengthMs) throws IOException
	{
		long cutoff = lastMinutes <= 0 ? 0 : Math.max(0, recordingLengthMs - lastMinutes * 60_000L);
		List<String> lines;
		try (BufferedReader in = recording.join(SessionLog.EVENTS_FILE).openBufferedReader())
		{
			lines = trim(gson, in, cutoff);
		}
		String fileName = name + (lastMinutes <= 0 ? "" : "-last" + lastMinutes + "min") + "-share.zip";
		Filepath zip = recording.getParent().join(fileName);
		try (OutputStream raw = zip.openOutputStream(); ZipOutputStream out = new ZipOutputStream(raw))
		{
			out.putNextEntry(new ZipEntry("README.txt"));
			out.write(README.getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
			out.putNextEntry(new ZipEntry(SessionLog.EVENTS_FILE));
			for (String line : lines)
			{
				out.write(line.getBytes(StandardCharsets.UTF_8));
				out.write('\n');
			}
			out.closeEntry();
			Filepath clientLog = recording.join(ClientLogTap.FILE);
			if (clientLog.isFile())
			{
				out.putNextEntry(new ZipEntry(ClientLogTap.FILE));
				try (InputStream in = clientLog.openInputStream())
				{
					byte[] buffer = new byte[8192];
					int n;
					while ((n = in.read(buffer)) > 0)
					{
						out.write(buffer, 0, n);
					}
				}
				out.closeEntry();
			}
		}
		return zip;
	}
}
