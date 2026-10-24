/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(BlackBoxConfig.GROUP)
public interface BlackBoxConfig extends Config
{
	String GROUP = "blackbox";

	@ConfigSection(name = "Screenshots", description = "Pictures of the game view saved beside the recording.", position = 10)
	String SCREENSHOTS = "screenshots";

	@ConfigSection(name = "Cleanup", description = "Old recordings are deleted on their own so the folder stays small.", position = 20)
	String CLEANUP = "cleanup";

	@ConfigSection(name = "Debug", description = "The Debug page in the sidebar: extra tests for a bug report.", position = 30)
	String DEBUG = "debug";

	@ConfigItem(
		keyName = "record",
		name = "Record",
		description = "Record the game while you are logged in: game messages, inventory and other item changes, objects and "
			+ "NPCs appearing, game values, XP, your animation and position, interfaces opening, your clicks and every "
			+ "plugin's notifications. Other players' names and chat are never recorded. Each login starts a new recording "
			+ "in the Black Box folder; nothing leaves it unless you make a clip. Off stops recording.",
		position = 1
	)
	default boolean record()
	{
		return true;
	}

	@ConfigItem(
		keyName = "markHotkey",
		name = "Mark hotkey",
		description = "Press while recording to mark the moment (and take a screenshot if screenshots are on), for "
			+ "something worth a look later.",
		position = 2
	)
	default Keybind markHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "recordSounds",
		name = "Sound effects",
		description = "Record the id of each sound effect the game plays, so a replay can use what a plugin hears. "
			+ "Only while the game's sound effects are on. Nothing about other players is recorded with it.",
		position = 3
	)
	default boolean recordSounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shotEverySeconds",
		name = "Every",
		description = "Take a screenshot this often while recording. 0, the default, leaves pictures to Mark and notifications.",
		section = SCREENSHOTS,
		position = 1
	)
	@Range(min = 0, max = 600)
	@Units(Units.SECONDS)
	default int shotEverySeconds()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "shotOnNotification",
		name = "On notifications",
		description = "Take a screenshot whenever any plugin sends a notification while recording.",
		section = SCREENSHOTS,
		position = 2
	)
	default boolean shotOnNotification()
	{
		return true;
	}

	@ConfigItem(
		keyName = "picturesInClips",
		name = "Pictures in clips",
		description = "Put the pictures of the clipped minutes into a clip. Off by default: a picture shows whatever was on "
			+ "screen, other players' names over their heads included (the chat box is blacked out).",
		section = SCREENSHOTS,
		position = 3
	)
	default boolean picturesInClips()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideChat",
		name = "Hide the chat box",
		description = "Black out the chat box, and split private chat above it, in every screenshot, so other players' names and"
			+ " messages are never saved.",
		section = SCREENSHOTS,
		position = 4
	)
	default boolean hideChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shotWidth",
		name = "Width",
		description = "Screenshots are shrunk to at most this many pixels across, to keep them small.",
		section = SCREENSHOTS,
		position = 5
	)
	@Range(min = 320, max = 3840)
	@Units(Units.PIXELS)
	default int shotWidth()
	{
		return 960;
	}

	@ConfigItem(
		keyName = "keepDays",
		name = "Keep for (days)",
		description = "Delete recordings older than this many days. 0 keeps them until the size limit.",
		section = CLEANUP,
		position = 1
	)
	@Range(min = 0, max = 365)
	default int keepDays()
	{
		return 7;
	}

	@ConfigItem(
		keyName = "maxMegabytes",
		name = "Size limit (MB)",
		description = "Then delete the oldest recordings until the rest fit in this many megabytes. 0 for no limit.",
		section = CLEANUP,
		position = 2
	)
	@Range(min = 0, max = 100_000)
	default int maxMegabytes()
	{
		return 500;
	}

	@ConfigItem(
		keyName = "debugOffAtLogout",
		name = "Also turn off at logout",
		description = "Switch Debug off when you log out, not only when the client closes. The ticked tests stay ticked.",
		section = DEBUG,
		position = 1
	)
	default boolean debugOffAtLogout()
	{
		return false;
	}

	/** The tests ticked on the Debug page, as {@link DebugTest#format} writes them. Set from the page only. */
	@ConfigItem(
		keyName = "debugTests",
		name = "Debug tests",
		description = "The tests ticked on the Debug page.",
		hidden = true
	)
	default String debugTests()
	{
		return "";
	}
}
