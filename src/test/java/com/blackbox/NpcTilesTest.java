/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NpcTilesTest
{
	@Test
	public void onlyNewOrMovedNpcsCount()
	{
		NpcTiles tiles = new NpcTiles();
		WorldPoint here = new WorldPoint(2850, 4090, 0);
		assertTrue("first sight", tiles.moved(7, here));
		assertFalse("same tile", tiles.moved(7, here));
		assertTrue("one tile east", tiles.moved(7, here.dx(1)));
		assertTrue("another NPC on the same tile", tiles.moved(8, here.dx(1)));
		assertTrue("a plane change is a move", tiles.moved(8, new WorldPoint(2851, 4090, 1)));
		tiles.forget(7);
		assertTrue("seen again after it left", tiles.moved(7, here.dx(1)));
		tiles.clear();
		assertTrue("a new recording starts afresh", tiles.moved(8, new WorldPoint(2851, 4090, 1)));
	}
}
