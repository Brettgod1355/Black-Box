/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import com.blackbox.replay.Recording;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.runelite.client.util.Filepath;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** What the plugin writes is exactly what a replay test reads back. */
public class SessionLogTest
{
	@Test
	public void linesWrittenInOrderReadBackTheSame() throws Exception
	{
		Path tmp = Files.createTempDirectory("black-box-test");
		Filepath dir = Filepath.Unchecked.getRooted(tmp).join("2026-10-13_10-00-00");
		SessionLog log = new SessionLog("2026-10-13_10-00-00", dir, 1_000);

		JsonObject first = log.line("session", 100, 1_000);
		first.addProperty("format", SessionLog.FORMAT);
		log.add(first);
		// Lines queued before the folder exists wait; nothing is lost.
		log.flush();
		assertFalse(Files.exists(tmp.resolve("2026-10-13_10-00-00").resolve(SessionLog.EVENTS_FILE)));

		log.open();
		assertTrue(Files.isDirectory(tmp.resolve("2026-10-13_10-00-00").resolve(SessionLog.SHOTS_DIR)));
		JsonObject chat = log.line("chat", 101, 1_600);
		chat.addProperty("text", "quotes \" and\nnew lines survive");
		log.add(chat);
		log.flush();
		log.add(log.line("end", 102, 2_200));
		log.close();
		log.close();

		Recording r = Recording.read(tmp.resolve("2026-10-13_10-00-00").resolve(SessionLog.EVENTS_FILE));
		assertEquals(3, r.lines().size());
		assertEquals(SessionLog.FORMAT, r.format());
		assertEquals("quotes \" and\nnew lines survive", r.of("chat").get(0).text("text"));
		assertEquals(600, r.of("chat").get(0).ms());
		assertEquals(102, r.of("end").get(0).tick());
	}
}
