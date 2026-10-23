/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The chart in its own window: the live series or any past recording, a time range, every note listed on
 * the right (click one to jump to it), and a slider to scrub a recording. Only what the sidebar shows,
 * larger, plus the recordings.
 */
class FpsWindow extends JFrame
{
	private static final Logger log = LoggerFactory.getLogger(FpsWindow.class);
	private static final String LIVE = "Live";

	/** Where past recordings come from: the plugin's own folder. */
	interface Source
	{
		List<String> recordings() throws IOException;

		FpsSeries load(String name) throws IOException;

		/** Asks for a clip a player sent (its zip, or an events file); null when cancelled. Swing thread. */
		Filepath chooseClip(Component parent);

		/** A clip's series, read from the file without changing or copying it. Executor thread. */
		FpsSeries loadClip(Filepath file) throws IOException;
	}

	/** What a clip is called in the Show list. */
	private static final String CLIP = "Clip: ";

	private final FpsSeries live;
	private final Source source;
	private final ScheduledExecutorService executor;
	private final FpsChart chart;
	private final JComboBox<String> sources = new JComboBox<>();
	private final DefaultListModel<FpsSeries.Note> noteModel = new DefaultListModel<>();
	private final JList<FpsSeries.Note> notes = new JList<>(noteModel);
	private final JSlider scrub = new JSlider(0, 1000, 1000);
	private final ButtonGroup ranges = new ButtonGroup();
	private final JButton resetZoom = new JButton("Reset zoom");
	/** The range picked before a zoom, to go back to. */
	private JToggleButton rangeButton;
	private long rangeMillis = 5 * 60_000L;
	private final JLabel status = new JLabel(" ");
	private final Timer timer;
	private FpsSeries shown;
	private int notesShown = -1;
	private boolean adjusting;
	/** Clips opened in this window, by their name in the Show list; Swing thread. */
	private final Map<String, FpsSeries> clips = new LinkedHashMap<>();

	FpsWindow(FpsSeries live, Source source, ScheduledExecutorService executor)
	{
		super("Black Box: frames per second");
		this.live = live;
		this.source = source;
		this.executor = executor;
		this.shown = live;
		setDefaultCloseOperation(HIDE_ON_CLOSE);
		setSize(960, 480);
		setLocationByPlatform(true);
		getContentPane().setBackground(ColorScheme.DARK_GRAY_COLOR);

		chart = new FpsChart(live);
		chart.setWindow(5 * 60_000L);
		chart.setOnCursor(this::cursorTo);
		chart.setOnZoom(this::zoom);
		resetZoom.setToolTipText("Back to the range picked before zooming in");
		resetZoom.addActionListener(e -> resetZoom());
		resetZoom.setVisible(false);

		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		top.add(new JLabel("Show"));
		sources.setPrototypeDisplayValue("2026-10-19_12-34-56");
		sources.addActionListener(e -> select((String) sources.getSelectedItem()));
		top.add(sources);
		JButton reload = new JButton("Reload list");
		reload.addActionListener(e -> reloadList());
		top.add(reload);
		JButton openClip = new JButton("Open a clip…");
		openClip.setToolTipText("Show a clip a player sent you: its frames per second, plugin and setting changes, marks");
		openClip.addActionListener(e -> openClip());
		top.add(openClip);
		top.add(new JLabel("   Range"));
		addRange(top, ranges, "1 min", 60_000L, false);
		addRange(top, ranges, "5 min", 5 * 60_000L, true);
		addRange(top, ranges, "15 min", 15 * 60_000L, false);
		addRange(top, ranges, "1 h", 60 * 60_000L, false);
		addRange(top, ranges, "All", 0, false);
		top.add(resetZoom);
		JCheckBox labels = new JCheckBox("Labels", true);
		labels.addActionListener(e ->
		{
			chart.setShowLabels(labels.isSelected());
			chart.repaint();
		});
		top.add(labels);
		add(top, BorderLayout.NORTH);

		add(chart, BorderLayout.CENTER);

		notes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		notes.setCellRenderer(new NoteRenderer());
		notes.addListSelectionListener(e ->
		{
			FpsSeries.Note note = notes.getSelectedValue();
			if (!e.getValueIsAdjusting() && note != null)
			{
				jumpTo(note.at);
			}
		});
		JScrollPane scroll = new JScrollPane(notes);
		scroll.setPreferredSize(new Dimension(300, 100));
		add(scroll, BorderLayout.EAST);

		JPanel bottom = new JPanel(new BorderLayout(6, 0));
		bottom.setBackground(ColorScheme.DARK_GRAY_COLOR);
		scrub.addChangeListener(e ->
		{
			if (!adjusting && shown.isRelative())
			{
				long first = Math.max(0, shown.first());
				long last = shown.last();
				cursorTo(first + (last - first) * scrub.getValue() / 1000);
			}
		});
		scrub.setVisible(false);
		bottom.add(scrub, BorderLayout.NORTH);
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		bottom.add(status, BorderLayout.SOUTH);
		add(bottom, BorderLayout.SOUTH);

		timer = new Timer(600, e -> tick());
		timer.setRepeats(true);
		reloadList();
	}

