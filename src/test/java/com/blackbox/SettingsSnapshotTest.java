/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigItemDescriptor;
import org.junit.Test;

public class SettingsSnapshotTest
{
	@ConfigGroup("example")
	interface ExampleConfig extends Config
	{
		@ConfigItem(keyName = "webhook", name = "Webhook", description = "", secret = true)
		String webhook();

		@ConfigItem(keyName = "internal", name = "Internal", description = "", hidden = true)
		int internal();

		@ConfigItem(keyName = "greeting", name = "Greeting", description = "")
		String greeting();

		@ConfigItem(keyName = "site", name = "Site", description = "")
		String site();

		@ConfigItem(keyName = "showMapLines", name = "Lines", description = "")
		boolean showMapLines();

		@ConfigItem(keyName = "radius", name = "Radius", description = "")
		int radius();
	}

	private static List<ConfigItemDescriptor> items() throws Exception
	{
		List<ConfigItemDescriptor> items = new ArrayList<>();
		for (Method method : ExampleConfig.class.getDeclaredMethods())
		{
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			items.add(new ConfigItemDescriptor(item, method.getGenericReturnType(), null, null, null));
		}
		return items;
	}

	@Test
	public void secretsHiddenAndLinksAreWithheld() throws Exception
	{
		Map<String, String> stored = new HashMap<>();
		stored.put("webhook", "https://example.org/hooks/1/a");
		stored.put("internal", "7");
		stored.put("greeting", "hello there");
		stored.put("site", "https://example.org/page");
		stored.put("showMapLines", "true");
		stored.put("radius", "50");
		JsonObject values = SettingsSnapshot.of(items(), stored::get);
		assertEquals(SettingsSnapshot.SECRET, values.get("webhook").getAsString());
		assertFalse(values.has("internal"));
		assertEquals("hello there", values.get("greeting").getAsString());
		assertEquals(SettingsSnapshot.WITHHELD, values.get("site").getAsString());
		assertEquals("true", values.get("showMapLines").getAsString());
		assertEquals("50", values.get("radius").getAsString());
	}

	@Test
	public void unsetValuesAreLeftOut() throws Exception
	{
		JsonObject values = SettingsSnapshot.of(items(), key -> null);
		assertTrue(values.has("webhook"));
		assertFalse(values.has("radius"));
	}

	@Test
	public void freeTextRules()
	{
		assertEquals("hello there", SettingsSnapshot.text("greeting", "hello there"));
		assertEquals(SettingsSnapshot.WITHHELD, SettingsSnapshot.text("greeting", "see http://x.y"));
		assertEquals(SettingsSnapshot.WITHHELD, SettingsSnapshot.text("greeting", "www.example.org"));
		assertEquals(SettingsSnapshot.WITHHELD, SettingsSnapshot.text("discordUrl", "anything"));
		assertEquals(SettingsSnapshot.WITHHELD, SettingsSnapshot.text("apiKey", "anything"));
		assertEquals(SettingsSnapshot.WITHHELD, SettingsSnapshot.text("note", "AB12cd34EF56gh78IJ90kl12MN34op"));
		assertEquals("Salty Pete", SettingsSnapshot.text("rsn", "Salty Pete"));
		assertEquals("abcdefghijklmnopqrstuvwxyzabc", SettingsSnapshot.text("note", "abcdefghijklmnopqrstuvwxyzabc"));
		assertEquals("12", SettingsSnapshot.text("count", "12"));
	}
}
