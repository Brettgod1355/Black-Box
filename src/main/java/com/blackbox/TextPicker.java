/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import net.runelite.client.ui.FontManager;

/**
 * Asked before every export when any plugin has a text setting: which of them go into the zip. None is ticked; the
 * player sees each value, so they know what they would send. Secrets, webhooks and keys are never offered. Swing
 * thread.
 */
final class TextPicker
{
	/** How much of a value the picker shows. */
	static final int SHOWN = 60;

	private TextPicker()
	{
	}

	/**
	 * The {@link Desk.TextSetting#id}s to include, empty when there is nothing to pick, or null when the player
	 * cancelled the export.
	 */
	static Set<String> ask(Component parent, List<Desk.TextSetting> settings)
	{
		if (settings.isEmpty())
		{
			return Collections.emptySet();
		}
		Map<JCheckBox, Desk.TextSetting> boxes = new LinkedHashMap<>();
		JPanel content = content(settings, boxes);
		Object[] options = {"Export", "Cancel"};
		int answer = JOptionPane.showOptionDialog(parent, content, "Export: text settings", JOptionPane.DEFAULT_OPTION,
			JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
		if (answer != 0)
		{
			return null;
		}
		List<String> picked = new ArrayList<>();
		boxes.forEach((box, setting) ->
		{
			if (box.isSelected())
			{
				picked.add(setting.id);
			}
		});
		return new TreeSet<>(picked);
	}

	/** The dialog's body: the explanation and one unticked box per setting, filling {@code boxes}. */
	static JPanel content(List<Desk.TextSetting> settings, Map<JCheckBox, Desk.TextSetting> boxes)
	{
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		for (Desk.TextSetting setting : settings)
		{
			JCheckBox box = new JCheckBox(setting.plugin + ": " + setting.name);
			box.setToolTipText(setting.value);
			boxes.put(box, setting);
			list.add(box);
			JLabel value = new JLabel(shown(setting.value));
			value.setFont(FontManager.getRunescapeSmallFont());
			value.setBorder(BorderFactory.createEmptyBorder(0, 24, 4, 0));
			list.add(value);
		}
		JScrollPane scroll = new JScrollPane(list);
		scroll.setPreferredSize(new Dimension(420, Math.min(320, 40 + settings.size() * 44)));
		JPanel content = new JPanel(new BorderLayout(0, 8));
		content.add(new JLabel("<html><body style='width:400px'>Text settings (anything typed into a plugin's settings, "
			+ "such as lists, names and links) are left out of the export. Tick any the developer needs to see. Secrets, "
			+ "webhooks and keys are never included.</body></html>"), BorderLayout.NORTH);
		content.add(scroll, BorderLayout.CENTER);
		return content;
	}

	/** A value cut to {@link #SHOWN} characters on one line, so a long list does not stretch the window. */
	static String shown(String value)
	{
		String one = value.replace('\n', ' ').replace('\r', ' ');
		return one.length() <= SHOWN ? one : one.substring(0, SHOWN - 1) + "…";
	}
}
