/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import net.runelite.api.Constants;

/**
 * Reads tile data out of the client's scene arrays: the water (or ground) overlay under a tile, and a
 * region's collision flags. The scene is 104 tiles across; with the GPU plugin's extended scene the arrays
 * are 184 across with the 104 in the middle, so an index is shifted by the difference. Pure functions, for tests.
 */
final class SceneData
{
	private SceneData()
	{
	}

	/** The index into a scene array for a scene coordinate, or -1 when it falls outside the array. */
	static int index(int sceneCoordinate, int arrayLength)
	{
		int offset = arrayLength > Constants.SCENE_SIZE ? (arrayLength - Constants.SCENE_SIZE) / 2 : 0;
		int i = sceneCoordinate + offset;
		return i < 0 || i >= arrayLength ? -1 : i;
	}

	/** One tile's value from a per-plane scene array, or -1 when the tile is not loaded. */
	static int at(short[][] plane, int sceneX, int sceneY)
	{
		if (plane == null || plane.length == 0)
		{
			return -1;
		}
		int x = index(sceneX, plane.length);
		int y = x < 0 ? -1 : index(sceneY, plane[x].length);
		return y < 0 ? -1 : plane[x][y];
	}

	/**
	 * A whole region's collision flags, 64 by 64 as {@code [x][y]}, or null when any of its tiles is outside the
	 * loaded scene. {@code baseX}/{@code baseY} are the scene's world origin, {@code regionX}/{@code regionY} the
	 * region's.
	 */
	static int[][] regionFlags(int[][] flags, int baseX, int baseY, int regionX, int regionY)
	{
		if (flags == null || flags.length == 0)
		{
			return null;
		}
		int[][] out = new int[Constants.REGION_SIZE][Constants.REGION_SIZE];
		for (int dx = 0; dx < Constants.REGION_SIZE; dx++)
		{
			int x = index(regionX + dx - baseX, flags.length);
			if (x < 0)
			{
				return null;
			}
			for (int dy = 0; dy < Constants.REGION_SIZE; dy++)
			{
				int y = index(regionY + dy - baseY, flags[x].length);
				if (y < 0)
				{
					return null;
				}
				out[dx][dy] = flags[x][y];
			}
		}
		return out;
	}
}
