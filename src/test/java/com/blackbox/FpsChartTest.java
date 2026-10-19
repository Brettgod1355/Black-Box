/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

public class FpsChartTest
{
	@Test
	public void paintsEmptyAndFullSeriesHeadless()
	{
		FpsSeries s = new FpsSeries("test", 1000, true);
		FpsChart chart = new FpsChart(s);
		chart.setSize(400, 200);
		assertNull(chart.span());
		paint(chart);
		for (int i = 0; i < 300; i++)
		{
			s.add(600L * i, 30 + (i % 40));
		}
		s.note(60_000, "plugin", "Safe Sailing on");
		s.note(90_000, "setting", "safesailing.showMapLines");
		s.note(120_000, "mark", "Mark");
		chart.setWindow(60_000);
		chart.setCursor(100_000);
		long[] span = chart.span();
		assertEquals(s.last(), span[1]);
		assertEquals(s.last() - 60_000, span[0]);
		paint(chart);
		chart.setWindow(0);
		assertEquals(0, chart.span()[0]);
		paint(chart);
		assertTrue(chart.timeAt(34) >= 0);
		assertEquals("1:40", FpsChart.clock(100_000, s));
	}

	private static void paint(FpsChart chart)
	{
		BufferedImage image = new BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try
		{
			chart.paint(g);
		}
		finally
		{
			g.dispose();
		}
	}
}