	@Override
	public void setVisible(boolean visible)
	{
		super.setVisible(visible);
		if (visible)
		{
			timer.start();
		}
		else
		{
			timer.stop();
		}
	}

	@Override
	public void dispose()
	{
		timer.stop();
		super.dispose();
	}

	private void addRange(JPanel to, ButtonGroup group, String text, long millis, boolean selected)
	{
		JToggleButton button = new JToggleButton(text, selected);
		button.setToolTipText("Show " + (millis <= 0 ? "the whole recording" : "this much") + "; or drag across the chart to zoom in");
		button.addActionListener(e ->
		{
			rangeButton = button;
			rangeMillis = millis;
			resetZoom.setVisible(false);
			if (!shown.isRelative())
			{
				chart.setFollow(true);
			}
			chart.setWindow(millis);
			refreshStatus();
		});
		if (selected)
		{
			rangeButton = button;
			rangeMillis = millis;
		}
		group.add(button);
		to.add(button);
	}

	/** Lists the recordings on the executor; the list arrives on the Swing thread. */
	private void reloadList()
	{
		executor.execute(() ->
		{
			List<String> names;
			try
			{
				names = source.recordings();
			}
			catch (IOException e)
			{
				log.warn("Black Box could not list recordings", e);
				names = new ArrayList<>();
			}
			List<String> items = new ArrayList<>();
			items.add(LIVE);
			items.addAll(names);
			SwingUtilities.invokeLater(() ->
			{
				items.addAll(1, clips.keySet());
				String current = (String) sources.getSelectedItem();
				adjusting = true;
				sources.setModel(new DefaultComboBoxModel<>(items.toArray(new String[0])));
				sources.setSelectedItem(current != null && items.contains(current) ? current : LIVE);
				adjusting = false;
			});
		});
	}

	private void select(String name)
	{
		if (adjusting || name == null)
		{
			return;
		}
		if (LIVE.equals(name))
		{
			show(live);
			return;
		}
		if (clips.containsKey(name))
		{
			show(clips.get(name));
			return;
		}
		status.setText("Reading " + name + "…");
		executor.execute(() ->
		{
			try
			{
				FpsSeries series = source.load(name);
				SwingUtilities.invokeLater(() -> show(series));
			}
			catch (IOException e)
			{
				log.warn("Black Box could not read recording {}", name, e);
				SwingUtilities.invokeLater(() -> status.setText("Could not read " + name + ": " + e.getMessage()));
			}
		});
	}

