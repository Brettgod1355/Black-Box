/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A box that reads "Add a plugin"; clicking it drops down a search field with the list of choices under it.
 * Typing narrows the list to the names containing the letters; Enter or a click picks one, Escape closes.
 */
class PluginPicker extends JButton
{
	private static final int ROWS = 9;

	private final Consumer<String> onPick;
	private List<String> choices = new ArrayList<>();

	PluginPicker(Consumer<String> onPick)
	{
		super("Add a plugin…");
		this.onPick = onPick;
		setHorizontalAlignment(LEFT);
		setFocusPainted(false);
		setToolTipText("Click, then type part of a plugin's name");
		addActionListener(e -> open());
	}

	void setChoices(List<String> names)
	{
		choices = new ArrayList<>(names);
	}

	private void open()
	{
		JPopupMenu popup = new JPopupMenu();
		popup.setFocusable(true);
		popup.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));

		JPanel body = new JPanel(new BorderLayout(0, 4));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		body.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		JTextField search = new JTextField();
		search.setToolTipText("Type part of a name");
		search.putClientProperty("JTextField.placeholderText", "Search plugins");
		body.add(search, BorderLayout.NORTH);

		DefaultListModel<String> model = new DefaultListModel<>();
		JList<String> list = new JList<>(model);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setVisibleRowCount(ROWS);
		list.setFont(FontManager.getRunescapeFont());
		JScrollPane scroll = new JScrollPane(list);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		body.add(scroll, BorderLayout.CENTER);

		JLabel count = new JLabel(" ");
		count.setFont(FontManager.getRunescapeSmallFont());
		count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		body.add(count, BorderLayout.SOUTH);

		Runnable filter = () ->
		{
			String needle = search.getText().trim().toLowerCase(Locale.ROOT);
			model.clear();
			for (String name : choices)
			{
				if (needle.isEmpty() || name.toLowerCase(Locale.ROOT).contains(needle))
				{
					model.addElement(name);
				}
			}
			if (!model.isEmpty())
			{
				list.setSelectedIndex(0);
			}
			count.setText(model.getSize() + " of " + choices.size() + (needle.isEmpty() ? "" : " match \"" + search.getText().trim() + "\""));
		};
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				filter.run();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				filter.run();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				filter.run();
			}
		});
		Runnable pick = () ->
		{
			String chosen = list.getSelectedValue();
			if (chosen != null)
			{
				popup.setVisible(false);
				onPick.accept(chosen);
			}
		};
		search.addActionListener(e -> pick.run());
		search.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				int i = list.getSelectedIndex();
				if (e.getKeyCode() == KeyEvent.VK_DOWN && i < model.getSize() - 1)
				{
					list.setSelectedIndex(i + 1);
					list.ensureIndexIsVisible(i + 1);
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_UP && i > 0)
				{
					list.setSelectedIndex(i - 1);
					list.ensureIndexIsVisible(i - 1);
					e.consume();
				}
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
				{
					popup.setVisible(false);
				}
			}
		});
		list.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (list.locationToIndex(e.getPoint()) >= 0)
				{
					list.setSelectedIndex(list.locationToIndex(e.getPoint()));
					pick.run();
				}
			}
		});
		list.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					pick.run();
				}
				else if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
				{
					popup.setVisible(false);
				}
			}
		});

		filter.run();
		body.setPreferredSize(new Dimension(Math.max(getWidth(), 200), 24 + ROWS * 18 + 30));
		popup.add(body);
		popup.show(this, 0, getHeight());
		SwingUtilities.invokeLater(search::requestFocusInWindow);
	}

	/** The popup's body with a given search text, for a picture of it; not used by the client. */
	static JPanel preview(List<String> choices, String typed)
	{
		JPanel body = new JPanel(new BorderLayout(0, 4));
		body.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		body.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		JTextField search = new JTextField(typed);
		body.add(search, BorderLayout.NORTH);
		DefaultListModel<String> model = new DefaultListModel<>();
		String needle = typed.toLowerCase(Locale.ROOT);
		for (String name : choices)
		{
			if (needle.isEmpty() || name.toLowerCase(Locale.ROOT).contains(needle))
			{
				model.addElement(name);
			}
		}
		JList<String> list = new JList<>(model);
		list.setSelectedIndex(0);
		list.setVisibleRowCount(ROWS);
		body.add(new JScrollPane(list), BorderLayout.CENTER);
		JLabel count = new JLabel(model.getSize() + " of " + choices.size() + (needle.isEmpty() ? "" : " match \"" + typed + "\""));
		count.setFont(FontManager.getRunescapeSmallFont());
		count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		body.add(count, BorderLayout.SOUTH);
		return body;
	}
}
