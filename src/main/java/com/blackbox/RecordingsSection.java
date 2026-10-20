/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalDateTime;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** The sidebar's list of recordings: when, how big, and Zip, Open and Delete for each. */
class RecordingsSection extends JPanel
{
	private static final int SHOWN = 8;

	private final Desk desk;
	private final RecorderSection recorder;
	private final JPanel rows = new JPanel();
	private final JLabel more = new JLabel();

	RecordingsSection(Desk desk, RecorderSection recorder)
	{
		this.desk = desk;
		this.recorder = recorder;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
		JLabel title = new JLabel("Recordings");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JPanel head = new JPanel(new BorderLayout());
		head.setBackground(ColorScheme.DARK_GRAY_COLOR);
		head.add(title, BorderLayout.WEST);
		JButton reload = RecorderSection.button("Refresh", "List the recordings again", e -> refresh());
		head.add(reload, BorderLayout.EAST);
		add(RecorderSection.fullWidth(head));
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setBackground(ColorScheme.DARK_GRAY_COLOR);
		rows.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		add(RecorderSection.fullWidth(rows));
		more.setFont(FontManager.getRunescapeSmallFont());
		more.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(RecorderSection.fullWidth(more));
	}

	/** Lists again; the rows arrive on the Swing thread. */
	void refresh()
	{
		desk.recordings(this::show);
	}

	void show(List<Desk.Entry> entries)
	{
		rows.removeAll();
		LocalDateTime now = LocalDateTime.now();
		int shown = 0;
		for (Desk.Entry entry : entries)
		{
			if (shown++ >= SHOWN)
			{
				break;
			}
			rows.add(row(entry, now));
			rows.add(javax.swing.Box.createVerticalStrut(3));
		}
		if (entries.isEmpty())
		{
			JLabel none = new JLabel("No recordings yet.");
			none.setFont(FontManager.getRunescapeSmallFont());
			none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			rows.add(none);
		}
		more.setText(entries.size() > SHOWN ? (entries.size() - SHOWN) + " older in the folder." : " ");
		rows.revalidate();
		revalidate();
		repaint();
	}

	private JComponent row(Desk.Entry entry, LocalDateTime now)
	{
		JPanel row = new JPanel(new BorderLayout(4, 2));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 4));
		JLabel when = new JLabel(Names.display(entry.name, now) + (entry.current ? "  (recording)" : ""));
		when.setToolTipText(entry.name);
		JLabel size = new JLabel(Names.size(entry.bytes));
		size.setFont(FontManager.getRunescapeSmallFont());
		size.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JPanel top = new JPanel(new BorderLayout());
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		top.add(when, BorderLayout.CENTER);
		top.add(size, BorderLayout.EAST);
		row.add(top, BorderLayout.NORTH);
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		buttons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		buttons.add(RecorderSection.button("Zip", "Write a zip of this whole recording into the clips folder", e ->
		{
			recorder.say("Writing the zip…");
			desk.zipRecording(entry.name, recorder::say);
		}));
		buttons.add(RecorderSection.button("Open", "Open the recording's folder", e -> desk.openFolder(recorder::say)));
		if (!entry.current)
		{
			buttons.add(RecorderSection.button("Delete", "Delete this recording and its screenshots", e ->
			{
				int answer = JOptionPane.showConfirmDialog(this, "Delete the recording " + Names.display(entry.name, LocalDateTime.now()) + "?", "Delete recording", JOptionPane.OK_CANCEL_OPTION);
				if (answer == JOptionPane.OK_OPTION)
				{
					desk.delete(entry.name, text ->
					{
						recorder.say(text);
						refresh();
					});
				}
			}));
		}
		row.add(buttons, BorderLayout.SOUTH);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
		return row;
	}
}
