/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** A line of small text that wraps to the sidebar's width, long file paths included; looks like a label. */
final class Wrap
{
	private Wrap()
	{
	}

	static JTextArea text(String text, Color color)
	{
		return text(text, color, 2);
	}

	/** With room for {@code rows} lines, so the layout never has to grow after the text wraps. */
	static JTextArea text(String text, Color color, int rows)
	{
		JTextArea area = new JTextArea(text, rows, 1);
		area.setEditable(false);
		area.setFocusable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setOpaque(false);
		area.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		area.setFont(FontManager.getRunescapeSmallFont());
		area.setForeground(color == null ? ColorScheme.LIGHT_GRAY_COLOR : color);
		area.setBackground(new Color(0, 0, 0, 0));
		return area;
	}

	static JTextArea text(String text)
	{
		return text(text, null);
	}
}
