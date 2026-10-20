/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;

/**
 * The client's own log, caught inside the client: every INFO, WARN and ERROR line the client and its plugins
 * log, in the same shape as {@code client.log}, with stack traces cut to a few frames. Nothing reads the log
 * file; this listens to the logging framework RuneLite already runs. Before a recording starts the last lines
 * wait in memory, so a recording begins with what happened since the client started, errors included. The
 * player's name becomes {@code <you>} and the home folder {@code <home>} in every line.
 */
final class ClientLogTap extends AppenderBase<ILoggingEvent>
{
	static final String FILE = "client-log.txt";
	/** Lines kept before a recording starts; the oldest go first. */
	static final int KEEP = 2000;
	static final int FRAMES = 10;
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss zzz");

	private final ArrayDeque<String> recent = new ArrayDeque<>();
	private volatile Consumer<String> sink;
	private volatile String ownName;
	private final String home;

	ClientLogTap(String home)
	{
		this.home = home == null || home.isEmpty() ? null : home;
		setName("blackbox");
	}

	/** Attaches to the root logger. */
	void attach()
	{
		if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext))
		{
			return;
		}
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		setContext(context);
		start();
		context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(this);
	}

	void detach()
	{
		if (LoggerFactory.getILoggerFactory() instanceof LoggerContext)
		{
			((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(this);
		}
		stop();
	}

	void setOwnName(String name)
	{
		ownName = name;
	}

	/**
	 * Starts sending lines to a recording: first everything kept so far, then each new line as it comes. The
	 * consumer must be quick and thread-safe (the log is written from any thread).
	 */
	synchronized void startSending(Consumer<String> to)
	{
		for (String line : recent)
		{
			to.accept(line);
		}
		sink = to;
	}

	synchronized void stopSending()
	{
		sink = null;
	}

	@Override
	protected void append(ILoggingEvent event)
	{
		if (!event.getLevel().isGreaterOrEqual(Level.INFO))
		{
			return;
		}
		String line;
		try
		{
			line = format(event);
		}
		catch (RuntimeException e)
		{
			return;
		}
		synchronized (this)
		{
			Consumer<String> to = sink;
			if (to != null)
			{
				to.accept(line);
			}
			recent.addLast(line);
			while (recent.size() > KEEP)
			{
				recent.removeFirst();
			}
		}
	}

	/** One event as client.log writes it, scrubbed, with the stack trace's first frames. */
	String format(ILoggingEvent event)
	{
		StringBuilder out = new StringBuilder();
		out.append(STAMP.format(Instant.ofEpochMilli(event.getTimeStamp()).atZone(ZoneId.systemDefault())))
			.append(" [").append(event.getThreadName()).append("] ")
			.append(String.format("%-5s", event.getLevel())).append(' ')
			.append(shortLogger(event.getLoggerName())).append(" - ")
			.append(scrub(event.getFormattedMessage()));
		for (String frame : frames(event.getThrowableProxy()))
		{
			out.append('\n').append(frame);
		}
		return out.toString();
	}

	/** The exception line and the first frames, nested causes included, cut like a short stack trace. */
	List<String> frames(IThrowableProxy error)
	{
		List<String> lines = new ArrayList<>();
		int depth = 0;
		while (error != null && depth++ < 4)
		{
			lines.add((depth == 1 ? "" : "Caused by: ") + error.getClassName() + ": " + scrub(error.getMessage()));
			StackTraceElementProxy[] trace = error.getStackTraceElementProxyArray();
			int shown = 0;
			for (StackTraceElementProxy element : trace == null ? new StackTraceElementProxy[0] : trace)
			{
				if (shown++ >= FRAMES)
				{
					lines.add("\t... " + (trace.length - FRAMES) + " more");
					break;
				}
				lines.add("\t" + element.getSTEAsString());
			}
			error = error.getCause();
		}
		return lines;
	}

	String scrub(String text)
	{
		if (text == null)
		{
			return "";
		}
		String out = Privacy.scrub(text, ownName);
		if (home != null && out.contains(home))
		{
			out = out.replace(home, "<home>");
		}
		return out;
	}

	/** {@code net.runelite.client.plugins.PluginManager} as {@code n.r.c.p.PluginManager}, like client.log's 36 columns. */
	static String shortLogger(String name)
	{
		if (name == null || name.length() <= 36)
		{
			return name == null ? "" : name;
		}
		String[] parts = name.split("\\.");
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < parts.length; i++)
		{
			boolean last = i == parts.length - 1;
			out.append(last ? parts[i] : parts[i].substring(0, Math.min(1, parts[i].length())));
			if (!last)
			{
				out.append('.');
			}
		}
		return out.toString();
	}
}
