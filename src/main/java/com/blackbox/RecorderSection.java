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
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The top of the sidebar: the recorder's state (a red dot, since when, the time so far and the size; recording
 * starts at login by itself), Export (the last 2, 10 or 30 minutes, a number of your own, or all of today) and
 * Open folder, then Mark. Pressing Mark keeps the moment aside and turns the Mark area into a small form: the
 * time that was saved, a box for what happened, Save flag and Cancel. Nothing goes into the recording until
 * Save flag. Built for a player a developer sent here: nothing to start, one button to send.
 */
class RecorderSection extends JPanel
{
	static final Color RED = new Color(230, 50, 50);

	private final Desk desk;
	private final Dot dot = new Dot();
	private final JLabel title = new JLabel("Not recording");
	private final JLabel detail = new JLabel(" ");
	private final JPanel markCards = new JPanel(new java.awt.CardLayout());
	private final JLabel flagTitle = new JLabel("Spot flagged");
	private final javax.swing.JTextArea flagText = new javax.swing.JTextArea();
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

		JPanel actions = new JPanel(new GridLayout(1, 2, 4, 0));
		actions.setBackground(ColorScheme.DARK_GRAY_COLOR);
		actions.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		JButton export = button("Export…", "Save the last minutes, or all of today, as a zip to send to a developer", null);
		export.setFont(FontManager.getRunescapeBoldFont());
		export.addActionListener(e -> exportMenu(export));
		actions.add(export);
		actions.add(button("Open folder", "Open the folder that holds the recordings and exports", e -> desk.openFolder(this::say)));
		add(fullWidth(actions));

		markCards.setBackground(ColorScheme.DARK_GRAY_COLOR);
		markCards.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

		JPanel markRow = new JPanel(new BorderLayout(0, 3));
		markRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JButton mark = button("Mark", "Flag this moment in the recording", e -> beginMark());
		mark.setFont(FontManager.getRunescapeBoldFont());
		markRow.add(mark, BorderLayout.NORTH);
		markRow.add(Wrap.text("Press Mark the moment you see the problem. The time is saved at once; you then say what happened and save the flag. Export afterwards.", null, 4), BorderLayout.CENTER);
		markCards.add(markRow, "mark");

		JPanel flagForm = new JPanel(new BorderLayout(0, 4));
		flagForm.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		flagForm.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		flagTitle.setFont(FontManager.getRunescapeBoldFont());
		flagTitle.setForeground(Color.WHITE);
		JPanel flagHead = new JPanel(new BorderLayout(0, 2));
		flagHead.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		flagHead.add(flagTitle, BorderLayout.NORTH);
		flagHead.add(Wrap.text("The time is saved. Say what happened, as much or as little as you like, then save the flag. Nothing is written until you do.", null, 3), BorderLayout.CENTER);
		flagForm.add(flagHead, BorderLayout.NORTH);
		flagText.setLineWrap(true);
		flagText.setWrapStyleWord(true);
		flagText.setRows(3);
		flagText.setToolTipText("What happened? Optional.");
		flagText.putClientProperty("JTextArea.placeholderText", "What happened? (optional)");
		javax.swing.JScrollPane flagScroll = new javax.swing.JScrollPane(flagText);
		flagScroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		flagForm.add(flagScroll, BorderLayout.CENTER);
		JPanel flagButtons = new JPanel(new GridLayout(1, 2, 4, 0));
		flagButtons.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JButton save = button("Save flag", "Write the flag and what you said into the recording", e -> saveMark());
		save.setFont(FontManager.getRunescapeBoldFont());
		flagButtons.add(save);
		flagButtons.add(button("Cancel", "Drop it; nothing is written", e -> cancelMark()));
		flagForm.add(flagButtons, BorderLayout.SOUTH);
		markCards.add(flagForm, "flag");
		add(fullWidth(markCards));

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

	private void beginMark()
	{
		desk.beginMark(when ->
		{
			flagTitle.setText("Spot flagged at " + when);
			flagText.setText("");
			((java.awt.CardLayout) markCards.getLayout()).show(markCards, "flag");
			flagText.requestFocusInWindow();
			say(" ");
		});
	}

	private void saveMark()
	{
		desk.saveMark(flagText.getText().trim());
		((java.awt.CardLayout) markCards.getLayout()).show(markCards, "mark");
		say("Flag saved into the recording. Export when you are ready.");
	}

	private void cancelMark()
	{
		desk.cancelMark();
		((java.awt.CardLayout) markCards.getLayout()).show(markCards, "mark");
		say("Flag dropped; nothing was written.");
	}

	/** For a picture of the form. */
	void showFlagForm(String when, String typed)
	{
		flagTitle.setText("Spot flagged at " + when);
		flagText.setText(typed);
		((java.awt.CardLayout) markCards.getLayout()).show(markCards, "flag");
	}

	private void exportMenu(JComponent at)
	{
		JPopupMenu menu = new JPopupMenu();
		for (int minutes : new int[]{2, 10, 30})
		{
			JMenuItem item = new JMenuItem("The last " + minutes + " minutes");
			item.addActionListener(e -> export(minutes));
			menu.add(item);
		}
		JMenuItem custom = new JMenuItem("The last… (choose the minutes)");
		custom.addActionListener(e ->
		{
			String answer = JOptionPane.showInputDialog(this, "Export the last how many minutes?", "Export", JOptionPane.PLAIN_MESSAGE);
			if (answer == null)
			{
				return;
			}
			try
			{
				int minutes = Integer.parseInt(answer.trim());
				if (minutes > 0 && minutes <= 24 * 60)
				{
					export(minutes);
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
		menu.addSeparator();
		JMenuItem today = new JMenuItem("All of today");
		today.addActionListener(e ->
		{
			say("Writing today's export…");
			desk.exportToday(this::say);
		});
		menu.add(today);
		menu.show(at, 0, at.getHeight());
	}

	private void export(int minutes)
	{
		say("Writing the export…");
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
