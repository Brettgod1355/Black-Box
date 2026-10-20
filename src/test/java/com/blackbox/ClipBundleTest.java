/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class ClipBundleTest
{
	private static final String TEXT = String.join("\n",
		"{\"e\":\"session\",\"tick\":1,\"ms\":0,\"format\":6}",
		"{\"e\":\"watch\",\"tick\":1,\"ms\":1,\"plugins\":[]}",
		"{\"e\":\"plugins\",\"tick\":1,\"ms\":1,\"plugins\":[]}",
		"{\"e\":\"settings\",\"tick\":1,\"ms\":2,\"plugin\":\"Safe Sailing\",\"values\":{}}",
		"{\"e\":\"tick\",\"tick\":2,\"ms\":600}",
		"{\"e\":\"settings\",\"tick\":3,\"ms\":1200,\"plugin\":\"Safe Sailing\",\"values\":{\"a\":\"1\"}}",
		"{\"e\":\"watch\",\"tick\":4,\"ms\":1800,\"plugins\":[{\"name\":\"Safe Sailing\",\"on\":true}]}",
		"{\"e\":\"shot\",\"tick\":4,\"ms\":1900,\"file\":\"00001-tick4-mark.jpg\"}",
		"{\"e\":\"tick\",\"tick\":5,\"ms\":2400}",
		"garbage",
		"{\"e\":\"shot\",\"tick\":6,\"ms\":2500,\"file\":\"00002-tick6-notify.jpg\"}",
		"{\"e\":\"tick\",\"tick\":6,\"ms\":3000}",
		"{\"e\":\"end\",\"tick\":7,\"ms\":3600,\"why\":\"logged out\"}");

	@Test
	public void everythingWhenNotTrimming() throws Exception
	{
		List<String> lines = ClipBundle.trim(new Gson(), new BufferedReader(new StringReader(TEXT)), 0);
		assertEquals(13, lines.size());
		assertTrue(lines.contains("garbage"));
	}

	@Test
	public void trimmingKeepsTheContextLines() throws Exception
	{
		List<String> lines = ClipBundle.trim(new Gson(), new BufferedReader(new StringReader(TEXT)), 2400);
		assertEquals(8, lines.size());
		assertTrue(lines.get(0).contains("\"session\""));
		assertTrue(lines.get(1).contains("\"plugins\""));
		assertTrue(lines.get(2).contains("\"ms\":1800"));          // the last watch line before the cut
		assertTrue(lines.get(3).contains("\"ms\":1200"));          // the last settings line per plugin
		assertTrue(lines.get(4).contains("\"ms\":2400"));
		assertTrue(lines.get(7).contains("\"end\""));
		assertTrue(lines.stream().noneMatch(l -> l.equals("garbage")));
		assertEquals(Arrays.asList("00002-tick6-notify.jpg"), ClipBundle.shotsIn(new Gson(), lines));
	}

	@Test
	public void clientLogIsCutByStampAndKeepsTraceLines()
	{
		String log = String.join("\n",
			"2026-10-20 18:00:00 CDT [Client] INFO  n.r.c.RuneLite - RuneLite version 1.13.2",
			"2026-10-20 18:01:00 CDT [Client] ERROR n.r.c.e.EventBus - Error in event handler",
			"java.lang.NullPointerException: early",
			"\tat com.other.Plugin.onGameTick(Plugin.java:10)",
			"2026-10-20 18:05:00 CDT [Client] WARN  c.s.SafeSailingPlugin - Hazard map not loaded",
			"java.io.IOException: missing",
			"\tat com.safesailing.HazardMap.load(HazardMap.java:60)",
			"2026-10-20 18:09:00 CDT [Client] INFO  n.r.c.p.PluginManager - Plugin started");
		List<String> all = readLog(log, null);
		assertEquals(8, all.size());
		List<String> late = readLog(log, LocalDateTime.of(2026, 10, 20, 18, 4, 0));
		assertEquals(7, late.size());                                  // the early error and its trace stay, the version line goes
		assertTrue(late.get(0).contains("Error in event handler"));
		assertTrue(late.get(2).startsWith("\tat"));
		assertTrue(late.get(3).contains("Hazard map"));
		assertTrue(late.stream().noneMatch(l -> l.contains("RuneLite version")));
		assertTrue(ClipBundle.isWarningOrError("2026-10-20 18:01:00 CDT [Client] ERROR x - y"));
		assertTrue(ClipBundle.isWarningOrError("2026-10-20 18:01:00 CDT [Client] WARN  x - y"));
		assertNull(ClipBundle.stamp("\tat something"));
		assertEquals(LocalDateTime.of(2026, 10, 20, 18, 9, 0), ClipBundle.stamp("2026-10-20 18:09:00 CDT [Client] INFO x"));
	}

	private static List<String> readLog(String log, LocalDateTime since)
	{
		try
		{
			return ClipBundle.trimLog(new BufferedReader(new StringReader(log)), since);
		}
		catch (java.io.IOException e)
		{
			throw new AssertionError(e);
		}
	}

	@Test
	public void coveringPicksTheRecordingsThatOverlapTheWindow()
	{
		ClipBundle.Source a = new ClipBundle.Source("a", null, 1_000_000, 600_000);   // 1,000,000 to 1,600,000
		ClipBundle.Source b = new ClipBundle.Source("b", null, 2_000_000, 300_000);   // 2,000,000 to 2,300,000
		ClipBundle.Source c = new ClipBundle.Source("c", null, 3_000_000, 100_000);
		List<ClipBundle.Source> got = ClipBundle.covering(Arrays.asList(c, a, b), 1_500_000, 2_100_000);
		assertEquals(2, got.size());
		assertEquals("a", got.get(0).name);
		assertEquals("b", got.get(1).name);
		assertTrue(ClipBundle.covering(Arrays.asList(a, b, c), 4_000_000, 5_000_000).isEmpty());
	}

	@Test
	public void readmeSaysWhatItNeverHolds()
	{
		assertTrue(ClipBundle.README.contains("never holds"));
		assertTrue(ClipBundle.README.contains("bank PIN"));
		assertTrue(ClipBundle.README.contains("only if you turned that on"));
	}
}
