/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;
import org.junit.Test;

public class ShareBundleTest
{
	private static final String TEXT = String.join("\n",
		"{\"e\":\"session\",\"tick\":1,\"ms\":0,\"format\":6}",
		"{\"e\":\"watch\",\"tick\":1,\"ms\":1,\"plugins\":[]}",
		"{\"e\":\"settings\",\"tick\":1,\"ms\":2,\"plugin\":\"Safe Sailing\",\"values\":{}}",
		"{\"e\":\"tick\",\"tick\":2,\"ms\":600}",
		"{\"e\":\"settings\",\"tick\":3,\"ms\":1200,\"plugin\":\"Safe Sailing\",\"values\":{\"a\":\"1\"}}",
		"{\"e\":\"watch\",\"tick\":4,\"ms\":1800,\"plugins\":[{\"name\":\"Safe Sailing\",\"on\":true}]}",
		"{\"e\":\"tick\",\"tick\":5,\"ms\":2400}",
		"garbage",
		"{\"e\":\"tick\",\"tick\":6,\"ms\":3000}",
		"{\"e\":\"end\",\"tick\":7,\"ms\":3600,\"why\":\"logged out\"}");

	@Test
	public void everythingWhenNotTrimming() throws Exception
	{
		List<String> lines = ShareBundle.trim(new Gson(), new BufferedReader(new StringReader(TEXT)), 0);
		assertEquals(10, lines.size());
		assertTrue(lines.contains("garbage"));
	}

	@Test
	public void trimmingKeepsTheContextLines() throws Exception
	{
		List<String> lines = ShareBundle.trim(new Gson(), new BufferedReader(new StringReader(TEXT)), 2400);
		assertEquals(6, lines.size());
		assertTrue(lines.get(0).contains("\"session\""));
		assertTrue(lines.get(1).contains("\"ms\":1800"));          // the last watch line before the cut
		assertTrue(lines.get(2).contains("\"ms\":1200"));          // the last settings line per plugin
		assertTrue(lines.get(3).contains("\"ms\":2400"));
		assertTrue(lines.get(5).contains("\"end\""));
		assertTrue(lines.stream().noneMatch(l -> l.equals("garbage")));
	}

	@Test
	public void readmeSaysWhatItNeverHolds()
	{
		assertTrue(ShareBundle.README.contains("never holds"));
		assertTrue(ShareBundle.README.contains("bank PIN"));
		assertTrue(ShareBundle.README.contains("screenshots"));
	}
}
