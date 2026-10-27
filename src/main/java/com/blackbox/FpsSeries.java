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
import java.util.ArrayList;
import java.util.List;

/**
 * Frames per second over time, with the moments worth lining up against it: a plugin switched on or off,
 * a setting changed, a mark, a notification, a game state change. The live series fills while the client
 * runs; a recording's series is read back from its {@code events.jsonl}. Thread-safe: samples arrive on the
 * client thread and the chart reads on the Swing thread.
 */
final class FpsSeries
{
	/** One reading: the client's frames per second at a moment (milliseconds). */
	static final class Sample
	{
		final long at;
		final int fps;

		Sample(long at, int fps)
		{
			this.at = at;
			this.fps = fps;
		}
	}

	/** Something that happened at a moment, to show on the chart. */
	static final class Note
	{
		final long at;
		/** plugin, setting, mark, note, notify, state, session, end, watch. */
		final String kind;
		final String text;

		Note(long at, String kind, String text)
		{
			this.at = at;
			this.kind = kind;
			this.text = text;
		}
	}

	/** Lowest, highest and mean reading over a span, or count 0 when there is none. */
	static final class Stats
	{
		final int min;
		final int max;
		final double mean;
		final int count;

		Stats(int min, int max, double mean, int count)
		{
			this.min = min;
			this.max = max;
			this.mean = mean;
			this.count = count;
		}
	}

	/** Two hours of ticks; older live samples are dropped. */
	static final int LIVE_CAPACITY = 12_000;

	private final String name;
	private final int capacity;
	/** True when times are milliseconds into a recording rather than the wall clock. */
	private final boolean relative;
	private final List<Sample> samples = new ArrayList<>();
	private final List<Note> notes = new ArrayList<>();
	/** The first recording's start on the wall clock (its session line's {@code startedAt}), or -1 when unknown. */
	private long startedAt = -1;

	FpsSeries(String name, int capacity)
	{
		this(name, capacity, false);
	}

	FpsSeries(String name, int capacity, boolean relative)
	{
		this.name = name;
		this.capacity = capacity;
		this.relative = relative;
	}

	synchronized long startedAt()
	{
		return startedAt;
	}

	boolean isRelative()
	{
		return relative;
	}

	String getName()
	{
		return name;
	}

	synchronized void add(long at, int fps)
	{
		samples.add(new Sample(at, fps));
		if (capacity < Integer.MAX_VALUE / 2 && samples.size() > capacity + capacity / 10)
		{
			samples.subList(0, samples.size() - capacity).clear();
		}
	}

	/** The last game state seen other than LOADING, to tell a scene reload from a login. */
	private String lastState;

	/**
	 * A game state, as the chart says it: LOADING then LOGGED_IN while already in the game is one "Scene reloaded" (a
	 * GPU plugin switched, a loading line crossed), "Logged in" only from the login screen, "Logged out" back to it.
	 * The recording keeps every state.
	 */
	synchronized void state(long at, String state)
	{
		if ("LOADING".equals(state))
		{
			return;
		}
		String text = stateText(lastState, state);
		lastState = state;
		if (text != null)
		{
			note(at, "state", text);
		}
	}

	/** The words for a game state after {@code previous} (LOADING never counts), or null for one not worth a mark. */
	static String stateText(String previous, String state)
	{
		switch (state == null ? "" : state)
		{
			case "LOGGED_IN":
				if ("LOGGED_IN".equals(previous))
				{
					return "Scene reloaded";
				}
				return "HOPPING".equals(previous) ? null : "Logged in";
			case "LOGIN_SCREEN":
				return "LOGGED_IN".equals(previous) || "HOPPING".equals(previous) || "CONNECTION_LOST".equals(previous)
					? "Logged out" : null;
			case "HOPPING":
				return "Hopping worlds";
			case "CONNECTION_LOST":
				return "Connection lost";
			case "LOGGING_IN":
			case "STARTING":
			case "UNKNOWN":
			case "":
				return null;
			default:
				return state;
		}
	}

	synchronized void note(long at, String kind, String text)
	{
		notes.add(new Note(at, kind, text == null ? "" : text));
		if (capacity < Integer.MAX_VALUE / 2 && notes.size() > capacity)
		{
			notes.subList(0, notes.size() - capacity / 2).clear();
		}
	}

	synchronized int size()
	{
		return samples.size();
	}

	/** The first sample's time, or -1 when empty. */
	synchronized long first()
	{
		return samples.isEmpty() ? -1 : samples.get(0).at;
	}

	/** The last sample's time, or -1 when empty. */
	synchronized long last()
	{
		return samples.isEmpty() ? -1 : samples.get(samples.size() - 1).at;
	}

	/** The newest reading, or -1. */
	synchronized int latest()
	{
		return samples.isEmpty() ? -1 : samples.get(samples.size() - 1).fps;
	}

	/** The samples with {@code from <= at <= to}, in order; a copy. */
	synchronized List<Sample> samples(long from, long to)
	{
		List<Sample> out = new ArrayList<>();
		int start = firstAtOrAfter(from);
		for (int i = start; i < samples.size() && samples.get(i).at <= to; i++)
		{
			out.add(samples.get(i));
		}
		return out;
	}

	/** The notes with {@code from <= at <= to}, in order; a copy. */
	synchronized List<Note> notes(long from, long to)
	{
		List<Note> out = new ArrayList<>();
		for (Note note : notes)
		{
			if (note.at >= from && note.at <= to)
			{
				out.add(note);
			}
		}
		return out;
	}

