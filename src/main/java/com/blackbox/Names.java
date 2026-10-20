/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Recording folder names ({@code 2026-10-20_18-02-11}) as a person reads them: "Today 18:02", "Oct 18 14:56". */
final class Names
{
	static final DateTimeFormatter FOLDER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d HH:mm");

	private Names()
	{
	}

	/** The folder name's moment, or null when the folder is not one of Black Box's. */
	static LocalDateTime parse(String folderName)
	{
		try
		{
			return LocalDateTime.parse(folderName, FOLDER);
		}
		catch (DateTimeParseException | NullPointerException e)
		{
			return null;
		}
	}

	static String display(String folderName, LocalDateTime now)
	{
		LocalDateTime at = parse(folderName);
		if (at == null)
		{
			return folderName;
		}
		LocalDate day = at.toLocalDate();
		if (day.equals(now.toLocalDate()))
		{
			return "Today " + at.format(TIME);
		}
		if (day.equals(now.toLocalDate().minusDays(1)))
		{
			return "Yesterday " + at.format(TIME);
		}
		return at.format(DAY);
	}

	/** "18:02" for a wall-clock moment in milliseconds. */
	static String clock(long epochMillis)
	{
		return java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).format(TIME);
	}

	/** "1.8 MB", "412 KB", "900 B". */
	static String size(long bytes)
	{
		if (bytes >= 1024L * 1024)
		{
			return String.format("%.1f MB", bytes / (1024.0 * 1024));
		}
		if (bytes >= 1024)
		{
			return (bytes / 1024) + " KB";
		}
		return bytes + " B";
	}

	/** "12:34" or "1:02:03". */
	static String elapsed(long millis)
	{
		long s = Math.max(0, millis / 1000);
		return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60) : String.format("%d:%02d", s / 60, s % 60);
	}
}
