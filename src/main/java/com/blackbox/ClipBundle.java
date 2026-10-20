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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.runelite.client.util.Filepath;

/**
 * A clip: what a player sends a developer. One zip holding the last minutes of whatever recordings cover
 * them (a bug just before a logout lives in the previous recording), each recording's slice starting with
 * its session, plugins, watch and settings lines so the context is whole; the matching slice of the client's
 * log; a README saying what the files hold and never hold; and the pictures of those minutes only when the
 * player turned that on. Never the chat, never the PIN, never other players.
 */
final class ClipBundle
{
	static final String CLIPS_DIR = "clips";
	static final String README = "This is a Black Box clip from RuneLite, for the developer who asked for it.\n"
		+ "events.jsonl holds: game messages to you, your position and animation, your items, objects and NPCs coming\n"
		+ "and going and where they were, game values, XP, interfaces opening and closing, your clicks, sound effects,\n"
		+ "frames per second, which plugins were installed and switched, every plugin's settings (webhooks, keys and\n"
		+ "secrets withheld), and the ground around you. Each recording's slice starts with its first lines, so the\n"
		+ "settings in force are always in the file.\n"
		+ "client-log.txt holds the client's own log lines for the same minutes, plus every warning and error since the\n"
		+ "client started, as RuneLite's client.log would, caught inside the client with your name and home folder replaced.\n"
		+ "names.txt gives RuneLite's name for every varbit, interface, sound, animation, NPC, object and item id the\n"
		+ "events mention.\n"
		+ "shots/ holds pictures of the game view at the marked moments, only if you turned that on; the chat box is\n"
		+ "blacked out in them.\n"
		+ "It never holds: other players' chat, your bank PIN, your character name (written as <you>), webhooks, keys or\n"
		+ "passwords, or anything outside the game client.\n"
		+ "You can open every file in any text editor or image viewer and look before sending.\n"
		+ "How to read it, for a person or an AI assistant: https://github.com/Brettgod1355/runelite-black-box/blob/main/READING.md\n"
		+ "Every line kind and field: https://github.com/Brettgod1355/runelite-black-box/blob/main/FORMAT.md\n"
		+ "Running it through a plugin in a test: https://github.com/Brettgod1355/runelite-black-box/blob/main/REPLAY.md\n";
	private static final DateTimeFormatter LOG_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/** One recording that may contribute to a clip. */
	static final class Source
	{
		final String name;
		final Filepath dir;
		/** Wall clock, milliseconds. */
		final long startedAt;
		final long lengthMs;

		Source(String name, Filepath dir, long startedAt, long lengthMs)
		{
			this.name = name;
			this.dir = dir;
			this.startedAt = startedAt;
			this.lengthMs = lengthMs;
		}

		long endsAt()
		{
			return startedAt + lengthMs;
		}
	}

	private ClipBundle()
	{
	}

