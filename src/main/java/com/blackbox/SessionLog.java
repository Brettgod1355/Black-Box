/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.runelite.client.util.Filepath;

/**
 * One recording: a folder holding {@code events.jsonl}, one JSON object per line in the order things
 * happened, and a {@code shots} folder for screenshots. Lines are queued on the client thread and written
 * on the plugin's executor, never on the client thread.
 */
final class SessionLog
{
	/**
	 * Bumped whenever a line's shape changes in a way a reader must know about, or a line kind is added that a
	 * reader may want to rely on. 1: the 0.1 lines. 2: {@code sound} lines. 3: {@code fps} on every {@code tick}
	 * line, and {@code plugin} (name, on) and {@code setting} (group, key) lines. 4: {@code watch} (the watched
	 * plugins) and {@code settings} (a watched plugin's values, secrets withheld) lines; a {@code setting} line of a
	 * watched plugin carries {@code plugin} and {@code value}. 5: NPC positions ({@code npcAdd}/{@code npcGone} tiles,
	 * {@code npcMove}). 6: {@code boatDir}, {@code overlay} and {@code underlay} on {@code tick} lines; {@code collision}
	 * (a region's flags, once), {@code npcTarget}, {@code npcAnim} and {@code graphic} lines.
	 */
	static final int FORMAT = 6;
	static final String EVENTS_FILE = "events.jsonl";
	static final String SHOTS_DIR = "shots";

	private final String name;
	private final Filepath dir;
	private final long startedAt;
	private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();
	/** The events file, open from {@link #open()} to {@link #close()}; executor thread only. */
	private Writer out;

	SessionLog(String name, Filepath dir, long startedAt)
	{
		this.name = name;
		this.dir = dir;
		this.startedAt = startedAt;
	}

	String getName()
	{
		return name;
	}

	/** A new line of the given kind, stamped with the game tick and the time since the recording began. */
	JsonObject line(String kind, int tick, long now)
	{
		JsonObject o = new JsonObject();
		o.addProperty("e", kind);
		o.addProperty("tick", tick);
		o.addProperty("ms", now - startedAt);
		return o;
	}

	void add(JsonObject line)
	{
		pending.add(line.toString());
	}

	/** Creates the folders and the events file. Executor thread only. */
	void open() throws IOException
	{
		dir.join(SHOTS_DIR).createDirectories();
		out = dir.join(EVENTS_FILE).openBufferedWriter();
	}

	/** Writes every queued line. Executor thread only; lines queued before {@link #open()} wait for it. */
	void flush() throws IOException
	{
		if (out == null || pending.isEmpty())
		{
			return;
		}
		String next;
		while ((next = pending.poll()) != null)
		{
			out.write(next);
			out.write('\n');
		}
		out.flush();
	}

	/** Writes what is left and closes the file. Executor thread only. */
	void close() throws IOException
	{
		if (out != null)
		{
			flush();
			out.close();
			out = null;
		}
	}

	/** Where a screenshot of this recording goes. */
	Filepath shot(String fileName)
	{
		return dir.join(SHOTS_DIR, fileName);
	}
}