	synchronized List<Note> allNotes()
	{
		return new ArrayList<>(notes);
	}

	synchronized Stats stats(long from, long to)
	{
		int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, count = 0;
		long sum = 0;
		int start = firstAtOrAfter(from);
		for (int i = start; i < samples.size() && samples.get(i).at <= to; i++)
		{
			int fps = samples.get(i).fps;
			min = Math.min(min, fps);
			max = Math.max(max, fps);
			sum += fps;
			count++;
		}
		return count == 0 ? new Stats(0, 0, 0, 0) : new Stats(min, max, sum / (double) count, count);
	}

	/** The reading at or just before a moment, or -1. */
	synchronized int at(long when)
	{
		int i = firstAtOrAfter(when);
		if (i < samples.size() && samples.get(i).at == when)
		{
			return samples.get(i).fps;
		}
		return i == 0 ? -1 : samples.get(i - 1).fps;
	}

	private int firstAtOrAfter(long at)
	{
		int lo = 0, hi = samples.size();
		while (lo < hi)
		{
			int mid = (lo + hi) >>> 1;
			if (samples.get(mid).at < at)
			{
				lo = mid + 1;
			}
			else
			{
				hi = mid;
			}
		}
		return lo;
	}

	/**
	 * A recording's series: the {@code fps} field of each {@code tick} line (format 3 and later), and its
	 * plugin, setting, mark, notify, state, session and end lines as notes. Times are the lines' {@code ms},
	 * milliseconds since the recording began. Lines that are not JSON are skipped, not fatal. A clip can hold several
	 * recordings one after another, each with its own {@code ms} from 0: each later one is placed by its session
	 * line's {@code startedAt} against the first's (or just after the last line, when an old one has none).
	 */
	static FpsSeries fromRecording(String name, Gson gson, BufferedReader in) throws IOException
	{
		Builder builder = new Builder(name, gson);
		String text;
		while ((text = in.readLine()) != null)
		{
			builder.line(text);
		}
		return builder.series;
	}

	/** As above, from lines already in memory (an export's), without joining them into one text. */
	/** A setting line for a key the player cannot see in the plugin's panel. */
	static boolean isHiddenSetting(JsonObject o)
	{
		if (o.has("hidden") && o.get("hidden").getAsBoolean())
		{
			return true;
		}
		String key = o.has("key") && !o.get("key").isJsonNull() ? o.get("key").getAsString() : "";
		return key.startsWith("<") && key.contains("sha256");
	}

	static FpsSeries fromLines(String name, Gson gson, Iterable<String> lines)
	{
		Builder builder = new Builder(name, gson);
		for (String text : lines)
		{
			builder.line(text);
		}
		return builder.series;
	}

	/** Reads a recording one line at a time. */
	private static final class Builder
	{
		private final FpsSeries series;
		private final Gson gson;
		private long offset;
		private long lastAt;
		private boolean seenSession;

		Builder(String name, Gson gson)
		{
			this.series = new FpsSeries(name, Integer.MAX_VALUE, true);
			this.gson = gson;
		}

		void line(String text)
		{
			if (text.trim().isEmpty())
			{
				return;
			}
			JsonObject o;
			try
			{
				o = gson.fromJson(text, JsonObject.class);
			}
			catch (JsonSyntaxException e)
			{
				return;
			}
			if (o == null || !o.has("e") || !o.has("ms"))
			{
				return;
			}
			String kind = o.get("e").getAsString();
			if ("session".equals(kind))
			{
				long started = o.has("startedAt") ? o.get("startedAt").getAsLong() : -1;
				if (!seenSession)
				{
					series.startedAt = started;
					offset = 0;
				}
				else if (started >= 0 && series.startedAt >= 0)
				{
					offset = started - series.startedAt;
				}
				else
				{
					offset = lastAt + 1000;
				}
				seenSession = true;
			}
			long at = o.get("ms").getAsLong() + offset;
			lastAt = Math.max(lastAt, at);
			switch (kind)
			{
				case "tick":
					if (o.has("fps"))
					{
						series.add(at, o.get("fps").getAsInt());
					}
					break;
				case "plugin":
					series.note(at, kind, field(o, "name") + (o.has("on") && o.get("on").getAsBoolean() ? " on" : " off"));
					break;
				case "setting":
					// Not a hidden or undeclared key: a plugin saving its own state is no change the player made, and
					// would bury the chart and the clip's frame-rate summary. Format 12 says so;
					// older recordings show it by the key written as a fingerprint.
					if (!isHiddenSetting(o))
					{
						series.note(at, kind, field(o, "group") + "." + field(o, "key") + (o.has("value") ? " = " + field(o, "value") : ""));
					}
					break;
				case "watch":
					series.note(at, kind, "Watching " + (o.has("plugins") ? o.get("plugins").getAsJsonArray().size() : 0) + " plugin(s)");
					break;
				case "mark":
					series.note(at, kind, o.has("text") ? field(o, "text") : "Mark");
					break;
				case "note":
					series.note(at, kind, field(o, "text"));
					break;
				case "notify":
					series.note(at, kind, field(o, "text"));
					break;
				case "state":
					series.state(at, field(o, "state"));
					break;
				case "session":
					series.note(at, kind, "Recording started" + (o.has("started") ? " " + field(o, "started") : ""));
					break;
				case "end":
					series.note(at, kind, "Recording ended: " + field(o, "why"));
					break;
				default:
					break;
			}
		}
	}

	private static String field(JsonObject o, String name)
	{
		return o.has(name) && !o.get(name).isJsonNull() ? o.get(name).getAsString() : "";
	}
}
