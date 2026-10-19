/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;

/** Each main-world NPC's last recorded tile, by index, so {@code npcMove} lines carry only the ones that moved. */
final class NpcTiles
{
	private final Map<Integer, Long> tiles = new HashMap<>();

	/** Notes the NPC at this tile; true when it is new or on a different tile from the last time. */
	boolean moved(int index, WorldPoint at)
	{
		long packed = ((long) at.getX() << 24) | ((long) at.getY() << 4) | at.getPlane();
		Long last = tiles.put(index, packed);
		return last == null || last != packed;
	}

	void forget(int index)
	{
		tiles.remove(index);
	}

	void clear()
	{
		tiles.clear();
	}
}
