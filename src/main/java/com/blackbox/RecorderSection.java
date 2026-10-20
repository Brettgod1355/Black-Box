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
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The top of the sidebar: the recorder's state (a red dot, since when, the name and size; recording starts at
 * login by itself), then Clip (the last 2, 10 or 30 minutes, or a number of your own), Export today, Mark and
 * Open folder, a box for a note, and a status line that says where a clip went. Built for a player a developer
 * sent here: nothing to start, one obvious button to send.
 */
class RecorderSection extends JPanel
{
	static final Color RED = new Color(230, 50, 50);

	private final Desk desk;
	private final Dot dot = new Dot();
	private final JLabel title = new JLabel("Not recording");
	private final JLabel detail = new JLabel(" ");
	private final JTextField noteBox = new JTextField();
	private final javax.swing.JTextArea status = Wrap.text(" ", null, 4);
	private final Timer timer;

	RecorderSection(Desk desk)
	{
		this.desk = desk;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel card = new JPanel(new BorderLayout(0, 4));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		JPanel top = new JPanel(new BorderLayout(8, 0));
		top.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		top.add(dot, BorderLayout.WEST);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		top.add(title, BorderLayout.CENTER);
		card.add(top, BorderLayout.NORTH);
		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		card.add(detail, BorderLayout.SOUTH);
		add(fullWidth(card));

		JPanel actions = new JPanel(new GridLayout(2, 2, 4, 4));
		actions.setBackground(ColorScheme.DARK_GRAY_COLOR);
		actions.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		JButton clip = button("Clip…", "Save the last minutes as a zip to send to a developer", null);
		clip.setFont(FontManager.getRunescapeBoldFont());
		clip.addActionListener(e -> clipMenu(clip));
		actions.add(clip);
		actions.add(button("Export today", "Save every recording of today as one zip", e ->
		{
			say("Writing today's export…");
			desk.exportToday(this::say);
		}));
		actions.add(button("Mark", "Mark this moment in the recording, with a picture", e -> desk.mark()));
		actions.add(button("Open folder", "Open the folder that holds the recordings and clips", e -> desk.openFolder(this::say)));
		add(fullWidth(actions));

		noteBox.setToolTipText("What happened? Press Enter to add it to the recording as a note at this moment.");
		noteBox.putClientProperty("JTextField.placeholderText", "What happened? Enter adds a note");
		noteBox.addActionListener(e ->
		{
			String text = noteBox.getText().trim();
			if (!text.isEmpty())
			{
				desk.note(text);
				noteBox.setText("");
				say("Note added.");
			}
		});
		JPanel noteRow = new JPanel(new BorderLayout(0, 2));
		noteRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		noteRow.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		JLabel noteCaption = new JLabel("What happened? Enter adds a note.");
		noteCaption.setFont(FontManager.getRunescapeSmallFont());
		noteCaption.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		noteRow.add(noteCaption, BorderLayout.NORTH);
		noteRow.add(noteBox, BorderLayout.CENTER);
		add(fullWidth(noteRow));

		JPanel statusRow = new JPanel(new BorderLayout());
		statusRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		statusRow.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		statusRow.add(status, BorderLayout.CENTER);
		add(fullWidth(statusRow));

		timer = new Timer(1000, e -> refresh());
		timer.setRepeats(true);
		refresh();
	}

	void start()
	{
		refresh();
		timer.start();
	}

	void stop()
	{
		timer.stop();
	}

	/** Reflects the recorder's state; Swing thread. */
	void refresh()
	{
		boolean on = desk.isRecording();
		dot.setOn(on);
		if (on)
		{
			title.setText("Recording since " + Names.clock(desk.startedAt()));
			detail.setText(Names.elapsed(System.currentTimeMillis() - desk.startedAt()) + " so far,  " + Names.size(desk.bytesSoFar()));
		}
		else if (desk.isRecordingEnabled())
		{
			title.setText("Not recording");
			detail.setText("Recording starts when you log in.");
		}
		else
		{
			title.setText("Recording is off");
			detail.setText("Turn Record on in Black Box's settings.");
		}
	}

	void say(String text)
	{
		status.setText(text);
		status.revalidate();
	}

	private void clipMenu(JComponent at)
	{
		JPopupMenu menu = new JPopupMenu();
		for (int minutes : new int[]{2, 10, 30})
		{
			JMenuItem item = new JMenuItem("Last " + minutes + " minutes");
			item.addActionListener(e -> clip(minutes));
			menu.add(item);
		}
		JMenuItem custom = new JMenuItem("Last… (a number of minutes)");
		custom.addActionListener(e ->
		{
			String answer = JOptionPane.showInputDialog(this, "Clip the last how many minutes?", "Clip", JOptionPane.PLAIN_MESSAGE);
			if (answer == null)
			{
				return;
			}
			try
			{
				int minutes = Integer.parseInt(answer.trim());
				if (minutes > 0 && minutes <= 24 * 60)
				{
					clip(minutes);
					return;
				}
			}
			catch (NumberFormatException ignored)
			{
				// fall through
			}
			say("Give a number of minutes from 1 to 1440.");
		});
		menu.add(custom);
		menu.show(at, 0, at.getHeight());
	}

	private void clip(int minutes)
	{
		say("Writing the clip…");
		desk.clip(minutes, this::say);
	}

	/** Puts text on the system clipboard; any failure is reported, not thrown. */
	static boolean copy(String text)
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
			return true;
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	static JButton button(String text, String tip, java.awt.event.ActionListener action)
	{
		JButton button = new JButton(text);
		button.setToolTipText(tip);
		button.setFocusPainted(false);
		button.setMargin(new java.awt.Insets(2, 4, 2, 4));
		if (action != null)
		{
			button.addActionListener(action);
		}
		return button;
	}

	static JComponent fullWidth(JComponent c)
	{
		c.setAlignmentX(LEFT_ALIGNMENT);
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 200));
		return c;
	}

	/** The recording dot: red while recording, gray otherwise. */
	static final class Dot extends JComponent
	{
		private boolean on;

		Dot()
		{
			setPreferredSize(new Dimension(22, 22));
		}

		void setOn(boolean on)
		{
			this.on = on;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(on ? RED : ColorScheme.MEDIUM_GRAY_COLOR);
				g2.fillOval(2, (getHeight() - 18) / 2, 18, 18);
			}
			finally
			{
				g2.dispose();
			}
		}
	}
}
