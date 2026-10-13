/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Starts a developer client with Black Box loaded. To record while testing another plugin, add Black Box's
 * class to that plugin's own launcher instead (see the README).
 */
public final class BlackBoxPluginTest
{
	@SuppressWarnings("unchecked") // RuneLite's loadBuiltin uses generic varargs.
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BlackBoxPlugin.class);
		RuneLite.main(args);
	}
}
