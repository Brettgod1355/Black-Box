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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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

	/**
	 * The dialog's body: the explanation, a search box, and one section per plugin, closed at first, with an unticked box
	 * per setting inside, filling {@code boxes} (owner, 2026-10-23: "each plugin has their own dropdown box that are all
	 * collapsed by default and add a searchbar to search by plugin").
	 */
	static JPanel content(List<Desk.TextSetting> settings, Map<JCheckBox, Desk.TextSetting> boxes)
	{
		JPanel list = new JPanel();
		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		Map<String, JPanel> sections = new LinkedHashMap<>();
		for (Map.Entry<String, List<Desk.TextSetting>> plugin : byPlugin(settings).entrySet())
		{
			JPanel section = section(plugin.getKey(), plugin.getValue(), boxes);
			sections.put(plugin.getKey(), section);
			list.add(section);
		}
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		JLabel about = new JLabel("<html><body style='width:400px'>Text settings (anything typed into a plugin's settings, "
			+ "such as lists, names and links) are left out of the export. Tick any the developer needs to see. Secrets, "
			+ "webhooks and keys are never included.</body></html>");
		about.setAlignmentX(Component.LEFT_ALIGNMENT);
		top.add(about);
		JTextField search = new JTextField();
		search.putClientProperty("JTextField.placeholderText", "Search plugins");
		search.setToolTipText("Show only the plugins whose name contains this");
		search.setAlignmentX(Component.LEFT_ALIGNMENT);
		search.setMaximumSize(new Dimension(Integer.MAX_VALUE, search.getPreferredSize().height));
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				filter();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				filter();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				filter();
			}

			private void filter()
			{
				String query = search.getText();
				sections.forEach((plugin, section) -> section.setVisible(matches(plugin, query)));
				list.revalidate();
				list.repaint();
			}
		});
		top.add(Box.createVerticalStrut(8));
		top.add(search);
		JScrollPane scroll = new JScrollPane(list);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setPreferredSize(new Dimension(420, 320));
		JPanel content = new JPanel(new BorderLayout(0, 8));
		content.add(top, BorderLayout.NORTH);
		content.add(scroll, BorderLayout.CENTER);
		return content;
	}

	/** One plugin: a header that opens and closes its settings, saying how many there are and how many are ticked. */
	private static JPanel section(String plugin, List<Desk.TextSetting> settings, Map<JCheckBox, Desk.TextSetting> boxes)
	{
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBorder(BorderFactory.createEmptyBorder(0, 16, 4, 0));
		body.setVisible(false);
		JButton header = new JButton();
		header.setHorizontalAlignment(SwingConstants.LEFT);
		header.setFocusPainted(false);
		header.setToolTipText("Show or hide " + plugin + "'s text settings");
		List<JCheckBox> own = new ArrayList<>();
		Runnable label = () ->
		{
			int ticked = (int) own.stream().filter(JCheckBox::isSelected).count();
			header.setText(header(plugin, settings.size(), ticked, body.isVisible()));
		};
		for (Desk.TextSetting setting : settings)
		{
			JCheckBox box = new JCheckBox(setting.name);
			box.setToolTipText(setting.value);
			box.setAlignmentX(Component.LEFT_ALIGNMENT);
			box.addActionListener(e -> label.run());
			boxes.put(box, setting);
			own.add(box);
			body.add(box);
			JLabel value = new JLabel(shown(setting.value));
			value.setFont(FontManager.getRunescapeSmallFont());
			value.setBorder(BorderFactory.createEmptyBorder(0, 24, 4, 0));
			value.setAlignmentX(Component.LEFT_ALIGNMENT);
			body.add(value);
		}
		// No taller than it needs: BoxLayout would otherwise share the list's spare height out as gaps under each header.
		JPanel section = new JPanel(new BorderLayout())
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		header.addActionListener(e ->
		{
			body.setVisible(!body.isVisible());
			label.run();
			section.revalidate();
			section.repaint();
		});
		label.run();
		section.add(header, BorderLayout.NORTH);
		section.add(body, BorderLayout.CENTER);
		section.setAlignmentX(Component.LEFT_ALIGNMENT);
		return section;
	}

	/** The settings by plugin, plugins in name order (any case), each plugin's settings in the order given. */
	static Map<String, List<Desk.TextSetting>> byPlugin(List<Desk.TextSetting> settings)
	{
		Map<String, List<Desk.TextSetting>> grouped = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (Desk.TextSetting setting : settings)
		{
			grouped.computeIfAbsent(setting.plugin, k -> new ArrayList<>()).add(setting);
		}
		return new LinkedHashMap<>(grouped);
	}

	/** Whether a plugin shows for the search box's text: its name contains it, any case; an empty search shows all. */
	static boolean matches(String plugin, String query)
	{
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		return q.isEmpty() || plugin.toLowerCase(Locale.ROOT).contains(q);
	}

	/** A plugin's header: "+ Ground Items (2 settings)" closed, "- ..." open, with how many are ticked when any are. */
	static String header(String plugin, int settings, int ticked, boolean open)
	{
		String count = settings == 1 ? "1 setting" : settings + " settings";
		return (open ? "- " : "+ ") + plugin + " (" + count + (ticked > 0 ? ", " + ticked + " ticked" : "") + ")";
	}

	/** A value cut to {@link #SHOWN} characters on one line, so a long list does not stretch the window. */
	static String shown(String value)
	{
		String one = value.replace('\n', ' ').replace('\r', ' ');
		return one.length() <= SHOWN ? one : one.substring(0, SHOWN - 1) + "…";
	}
}
