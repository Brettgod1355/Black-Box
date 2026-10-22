/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Short hashes: the same input gives the same 12 characters, so recordings compare without holding the value. */
final class Hashes
{
	private Hashes()
	{
	}

	/** The first 12 hex characters of the SHA-256 of the text, or null for null. */
	static String short12(String text)
	{
		if (text == null)
		{
			return null;
		}
		try
		{
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (int i = 0; i < 6; i++)
			{
				hex.append(String.format("%02x", digest[i]));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			// Every Java has SHA-256; without it, say so rather than write the value.
			return "unhashed";
		}
	}

	/**
	 * A config key with the RuneScape profile in it ({@code rsprofile.<id>.key}, the id derived from the account)
	 * rewritten with the id hashed, the same way the {@code profile} line hashes it.
	 */
	static String profileKey(String key)
	{
		int at = key.indexOf("rsprofile.");
		if (at < 0)
		{
			return key;
		}
		int start = at + "rsprofile.".length();
		int end = key.indexOf('.', start);
		if (end < 0)
		{
			return key;
		}
		return key.substring(0, start) + short12(key.substring(start, end)) + key.substring(end);
	}
}
