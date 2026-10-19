/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.function.LongConsumer;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Draws an {@link FpsSeries}: frames per second against time, with each note as a vertical line and a
 * label, the hovered moment's reading, and an optional cursor (a recording being scrubbed). Pure Swing;
 * repaint it when the series changes.
 */
class FpsChart extends JComponent
{
	private static final Color LINE = new Color(120, 200, 255);
	private static final Color FILL = new Color(120, 200, 255, 40);
	private static final Color GRID = new Color(255, 255, 255, 28);
	private static final Color CURSOR = new Color(255, 255, 255, 170);
	private static final BasicStroke DASH = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{3f, 3f}, 0f);
	private static final int PAD_LEFT = 34;
	private static final int PAD_RIGHT = 8;
	private static final int PAD_TOP = 14;
	private static final int PAD_BOTTOM = 16;

	private FpsSeries series;
	/** Milliseconds shown; 0 shows the whole series. */
	private long window = 60_000;
	/** The right edge: the series' newest sample when following, else {@link #end}. */
	private boolean follow = true;
	private long end;
	private long cursor = -1;
	private int hoverX = -1;
	private boolean showLabels = true;
	private Runnable onClick;
	private LongConsumer onCursor;

	FpsChart(FpsSeries series)
	{
		this.series = series;
		setOpaque(true);
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setPreferredSize(new Dimension(200, 150));
		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				hoverX = e.getX();
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hoverX = -1;
				repaint();
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (onCursor != null)
				{
					long at = timeAt(e.getX());
					if (at >= 0)
					{
						onCursor.accept(at);
					}
				}
				if (onClick != null)
				{
					onClick.run();
				}
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	void setSeries(FpsSeries series)
	{
		this.series = series;
		repaint();
	}

	FpsSeries getSeries()
	{
		return series;
	}

	void setWindow(long millis)
	{
		window = millis;
		repaint();
	}

	long getWindow()
	{
		return window;
	}

	/** Follow the newest sample (live), or keep the right edge where {@link #setEnd} put it. */
	void setFollow(boolean follow)
	{
		this.follow = follow;
		repaint();
	}

	void setEnd(long end)
	{
		this.end = end;
		repaint();
	}

	void setCursor(long at)
	{
		cursor = at;
		repaint();
	}

	/** The cursor's moment, or -1. */
	long timeAtCursor()
	{
		return cursor;
	}

	void setShowLabels(boolean show)
	{
		showLabels = show;
	}

	void setOnClick(Runnable onClick)
	{
		this.onClick = onClick;
	}

	/** Called with the moment under a click, for scrubbing. */
	void setOnCursor(LongConsumer onCursor)
	{
		this.onCursor = onCursor;
	}

	/** The span shown right now: [from, to], or null when the series is empty. */
	long[] span()
	{
		long last = series.last();
		if (last < 0)
		{
			return null;
		}
		long to = follow ? last : end;
		long from = window <= 0 ? series.first() : to - window;
		return new long[]{Math.min(from, to), to};
	}

	/** The moment under an x pixel, or -1. */
	long timeAt(int x)
	{
		long[] span = span();
		int width = plotWidth();
		if (span == null || width <= 0)
		{
			return -1;
		}
		double k = (x - PAD_LEFT) / (double) width;
		return span[0] + Math.round(k * (span[1] - span[0]));
	}

	private int plotWidth()
	{
		return getWidth() - PAD_LEFT - PAD_RIGHT;
	}

	private int plotHeight()
	{
		return getHeight() - PAD_TOP - PAD_BOTTOM;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setColor(getBackground());
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setFont(FontManager.getRunescapeSmallFont());
			long[] span = span();
			int width = plotWidth(), height = plotHeight();
			if (span == null || width <= 10 || height <= 10)
			{
				g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
				g.drawString("No readings yet", PAD_LEFT, PAD_TOP + 14);
				return;
			}
			long from = span[0], to = Math.max(span[1], span[0] + 1);
			List<FpsSeries.Sample> samples = series.samples(from, to);
			FpsSeries.Stats stats = series.stats(from, to);
			int top = Math.max(60, roundUp(stats.max));
			// Grid: a line every 10, 20 or 50 frames depending on the ceiling.
			int step = top <= 100 ? 20 : top <= 200 ? 50 : 100;
			FontMetrics fm = g.getFontMetrics();
			for (int v = 0; v <= top; v += step)
			{
				int y = yOf(v, top, height);
				g.setColor(GRID);
				g.drawLine(PAD_LEFT, y, PAD_LEFT + width, y);
				g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
				String label = Integer.toString(v);
				g.drawString(label, PAD_LEFT - 4 - fm.stringWidth(label), y + 4);
			}
			// Time labels: left and right edge.
			g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			String left = clock(from, series), right = clock(to, series);
			g.drawString(left, PAD_LEFT, getHeight() - 4);
			g.drawString(right, PAD_LEFT + width - fm.stringWidth(right), getHeight() - 4);
			// The reading.
			if (!samples.isEmpty())
			{
				Path2D.Double line = new Path2D.Double();
				Path2D.Double fill = new Path2D.Double();
				int firstX = -1, lastX = -1;
				for (int i = 0; i < samples.size(); i++)
				{
					FpsSeries.Sample s = samples.get(i);
					int x = xOf(s.at, from, to, width), y = yOf(s.fps, top, height);
					if (i == 0)
					{
						line.moveTo(x, y);
						fill.moveTo(x, PAD_TOP + height);
						fill.lineTo(x, y);
						firstX = x;
					}
					else
					{
						line.lineTo(x, y);
						fill.lineTo(x, y);
					}
					lastX = x;
				}
				fill.lineTo(lastX, PAD_TOP + height);
				fill.closePath();
				g.setColor(FILL);
				g.fill(fill);
				g.setColor(LINE);
				g.setStroke(new BasicStroke(1.5f));
				g.draw(line);
				g.setStroke(new BasicStroke(1f));
				if (firstX == lastX)
				{
					g.fillOval(firstX - 2, yOf(samples.get(0).fps, top, height) - 2, 4, 4);
				}
			}
			// Notes.
			int row = 0;
			for (FpsSeries.Note note : series.notes(from, to))
			{
				int x = xOf(note.at, from, to, width);
				g.setColor(noteColor(note.kind));
				g.setStroke(DASH);
				g.drawLine(x, PAD_TOP, x, PAD_TOP + height);
				g.setStroke(new BasicStroke(1f));
				if (showLabels)
				{
					String label = shorten(note.text, 22);
					int y = PAD_TOP + 10 + (row % 3) * 11;
					g.drawString(label, Math.min(x + 3, PAD_LEFT + width - fm.stringWidth(label)), y);
					row++;
				}
			}
			// Cursor and hover.
			if (cursor >= from && cursor <= to)
			{
				int x = xOf(cursor, from, to, width);
				g.setColor(CURSOR);
				g.drawLine(x, PAD_TOP, x, PAD_TOP + height);
			}
			if (hoverX >= PAD_LEFT && hoverX <= PAD_LEFT + width)
			{
				long at = timeAt(hoverX);
				int fps = series.at(at);
				g.setColor(CURSOR);
				g.setStroke(DASH);
				g.drawLine(hoverX, PAD_TOP, hoverX, PAD_TOP + height);
				g.setStroke(new BasicStroke(1f));
				String text = (fps < 0 ? "-" : fps + " fps") + "  " + clock(at, series);
				List<FpsSeries.Note> near = series.notes(at - 1500, at + 1500);
				if (!near.isEmpty())
				{
					text += "  " + shorten(near.get(0).text, 40);
				}
				int tw = fm.stringWidth(text) + 8;
				int bx = Math.min(hoverX + 6, PAD_LEFT + width - tw);
				g.setColor(new Color(0, 0, 0, 190));
				g.fillRect(bx, PAD_TOP + 2, tw, 16);
				g.setColor(Color.WHITE);
				g.drawString(text, bx + 4, PAD_TOP + 14);
			}
		}
		finally
		{
			g.dispose();
		}
	}

	private static int roundUp(int max)
	{
		int step = max <= 100 ? 20 : max <= 200 ? 50 : 100;
		return ((max + step - 1) / step) * step;
	}

	private int xOf(long at, long from, long to, int width)
	{
		return PAD_LEFT + (int) Math.round((at - from) / (double) (to - from) * width);
	}

	private int yOf(int fps, int top, int height)
	{
		return PAD_TOP + height - (int) Math.round(Math.min(fps, top) / (double) top * height);
	}

	/** mm:ss into a recording, or the wall clock for the live series. */
	static String clock(long at, FpsSeries series)
	{
		if (series.isRelative())
		{
			long s = at / 1000;
			return String.format("%d:%02d", s / 60, s % 60);
		}
		return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(at));
	}

	static Color noteColor(String kind)
	{
		switch (kind)
		{
			case "plugin":
				return new Color(255, 160, 60);
			case "setting":
				return new Color(255, 220, 80);
			case "mark":
				return new Color(80, 230, 230);
			case "notify":
				return new Color(120, 230, 120);
			default:
				return new Color(170, 170, 170);
		}
	}

	private static String shorten(String text, int max)
	{
		return text.length() <= max ? text : text.substring(0, max - 1) + "…";
	}

}
