/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.externalplugins.PluginHubManifest;
import net.runelite.client.plugins.Plugin;

/**
 * What RuneLite writes in the first lines of its own log at launch, and what a developer asks for first:
 * the client, launcher and Plugin Hub versions, Java, the operating system, the window. None of it says who
 * the player is. Hub plugins carry their version too, from the manifest the client loaded them with.
 */
final class Environment
{
	private Environment()
	{
	}

	/** Adds the environment fields to a recording's first line. Client thread. */
	static void describe(JsonObject header, Client client)
	{
		header.addProperty("runeliteCommit", RuneLiteProperties.getCommit());
		header.addProperty("launcher", RuneLiteProperties.getLauncherVersion());
		header.addProperty("pluginHub", RuneLiteProperties.getPluginHubVersion());
		header.addProperty("java", property("java.version") + " " + property("java.vendor"));
		header.addProperty("os", property("os.name") + " " + property("os.version") + " " + property("os.arch"));
		header.addProperty("canvas", client.getCanvasWidth() + "x" + client.getCanvasHeight());
		header.addProperty("resizable", client.isResized());
		header.addProperty("stretched", client.isStretchedEnabled());
	}

	/** A Hub plugin's version from the manifest it was loaded with, or null for RuneLite's own plugins and sideloaded jars. */
	static String versionOf(Plugin plugin)
	{
		PluginHubManifest.DisplayData data = ExternalPluginManager.getDisplayData(plugin.getClass());
		return data == null ? null : data.getVersion();
	}

	private static String property(String name)
	{
		String value = System.getProperty(name);
		return value == null ? "?" : value;
	}
}
