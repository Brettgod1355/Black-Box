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

/**
 * The sidebar, top to bottom: the recorder (record, mark, share, note), frames per second and the recordings.
 * Timers run only while the panel is showing.
 */
class BlackBoxPanel extends PluginPanel
{
	private final RecorderSection recorder;
	private final FpsSection fps;
	private final RecordingsSection recordings;

	BlackBoxPanel(RecorderSection recorder, FpsSection fps, RecordingsSection recordings)
	{
		super();
		this.recorder = recorder;
		this.fps = fps;
		this.recordings = recordings;
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
		add(column, BorderLayout.NORTH);
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
