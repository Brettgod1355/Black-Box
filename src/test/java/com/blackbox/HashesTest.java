/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HashesTest
{
	@Test
	public void sameInputSameTwelveCharacters()
	{
		assertEquals("ba7816bf8f01", Hashes.short12("abc"));
		assertEquals(12, Hashes.short12("anything").length());
		assertNull(Hashes.short12(null));
	}

	@Test
	public void theProfileIdInAKeyIsHashed()
	{
		String key = Hashes.profileKey("MahoganyHomes.rsprofile.AbC123.contract");
		assertTrue(key, key.startsWith("MahoganyHomes.rsprofile."));
		assertTrue(key, key.endsWith(".contract"));
		assertFalse(key, key.contains("AbC123"));
		assertEquals("plain.key", Hashes.profileKey("plain.key"));
	}

	@Test
	public void longOrRiskyOtherValuesNeverWrittenInFull()
	{
		StringBuilder big = new StringBuilder();
		for (int i = 0; i < 300; i++)
		{
			big.append("item ").append(i).append(", ");
		}
		String other = SettingsSnapshot.other("layout", big.toString());
		assertTrue(other, other.startsWith("<" + big.length() + " chars, sha256 "));
		assertEquals(SettingsSnapshot.WITHHELD, SettingsSnapshot.other("webhookUrl", big.toString()));
		// Plain shapes stay; text is hashed: the saved login name, a party passphrase, recent friends chats.
		assertEquals("42", SettingsSnapshot.other("count", "42"));
		assertEquals("true", SettingsSnapshot.other("seen", "true"));
		assertEquals("TOP_LEFT", SettingsSnapshot.other("position", "TOP_LEFT"));
		assertEquals("120:340", SettingsSnapshot.other("xpTracker_preferredLocation", "120:340"));
		assertEquals("765x503", SettingsSnapshot.other("size", "765x503"));
		assertTrue(SettingsSnapshot.other("username", "someone@example.com").startsWith("<19 chars, sha256 "));
		assertTrue(SettingsSnapshot.other("previousPartyId", "apple-river-stone").startsWith("<17 chars, sha256 "));
		assertTrue(SettingsSnapshot.other("chatsData", "[{\"owner\":\"Zezima\"}]").startsWith("<"));
		assertTrue(SettingsSnapshot.other("hiddenList", "12,34,56").startsWith("<8 chars"));
		// An account hash is a long number: hashed, not written.
		assertTrue(SettingsSnapshot.other("accountHash", "1234567890123456789").startsWith("<19 chars"));
	}

	@Test
	public void frameClockKeepsTheLongestGap()
	{
		FrameClock clock = new FrameClock();
		clock.frame(0);
		clock.frame(20_000_000);
		clock.frame(95_500_000);
		assertEquals(3, clock.frames());
		assertEquals(75.5, clock.longestGapMs(), 0.0001);
		clock.reset();
		clock.frame(115_500_000);
		assertEquals(1, clock.frames());
		assertEquals(20.0, clock.longestGapMs(), 0.0001);
	}
}
