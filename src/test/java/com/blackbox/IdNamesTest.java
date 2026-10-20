/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class IdNamesTest
{
	@Test
	public void bundledTableNamesTheIdsThePluginUses() throws Exception
	{
		IdNames names = IdNames.load();
		assertTrue(names.size() > 100_000);
		assertEquals("SAILING_PLAYER_IS_ON_PLAYER_BOAT", names.lookup("varbit", 19104));
		assertEquals("BANKPIN_KEYPAD", names.lookup("interface", 213));
		assertNull(names.lookup("varbit", -5));
	}

	@Test
	public void tableForListsOnlyTheIdsMentioned() throws Exception
	{
		IdNames names = IdNames.of("varbit\t19104\tSAILING_PLAYER_IS_ON_PLAYER_BOAT\ninterface\t213\tBANKPIN_KEYPAD\nitem\t995\tCOINS\ninventory\t93\tINV\n");
		List<String> lines = Arrays.asList(
			"{\"e\":\"var\",\"tick\":1,\"ms\":1,\"varbit\":19104,\"varp\":4428,\"value\":1}",
			"{\"e\":\"open\",\"tick\":1,\"ms\":1,\"interface\":213}",
			"{\"e\":\"items\",\"tick\":1,\"ms\":1,\"container\":93,\"items\":[[995,10],[-1,0]]}",
			"{\"e\":\"tick\",\"tick\":2,\"ms\":600,\"anim\":-1}",
			"not json");
		List<String> table = names.tableFor(new Gson(), lines);
		assertEquals(Arrays.asList("interface 213 BANKPIN_KEYPAD", "inventory 93 INV", "item 995 COINS", "varbit 19104 SAILING_PLAYER_IS_ON_PLAYER_BOAT", "varp 4428 ?"), table);
	}
}
