/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.geom.AffineTransform;
import java.util.Locale;
import javax.swing.UIManager;
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
		// Format 9: the game build, the renderer, and how the desktop draws the window. No JVM flags or heap: those
		// need java.lang.management, which the Hub refuses (shared research §12).
		header.addProperty("revision", client.getRevision());
		header.addProperty("gpu", client.isGpu());
		header.addProperty("locale", Locale.getDefault().toString());
		header.addProperty("lookAndFeel", UIManager.getLookAndFeel() == null ? null : UIManager.getLookAndFeel().getName());
		header.addProperty("toolkit", System.getProperty("awt.toolkit"));
		JsonObject java2d = new JsonObject();
		for (String name : new String[]{"sun.java2d.opengl", "sun.java2d.d3d", "sun.java2d.noddraw", "sun.java2d.uiScale"})
		{
			String value = System.getProperty(name);
			if (value != null)
			{
				java2d.addProperty(name.substring("sun.java2d.".length()), value);
			}
		}
		if (java2d.size() > 0)
		{
			header.add("java2d", java2d);
		}
		if (!GraphicsEnvironment.isHeadless())
		{
			// Each screen's size and the desktop's scaling, which changes how overlays line up.
			JsonArray screens = new JsonArray();
			for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
			{
				AffineTransform scale = device.getDefaultConfiguration().getDefaultTransform();
				JsonObject one = new JsonObject();
				one.addProperty("size", device.getDefaultConfiguration().getBounds().width + "x"
					+ device.getDefaultConfiguration().getBounds().height);
				one.addProperty("scale", scale.getScaleX());
				screens.add(one);
			}
			header.add("screens", screens);
		}
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
