/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A clip's frames per second in a few lines for its README: low, mean and high, and the lowest moments with the
 * last thing that happened before each and how long before (a plugin switched, a setting changed, a mark, a
 * notification, a login): a plugin switched on two minutes earlier is still on. Pure, so it is tested without a
 * client.
 */
final class FpsSummary
{
	/** How many of the lowest moments are listed. */
	static final int DIPS = 5;
	/** Two listed moments are at least this far apart, so one long dip is not listed five times. */
	static final long APART_MS = 30_000;
	/** A moment is a dip when it is this far below the mean. */
	static final double DIP_SHARE = 0.85;

	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

	private FpsSummary()
	{
	}

	static String of(FpsSeries series, ZoneId zone)
	{
		FpsSeries.Stats all = series.stats(Long.MIN_VALUE, Long.MAX_VALUE);
		if (all.count == 0)
		{
			return "events.jsonl has no frames-per-second readings (a recording older than format 3, or too short).\n";
		}
		StringBuilder out = new StringBuilder();
		out.append(String.format(Locale.ROOT, "Frames per second over these minutes: low %d, mean %.0f, high %d (%d readings,"
			+ " one a game tick).\n", all.min, all.mean, all.max, all.count));
		List<FpsSeries.Sample> lowest = new ArrayList<>(series.samples(Long.MIN_VALUE, Long.MAX_VALUE));
		lowest.sort(Comparator.comparingInt((FpsSeries.Sample s) -> s.fps).thenComparingLong(s -> s.at));
		List<FpsSeries.Sample> dips = new ArrayList<>();
		for (FpsSeries.Sample s : lowest)
		{
			if (dips.size() >= DIPS || s.fps >= all.mean * DIP_SHARE)
			{
				break;
			}
			if (dips.stream().allMatch(d -> Math.abs(d.at - s.at) >= APART_MS))
			{
				dips.add(s);
			}
		}
		if (dips.isEmpty())
		{
			out.append("No reading fell more than 15% below the mean.\n");
			return out.toString();
		}
		dips.sort(Comparator.comparingLong(s -> s.at));
		out.append("The lowest moments, and the last thing that happened before each:\n");
		for (FpsSeries.Sample dip : dips)
		{
			out.append("  ").append(clock(series, dip.at, zone)).append("  ").append(dip.fps).append(" fps  ")
				.append(before(series, dip.at)).append('\n');
		}
		return out.toString();
	}

	private static String before(FpsSeries series, long at)
	{
		// The latest by time, not by file order: a clip's lines need not be sorted.
		FpsSeries.Note last = null;
		for (FpsSeries.Note note : series.notes(Long.MIN_VALUE, at))
		{
			if (!"session".equals(note.kind) && !"end".equals(note.kind) && (last == null || note.at >= last.at))
			{
				last = note;
			}
		}
		if (last == null)
		{
			return "(nothing marked before it)";
		}
		long seconds = Math.max(0, (at - last.at) / 1000);
		String ago = seconds < 60 ? seconds + "s" : seconds / 60 + "m " + seconds % 60 + "s";
		return ago + " after " + last.kind + ": " + shorten(last.text, 70);
	}

	/** The wall-clock moment when the recording's start is known, else minutes and seconds into the clip. */
	private static String clock(FpsSeries series, long at, ZoneId zone)
	{
		if (series.isRelative() && series.startedAt() < 0)
		{
			long s = at / 1000;
			return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
		}
		long wall = series.isRelative() ? series.startedAt() + at : at;
		return LocalTime.from(Instant.ofEpochMilli(wall).atZone(zone)).format(CLOCK);
	}

	private static String shorten(String text, int max)
	{
		return text.length() <= max ? text : text.substring(0, max - 1) + "…";
	}
}
