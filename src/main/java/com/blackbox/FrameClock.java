/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

/**
 * Frames drawn since the last game tick and the longest gap between two of them (format 9): a stutter shows as one
 * long gap even when the second's frames-per-second looks fine. Client thread.
 */
final class FrameClock
{
	private long last = -1;
	private int frames;
	private long longest;

	/** A frame is about to be drawn, at this {@code System.nanoTime()}. */
	void frame(long now)
	{
		if (last >= 0)
		{
			longest = Math.max(longest, now - last);
		}
		last = now;
		frames++;
	}

	int frames()
	{
		return frames;
	}

	/** The longest gap between two frames since the last {@link #reset()}, in milliseconds with one decimal. */
	double longestGapMs()
	{
		return Math.round(longest / 100_000.0) / 10.0;
	}

	/** Starts the next tick's count; the gap across the tick boundary still counts for the next tick. */
	void reset()
	{
		frames = 0;
		longest = 0;
	}
}
