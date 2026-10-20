/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/** FORMAT.md must describe every kind of line the plugin can write, and nothing it cannot. */
public class FormatDocTest
{
	/** Kinds written by code outside this branch (the NPC positions branch), documented ahead of it. */
	private static final Set<String> ELSEWHERE = new HashSet<>(Arrays.asList("npcMove"));

	@Test
	public void everyLineKindIsDocumented() throws IOException
	{
		Set<String> written = writtenKinds();
		Set<String> documented = documentedKinds();
		assertTrue("kinds the code writes: " + written, written.size() >= 30);
		Set<String> missing = new TreeSet<>(written);
		missing.removeAll(documented);
		assertEquals("line kinds without a section in FORMAT.md: " + missing, 0, missing.size());
		Set<String> stale = new TreeSet<>(documented);
		stale.removeAll(written);
		stale.removeAll(ELSEWHERE);
		assertEquals("sections in FORMAT.md for kinds the code never writes: " + stale, 0, stale.size());
		String format = new String(Files.readAllBytes(Paths.get("FORMAT.md")), StandardCharsets.UTF_8);
		assertTrue("FORMAT.md must name the current format: " + SessionLog.FORMAT, format.contains("This is format **" + SessionLog.FORMAT + "**"));
	}

	/** Every string literal handed to a line(...) call, plus the kinds passed through helpers as arguments. */
	private static Set<String> writtenKinds() throws IOException
	{
		Set<String> kinds = new TreeSet<>();
		Pattern direct = Pattern.compile("line\\(\"([a-zA-Z]+)\"");
		Pattern helper = Pattern.compile("(?:object|npc|worldEntity|pinLine)\\((?:[a-z]+, )?\"([a-zA-Z]+)\"");
		Pattern ternary = Pattern.compile("\\b[a-z]+ \\? \"([a-zA-Z]+)\" : \"([a-zA-Z]+)\"");
		try (Stream<Path> files = Files.walk(Paths.get("src", "main", "java")))
		{
			for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator)
			{
				String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
				for (Pattern pattern : new Pattern[]{direct, helper, ternary})
				{
					Matcher m = pattern.matcher(source);
					while (m.find())
					{
						for (int i = 1; i <= m.groupCount(); i++)
						{
							if (!m.group(i).equals("kind"))
							{
								kinds.add(m.group(i));
							}
						}
					}
				}
			}
		}
		return kinds;
	}

	private static Set<String> documentedKinds() throws IOException
	{
		Set<String> kinds = new TreeSet<>();
		Matcher m = Pattern.compile("(?m)^### (.+)$").matcher(new String(Files.readAllBytes(Paths.get("FORMAT.md")), StandardCharsets.UTF_8));
		while (m.find())
		{
			Matcher name = Pattern.compile("`([a-zA-Z]+)`").matcher(m.group(1));
			while (name.find())
			{
				kinds.add(name.group(1));
			}
		}
		return kinds;
	}
}
