/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SceneDataTest
{
	@Test
	public void plainAndExtendedScenesIndexDifferently()
	{
		assertEquals(5, SceneData.index(5, 104));
		assertEquals(45, SceneData.index(5, 184));
		assertEquals(-1, SceneData.index(-41, 184));
		assertEquals(-1, SceneData.index(104, 104));
		assertEquals(103, SceneData.index(103, 104));
	}

	@Test
	public void tileValueFromAPlane()
	{
		short[][] plane = new short[104][104];
		plane[10][20] = 595;
		assertEquals(595, SceneData.at(plane, 10, 20));
		assertEquals(0, SceneData.at(plane, 11, 20));
		assertEquals(-1, SceneData.at(plane, 200, 20));
		assertEquals(-1, SceneData.at(null, 1, 1));
		short[][] extended = new short[184][184];
		extended[50][60] = 442;
		assertEquals(442, SceneData.at(extended, 10, 20));
	}

	@Test
	public void regionFlagsOnlyWhenWhollyLoaded()
	{
		int[][] flags = new int[104][104];
		// scene base at world (3200, 3200): the region at (3200, 3200) sits in the scene's lower left 64x64
		flags[3][4] = 0x100;
		int[][] region = SceneData.regionFlags(flags, 3200, 3200, 3200, 3200);
		assertNotNull(region);
		assertEquals(0x100, region[3][4]);
		assertEquals(64, region.length);
		// the region starting 50 tiles east runs past the scene's edge: not loaded yet
		assertNull(SceneData.regionFlags(flags, 3200, 3200, 3250, 3200));
		assertNull(SceneData.regionFlags(null, 0, 0, 0, 0));
	}
}
