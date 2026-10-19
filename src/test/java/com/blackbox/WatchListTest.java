/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class WatchListTest
{
	@Test
	public void roundTripsWithoutBlanksOrRepeats()
	{
		assertEquals(Arrays.asList("Safe Sailing", "AFK Shipwrecks"), WatchList.decode("Safe Sailing| AFK Shipwrecks ||Safe Sailing"));
		assertEquals("Safe Sailing|AFK Shipwrecks", WatchList.encode(Arrays.asList("Safe Sailing", "AFK Shipwrecks", "Safe Sailing")));
		assertTrue(WatchList.decode(null).isEmpty());
		assertTrue(WatchList.decode("").isEmpty());
	}

	@Test
	public void setNames()
	{
		assertTrue(WatchList.isValidSetName("Sailing"));
		assertFalse(WatchList.isValidSetName(""));
		assertFalse(WatchList.isValidSetName("a.b"));
		assertFalse(WatchList.isValidSetName("a|b"));
		assertFalse(WatchList.isValidSetName(null));
	}
}
