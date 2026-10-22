/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.awt.Color;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The Debug page: the Debug switch, the tests under it and one Select all / Clear all button. The switch is never
 * saved, so it is off whenever the client starts (and at logout with "Also turn off at logout"); the ticks are
 * saved. Swing thread.
 */
class DebugSection extends JPanel
{
	/** What the page reads and changes; the plugin's side. */
	interface Controls
	{
		boolean debugOn();

		void setDebugOn(boolean on);

		Set<DebugTest> tests();

		void setTests(Set<DebugTest> tests);
	}

	private static final Color COST = new Color(0xE0, 0xA0, 0x40);

	private final Controls controls;
	private final JToggleButton main = new JToggleButton();
	private final JLabel status = small("");
	private final Map<DebugTest, JCheckBox> boxes = new EnumMap<>(DebugTest.class);
	private final JButton select;

	DebugSection(Controls controls)
	{
		this.controls = controls;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		JLabel title = new JLabel("Debug");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(RecorderSection.fullWidth(title));
		add(RecorderSection.fullWidth(small("Extra detail for a bug report, for every plugin. Tick the tests a developer "
			+ "asks for, then switch Debug on. Debug turns off when the client closes, and at logout if that is set in "
			+ "the settings; the ticks stay. Nothing is written while the bank PIN keypad shows.")));
		main.setFocusPainted(false);
		main.addActionListener(e ->
		{
			controls.setDebugOn(main.isSelected());
			refresh();
		});
		JPanel switchRow = new JPanel(new java.awt.BorderLayout());
		switchRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		switchRow.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
		switchRow.add(main, java.awt.BorderLayout.CENTER);
		add(RecorderSection.fullWidth(switchRow));
		add(RecorderSection.fullWidth(status));

		JLabel tests = new JLabel("Tests");
		tests.setFont(FontManager.getRunescapeBoldFont());
		tests.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		tests.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0));
		add(RecorderSection.fullWidth(tests));
		for (DebugTest test : DebugTest.values())
		{
			JCheckBox box = new JCheckBox(test.label);
			box.setBackground(ColorScheme.DARK_GRAY_COLOR);
			box.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			box.setFocusPainted(false);
			box.setToolTipText(test.description);
			box.addActionListener(e ->
			{
				Set<DebugTest> now = ticked();
				controls.setTests(now);
				refresh();
			});
			boxes.put(test, box);
			add(RecorderSection.fullWidth(box));
			JLabel about = small((test.heavy ? "Can cost frames. " : "Light. ") + test.description);
			about.setBorder(BorderFactory.createEmptyBorder(0, 22, 4, 0));
			if (test.heavy)
			{
				about.setForeground(COST);
			}
			add(RecorderSection.fullWidth(about));
		}
		select = RecorderSection.button("Select all", "Tick every test, or untick them all once all are ticked", e ->
		{
			controls.setTests(DebugTest.afterSelect(ticked()));
			refresh();
		});
		JPanel selectRow = new JPanel(new java.awt.BorderLayout());
		selectRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		selectRow.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		selectRow.add(select, java.awt.BorderLayout.CENTER);
		add(RecorderSection.fullWidth(selectRow));
		refresh();
	}

	boolean isOn()
	{
		return controls.debugOn();
	}

	/** Shows the switch and the boxes as the plugin has them. */
	void refresh()
	{
		boolean on = controls.debugOn();
		Set<DebugTest> tests = controls.tests();
		main.setSelected(on);
		main.setText(on ? "Debug is on" : "Debug is off");
		boxes.forEach((test, box) -> box.setSelected(tests.contains(test)));
		select.setText(DebugTest.selectLabel(tests));
		if (!on)
		{
			setWrapped(status, "Off: no test runs, whatever is ticked.");
		}
		else if (tests.isEmpty())
		{
			setWrapped(status, "On, but no test is ticked.");
		}
		else
		{
			setWrapped(status, "On: " + tests.size() + (tests.size() == 1 ? " test runs" : " tests run") + " while you record.");
		}
	}

	private Set<DebugTest> ticked()
	{
		Set<DebugTest> now = EnumSet.noneOf(DebugTest.class);
		boxes.forEach((test, box) ->
		{
			if (box.isSelected())
			{
				now.add(test);
			}
		});
		return now;
	}

	/** Small gray text that wraps to the sidebar's width. */
	private static JLabel small(String text)
	{
		JLabel label = new JLabel();
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		setWrapped(label, text);
		return label;
	}

	private static void setWrapped(JLabel label, String text)
	{
		label.setText("<html><body style='width:170px'>" + text + "</body></html>");
	}
}
