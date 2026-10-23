/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Text settings stay out of an export unless the player picks them (format 11). */
public class TextExportTest
{
	private static final Gson GSON = new Gson();
	private static final String SESSION_11 = "{\"e\":\"session\",\"tick\":1,\"ms\":0,\"format\":11}";
	private static final String SESSION_10 = "{\"e\":\"session\",\"tick\":1,\"ms\":0,\"format\":10}";
	/** A format 11 settings line: two text keys, a number, a toggle, a secret and a withheld link. */
	private static final String SETTINGS_11 = "{\"e\":\"settings\",\"tick\":1,\"ms\":0,\"plugin\":\"Ground Items\",\"group\":\"grounditems\","
		+ "\"values\":{\"highlightedItems\":\"Dragon bones,Rune*\",\"hiddenItems\":\"Ashes\",\"maxPrice\":\"1000\",\"showValue\":\"true\","
		+ "\"apiKey\":\"<secret>\",\"hook\":\"<withheld>\"},\"text\":[\"highlightedItems\",\"hiddenItems\",\"hook\"]}";

	private static JsonObject values(String line)
	{
		return GSON.fromJson(line, JsonObject.class).getAsJsonObject("values");
	}

	@Test
	public void textSettingsAreLeftOutUnlessPicked()
	{
		List<String> out = ClipBundle.forExport(GSON, Arrays.asList(SESSION_11, SETTINGS_11), Collections.emptySet());
		JsonObject v = values(out.get(1));
		assertEquals(SettingsSnapshot.NOT_INCLUDED, v.get("highlightedItems").getAsString());
		assertEquals(SettingsSnapshot.NOT_INCLUDED, v.get("hiddenItems").getAsString());
		// Plain values and what was already withheld stay as they are.
		assertEquals("1000", v.get("maxPrice").getAsString());
		assertEquals("true", v.get("showValue").getAsString());
		assertEquals("<secret>", v.get("apiKey").getAsString());
		assertEquals("<withheld>", v.get("hook").getAsString());

		Set<String> picked = new TreeSet<>(Collections.singleton("grounditems.highlightedItems"));
		v = values(ClipBundle.forExport(GSON, Arrays.asList(SESSION_11, SETTINGS_11), picked).get(1));
		assertEquals("Dragon bones,Rune*", v.get("highlightedItems").getAsString());
		assertEquals(SettingsSnapshot.NOT_INCLUDED, v.get("hiddenItems").getAsString());
	}

	@Test
	public void aChangedTextSettingIsLeftOutToo()
	{
		String text = "{\"e\":\"setting\",\"tick\":5,\"ms\":3000,\"group\":\"grounditems\",\"key\":\"hiddenItems\",\"value\":\"Ashes,Bones\",\"text\":true}";
		String number = "{\"e\":\"setting\",\"tick\":5,\"ms\":3000,\"group\":\"grounditems\",\"key\":\"maxPrice\",\"value\":\"Rune\"}";
		List<String> out = ClipBundle.forExport(GSON, Arrays.asList(SESSION_11, text, number), Collections.emptySet());
		assertEquals(SettingsSnapshot.NOT_INCLUDED, GSON.fromJson(out.get(1), JsonObject.class).get("value").getAsString());
		// Format 11 says which keys are text: an unmarked one stays, whatever it looks like.
		assertEquals("Rune", GSON.fromJson(out.get(2), JsonObject.class).get("value").getAsString());
		assertEquals("Ashes,Bones", GSON.fromJson(ClipBundle.forExport(GSON, Arrays.asList(SESSION_11, text),
			Collections.singleton("grounditems.hiddenItems")).get(1), JsonObject.class).get("value").getAsString());
	}

	@Test
	public void anOlderRecordingIsJudgedByItsValues()
	{
		String settings = "{\"e\":\"settings\",\"tick\":1,\"ms\":0,\"plugin\":\"Ground Items\",\"group\":\"grounditems\","
			+ "\"values\":{\"highlightedItems\":\"Dragon bones\",\"maxPrice\":\"1000\",\"color\":\"#FFFF0000\",\"style\":\"BOTH\",\"hotkey\":\"65:128\"}}";
		JsonObject v = values(ClipBundle.forExport(GSON, Arrays.asList(SESSION_10, settings), Collections.emptySet()).get(1));
		assertEquals(SettingsSnapshot.NOT_INCLUDED, v.get("highlightedItems").getAsString());
		assertEquals("1000", v.get("maxPrice").getAsString());
		assertEquals("#FFFF0000", v.get("color").getAsString());
		assertEquals("BOTH", v.get("style").getAsString());
		assertEquals("65:128", v.get("hotkey").getAsString());
		// No session line at all: judged by values as well.
		v = values(ClipBundle.forExport(GSON, Collections.singletonList(settings), Collections.emptySet()).get(0));
		assertEquals(SettingsSnapshot.NOT_INCLUDED, v.get("highlightedItems").getAsString());
	}

	@Test
	public void otherLinesPassUntouched()
	{
		String tick = "{\"e\":\"tick\",\"tick\":2,\"ms\":600,\"fps\":50}";
		String chat = "{\"e\":\"chat\",\"tick\":2,\"ms\":600,\"type\":\"GAMEMESSAGE\",\"text\":\"Rune\"}";
		List<String> out = ClipBundle.forExport(GSON, Arrays.asList(SESSION_11, tick, chat), Collections.emptySet());
		assertEquals(Arrays.asList(SESSION_11, tick, chat), out);
	}

	@Test
	public void theReadmeSaysWhatWasIncluded()
	{
		assertTrue(ClipBundle.textLine(Collections.emptySet()).contains("are left out"));
		String some = ClipBundle.textLine(new TreeSet<>(Arrays.asList("b.y", "a.x")));
		assertTrue(some, some.contains("you chose to include: a.x, b.y"));
		String readme = ClipBundle.readme(false, true, Collections.emptyList(), "", Collections.emptySet());
		assertTrue(readme, readme.contains("Text settings"));
		assertFalse(readme, readme.contains("%TEXT%"));
	}

	@Test
	public void thePickerShowsAValueOnOneShortLine()
	{
		assertEquals("a b", TextPicker.shown("a\nb"));
		String longValue = new String(new char[100]).replace('\0', 'x');
		assertEquals(TextPicker.SHOWN, TextPicker.shown(longValue).length());
	}

	@Test
	public void thePickerGroupsByPluginAndSearchesByPluginName()
	{
		// Owner, 2026-10-23: one closed section per plugin, and a search box by plugin.
		java.util.List<Desk.TextSetting> settings = java.util.Arrays.asList(
			new Desk.TextSetting("b.one", "Zebra Helper", "One", "x"),
			new Desk.TextSetting("a.list", "ground items", "Highlighted items", "coins"),
			new Desk.TextSetting("b.two", "Zebra Helper", "Two", "y"));
		java.util.Map<String, java.util.List<Desk.TextSetting>> byPlugin = TextPicker.byPlugin(settings);
		assertEquals(java.util.Arrays.asList("ground items", "Zebra Helper"), new java.util.ArrayList<>(byPlugin.keySet()));
		assertEquals(2, byPlugin.get("Zebra Helper").size());
		assertTrue(TextPicker.matches("Ground Items", "ground"));
		assertTrue(TextPicker.matches("Ground Items", "  "));
		assertFalse(TextPicker.matches("Ground Items", "zebra"));
		assertEquals("+ Ground Items (1 setting)", TextPicker.header("Ground Items", 1, 0, false));
		assertEquals("- Zebra Helper (2 settings, 1 ticked)", TextPicker.header("Zebra Helper", 2, 1, true));
	}
}
