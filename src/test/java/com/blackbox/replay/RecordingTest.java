/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox.replay;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RecordingTest
{
	private static Recording sample() throws IOException
	{
		try (Reader in = new InputStreamReader(RecordingTest.class.getResourceAsStream("sample.jsonl"), StandardCharsets.UTF_8))
		{
			return Recording.read(in);
		}
	}

	@Test
	public void readsEveryLineInOrderAndSkipsBlankOnes() throws IOException
	{
		Recording r = sample();
		assertEquals(6, r.lines().size());
		assertEquals(1, r.format());
		assertEquals("session", r.lines().get(0).kind());
		assertEquals("end", r.lines().get(5).kind());
	}

	@Test
	public void linesGroupByTickAndKind() throws IOException
	{
		Recording r = sample();
		Map<Integer, List<Recording.Line>> ticks = r.byTick();
		assertEquals(4, ticks.size());
		assertEquals(2, ticks.get(102).size());
		Recording.Line chat = r.of("chat").get(0);
		assertEquals("<you> salvages some debris.", chat.text("text"));
		assertEquals(102, chat.tick());
		assertEquals(1200, chat.ms());
		Recording.Line items = r.of("items").get(0);
		assertEquals(93, items.number("container", -1));
		assertEquals(32847, items.json().getAsJsonArray("items").get(0).getAsJsonArray().get(0).getAsInt());
		assertTrue(r.of("tick").get(0).has("topX"));
		assertFalse(r.of("tick").get(0).has("boatX"));
		assertEquals(-5, r.of("tick").get(0).number("missing", -5));
	}

	@Test
	public void aBrokenLineIsReportedWithItsNumber()
	{
		try
		{
			Recording.read(new StringReader("{\"e\":\"tick\",\"tick\":1}\nnot json at all{\n"));
			fail("a broken line must not be skipped");
		}
		catch (IOException e)
		{
			assertTrue(e.getMessage(), e.getMessage().startsWith("line 2"));
		}
	}

	@Test
	public void aRecordingWithoutASessionLineHasNoFormat() throws IOException
	{
		assertEquals(-1, Recording.read(new StringReader("{\"e\":\"tick\",\"tick\":1}")).format());
	}
}
