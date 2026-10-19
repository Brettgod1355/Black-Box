/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
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
 * The sidebar: the current frames per second, the last minute's low, mean and high, and a small chart of
 * the live series. A click on the chart opens {@link FpsWindow}. Refreshes only while the panel is showing.
 */
class FpsPanel extends PluginPanel
{
	private final FpsSeries live;
	private final FpsChart chart;
	private final JLabel now = new JLabel("-", SwingConstants.CENTER);
	private final JLabel minute = new JLabel(" ", SwingConstants.CENTER);
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
		minute.setFont(FontManager.getRunescapeSmallFont());
		minute.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		minute.setAlignmentX(CENTER_ALIGNMENT);
		top.add(title);
		top.add(now);
		top.add(minute);
		add(top, BorderLayout.NORTH);

		chart = new FpsChart(live);
		chart.setWindow(60_000);
		chart.setPreferredSize(new Dimension(PANEL_WIDTH - 16, 150));
		chart.setShowLabels(false);
		chart.setOnClick(openWindow);
		chart.setToolTipText("Click to open the chart in its own window");
		add(chart, BorderLayout.CENTER);

		JLabel hint = new JLabel("Last minute. Click the chart to open it in its own window, with past recordings.");
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

	private void refresh()
	{
		int latest = live.latest();
		now.setText(latest < 0 ? "-" : Integer.toString(latest));
		long last = live.last();
		if (last >= 0)
		{
			FpsSeries.Stats stats = live.stats(last - 60_000, last);
			minute.setText(stats.count == 0 ? " " : "low " + stats.min + "  mean " + Math.round(stats.mean) + "  high " + stats.max);
		}
		chart.repaint();
	}
}
