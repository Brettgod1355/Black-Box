/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.util.List;

/** What the sidebar's recorder controls and recordings list read and do. Called on the Swing thread; results may arrive later. */
interface Desk
{
	/** One past or current recording. */
	final class Entry
	{
		final String name;
		final long bytes;
		final boolean current;

		Entry(String name, long bytes, boolean current)
		{
			this.name = name;
			this.bytes = bytes;
			this.current = current;
		}
	}

	/** Called on the Swing thread with a short line for the status label. */
	interface Report
	{
		void say(String text);
	}

	boolean isRecording();

	/** The current recording's folder name, or null. */
	String currentName();

	/** When the current recording began (wall clock), or 0. */
	long startedAt();

	/** Bytes written so far to the current recording, or 0. */
	long bytesSoFar();

	void setRecording(boolean on);

	void mark();

	void note(String text);

	/** Lists recordings on a worker and hands them back on the Swing thread, newest first. */
	void recordings(java.util.function.Consumer<List<Entry>> then);

	/** Writes the share zip of a recording (0 = all of it) and reports where it went; copies the path to the clipboard. */
	void share(String name, int lastMinutes, Report report);

	void openFolder(Report report);

	void delete(String name, Report report);
}
