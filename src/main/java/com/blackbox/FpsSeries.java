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
		/** plugin, setting, mark, notify, state, session, end. */
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
	 * milliseconds since the recording began. Lines that are not JSON are skipped, not fatal.
	 */
	static FpsSeries fromRecording(String name, Gson gson, BufferedReader in) throws IOException
	{
		FpsSeries series = new FpsSeries(name, Integer.MAX_VALUE, true);
		String text;
		while ((text = in.readLine()) != null)
		{
			if (text.trim().isEmpty())
			{
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
			if (o == null || !o.has("e") || !o.has("ms"))
			{
				continue;
			}
			String kind = o.get("e").getAsString();
			long at = o.get("ms").getAsLong();
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
					series.note(at, kind, field(o, "group") + "." + field(o, "key"));
					break;
				case "mark":
					series.note(at, kind, "Mark");
					break;
				case "notify":
					series.note(at, kind, field(o, "text"));
					break;
				case "state":
					series.note(at, kind, field(o, "state"));
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
		return series;
	}

	private static String field(JsonObject o, String name)
	{
		return o.has(name) && !o.get(name).isJsonNull() ? o.get(name).getAsString() : "";
	}
}
