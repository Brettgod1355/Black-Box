/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ClientLogTapTest
{
	private static LoggingEvent event(Level level, String message, Throwable error)
	{
		LoggerContext context = new LoggerContext();
		Logger logger = context.getLogger("net.runelite.client.plugins.PluginManager");
		LoggingEvent event = new LoggingEvent("x", logger, level, message, error, null);
		event.setThreadName("Client");
		return event;
	}

	@Test
	public void formatsLikeClientLogAndScrubs()
	{
		ClientLogTap tap = new ClientLogTap("C:\\Users\\brett");
		tap.setOwnName("Cow31337Killer");
		String line = tap.format(event(Level.WARN, "Cow31337Killer could not read C:\\Users\\brett\\.runelite\\x", null));
		assertTrue(line, line.contains(" [Client] WARN  n.r.c.p.PluginManager - <you> could not read <home>\\.runelite\\x"));
		assertTrue(line.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} .*"));
	}

	@Test
	public void accountNamesOtherSoftwareLogsAreWithheld()
	{
		ClientLogTap tap = new ClientLogTap(null);
		assertEquals("Discord RPC service is ready with user <withheld>.",
			tap.scrub("Discord RPC service is ready with user someone_g."));
		assertEquals("user=<withheld>, loading 0 updated flips", tap.scrub("user=31654, loading 0 updated flips"));
		assertEquals("logged in as user <withheld> (Username: <withheld>)", tap.scrub("logged in as user Abc (Username: Abc)"));
		assertEquals("account=<withheld>", tap.scrub("account=12345"));
		assertEquals("a user clicked the user interface", tap.scrub("a user clicked the user interface"));
	}

	@Test
	public void stackTracesAreCutShort()
	{
		ClientLogTap tap = new ClientLogTap(null);
		RuntimeException deep = new RuntimeException("inner");
		IllegalStateException outer = new IllegalStateException("outer", deep);
		List<String> frames = tap.frames(new ThrowableProxy(outer));
		assertEquals("java.lang.IllegalStateException: outer", frames.get(0));
		assertTrue(frames.stream().anyMatch(f -> f.startsWith("Caused by: java.lang.RuntimeException: inner")));
		long traceLines = frames.stream().filter(f -> f.startsWith("\t")).count();
		assertTrue(traceLines <= 2 * (ClientLogTap.FRAMES + 1));
	}

	@Test
	public void keepsRecentLinesUntilARecordingStarts()
	{
		ClientLogTap tap = new ClientLogTap(null);
		tap.start();
		tap.doAppend(event(Level.DEBUG, "quiet", null));
		tap.doAppend(event(Level.INFO, "one", null));
		tap.doAppend(event(Level.ERROR, "two", null));
		List<String> got = new ArrayList<>();
		tap.startSending(got::add);
		tap.doAppend(event(Level.WARN, "three", null));
		tap.stopSending();
		tap.doAppend(event(Level.WARN, "four", null));
		assertEquals(3, got.size());
		assertTrue(got.get(0).endsWith("- one"));
		assertTrue(got.get(2).endsWith("- three"));
		assertFalse(got.stream().anyMatch(l -> l.contains("quiet")));
	}

	@Test
	public void loggerNamesAreShortened()
	{
		assertEquals("n.r.c.p.PluginManager", ClientLogTap.shortLogger("net.runelite.client.plugins.PluginManager"));
		assertEquals("short", ClientLogTap.shortLogger("short"));
	}
}
