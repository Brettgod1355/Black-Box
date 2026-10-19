/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import org.junit.Test;

public class FpsSeriesTest
{
	@Test
	public void statsAndLookupsOverAWindow()
	{
		FpsSeries s = new FpsSeries("test", 100);
		for (int i = 0; i < 10; i++)
		{
			s.add(1000L * i, 40 + i);
		}
		assertEquals(0, s.first());
		assertEquals(9000, s.last());
		assertEquals(49, s.latest());
		FpsSeries.Stats all = s.stats(0, 9000);
		assertEquals(10, all.count);
		assertEquals(40, all.min);
		assertEquals(49, all.max);
		assertEquals(44.5, all.mean, 1e-9);
		assertEquals(3, s.samples(2000, 4000).size());
		assertEquals(42, s.at(2000));
		assertEquals(42, s.at(2500));
		assertEquals(-1, s.at(-5));
		assertEquals(0, s.stats(20_000, 30_000).count);
	}

	@Test
	public void liveSeriesDropsTheOldestBeyondCapacity()
	{
		FpsSeries s = new FpsSeries("live", 100);
		for (int i = 0; i < 1000; i++)
		{
			s.add(i, 50);
		}
		assertTrue(s.size() <= 110);
		assertTrue(s.first() >= 890);
		assertEquals(999, s.last());
	}

	@Test
	public void readsARecordingsFpsAndNotes() throws Exception
	{
		String text = String.join("\n",
			"{\"e\":\"session\",\"tick\":10,\"ms\":0,\"format\":3,\"started\":\"2026-10-19_12-00-00\"}",
			"{\"e\":\"tick\",\"tick\":11,\"ms\":600,\"fps\":50}",
			"{\"e\":\"tick\",\"tick\":12,\"ms\":1200,\"fps\":30}",
			"not json at all",
			"{\"e\":\"plugin\",\"tick\":12,\"ms\":1250,\"name\":\"Safe Sailing\",\"on\":true}",
			"{\"e\":\"setting\",\"tick\":13,\"ms\":1800,\"group\":\"safesailing\",\"key\":\"showMapLines\"}",
			"{\"e\":\"tick\",\"tick\":13,\"ms\":1800}",
			"{\"e\":\"mark\",\"tick\":14,\"ms\":2400}",
			"{\"e\":\"end\",\"tick\":15,\"ms\":3000,\"why\":\"logged out\"}");
		FpsSeries s = FpsSeries.fromRecording("r", new Gson(), new BufferedReader(new StringReader(text)));
		assertTrue(s.isRelative());
		assertEquals(2, s.size());
		assertEquals(30, s.latest());
		List<FpsSeries.Note> notes = s.allNotes();
		assertEquals(5, notes.size());
		assertEquals("session", notes.get(0).kind);
		assertEquals("Safe Sailing on", notes.get(1).text);
		assertEquals("safesailing.showMapLines", notes.get(2).text);
		assertEquals("Mark", notes.get(3).text);
		assertEquals("Recording ended: logged out", notes.get(4).text);
		assertEquals(1, s.notes(1200, 1300).size());
		assertFalse(new FpsSeries("live", 10).isRelative());
	}
}
