/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** The watched plugins and named sets of them, as Black Box stores them in its own configuration. */
final class WatchList
{
	/** The key under {@code blackbox} holding the watched plugin names. */
	static final String WATCHED_KEY = "watched";
	/** The key prefix under {@code blackbox} for a named set; the set's name follows. */
	static final String SET_PREFIX = "set.";
	/** Plugin names are joined with this; no RuneLite plugin name contains it. */
	static final String SEPARATOR = "|";

	private WatchList()
	{
	}

	/** The names in a stored value, in order, without blanks or repeats. */
	static List<String> decode(String stored)
	{
		if (stored == null || stored.isEmpty())
		{
			return Collections.emptyList();
		}
		LinkedHashSet<String> names = new LinkedHashSet<>();
		for (String part : stored.split("\\|"))
		{
			String name = part.trim();
			if (!name.isEmpty())
			{
				names.add(name);
			}
		}
		return new ArrayList<>(names);
	}

	static String encode(List<String> names)
	{
		return String.join(SEPARATOR, new LinkedHashSet<>(names));
	}

	/** Whether a set name is storable: not blank, no separator, no dot (a dot would split the config key). */
	static boolean isValidSetName(String name)
	{
		return name != null && !name.trim().isEmpty() && !name.contains(SEPARATOR) && !name.contains(".") && name.length() <= 40;
	}
}
