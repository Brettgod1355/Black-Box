/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Screenshots are shrunk and the chat box blacked out wherever the frame was stretched. */
public class ShrinkTest
{
	private static BufferedImage white(int w, int h)
	{
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0, 0, w, h);
		g.dispose();
		return image;
	}

	@Test
	public void aWideFrameIsShrunkToTheWidthAndKeepsItsShape()
	{
		BufferedImage out = BlackBoxPlugin.shrink(white(1920, 1080), 960, Collections.emptyList(), 1920);
		assertEquals(960, out.getWidth());
		assertEquals(540, out.getHeight());
		assertEquals(Color.WHITE.getRGB(), out.getRGB(10, 10));
	}

	@Test
	public void aSmallFrameIsNeverEnlarged()
	{
		BufferedImage out = BlackBoxPlugin.shrink(white(800, 600), 960, Collections.emptyList(), 800);
		assertEquals(800, out.getWidth());
	}

	@Test
	public void theChatBoxIsBlackEvenInAStretchedFrame()
	{
		// Game pixels 765 wide, drawn stretched to 1530, then shrunk to 765 again: the box lands where it was.
		Rectangle chat = new Rectangle(0, 338, 519, 165);
		BufferedImage out = BlackBoxPlugin.shrink(white(1530, 1006), 765, Collections.singletonList(chat), 765);
		assertEquals(Color.BLACK.getRGB(), out.getRGB(5, 345));
		assertEquals(Color.BLACK.getRGB(), out.getRGB(515, 500));
		assertEquals(Color.WHITE.getRGB(), out.getRGB(600, 345));
		assertEquals(Color.WHITE.getRGB(), out.getRGB(5, 300));
	}

	@Test
	public void splitPrivateChatLinesAreBlackToo()
	{
		// The chat box and one split-chat line above it: both black, the game view between them untouched.
		Rectangle chat = new Rectangle(0, 338, 519, 165);
		Rectangle line = new Rectangle(8, 300, 200, 14);
		BufferedImage out = BlackBoxPlugin.shrink(white(765, 503), 765, Arrays.asList(chat, line), 765);
		assertEquals(Color.BLACK.getRGB(), out.getRGB(5, 345));
		assertEquals(Color.BLACK.getRGB(), out.getRGB(100, 306));
		assertEquals(Color.WHITE.getRGB(), out.getRGB(100, 325));
		assertEquals(Color.WHITE.getRGB(), out.getRGB(300, 306));
	}

	@Test
	public void anEmptyFrameGivesNothing()
	{
		assertNull(BlackBoxPlugin.shrink(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
		{
			@Override
			public int getWidth(java.awt.image.ImageObserver observer)
			{
				return 0;
			}
		}, 960, Collections.emptyList(), 765));
	}
}
