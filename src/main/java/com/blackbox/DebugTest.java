/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.util.EnumSet;
import java.util.Set;
import java.util.StringJoiner;

/**
 * One extra test on the Debug page. Which ones are ticked is saved (a comma list of keys); the Debug switch that
 * runs them is not, so it is off whenever the client starts.
 */
enum DebugTest
{
	PROJECTILES("projectiles", "Projectiles", false,
		"Each projectile: what fired it at what, from where to where, and when it lands. "
			+ "Projectiles to or from other players are left out."),
	NPC_DETAILS("npcDetails", "NPC details", false,
		"Each NPC's make-up (what it turns into, combat level, size, options), its health bar, and what it is fighting."),
	WIDGETS("widgets", "Interface contents", true,
		"What the open interfaces show (items, pictures, hidden parts, and text on your own screens), when it "
			+ "changes. Chat, friends, clan, trade and duel screens are left out and other players' names removed."),
	SCRIPTS("scripts", "Game scripts", true,
		"How many times each game script ran, per tick. Never what was passed to them."),
	VARCS("varcs", "Client values", false,
		"The client's own counters that game scripts keep (varc numbers), when they change. Never the text ones, "
			+ "which hold what you type."),
	MOUSE("mouse", "Mouse position", false,
		"Where the mouse is over the game, once a tick when it moved."),
	TIMING("timing", "Tick and frame timing", false,
		"How long all plugins' tick handlers took together, the slowest client tick, and how long frames took to draw.");

	final String key;
	final String label;
	/** Whether it can cost frames, shown beside its box. */
	final boolean heavy;
	final String description;

	DebugTest(String key, String label, boolean heavy, String description)
	{
		this.key = key;
		this.label = label;
		this.heavy = heavy;
		this.description = description;
	}

	/** The tests in a saved list; unknown keys (a test that was removed) are skipped. */
	static Set<DebugTest> parse(String saved)
	{
		Set<DebugTest> tests = EnumSet.noneOf(DebugTest.class);
		if (saved == null)
		{
			return tests;
		}
		for (String key : saved.split(","))
		{
			for (DebugTest test : values())
			{
				if (test.key.equalsIgnoreCase(key.trim()))
				{
					tests.add(test);
				}
			}
		}
		return tests;
	}

	/** The saved form: keys in the page's order, comma separated. */
	static String format(Set<DebugTest> tests)
	{
		StringJoiner keys = new StringJoiner(",");
		for (DebugTest test : values())
		{
			if (tests.contains(test))
			{
				keys.add(test.key);
			}
		}
		return keys.toString();
	}

	/** The Select all button's label: "Clear all" once every box is ticked, "Select all" otherwise. */
	static String selectLabel(Set<DebugTest> ticked)
	{
		return ticked.size() == values().length ? "Clear all" : "Select all";
	}

	/** What pressing Select all leaves ticked: nothing when everything was ticked, everything otherwise. */
	static Set<DebugTest> afterSelect(Set<DebugTest> ticked)
	{
		return ticked.size() == values().length ? EnumSet.noneOf(DebugTest.class) : EnumSet.allOf(DebugTest.class);
	}
}
