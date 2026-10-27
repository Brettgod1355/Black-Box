/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.util.List;
import java.util.Set;

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

	/** A plugin's text setting the player may choose to include in an export. */
	final class TextSetting
	{
		/** {@code group.key}, as the export's filter names it. */
		final String id;
		final String plugin;
		final String name;
		/** Its value now, shown in the picker so the player sees what they would send. */
		final String value;

		TextSetting(String id, String plugin, String name, String value)
		{
			this.id = id;
			this.plugin = plugin;
			this.name = name;
			this.value = value;
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

	/** Whether an export's pictures would show the chat: Pictures in clips on and Hide the chat box off. */
	boolean picturesShowChat();

	/** The current recording's folder name, or null. */
	String currentName();

	/** When the current recording began (wall clock), or 0. */
	long startedAt();

	/** The current recording's size so far (its whole folder, as the recordings list shows it), or 0. */
	long bytesSoFar();

	/**
	 * Mark pressed: the moment (tick and time) and a picture are kept aside, nothing is written yet. The callback
	 * gets the moment's clock text on the Swing thread.
	 */
	void beginMark(java.util.function.Consumer<String> then);

	/** Writes the kept moment into the recording as a mark, with what the player wrote (may be empty). */
	void saveMark(String text);

	/** Drops the kept moment; nothing is written. */
	void cancelMark();

	void note(String text);

	/** Lists recordings on a worker and hands them back on the Swing thread, newest first. */
	void recordings(java.util.function.Consumer<List<Entry>> then);

	/** The installed plugins' text settings that hold something, for the export's picker. Swing thread. */
	List<TextSetting> textSettings();

	/**
	 * Writes a clip of the last minutes (across recordings) into the clips folder, copies its path, reports. Text
	 * settings are left out except those in {@code includeText} ({@link TextSetting#id}).
	 */
	void clip(int lastMinutes, Set<String> includeText, Report report);

	/** Writes every recording of today into one zip; text settings as for {@link #clip}. */
	void exportToday(Set<String> includeText, Report report);

	/** Writes one whole recording into a zip; text settings as for {@link #clip}. */
	void zipRecording(String name, Set<String> includeText, Report report);

	void openFolder(Report report);

	/** Opens the folder the exports are written to, so the zip just made can be sent. */
	void openExports(Report report);

	void delete(String name, Report report);
}
