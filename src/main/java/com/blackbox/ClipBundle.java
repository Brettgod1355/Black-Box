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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
		+ "frames per second, which plugins were installed and switched, every plugin's settings (typed text only if you\n"
		+ "chose it; webhooks, keys and secrets never), and the ground around you. Each recording's slice starts with its first lines, so the\n"
		+ "settings in force are always in the file.\n"
		+ "client-log.txt holds the client's own log lines for the same minutes, plus every warning and error since the\n"
		+ "client started, as RuneLite's client.log would, caught inside the client with your name and home folder replaced.\n"
		+ "%LOG%"
		+ "%FPS%"
		+ "%TEXT%"
		+ "names.txt gives RuneLite's name for every varbit, interface, sound, animation, NPC, object and item id the\n"
		+ "events mention.\n"
		+ "%PICTURES%"
		+ "%NEVER% your bank PIN, your character name (written as <you>), webhooks, keys or\n"
		+ "passwords, or anything outside the game client.\n"
		+ "You can open every file in any text editor or image viewer and look before sending.\n"
		+ "How to read it, for a person or an AI assistant: https://github.com/Brettgod1355/Black-Box/blob/main/READING.md\n"
		+ "Every line kind and field: https://github.com/Brettgod1355/Black-Box/blob/main/FORMAT.md\n"
		+ "Running it through a plugin in a test: https://github.com/Brettgod1355/Black-Box/blob/main/REPLAY.md\n";

	/**
	 * The README for a clip: what the pictures show depends on whether there are any and on the Hide chat setting,
	 * and a clip never claims the chat is blacked out when it is not.
	 */
	static String readme(boolean pictures, boolean chatHidden)
	{
		return readme(pictures, chatHidden, Collections.emptyList());
	}

	/** As above, with the warnings and errors of the clip's client log summed up (the lines of {@code client-log.txt}). */
	static String readme(boolean pictures, boolean chatHidden, List<String> log)
	{
		return readme(pictures, chatHidden, log, "");
	}

	/** As above, with the clip's frames per second summed up ({@link FpsSummary}). */
	static String readme(boolean pictures, boolean chatHidden, List<String> log, String fps)
	{
		return readme(pictures, chatHidden, log, fps, Collections.emptySet());
	}

	/** What the README says about text settings: left out, except the ones the player picked at export. */
	static String textLine(Set<String> included)
	{
		if (included.isEmpty())
		{
			return "Text settings (anything typed into a plugin's settings: lists, names, links) are left out and read "
				+ SettingsSnapshot.NOT_INCLUDED + ".\n";
		}
		return "Text settings are left out except the ones you chose to include: " + String.join(", ", new TreeSet<>(included))
			+ ".\n";
	}

	/** As above, saying which text settings the player chose to include. */
	static String readme(boolean pictures, boolean chatHidden, List<String> log, String fps, Set<String> included)
	{
		String shots;
		String never;
		if (!pictures)
		{
			shots = "There are no pictures: Pictures in clips is off in Black Box's settings, or none were taken.\n";
			never = "It never holds: other players' chat,";
		}
		else if (chatHidden)
		{
			shots = "shots/ holds pictures of the game view from these minutes. The chat box and split private chat are\n"
				+ "blacked out in them, but anything else on screen can still show: look at them before sending.\n";
			never = "Apart from what the pictures show, it never holds: other players' chat,";
		}
		else
		{
			shots = "shots/ holds pictures of the game view from these minutes, and the chat box is NOT blacked out in them\n"
				+ "(Hide chat is off in Black Box's settings): they can show your character name and other players' names\n"
				+ "and chat. Look at them before sending, or turn Pictures in clips off and export again.\n";
			never = "Apart from what the pictures show, it never holds: other players' chat,";
		}
		return README.replace("%PICTURES%", shots).replace("%NEVER%", never).replace("%LOG%", logSummary(log))
			.replace("%FPS%", fps).replace("%TEXT%", textLine(included));
	}
	/** How many groups the summary lists before saying "and N more". */
	static final int SUMMARY_GROUPS = 25;

	/**
	 * The warnings and errors of a client log in a few lines, so a developer sees at once whether any are their
	 * plugin's: one group per level, logger, message and the code the stack trace starts in (the frame that
	 * tells one plugin's "Error during overlay rendering" from another's), biggest first, each with its count and
	 * the time of its first and last line. A player's log after a login or a death is mostly other plugins'
	 * errors; this keeps a developer from reading three hundred lines to learn that none were theirs.
	 */
	static String logSummary(List<String> log)
	{
		// Pass one: each warning or error line with the first exception name and stack frame that follow it.
		List<String[]> entries = new ArrayList<>();          // level, logger, message, clock, exception, frame
		String[] entry = null;
		for (String line : log)
		{
			if (stamp(line) != null)
			{
				entry = null;
				if (!isWarningOrError(line))
				{
					continue;
				}
				int bracket = line.indexOf("] ");
				String rest = line.substring(bracket + 2).trim();            // "WARN  n.r.c.u.OverlayRenderer - Error during ..."
				int space = rest.indexOf(' ');
				String level = space < 0 ? rest : rest.substring(0, space);
				rest = space < 0 ? "" : rest.substring(space).trim();
				int dash = rest.indexOf(" - ");
				String logger = dash < 0 ? rest : rest.substring(0, dash);
				String message = dash < 0 ? "" : rest.substring(dash + 3).trim();
				if (message.length() > 100)
				{
					message = message.substring(0, 100) + "…";
				}
				entry = new String[]{level, logger, message, line.substring(11, 19), null, null};
				entries.add(entry);
			}
			else if (entry != null && entry[5] == null)
			{
				String text = line.trim();
				if (text.startsWith("at "))
				{
					int paren = text.indexOf('(');
					entry[5] = paren < 0 ? text.substring(3) : text.substring(3, paren);
				}
				else if (entry[4] == null && !text.isEmpty() && !text.startsWith("...") && !text.startsWith("Caused by"))
				{
					int colon = text.indexOf(':');
					String exception = colon < 0 ? text : text.substring(0, colon);
					entry[4] = exception.substring(exception.lastIndexOf('.') + 1);
				}
			}
		}
		if (entries.isEmpty())
		{
			return "client-log.txt holds no warnings or errors.\n";
		}
		// Pass two: group by level, logger, message, exception and frame; count and span each group.
		Map<String, int[]> counts = new LinkedHashMap<>();
		Map<String, String[]> first = new LinkedHashMap<>();
		Map<String, String> last = new LinkedHashMap<>();
		for (String[] e : entries)
		{
			String key = String.join("\t", e[0], e[1], e[2], String.valueOf(e[4]), String.valueOf(e[5]));
			counts.computeIfAbsent(key, k -> new int[1])[0]++;
			first.putIfAbsent(key, e);
			last.put(key, e[3]);
		}
		List<String> keys = new ArrayList<>(counts.keySet());
		keys.sort((a, b) -> counts.get(b)[0] - counts.get(a)[0]);
		int total = entries.size();
		StringBuilder out = new StringBuilder();
		out.append(total).append(total == 1 ? " warning or error line" : " warning and error lines")
			.append(" in client-log.txt, grouped, most first (count, first to last time, level, logger, message, then where\n")
			.append("the stack trace starts, which tells one plugin's error from another's):\n");
		int shown = 0;
		for (String k : keys)
		{
			if (shown == SUMMARY_GROUPS)
			{
				out.append("  and ").append(keys.size() - shown).append(" more groups.\n");
				break;
			}
			shown++;
			String[] e = first.get(k);
			out.append(String.format("  %5d  %s", counts.get(k)[0], e[3]));
			if (!last.get(k).equals(e[3]))
			{
				out.append(" to ").append(last.get(k));
			}
			out.append("  ").append(e[0]).append(' ').append(e[1]).append(" - ").append(e[2]).append('\n');
			if (e[4] != null || e[5] != null)
			{
				out.append("         ");
				if (e[4] != null)
				{
					out.append(e[4]);
				}
				if (e[5] != null)
				{
					out.append(e[4] != null ? " in " : "in ").append(e[5]);
				}
				out.append('\n');
			}
		}
		return out.toString();
	}

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

	/**
	 * The most recent recording, for "the last N minutes" when nothing was recorded in them: the player was logged out
	 * when they noticed the problem. Null when there is none.
	 */
	static Source latest(List<Source> all)
	{
		Source latest = null;
		for (Source source : all)
		{
			if (latest == null || source.endsAt() > latest.endsAt())
			{
				latest = source;
			}
		}
		return latest;
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
	/**
	 * A clip's frames per second for the window's chart: the {@code events.jsonl} inside a clip's zip, or an events
	 * file itself. Read only; the file is not changed or copied.
	 */
	static FpsSeries readSeries(Gson gson, Filepath file) throws IOException
	{
		try (InputStream in = file.openInputStream())
		{
			return readSeries(gson, file.getFileName(), in);
		}
	}

	/** As above, from the file's bytes; {@code name} says which kind it is (an events file ends in .jsonl). */
	static FpsSeries readSeries(Gson gson, String name, InputStream file) throws IOException
	{
		if (name.toLowerCase(Locale.ROOT).endsWith(".jsonl"))
		{
			return FpsSeries.fromRecording(name, gson, new BufferedReader(new InputStreamReader(file, StandardCharsets.UTF_8)));
		}
		try (ZipInputStream zip = new ZipInputStream(file))
		{
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null)
			{
				String path = entry.getName();
				if (path.equals(SessionLog.EVENTS_FILE) || path.endsWith("/" + SessionLog.EVENTS_FILE))
				{
					return FpsSeries.fromRecording(name, gson,
						new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8)));
				}
			}
		}
		throw new IOException("there is no " + SessionLog.EVENTS_FILE + " in it; is it a Black Box clip?");
	}

	static Filepath write(Gson gson, Filepath root, List<Source> sources, long since, boolean pictures, String fileName) throws IOException
	{
		return write(gson, root, sources, since, pictures, true, fileName, null);
	}

	/** As above, with the names table for a {@code names.txt} (skipped when null); no text setting included. */
	static Filepath write(Gson gson, Filepath root, List<Source> sources, long since, boolean pictures, boolean chatHidden,
		String fileName, IdNames names) throws IOException
	{
		return write(gson, root, sources, since, pictures, chatHidden, fileName, names, Collections.emptySet());
	}

	/**
	 * One recording's lines as an export holds them: text settings the player did not pick ({@code group.key})
	 * written as {@link SettingsSnapshot#NOT_INCLUDED}. A recording of format 11 or later marks which keys are text;
	 * an older one is judged by its values. Only settings and setting lines are parsed; the rest pass as they are.
	 */
	static List<String> forExport(Gson gson, List<String> lines, Set<String> include)
	{
		List<String> out = new ArrayList<>(lines.size());
		boolean marked = false;
		for (String text : lines)
		{
			boolean session = text.contains("\"e\":\"session\"");
			if (!session && !text.contains("\"e\":\"setting"))
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
				// Not a line Black Box wrote; an export never carries what it cannot judge.
				continue;
			}
			if (o == null)
			{
				continue;
			}
			if (session)
			{
				marked = o.has("format") && o.get("format").getAsInt() >= TEXT_MARKED_FORMAT;
				out.add(text);
				continue;
			}
			out.add(SettingsSnapshot.forExport(o, marked, include).toString());
		}
		return out;
	}

	/** The first format whose settings and setting lines say which keys are text. */
	static final int TEXT_MARKED_FORMAT = 11;

	/** As above, with the text settings the player picked to include ({@code group.key}). */
	static Filepath write(Gson gson, Filepath root, List<Source> sources, long since, boolean pictures, boolean chatHidden,
		String fileName, IdNames names, Set<String> includeText) throws IOException
	{
		Filepath clips = root.join(CLIPS_DIR);
		clips.createDirectories();
		Filepath zip = clips.join(fileName);
		try (OutputStream raw = zip.openOutputStream(); ZipOutputStream out = new ZipOutputStream(raw))
		{
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
						List<String> lines = forExport(gson, trim(gson, in, cutoff), includeText);
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
			boolean anyPicture = shots.values().stream().anyMatch(list -> !list.isEmpty());
			out.putNextEntry(new ZipEntry("README.txt"));
			// Read in place: an export of a whole day must not be copied into one text first.
			FpsSeries fps = FpsSeries.fromLines(fileName, gson, events);
			out.write(readme(anyPicture, chatHidden, log, FpsSummary.of(fps, ZoneId.systemDefault()), includeText)
				.getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
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
