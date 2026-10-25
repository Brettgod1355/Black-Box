/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WildernessLevelTest
{
	@Test
	public void thePlainHudTextGivesTheLevel()
	{
		assertEquals("Level: 23", BlackBoxPlugin.wildernessText("Level: 23"));
		assertEquals(Integer.valueOf(23), BlackBoxPlugin.wildernessLevelOf("Level: 23"));
		assertEquals(Integer.valueOf(1), BlackBoxPlugin.wildernessLevelOf(BlackBoxPlugin.wildernessText("Level: 1")));
	}

	@Test
	public void onlyTheFirstLineCountsAndColorsAreTakenOut()
	{
		// RuneLite's Combat Level plugin adds the attackable combat range on a second line
		String shown = "<col=ffff00>Level: 30</col><br>96-126";
		assertEquals("Level: 30", BlackBoxPlugin.wildernessText(shown));
		assertEquals(Integer.valueOf(30), BlackBoxPlugin.wildernessLevelOf(BlackBoxPlugin.wildernessText(shown)));
	}

	@Test
	public void otherTextIsNotALevel()
	{
		assertNull(BlackBoxPlugin.wildernessLevelOf("Level: 1-35"));
		assertNull(BlackBoxPlugin.wildernessLevelOf("Safe zone"));
	}
}
