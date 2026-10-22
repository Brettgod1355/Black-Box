/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.DrawManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DebugTestTest
{
	@Test
	public void theTickedTestsRoundTripThroughTheSavedSetting()
	{
		Set<DebugTest> ticked = EnumSet.of(DebugTest.WIDGETS, DebugTest.PROJECTILES);
		assertEquals("projectiles,widgets", DebugTest.format(ticked));
		assertEquals(ticked, DebugTest.parse("projectiles,widgets"));
		// A test that was removed, spaces and an empty setting are all fine.
		assertEquals(EnumSet.of(DebugTest.MOUSE), DebugTest.parse(" mouse , gone"));
		assertTrue(DebugTest.parse("").isEmpty());
		assertTrue(DebugTest.parse(null).isEmpty());
	}

	@Test
	public void selectAllTicksEverythingThenClearsEverything()
	{
		Set<DebugTest> none = EnumSet.noneOf(DebugTest.class);
		Set<DebugTest> some = EnumSet.of(DebugTest.TIMING);
		Set<DebugTest> all = EnumSet.allOf(DebugTest.class);
		assertEquals("Select all", DebugTest.selectLabel(none));
		assertEquals("Select all", DebugTest.selectLabel(some));
		assertEquals("Clear all", DebugTest.selectLabel(all));
		assertEquals(all, DebugTest.afterSelect(none));
		assertEquals(all, DebugTest.afterSelect(some));
		assertEquals(none, DebugTest.afterSelect(all));
	}

	@Test
	public void everyHandlerRegistersOnARealEventBus()
	{
		// RuneLite's event bus refuses an @Subscribe method not named on<Event>; the client would not start the plugin.
		EventBus bus = new EventBus();
		bus.register(new BlackBoxPlugin());
		DebugProbes probes = new DebugProbes(null, bus, new DrawManager(), null);
		probes.run(EnumSet.allOf(DebugTest.class));
		probes.stopAll();
	}

	@Test
	public void theOwnNameIsReplacedOnlyAsAWholeName()
	{
		assertEquals("<you> waves at Bobby.", Privacy.scrub("Bob waves at Bobby.", "Bob"));
		assertEquals("<you>'s boat", Privacy.scrub("bob's boat", "Bob"));
	}

	@Test
	public void namesJoinedByALineBreakAreBothFound()
	{
		Pattern names = Privacy.namesPattern(Arrays.asList("Zezima", "Bobby"));
		assertEquals("<player> <player>", Privacy.withoutNames("Zezima Bobby", names));
		assertEquals(DebugProbes.TEXT_WITHHELD, Privacy.WITHHELD);
		assertTrue(DebugProbes.TEXT_INTERFACES.contains(net.runelite.api.gameval.InterfaceID.SAILING_BOAT_CARGOHOLD));
		assertTrue(DebugProbes.SOCIAL_INTERFACES.contains(net.runelite.api.gameval.InterfaceID.CHATBOX));
	}

	@Test
	public void otherPlayersNamesAreTakenOutOfInterfaceText()
	{
		Pattern names = Privacy.namesPattern(Arrays.asList("Zezima", "Iron Mule", "Bob", "Bobby"));
		assertEquals("Trading with: <player>", Privacy.withoutNames("Trading with: Zezima", names));
		// The game's separators all match, and case does not matter.
		assertEquals("<player> has logged in.", Privacy.withoutNames("Iron mule has logged in.", names));
		assertEquals("<player> and <player>", Privacy.withoutNames("Iron_Mule and iron-mule", names));
		// The longer name wins, and a name inside a word is left alone.
		assertEquals("<player> waves.", Privacy.withoutNames("Bobby waves.", names));
		assertEquals("Bobcat", Privacy.withoutNames("Bobcat", names));
		assertNull(Privacy.namesPattern(Arrays.asList("", null)));
		assertEquals("text", Privacy.withoutNames("text", null));
	}
}
