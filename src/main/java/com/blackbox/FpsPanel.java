/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The sidebar: the current frames per second, the low, mean and high over the last 1, 5, 10 and 15 minutes,
 * and a small chart of the live series. A click on the chart opens {@link FpsWindow}. Refreshes only while
 * the panel is showing.
 */
class FpsPanel extends PluginPanel
{
	private final FpsSeries live;
	private final FpsChart chart;
	/** The spans of the history rows, in minutes. */
	private static final int[] SPANS = {1, 5, 10, 15};

	private final JLabel now = new JLabel("-", SwingConstants.CENTER);
	/** One row per span: low, mean, high. */
	private final JLabel[][] history = new JLabel[SPANS.length][3];
	private final Timer timer;

	FpsPanel(FpsSeries live, Runnable openWindow)
	{
		super(false);
		this.live = live;
		setLayout(new BorderLayout(0, 6));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JLabel title = new JLabel("Frames per second", SwingConstants.CENTER);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		title.setAlignmentX(CENTER_ALIGNMENT);
		now.setFont(now.getFont().deriveFont(Font.BOLD, 34f));
		now.setForeground(ColorScheme.BRAND_ORANGE);
		now.setAlignmentX(CENTER_ALIGNMENT);
		top.add(title);
		top.add(now);
		top.add(historyTable());
		add(top, BorderLayout.NORTH);

		chart = new FpsChart(live);
		chart.setWindow(60_000);
		chart.setPreferredSize(new Dimension(PANEL_WIDTH - 16, 150));
		chart.setShowLabels(false);
		chart.setOnClick(openWindow);
		chart.setToolTipText("Click to open the chart in its own window");
		add(chart, BorderLayout.CENTER);

		JLabel hint = new JLabel("Last minute. Click the chart for its own window and past recordings.");
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		add(hint, BorderLayout.SOUTH);

		timer = new Timer(600, e -> refresh());
		timer.setRepeats(true);
	}

	@Override
	public void onActivate()
	{
		refresh();
		timer.start();
	}

	@Override
	public void onDeactivate()
	{
		timer.stop();
	}

	void stop()
	{
		timer.stop();
	}

	/** A small table: a header row, then low, mean and high for each span. */
	private JPanel historyTable()
	{
		JPanel table = new JPanel(new GridLayout(SPANS.length + 1, 4, 4, 1));
		table.setBackground(ColorScheme.DARK_GRAY_COLOR);
		table.setAlignmentX(CENTER_ALIGNMENT);
		table.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
		table.add(cell("", SwingConstants.LEFT, ColorScheme.LIGHT_GRAY_COLOR));
		table.add(cell("low", SwingConstants.RIGHT, ColorScheme.LIGHT_GRAY_COLOR));
		table.add(cell("mean", SwingConstants.RIGHT, ColorScheme.LIGHT_GRAY_COLOR));
		table.add(cell("high", SwingConstants.RIGHT, ColorScheme.LIGHT_GRAY_COLOR));
		for (int i = 0; i < SPANS.length; i++)
		{
			table.add(cell(SPANS[i] + " min", SwingConstants.LEFT, ColorScheme.LIGHT_GRAY_COLOR));
			for (int j = 0; j < 3; j++)
			{
				history[i][j] = cell("-", SwingConstants.RIGHT, Color.WHITE);
				table.add(history[i][j]);
			}
		}
		return table;
	}

	private static JLabel cell(String text, int align, Color color)
	{
		JLabel label = new JLabel(text, align);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		return label;
	}

	private void refresh()
	{
		int latest = live.latest();
		now.setText(latest < 0 ? "-" : Integer.toString(latest));
		long last = live.last();
		for (int i = 0; i < SPANS.length; i++)
		{
			FpsSeries.Stats stats = last < 0 ? null : live.stats(last - SPANS[i] * 60_000L, last);
			boolean any = stats != null && stats.count > 0;
			history[i][0].setText(any ? Integer.toString(stats.min) : "-");
			history[i][1].setText(any ? Long.toString(Math.round(stats.mean)) : "-");
			history[i][2].setText(any ? Integer.toString(stats.max) : "-");
			// A span the series does not reach yet is shown dimmer, so a "15 min" row two minutes after login reads as partial.
			Color color = any && last - live.first() >= SPANS[i] * 60_000L - 1000 ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR;
			for (JLabel cellLabel : history[i])
			{
				cellLabel.setForeground(color);
			}
		}
		chart.repaint();
	}
}
