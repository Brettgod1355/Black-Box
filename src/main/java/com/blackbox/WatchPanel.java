/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The sidebar's "Watched plugins" section (layout A): a Set dropdown with Save and Delete, a searchable picker of
 * the installed plugins, and one row per watched plugin with its on/off dot and a remove cross. What is
 * watched is the plugin's business ({@link Model}); this only shows and edits it.
 */
class WatchPanel extends JPanel
{
	/** What the panel reads and writes; every call is on the Swing thread. */
	interface Model
	{
		/** Every installed plugin's name, sorted, Black Box included. */
		List<String> installed();

		List<String> watched();

		void setWatched(List<String> names);

		boolean isOn(String name);

		/** The named sets, sorted. */
		List<String> sets();

		List<String> loadSet(String name);

		void saveSet(String name, List<String> names);

		void deleteSet(String name);
	}

	private static final String NO_SET = "(no set)";
	private static final String CHOOSE = "Choose…";

	private final Model model;
	private final JComboBox<String> sets = new JComboBox<>();
	private final PluginPicker picker = new PluginPicker(this::add);
	private final JPanel rows = new JPanel();
	private final javax.swing.JTextArea footer = Wrap.text(" ", null, 3);
	private boolean adjusting;

	WatchPanel(Model model)
	{
		this.model = model;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

		JLabel title = new JLabel("Watched plugins");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(left(title));

		JPanel setRow = new JPanel(new BorderLayout(4, 0));
		setRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		setRow.add(dim("Set"), BorderLayout.WEST);
		sets.setPrototypeDisplayValue("A long set name here");
		sets.addActionListener(e -> setChosen());
		setRow.add(sets, BorderLayout.CENTER);
		JPanel setButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		setButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		setButtons.add(small("Save", "Save the watched plugins as a set, under the chosen name or a new one", this::saveSet));
		setButtons.add(small("Del", "Delete the chosen set; the watched plugins stay", this::deleteSet));
		setRow.add(setButtons, BorderLayout.EAST);
		add(fullWidth(setRow));

		JPanel addRow = new JPanel(new BorderLayout(4, 0));
		addRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		addRow.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		addRow.add(picker, BorderLayout.CENTER);
		add(fullWidth(addRow));

		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setBackground(ColorScheme.DARK_GRAY_COLOR);
		rows.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		add(fullWidth(rows));

		footer.setAlignmentX(LEFT_ALIGNMENT);
		add(fullWidth(footer));
		refresh();
	}

	/** Rebuilds every control from the model. Swing thread. */
	void refresh()
	{
		adjusting = true;
		try
		{
			String chosenSet = (String) sets.getSelectedItem();
			List<String> setNames = new ArrayList<>();
			setNames.add(NO_SET);
			setNames.addAll(model.sets());
			sets.setModel(new DefaultComboBoxModel<>(setNames.toArray(new String[0])));
			sets.setSelectedItem(chosenSet != null && setNames.contains(chosenSet) ? chosenSet : NO_SET);

			List<String> watched = model.watched();
			List<String> free = new ArrayList<>();
			for (String name : model.installed())
			{
				if (!watched.contains(name))
				{
					free.add(name);
				}
			}
			picker.setChoices(free);

			rows.removeAll();
			for (String name : watched)
			{
				rows.add(row(name, model.isOn(name)));
				rows.add(javax.swing.Box.createVerticalStrut(3));
			}
			footer.setText(watched.isEmpty()
				? "Every plugin's settings are recorded. Watch the plugin you are reporting on: its setting changes show on the chart and the recording names it."
				: watched.size() + " watched: named in the recording, their setting changes on the chart. Every plugin's settings are recorded anyway.");
			rows.revalidate();
			revalidate();
			repaint();
		}
		finally
		{
			adjusting = false;
		}
	}

	private JComponent row(String name, boolean on)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 4));
		row.add(new Dot(on), BorderLayout.WEST);
		JLabel label = new JLabel(name);
		label.setToolTipText(on ? name + " is on" : name + " is off");
		row.add(label, BorderLayout.CENTER);
		JButton remove = new JButton("x");
		remove.setForeground(new Color(230, 90, 90));
		remove.setToolTipText("Stop watching " + name);
		remove.setMargin(new java.awt.Insets(0, 4, 0, 4));
		remove.setFocusPainted(false);
		remove.addActionListener(e ->
		{
			List<String> names = new ArrayList<>(model.watched());
			names.remove(name);
			model.setWatched(names);
			refresh();
		});
		row.add(remove, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		return row;
	}

	private void add(String name)
	{
		List<String> names = new ArrayList<>(model.watched());
		if (!names.contains(name))
		{
			names.add(name);
			model.setWatched(names);
		}
		refresh();
	}

	private void setChosen()
	{
		if (adjusting)
		{
			return;
		}
		String name = (String) sets.getSelectedItem();
		if (name == null || NO_SET.equals(name))
		{
			return;
		}
		model.setWatched(model.loadSet(name));
		refresh();
	}

	private void saveSet()
	{
		String name = (String) sets.getSelectedItem();
		if (name == null || NO_SET.equals(name))
		{
			name = JOptionPane.showInputDialog(this, "Name for this set of plugins:", "Save set", JOptionPane.PLAIN_MESSAGE);
			if (name == null)
			{
				return;
			}
			name = name.trim();
			if (!WatchList.isValidSetName(name))
			{
				JOptionPane.showMessageDialog(this, "A set name is up to 40 characters, without '.' or '|'.", "Save set", JOptionPane.WARNING_MESSAGE);
				return;
			}
		}
		model.saveSet(name, model.watched());
		refresh();
		sets.setSelectedItem(name);
	}

	private void deleteSet()
	{
		String name = (String) sets.getSelectedItem();
		if (name == null || NO_SET.equals(name))
		{
			return;
		}
		model.deleteSet(name);
		refresh();
	}

	private static JButton small(String text, String tip, Runnable action)
	{
		JButton button = new JButton(text);
		button.setToolTipText(tip);
		button.setMargin(new java.awt.Insets(1, 6, 1, 6));
		button.setFocusPainted(false);
		button.addActionListener(e -> action.run());
		return button;
	}

	private static JLabel dim(String text)
	{
		JLabel label = new JLabel(text, SwingConstants.LEFT);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	private static JComponent left(JComponent c)
	{
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrap.add(c, BorderLayout.WEST);
		wrap.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		return fullWidth(wrap);
	}

	private static JComponent fullWidth(JComponent c)
	{
		c.setAlignmentX(LEFT_ALIGNMENT);
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 400));
		return c;
	}

	/** The on/off dot. */
	private static final class Dot extends JComponent
	{
		private final boolean on;

		Dot(boolean on)
		{
			this.on = on;
			setPreferredSize(new Dimension(10, 10));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(on ? new Color(120, 210, 120) : ColorScheme.LIGHT_GRAY_COLOR);
				int d = Math.min(getWidth(), getHeight()) - 2;
				g2.fillOval((getWidth() - d) / 2, (getHeight() - d) / 2, d, d);
			}
			finally
			{
				g2.dispose();
			}
		}
	}
}
