/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * The sidebar, two pages. Recorder, top to bottom: the recorder (record, mark, share, note), frames per second and
 * the recordings. Debug: the Debug switch and its tests. Timers run only while the panel is showing.
 */
class BlackBoxPanel extends PluginPanel
{
	private final RecorderSection recorder;
	private final FpsSection fps;
	private final RecordingsSection recordings;
	private final DebugSection debug;
	private final MaterialTab debugTab;

	BlackBoxPanel(RecorderSection recorder, FpsSection fps, RecordingsSection recordings, DebugSection debug)
	{
		super();
		this.recorder = recorder;
		this.fps = fps;
		this.recordings = recordings;
		this.debug = debug;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (JPanel section : new JPanel[]{recorder, fps, recordings})
		{
			section.setAlignmentX(LEFT_ALIGNMENT);
			column.add(section);
		}
		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);
		MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
		MaterialTab recorderTab = new MaterialTab("Recorder", tabs, top(column));
		debugTab = new MaterialTab(debugTabText(debug.isOn()), tabs, top(debug));
		tabs.addTab(recorderTab);
		tabs.addTab(debugTab);
		tabs.select(recorderTab);
		add(tabs, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
		debugChanged();
	}

	/** A page's content held at the top, so a short page does not stretch. */
	private static JPanel top(JPanel content)
	{
		JPanel holder = new JPanel(new BorderLayout());
		holder.setBackground(ColorScheme.DARK_GRAY_COLOR);
		holder.add(content, BorderLayout.NORTH);
		return holder;
	}

	/** After Debug was switched, here or at logout: the page and the tab's on/off mark. Swing thread. */
	void debugChanged()
	{
		debug.refresh();
		debugTab.setText(debugTabText(debug.isOn()));
	}

	/** The Debug tab says whether Debug is on or off, both ways. */
	static String debugTabText(boolean on)
	{
		return on ? "Debug (on)" : "Debug (off)";
	}

	@Override
	public void onActivate()
	{
		recorder.start();
		fps.start();
		recordings.refresh();
	}

	@Override
	public void onDeactivate()
	{
		recorder.stop();
		fps.stop();
	}

	/** After a recording starts or ends: the recorder card and the list change. Swing thread. */
	void recordingChanged()
	{
		recorder.refresh();
		recordings.refresh();
	}
}
