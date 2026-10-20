/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.util.List;

/** What the sidebar's recorder card and recordings list read and do. Called on the Swing thread; results may arrive later. */
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

	/** Whether the Record setting is on: recording starts at login by itself. */
	boolean isRecordingEnabled();

	/** The current recording's folder name, or null. */
	String currentName();

	/** When the current recording began (wall clock), or 0. */
	long startedAt();

	/** Bytes written so far to the current recording, or 0. */
	long bytesSoFar();

	void mark();

	void note(String text);

	/** Lists recordings on a worker and hands them back on the Swing thread, newest first. */
	void recordings(java.util.function.Consumer<List<Entry>> then);

	/** Writes a clip of the last minutes (across recordings) into the clips folder, copies its path, reports. */
	void clip(int lastMinutes, Report report);

	/** Writes every recording of today into one zip. */
	void exportToday(Report report);

	/** Writes one whole recording into a zip. */
	void zipRecording(String name, Report report);

	void openFolder(Report report);

	void delete(String name, Report report);
}
