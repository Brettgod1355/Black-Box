/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ShownCardLayoutTest
{
	@Test
	public void theAreaIsAsTallAsTheCardOnScreen()
	{
		RecorderSection.ShownCardLayout layout = new RecorderSection.ShownCardLayout();
		JPanel cards = new JPanel(layout);
		cards.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
		JPanel mark = new JPanel();
		mark.setPreferredSize(new Dimension(200, 40));
		JPanel flag = new JPanel();
		flag.setPreferredSize(new Dimension(200, 150));
		cards.add(mark, "mark");
		cards.add(flag, "flag");

		assertEquals("the Mark button only, not the hidden form", 50, cards.getPreferredSize().height);
		layout.show(cards, "flag");
		assertEquals("the form, once Mark is pressed", 160, cards.getPreferredSize().height);
		layout.show(cards, "mark");
		assertEquals("back to the button after Save or Cancel", 50, cards.getPreferredSize().height);
	}
}
