/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Old recordings go on their own, newest kept, the one in progress never touched. */
public class RetentionTest
{
	private static final long DAY = 24L * 60 * 60 * 1000;
	private static final long NOW = 100 * DAY;
	private static final long MB = 1024L * 1024;

	private static Retention.Session s(String name, long daysAgo, long megabytes)
	{
		return new Retention.Session(name, NOW - daysAgo * DAY, megabytes * MB);
	}

	@Test
	public void twoRecordingsStartedInTheSameSecondGetTheirOwnFolders()
	{
		// Two clients sharing one RuneLite folder, logging in within the same second.
		java.util.Set<String> made = new java.util.HashSet<>();
		BlackBoxPlugin.FolderClaim disk = made::add;
		assertEquals("2026-10-22_16-06-59", BlackBoxPlugin.claimName("2026-10-22_16-06-59", disk));
		assertEquals("2026-10-22_16-06-59-2", BlackBoxPlugin.claimName("2026-10-22_16-06-59", disk));
		assertEquals("2026-10-22_16-06-59-3", BlackBoxPlugin.claimName("2026-10-22_16-06-59", disk));
		assertEquals("a disk error keeps the plain name", "2026-10-22_16-07-00",
			BlackBoxPlugin.claimName("2026-10-22_16-07-00", name -> { throw new java.io.IOException("read-only"); }));
	}

	@Test
	public void olderThanTheAgeLimitGoes()
	{
		List<Retention.Session> all = Arrays.asList(s("a", 10, 1), s("b", 8, 1), s("c", 2, 1), s("d", 0, 1));
		assertEquals(Arrays.asList("a", "b"), Retention.toDelete(all, "d", NOW, 7, 0));
	}

	@Test
	public void thenTheOldestGoUntilTheRestFit()
	{
		List<Retention.Session> all = Arrays.asList(s("c", 3, 200), s("a", 5, 200), s("b", 4, 200), s("d", 0, 200));
		assertEquals(Arrays.asList("a", "b"), Retention.toDelete(all, "d", NOW, 0, 450 * MB));
	}

	@Test
	public void theRecordingInProgressIsNeverDeleted()
	{
		List<Retention.Session> all = Arrays.asList(s("old", 30, 900), s("now", 40, 900));
		// "now" has an old date (a clock change) and is over the size limit alone: it still stays.
		assertEquals(Collections.singletonList("old"), Retention.toDelete(all, "now", NOW, 7, 500 * MB));
	}

	@Test
	public void zeroMeansNoLimit()
	{
		List<Retention.Session> all = Arrays.asList(s("a", 300, 9000), s("b", 1, 1));
		assertTrue(Retention.toDelete(all, "b", NOW, 0, 0).isEmpty());
	}
}
