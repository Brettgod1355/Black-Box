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
 * happened, {@code client-log.txt} with the client's own log lines for the same time, and a {@code shots}
 * folder for screenshots. Lines are queued on the client thread and written
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
	 * (a region's flags, once), {@code npcTarget}, {@code npcAnim}, {@code graphic} and {@code note} lines; a
	 * {@code plugins} line and a {@code settings} line for every plugin; {@code client-log.txt} beside the events;
	 * the session line carries {@code startedAt} (wall clock, milliseconds) and the environment (client commit,
	 * launcher, Plugin Hub, Java, OS, canvas, resizable, stretched); Hub plugins carry {@code version}. 7: {@code shown} on
	 * {@code npcSay} (what the other subscribers left) and {@code npcShown} (what the NPC shows a client tick later, once
	 * the game tick and the plugins' tick handlers that follow the event have run, when that differs). 8: no
	 * {@code watch} line; every recording covers every plugin and nothing is picked. 9: the cheap batch from the Hub
	 * sweep (FORMAT.md, Versions): more on the session, tick, plugins, settings, setting, click, chat and notify
	 * lines; profile, playerOptions, window, menu, hint, overlays, infoboxes, varps, scene, itemAdd/itemGone, loot
	 * and party lines. 10: the Debug page's {@code debug} line and its tests' lines (projectile, npcInfo, npcHealth,
	 * npcFight, widgets, scripts, varcs, mouse, timing).
	 */
	static final int FORMAT = 10;
	static final String EVENTS_FILE = "events.jsonl";
	static final String SHOTS_DIR = "shots";

	private final String name;
	private final Filepath dir;
	private final long startedAt;
	private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();
	/** Lines of the client's log waiting to be written to {@code client-log.txt}; any thread adds. */
	private final ConcurrentLinkedQueue<String> pendingLog = new ConcurrentLinkedQueue<>();
	private Writer logOut;
	/** The events file, open from {@link #open()} to {@link #close()}; executor thread only. */
	private Writer out;
	/** Bytes written so far, for the sidebar. */
	private volatile long bytes;

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

	Filepath getDir()
	{
		return dir;
	}

	long getStartedAt()
	{
		return startedAt;
	}

	long getBytes()
	{
		return bytes;
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

	/** One line of the client's log, as {@link ClientLogTap} caught it. Any thread. */
	void addLogLine(String line)
	{
		pendingLog.add(line);
	}

	/** Creates the folders and the events file. Executor thread only. */
	void open() throws IOException
	{
		dir.join(SHOTS_DIR).createDirectories();
		out = dir.join(EVENTS_FILE).openBufferedWriter();
		logOut = dir.join(ClientLogTap.FILE).openBufferedWriter();
	}

	/** Writes every queued line. Executor thread only; lines queued before {@link #open()} wait for it. */
	void flush() throws IOException
	{
		if (out == null)
		{
			return;
		}
		String next;
		if (!pending.isEmpty())
		{
			while ((next = pending.poll()) != null)
			{
				out.write(next);
				out.write('\n');
				bytes += next.length() + 1;
			}
			out.flush();
		}
		if (logOut != null && !pendingLog.isEmpty())
		{
			while ((next = pendingLog.poll()) != null)
			{
				logOut.write(next);
				logOut.write('\n');
			}
			logOut.flush();
		}
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
		if (logOut != null)
		{
			logOut.close();
			logOut = null;
		}
	}

	/** Where a screenshot of this recording goes. */
	Filepath shot(String fileName)
	{
		return dir.join(SHOTS_DIR, fileName);
	}
}