	/**
	 * The lines to keep from one recording: all of them when {@code cutoffMs} is 0, else the context lines
	 * (session, plugins, the last watch and the last settings per plugin before the cut) and those at or after
	 * it. Lines that are not JSON are kept as they are when nothing is cut, dropped when trimming.
	 */
	static List<String> trim(Gson gson, BufferedReader in, long cutoffMs) throws IOException
	{
		List<String> out = new ArrayList<>();
		String session = null;
		String plugins = null;
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
			else if ("plugins".equals(kind))
			{
				plugins = text;
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
		if (plugins != null)
		{
			out.add(plugins);
		}
		if (watch != null)
		{
			out.add(watch);
		}
		out.addAll(settings.values());
		out.addAll(after);
		return out;
	}

	/**
	 * The client log's lines for a clip: every WARN and ERROR line from the whole recording, with the stack-trace
	 * lines that follow it (an error an hour before the clip is often the cause of what the player saw), plus
	 * every line stamped at or after {@code since}; everything when {@code since} is null.
	 */
	static List<String> trimLog(BufferedReader in, LocalDateTime since) throws IOException
	{
		List<String> out = new ArrayList<>();
		boolean keeping = since == null;
		String text;
		while ((text = in.readLine()) != null)
		{
			LocalDateTime at = stamp(text);
			if (at != null)
			{
				keeping = since == null || !at.isBefore(since) || isWarningOrError(text);
			}
			if (keeping)
			{
				out.add(text);
			}
		}
		return out;
	}

	/** Whether a stamped client log line is at WARN or ERROR level. */
	static boolean isWarningOrError(String line)
	{
		int bracket = line.indexOf("] ");
		if (bracket < 0 || bracket + 7 > line.length())
		{
			return false;
		}
		String level = line.substring(bracket + 2, bracket + 7);
		return level.startsWith("WARN") || level.startsWith("ERROR");
	}

	/** The timestamp a client log line starts with, or null for a continuation line. */
	static LocalDateTime stamp(String line)
	{
		if (line.length() < 19)
		{
			return null;
		}
		try
		{
			return LocalDateTime.parse(line.substring(0, 19), LOG_STAMP);
		}
		catch (DateTimeParseException e)
		{
			return null;
		}
	}

	/** The recordings that overlap a window, oldest first. */
	static List<Source> covering(List<Source> all, long since, long until)
	{
		List<Source> out = new ArrayList<>();
		for (Source source : all)
		{
			if (source.endsAt() >= since && source.startedAt <= until)
			{
				out.add(source);
			}
		}
		out.sort((a, b) -> Long.compare(a.startedAt, b.startedAt));
		return out;
	}

	/** The names of the pictures taken at or after a moment, from the shot lines kept. */
	static List<String> shotsIn(Gson gson, List<String> lines)
	{
		List<String> names = new ArrayList<>();
		for (String text : lines)
		{
			if (!text.contains("\"shot\""))
			{
				continue;
			}
			try
			{
				JsonObject o = gson.fromJson(text, JsonObject.class);
				if (o != null && o.has("e") && "shot".equals(o.get("e").getAsString()) && o.has("file"))
				{
					names.add(o.get("file").getAsString());
				}
			}
			catch (JsonSyntaxException e)
			{
				// not a line we need
			}
		}
		return names;
	}

	/**
	 * Writes the zip for a window ({@code since} 0 means everything in the sources) into the clips folder and
	 * returns it. Each source's events are trimmed to the window, its client log too, its pictures included only
	 * when asked.
	 */
	static Filepath write(Gson gson, Filepath root, List<Source> sources, long since, boolean pictures, String fileName) throws IOException
	{
		return write(gson, root, sources, since, pictures, fileName, null);
	}

	/** As above, with the names table for a {@code names.txt} (skipped when null). */
	static Filepath write(Gson gson, Filepath root, List<Source> sources, long since, boolean pictures, String fileName, IdNames names) throws IOException
	{
		Filepath clips = root.join(CLIPS_DIR);
		clips.createDirectories();
		Filepath zip = clips.join(fileName);
		try (OutputStream raw = zip.openOutputStream(); ZipOutputStream out = new ZipOutputStream(raw))
		{
			out.putNextEntry(new ZipEntry("README.txt"));
			out.write(README.getBytes(StandardCharsets.UTF_8));
			out.closeEntry();

			List<String> events = new ArrayList<>();
			List<String> log = new ArrayList<>();
			Map<String, List<String>> shots = new LinkedHashMap<>();
			for (Source source : sources)
			{
				long cutoff = since <= 0 ? 0 : Math.max(0, since - source.startedAt);
				Filepath eventsFile = source.dir.join(SessionLog.EVENTS_FILE);
				if (eventsFile.isFile())
				{
					try (BufferedReader in = eventsFile.openBufferedReader())
					{
						List<String> lines = trim(gson, in, cutoff);
						events.addAll(lines);
						if (pictures)
						{
							shots.put(source.name, shotsIn(gson, lines));
						}
					}
				}
				Filepath logFile = source.dir.join(ClientLogTap.FILE);
				if (logFile.isFile())
				{
					LocalDateTime from = since <= 0 ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(since), ZoneId.systemDefault());
					try (BufferedReader in = logFile.openBufferedReader())
					{
						log.addAll(trimLog(in, from));
					}
				}
			}
			out.putNextEntry(new ZipEntry(SessionLog.EVENTS_FILE));
			for (String line : events)
			{
				out.write(line.getBytes(StandardCharsets.UTF_8));
				out.write('\n');
			}
			out.closeEntry();
			out.putNextEntry(new ZipEntry(ClientLogTap.FILE));
			for (String line : log)
			{
				out.write(line.getBytes(StandardCharsets.UTF_8));
				out.write('\n');
			}
			out.closeEntry();
			if (names != null)
			{
				out.putNextEntry(new ZipEntry(IdNames.FILE));
				out.write("# The names behind the numbers in events.jsonl: kind, id, RuneLite's name for it. ? = not in the table.\n".getBytes(StandardCharsets.UTF_8));
				for (String line : names.tableFor(gson, events))
				{
					out.write(line.getBytes(StandardCharsets.UTF_8));
					out.write('\n');
				}
				out.closeEntry();
			}
			for (Map.Entry<String, List<String>> entry : shots.entrySet())
			{
				for (String name : entry.getValue())
				{
					Filepath picture = root.join(entry.getKey(), SessionLog.SHOTS_DIR, name);
					if (!picture.isFile())
					{
						continue;
					}
					out.putNextEntry(new ZipEntry("shots/" + entry.getKey() + "/" + name));
					try (InputStream in = picture.openInputStream())
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
		}
		return zip;
	}

	/** Every recording folder under the root, with its start (from its first line, else its name) and length. */
	static List<Source> sources(Gson gson, Filepath root, String currentName, long currentStartedAt, long now) throws IOException
	{
		List<Source> out = new ArrayList<>();
		try (Stream<Filepath> dirs = root.walk(1))
		{
			for (Filepath dir : dirs.filter(p -> !p.equals(root) && p.isDirectory()).collect(Collectors.toList()))
			{
				String name = dir.getFileName();
				LocalDateTime named = Names.parse(name);
				if (named == null)
				{
					continue;
				}
				if (name.equals(currentName))
				{
					out.add(new Source(name, dir, currentStartedAt, now - currentStartedAt));
					continue;
				}
				long[] span = span(gson, dir, named);
				out.add(new Source(name, dir, span[0], span[1]));
			}
		}
		return out;
	}

	/** A finished recording's start (its session line's startedAt, else its folder name) and length (its last ms). */
	static long[] span(Gson gson, Filepath dir, LocalDateTime named)
	{
		long startedAt = named.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		long last = 0;
		Filepath file = dir.join(SessionLog.EVENTS_FILE);
		if (!file.isFile())
		{
			return new long[]{startedAt, 0};
		}
		try (BufferedReader in = file.openBufferedReader())
		{
			String text;
			boolean first = true;
			while ((text = in.readLine()) != null)
			{
				if (first)
				{
					first = false;
					try
					{
						JsonObject o = gson.fromJson(text, JsonObject.class);
						if (o != null && o.has("startedAt"))
						{
							startedAt = o.get("startedAt").getAsLong();
						}
					}
					catch (JsonSyntaxException e)
					{
						// the folder name stands
					}
				}
				int i = text.indexOf("\"ms\":");
				if (i < 0)
				{
					continue;
				}
				int start = i + 5, end = start;
				while (end < text.length() && Character.isDigit(text.charAt(end)))
				{
					end++;
				}
				if (end > start)
				{
					last = Long.parseLong(text.substring(start, end));
				}
			}
		}
		catch (IOException | NumberFormatException e)
		{
			// what was read stands
		}
		return new long[]{startedAt, last};
	}
}