	/** Picks a clip, reads it on the executor, and shows it like a recording; it stays in the Show list. */
	private void openClip()
	{
		Filepath file = source.chooseClip(this);
		if (file == null)
		{
			return;
		}
		String name = CLIP + file.getFileName();
		status.setText("Reading " + file.getFileName() + "…");
		executor.execute(() ->
		{
			try
			{
				FpsSeries series = source.loadClip(file);
				SwingUtilities.invokeLater(() ->
				{
					clips.put(name, series);
					adjusting = true;
					DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) sources.getModel();
					if (model.getIndexOf(name) < 0)
					{
						model.insertElementAt(name, 1);
					}
					sources.setSelectedItem(name);
					adjusting = false;
					show(series);
				});
			}
			catch (IOException e)
			{
				log.warn("Black Box could not read clip {}", file.getFileName(), e);
				SwingUtilities.invokeLater(() -> status.setText("Could not read " + file.getFileName() + ": " + e.getMessage()));
			}
		});
	}

	private void show(FpsSeries series)
	{
		shown = series;
		chart.setSeries(series);
		chart.setCursor(-1);
		if (series.isRelative())
		{
			chart.setFollow(false);
			chart.setEnd(Math.max(0, series.last()));
			chart.setWindow(0);
			scrub.setVisible(true);
			adjusting = true;
			scrub.setValue(1000);
			adjusting = false;
		}
		else
		{
			chart.setFollow(true);
			scrub.setVisible(false);
		}
		notesShown = -1;
		tick();
		revalidate();
	}

	private void tick()
	{
		List<FpsSeries.Note> all = shown.allNotes();
		if (all.size() != notesShown)
		{
			FpsSeries.Note selected = notes.getSelectedValue();
			noteModel.clear();
			for (FpsSeries.Note note : all)
			{
				noteModel.addElement(note);
			}
			if (selected != null)
			{
				notes.setSelectedValue(selected, false);
			}
			notesShown = all.size();
		}
		chart.repaint();
		refreshStatus();
	}

	private void refreshStatus()
	{
		long[] span = chart.span();
		if (span == null)
		{
			status.setText(shown.getName() + ": no readings");
			return;
		}
		FpsSeries.Stats stats = shown.stats(span[0], span[1]);
		status.setText(shown.getName() + ": " + shown.size() + " readings, showing " + FpsChart.clock(span[0], shown) + " to "
			+ FpsChart.clock(span[1], shown) + ": low " + stats.min + ", mean " + Math.round(stats.mean) + ", high " + stats.max
			+ (chart.timeAtCursor() < 0 ? "" : "; at cursor " + Math.max(0, shown.at(chart.timeAtCursor())) + " fps"));
	}

	/** Shows just the span dragged over, filling the chart; Reset zoom goes back. */
	private void zoom(long from, long to)
	{
		chart.setFollow(false);
		chart.setEnd(to);
		chart.setWindow(Math.max(1, to - from));
		ranges.clearSelection();
		resetZoom.setVisible(true);
		refreshStatus();
	}

	/** Back to the range button picked before zooming: following the newest reading when live, else the whole of it. */
	private void resetZoom()
	{
		resetZoom.setVisible(false);
		if (rangeButton != null)
		{
			rangeButton.setSelected(true);
		}
		if (shown.isRelative())
		{
			chart.setEnd(Math.max(0, shown.last()));
		}
		else
		{
			chart.setFollow(true);
		}
		chart.setWindow(rangeMillis);
		refreshStatus();
	}

	/** Puts the cursor on a moment, moving the view when it is off screen. */
	private void jumpTo(long at)
	{
		long[] span = chart.span();
		if (span != null && (at < span[0] || at > span[1]) && chart.getWindow() > 0)
		{
			chart.setFollow(false);
			chart.setEnd(Math.min(shown.last(), at + chart.getWindow() / 2));
		}
		cursorTo(at);
	}

	private void cursorTo(long at)
	{
		chart.setCursor(at);
		if (shown.isRelative())
		{
			long first = Math.max(0, shown.first());
			long last = shown.last();
			if (last > first)
			{
				adjusting = true;
				scrub.setValue((int) ((at - first) * 1000 / (last - first)));
				adjusting = false;
			}
		}
		refreshStatus();
	}

	private static final class NoteRenderer extends DefaultListCellRenderer
	{
		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
		{
			super.getListCellRendererComponent(list, value, index, selected, focus);
			if (value instanceof FpsSeries.Note)
			{
				FpsSeries.Note note = (FpsSeries.Note) value;
				setText(FpsChart.clock(note.at, note.at < 1_000_000_000_000L ? RELATIVE : WALL) + "  " + note.kind + ": " + note.text);
				if (!selected)
				{
					setForeground(FpsChart.noteColor(note.kind));
				}
			}
			return this;
		}
	}

	/** Dummies for the renderer's clock formatting: a note from a recording has a small time, a live one a wall-clock time. */
	private static final FpsSeries RELATIVE = new FpsSeries("", 1, true);
	private static final FpsSeries WALL = new FpsSeries("", 1, false);
}
