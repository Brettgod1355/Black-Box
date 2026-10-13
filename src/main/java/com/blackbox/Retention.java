/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which old recordings to delete, so the folder never grows without bound (owner, 2026-10-13: screenshots
 * must not take up a lot of room). First everything older than the age limit, then the oldest until the
 * rest fits the size limit. The recording in progress is never deleted. Pure, so it is tested without files.
 */
final class Retention
{
	/** One recording's folder as seen on disk. */
	static final class Session
	{
		final String name;
		final long modifiedMillis;
		final long bytes;

		Session(String name, long modifiedMillis, long bytes)
		{
			this.name = name;
			this.modifiedMillis = modifiedMillis;
			this.bytes = bytes;
		}
	}

	private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

	private Retention()
	{
	}

	/**
	 * @param keepDays  delete recordings older than this many days; 0 keeps them whatever their age
	 * @param maxBytes  then delete the oldest until the rest fit in this many bytes; 0 for no size limit
	 * @return the names to delete, oldest first
	 */
	static List<String> toDelete(List<Session> sessions, String current, long now, int keepDays, long maxBytes)
	{
		List<Session> kept = new ArrayList<>(sessions);
		kept.sort(Comparator.comparingLong((Session s) -> s.modifiedMillis).thenComparing(s -> s.name));
		List<String> doomed = new ArrayList<>();
		if (keepDays > 0)
		{
			long cutoff = now - keepDays * DAY_MILLIS;
			kept.removeIf(s ->
			{
				boolean old = !s.name.equals(current) && s.modifiedMillis < cutoff;
				if (old)
				{
					doomed.add(s.name);
				}
				return old;
			});
		}
		if (maxBytes > 0)
		{
			long total = 0;
			for (Session s : kept)
			{
				total += s.bytes;
			}
			for (Session s : kept)
			{
				if (total <= maxBytes)
				{
					break;
				}
				if (!s.name.equals(current))
				{
					doomed.add(s.name);
					total -= s.bytes;
				}
			}
		}
		return doomed;
	}
}
