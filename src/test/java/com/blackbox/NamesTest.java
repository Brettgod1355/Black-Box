/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.time.LocalDateTime;
import org.junit.Test;

public class NamesTest
{
	@Test
	public void displayNames()
	{
		LocalDateTime now = LocalDateTime.of(2026, 10, 20, 19, 0);
		assertEquals("Today 18:02", Names.display("2026-10-20_18-02-11", now));
		assertEquals("Yesterday 14:56", Names.display("2026-10-19_14-56-03", now));
		assertEquals("Oct 2 09:05", Names.display("2026-10-02_09-05-00", now));
		assertEquals("shots", Names.display("shots", now));
		assertNull(Names.parse("not-a-recording"));
	}

	@Test
	public void sizesAndElapsed()
	{
		assertEquals("900 B", Names.size(900));
		assertEquals("412 KB", Names.size(412 * 1024));
		assertEquals("1.8 MB", Names.size((long) (1.8 * 1024 * 1024)));
		assertEquals("12:34", Names.elapsed(12 * 60_000 + 34_000));
		assertEquals("1:02:03", Names.elapsed(3723_000));
		assertEquals("0:00", Names.elapsed(-5));
	}
}
