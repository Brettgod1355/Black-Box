/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
	public void logSummaryGroupsWarningsByWhereTheyStart()
	{
		List<String> log = Arrays.asList(
			"2026-10-20 16:34:19 CDT [Client] INFO  n.r.c.RuneLite - RuneLite version 1.13.2",
			"2026-10-20 16:34:19 CDT [Client] WARN  n.r.c.u.OverlayRenderer - Error during overlay rendering",
			"java.lang.NullPointerException: Cannot invoke \"Player.getLocalLocation()\" because \"local\" is null",
			"\tat com.easyempty.EasyEmptyOverlay.render(EasyEmptyOverlay.java:50)",
			"\tat net.runelite.client.ui.overlay.OverlayRenderer.safeRender(OverlayRenderer.java:570)",
			"2026-10-20 16:34:20 CDT [Client] WARN  n.r.c.u.OverlayRenderer - Error during overlay rendering",
			"java.lang.NullPointerException: Cannot invoke \"Player.getLocalLocation()\" because \"local\" is null",
			"\tat com.easyempty.EasyEmptyOverlay.render(EasyEmptyOverlay.java:50)",
			"2026-10-20 16:40:10 CDT [Client] WARN  n.r.c.u.OverlayRenderer - Error during overlay rendering",
			"java.lang.NullPointerException: x",
			"\tat com.treecount.TreeOverlay.render(TreeOverlay.java:9)",
			"2026-10-20 16:40:11 CDT [Client] ERROR c.b.BlackBoxPlugin - Picture failed",
			"java.lang.AssertionError: must be called on client thread",
			"\tat net.runelite.api.Client.checkThread(Client.java:1)",
			"\tat com.blackbox.BlackBoxPlugin.chatBounds(BlackBoxPlugin.java:1700)",
			"2026-10-20 16:40:12 CDT [Client] INFO  c.b.BlackBoxPlugin - fine");
		String summary = ClipBundle.logSummary(log);
		String[] lines = summary.split("\n");
		assertTrue(lines[0], lines[0].startsWith("4 warning and error lines in client-log.txt"));
		// Biggest group first, with its span, level, logger, message, exception and first frame.
		assertEquals("      2  16:34:19 to 16:34:20  WARN n.r.c.u.OverlayRenderer - Error during overlay rendering", lines[2]);
		assertEquals("         NullPointerException in com.easyempty.EasyEmptyOverlay.render", lines[3]);
		// The same message from another plugin is its own group, told apart by the frame.
		assertTrue(summary.contains("      1  16:40:10  WARN n.r.c.u.OverlayRenderer - Error during overlay rendering\n"
			+ "         NullPointerException in com.treecount.TreeOverlay.render\n"));
		assertTrue(summary.contains("      1  16:40:11  ERROR c.b.BlackBoxPlugin - Picture failed\n"
			+ "         AssertionError in net.runelite.api.Client.checkThread\n"));
		assertFalse("INFO lines are not counted", summary.contains("fine"));

		assertEquals("client-log.txt holds no warnings or errors.\n", ClipBundle.logSummary(Collections.emptyList()));
		assertEquals("client-log.txt holds no warnings or errors.\n", ClipBundle.logSummary(Collections.singletonList(log.get(0))));
	}

	@Test
	public void logSummaryStopsAtTheGroupLimit()
	{
		List<String> log = new ArrayList<>();
		for (int i = 0; i < ClipBundle.SUMMARY_GROUPS + 3; i++)
		{
			log.add("2026-10-20 16:34:19 CDT [Client] WARN  a.Logger - message " + i);
		}
		String summary = ClipBundle.logSummary(log);
		assertTrue(summary.contains("  and 3 more groups.\n"));
		assertEquals(ClipBundle.SUMMARY_GROUPS + 3, summary.split("\n").length);   // the heading's two lines, the groups, the "more" line
	}

	@Test
	public void readmeCarriesTheLogSummary()
	{
		String readme = ClipBundle.readme(false, true, Collections.singletonList("2026-10-20 16:34:19 CDT [Client] WARN  a.B - oops"));
		assertTrue(readme.contains("1 warning or error line in client-log.txt"));
		assertTrue(readme.contains("WARN a.B - oops"));
		assertFalse(readme.contains("%"));
	}

	@Test
	public void readmeSaysWhatItNeverHolds()
	{
		for (boolean pictures : new boolean[]{false, true})
		{
			for (boolean chatHidden : new boolean[]{false, true})
			{
				String readme = ClipBundle.readme(pictures, chatHidden);
				assertTrue(readme.contains("never holds: other players' chat, your bank PIN"));
				assertFalse("no placeholder left", readme.contains("%"));
			}
		}
	}

	@Test
	public void readmeNeverClaimsAChatBlackoutThatIsOff()
	{
		String none = ClipBundle.readme(false, false);
		assertTrue(none.contains("There are no pictures"));
		assertFalse(none.contains("shots/"));

		String hidden = ClipBundle.readme(true, true);
		assertTrue(hidden.contains("The chat box and split private chat are"));
		assertTrue(hidden.contains("Apart from what the pictures show"));

		String open = ClipBundle.readme(true, false);
		assertTrue(open.contains("the chat box is NOT blacked out"));
		assertFalse(open.contains("is blacked out in them"));
		assertTrue(open.contains("Apart from what the pictures show"));
	}

	@Test
	public void theMostRecentRecordingIsTheOneThatEndedLast()
	{
		// Logged out, then pressed Export: the last minutes come from the recording that ended last (owner, 2026-10-23).
		ClipBundle.Source morning = new ClipBundle.Source("2026-10-23_09-00-00", null, 1_000L, 60_000L);
		ClipBundle.Source evening = new ClipBundle.Source("2026-10-23_18-32-31", null, 500_000L, 120_000L);
		assertEquals("2026-10-23_18-32-31", ClipBundle.latest(java.util.Arrays.asList(evening, morning)).name);
		assertEquals(null, ClipBundle.latest(java.util.Collections.emptyList()));
		assertEquals(1, ClipBundle.covering(java.util.Arrays.asList(morning, evening), 620_000L - 120_000L, 620_000L).size());
	}
}
