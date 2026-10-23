/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FpsSummaryTest
{
	private static final Gson GSON = new Gson();
	/** 2026-10-23 12:00:00 UTC. */
	private static final long START = 1792756800000L;

	/** Ten minutes at 50 fps, a plugin switched on at 2:00 and a dip to 20 at 2:10; a mark at 6:00, a dip to 30 at 6:05. */
	private static String recording()
	{
		StringBuilder lines = new StringBuilder();
		lines.append("{\"e\":\"session\",\"tick\":1,\"ms\":0,\"startedAt\":").append(START).append("}\n");
		for (long ms = 600; ms <= 600_000; ms += 600)
		{
			int fps = ms == 130_200 ? 20 : ms == 365_400 ? 30 : 50;
			lines.append("{\"e\":\"tick\",\"tick\":1,\"ms\":").append(ms).append(",\"fps\":").append(fps).append("}\n");
		}
		lines.append("{\"e\":\"plugin\",\"tick\":1,\"ms\":120000,\"name\":\"Ground Items\",\"on\":true}\n");
		lines.append("{\"e\":\"mark\",\"tick\":1,\"ms\":360000,\"text\":\"stutter here\"}\n");
		return lines.toString();
	}

	private static FpsSeries read(String text) throws IOException
	{
		return FpsSeries.fromRecording("clip", GSON, new BufferedReader(new StringReader(text)));
	}

	@Test
	public void theSummaryNamesTheLowestMomentsAndWhatCameBefore() throws IOException
	{
		String summary = FpsSummary.of(read(recording()), ZoneOffset.UTC);
		assertTrue(summary, summary.startsWith("Frames per second over these minutes: low 20, mean 50, high 50 (1000 readings"));
		assertTrue(summary, summary.contains("12:02:10  20 fps  10s after plugin: Ground Items on"));
		assertTrue(summary, summary.contains("12:06:05  30 fps  5s after mark: stutter here"));
		// Long after the last event it still says what was last, and how long ago.
		FpsSeries late = read(recording().replace("\"ms\":360000,\"text\":\"stutter here\"", "\"ms\":1,\"text\":\"early\""));
		assertTrue(FpsSummary.of(late, ZoneOffset.UTC).contains("12:06:05  30 fps  4m 5s after plugin: Ground Items on"));
	}

	@Test
	public void aSteadyClipSaysSo() throws IOException
	{
		String steady = "{\"e\":\"tick\",\"tick\":1,\"ms\":600,\"fps\":50}\n{\"e\":\"tick\",\"tick\":2,\"ms\":1200,\"fps\":48}\n";
		assertTrue(FpsSummary.of(read(steady), ZoneOffset.UTC).contains("No reading fell more than 15% below the mean."));
		assertTrue(FpsSummary.of(read(""), ZoneOffset.UTC).contains("no frames-per-second readings"));
	}

	@Test
	public void oneLongDipIsListedOnceAndAtMostFiveAreListed() throws IOException
	{
		StringBuilder lines = new StringBuilder();
		for (long ms = 600; ms <= 600_000; ms += 600)
		{
			// Low for 20 seconds at every whole minute: ten dips, each 33 ticks long.
			int fps = ms % 60_000 < 20_000 ? 10 : 50;
			lines.append("{\"e\":\"tick\",\"tick\":1,\"ms\":").append(ms).append(",\"fps\":").append(fps).append("}\n");
		}
		String summary = FpsSummary.of(read(lines.toString()), ZoneOffset.UTC);
		assertEquals(summary, FpsSummary.DIPS, summary.split("10 fps", -1).length - 1);
		// Without a session line the times are into the clip.
		assertTrue(summary, summary.contains("  0:00  10 fps  (nothing marked before it)"));
	}

	@Test
	public void aClipOfTwoRecordingsPlacesTheSecondByItsStart() throws IOException
	{
		String two = "{\"e\":\"session\",\"tick\":1,\"ms\":0,\"startedAt\":" + START + "}\n"
			+ "{\"e\":\"tick\",\"tick\":2,\"ms\":600,\"fps\":50}\n"
			+ "{\"e\":\"session\",\"tick\":1,\"ms\":0,\"startedAt\":" + (START + 300_000) + "}\n"
			+ "{\"e\":\"tick\",\"tick\":2,\"ms\":600,\"fps\":40}\n";
		FpsSeries series = read(two);
		assertEquals(START, series.startedAt());
		assertEquals(40, series.at(300_600));
		assertEquals(50, series.at(600));
	}

	@Test
	public void aClipZipOpensFromItsEventsFile() throws IOException
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes))
		{
			zip.putNextEntry(new ZipEntry("README.txt"));
			zip.write("readme".getBytes(StandardCharsets.UTF_8));
			zip.putNextEntry(new ZipEntry(SessionLog.EVENTS_FILE));
			zip.write(recording().getBytes(StandardCharsets.UTF_8));
		}
		FpsSeries series = ClipBundle.readSeries(GSON, "clip.zip", new ByteArrayInputStream(bytes.toByteArray()));
		assertEquals(1000, series.size());
		assertEquals(20, series.at(130_200));
		FpsSeries events = ClipBundle.readSeries(GSON, "events.jsonl", new ByteArrayInputStream(recording().getBytes(StandardCharsets.UTF_8)));
		assertEquals(1000, events.size());
		try
		{
			ClipBundle.readSeries(GSON, "other.zip", new ByteArrayInputStream(new ByteArrayOutputStream().toByteArray()));
			fail("a zip without events.jsonl");
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains("is it a Black Box clip"));
		}
	}

	@Test
	public void theClipReadmeCarriesTheSummary()
	{
		String readme = ClipBundle.readme(false, true, java.util.Collections.emptyList(), "Frames per second over these minutes: low 1\n");
		assertTrue(readme, readme.contains("Frames per second over these minutes: low 1\nText settings"));
	}
}
