/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.WorldEntityDespawned;
import net.runelite.api.events.WorldEntitySpawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Black Box: records what the game shows a plugin while you play, so a plugin's logic can be tested against
 * a real session later without playing again. A developer tool: it only loads in RuneLite's developer mode.
 * <p>
 * It only reads what RuneLite already exposes. It sends nothing to the game and nothing over the network,
 * and writes only to its own folder. Other players' names and chat are left out, and the player's own name
 * is replaced (see {@link Privacy}).
 */
@PluginDescriptor(
	name = "Black Box",
	description = "Developer tool: records game events and screenshots while you play, for testing plugins against later",
	tags = {"developer", "testing", "recorder", "replay", "screenshot"},
	internalName = "black-box",
	developerPlugin = true
)
public class BlackBoxPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(BlackBoxPlugin.class);

	/** The version written into every recording's first line. */
	static final String VERSION = "0.1";

	private static final DateTimeFormatter SESSION_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	private static final long BYTES_PER_MEGABYTE = 1024L * 1024;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private BlackBoxConfig config;

	@Inject
	private DrawManager drawManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ScheduledExecutorService executor;

	private SessionLog session;
	private ScheduledFuture<?> flushTask;
	private long lastShotAt;
	private int shotCount;
	private boolean shotPending;
	/**
	 * The bank PIN keypad is showing: no clicks, game values or screenshots are recorded until it closes.
	 * Read by the frame listener, which may not run on the client thread.
	 */
	private volatile boolean pinPause;
	private long shotRequestedAt;
	/** A frame that never arrives (a minimized window) must not block screenshots for good. */
	private static final long SHOT_GIVE_UP_MILLIS = 5_000;

	/**
	 * The only key Black Box listens for. It never records keys: a general key listener would capture a bank PIN
	 * typed on the number pad, and passwords.
	 */
	private final HotkeyListener markListener = new HotkeyListener(() -> config.markHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(BlackBoxPlugin.this::mark);
		}
	};

	@Provides
	BlackBoxConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BlackBoxConfig.class);
	}

	@Override
	protected void startUp()
	{
		keyManager.registerKeyListener(markListener);
		flushTask = executor.scheduleWithFixedDelay(this::flushQuietly, 1, 1, TimeUnit.SECONDS);
		clientThread.invoke(this::startIfWanted);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(markListener);
		stop("plugin stopped");
		if (flushTask != null)
		{
			flushTask.cancel(false);
			flushTask = null;
		}
	}

	// ---- Starting and stopping ----

	private void startIfWanted()
	{
		if (session != null || !config.record() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Filepath root;
		try
		{
			root = getPluginDirectory();
		}
		catch (IOException e)
		{
			log.warn("Black Box cannot find its folder; not recording", e);
			return;
		}
		long now = System.currentTimeMillis();
		String name = LocalDateTime.now().format(SESSION_NAME);
		SessionLog started = new SessionLog(name, root.join(name), now);
		session = started;
		shotCount = 0;
		lastShotAt = 0;
		pinPause = pinShowing();
		JsonObject header = started.line("session", client.getTickCount(), now);
		header.addProperty("format", SessionLog.FORMAT);
		header.addProperty("blackBox", VERSION);
		header.addProperty("runelite", RuneLiteProperties.getVersion());
		header.addProperty("world", client.getWorld());
		header.addProperty("started", name);
		started.add(header);
		if (pinPause)
		{
			pinLine(started, "pause", now);
		}
		executor.execute(() ->
		{
			try
			{
				started.open();
			}
			catch (IOException e)
			{
				log.warn("Black Box cannot create {}", name, e);
			}
			cleanUp(root, name);
		});
		log.debug("Black Box recording {}", name);
	}

	private void stop(String why)
	{
		SessionLog ending = session;
		if (ending == null)
		{
			return;
		}
		JsonObject end = ending.line("end", client.getTickCount(), System.currentTimeMillis());
		end.addProperty("why", why);
		ending.add(end);
		session = null;
		executor.execute(() ->
		{
			try
			{
				ending.close();
			}
			catch (IOException e)
			{
				log.warn("Black Box could not finish {}", ending.getName(), e);
			}
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BlackBoxConfig.GROUP.equals(event.getGroup()) || !"record".equals(event.getKey()))
		{
			return;
		}
		clientThread.invoke(() ->
		{
			if (config.record())
			{
				startIfWanted();
			}
			else
			{
				stop("recording switched off");
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (session != null)
		{
			JsonObject o = line("state");
			o.addProperty("state", state.name());
			session.add(o);
		}
		if (state == GameState.LOGGED_IN)
		{
			startIfWanted();
		}
		else if (state == GameState.LOGIN_SCREEN)
		{
			stop("logged out");
		}
	}

	// ---- What gets recorded ----

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (session == null)
		{
			return;
		}
		// The keypad's load and close events cover this; a tick re-check catches anything they miss.
		setPinPause(pinShowing());
		JsonObject o = line("tick");
		Player me = client.getLocalPlayer();
		if (me != null)
		{
			WorldView view = me.getWorldView();
			o.addProperty("wv", view == null ? -1 : view.getId());
			point(o, "", me.getWorldLocation());
			o.addProperty("anim", me.getAnimation());
			WorldEntity boat = view == null || view.isTopLevel() ? null : boatOf(view);
			if (boat != null)
			{
				// Aboard a boat: where the player and the boat are in the top-level world too.
				LocalPoint top = boat.transformToMainWorld(me.getLocalLocation());
				point(o, "top", top == null ? null : WorldPoint.fromLocal(client, top));
				LocalPoint hull = boat.getLocalLocation();
				point(o, "boat", hull == null ? null : WorldPoint.fromLocal(client, hull));
				o.addProperty("boatConfig", boat.getConfig() == null ? -1 : boat.getConfig().getId());
				o.addProperty("boatOwner", boat.getOwnerType());
			}
		}
		session.add(o);

		int every = config.shotEverySeconds();
		if (every > 0 && !pinPause && System.currentTimeMillis() - lastShotAt >= every * 1000L)
		{
			shoot("timer");
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (session == null || !Privacy.recordsChat(event.getType()))
		{
			return;
		}
		JsonObject o = line("chat");
		o.addProperty("type", event.getType().name());
		o.addProperty("text", scrub(event.getMessage()));
		session.add(o);
	}

	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged event)
	{
		Actor actor = event.getActor();
		if (session == null || !(actor instanceof NPC))
		{
			// Only NPCs: what other players say overhead is their chat, which is never recorded.
			return;
		}
		NPC npc = (NPC) actor;
		JsonObject o = line("npcSay");
		o.addProperty("npc", npc.getId());
		o.addProperty("index", npc.getIndex());
		o.addProperty("name", scrub(npc.getName()));
		o.addProperty("text", scrub(event.getOverheadText()));
		worldView(o, npc.getWorldView());
		session.add(o);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (session == null)
		{
			return;
		}
		JsonObject o = line("items");
		o.addProperty("container", event.getContainerId());
		JsonArray items = new JsonArray();
		Item[] all = event.getItemContainer() == null ? null : event.getItemContainer().getItems();
		if (all != null)
		{
			for (Item item : all)
			{
				JsonArray pair = new JsonArray();
				pair.add(item == null ? -1 : item.getId());
				pair.add(item == null ? 0 : item.getQuantity());
				items.add(pair);
			}
		}
		o.add("items", items);
		session.add(o);
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		object("objectAdd", event.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		object("objectGone", event.getGameObject());
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		npc("npcAdd", event.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		npc("npcGone", event.getNpc());
	}

	@Subscribe
	public void onWorldEntitySpawned(WorldEntitySpawned event)
	{
		worldEntity("boatAdd", event.getWorldEntity());
	}

	@Subscribe
	public void onWorldEntityDespawned(WorldEntityDespawned event)
	{
		worldEntity("boatGone", event.getWorldEntity());
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (session == null || pinPause)
		{
			return;
		}
		JsonObject o = line("var");
		o.addProperty("varbit", event.getVarbitId());
		o.addProperty("varp", event.getVarpId());
		o.addProperty("value", event.getValue());
		session.add(o);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (session == null)
		{
			return;
		}
		JsonObject o = line("stat");
		o.addProperty("skill", event.getSkill().name());
		o.addProperty("xp", event.getXp());
		o.addProperty("level", event.getLevel());
		o.addProperty("boosted", event.getBoostedLevel());
		session.add(o);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor actor = event.getActor();
		boolean me = actor == client.getLocalPlayer();
		if (session == null || (!me && !(actor instanceof NPC)))
		{
			// Never other players: who gets hit near you is their business.
			return;
		}
		JsonObject o = line("hit");
		if (me)
		{
			o.addProperty("on", Privacy.YOU);
		}
		else
		{
			o.addProperty("npc", ((NPC) actor).getId());
			o.addProperty("index", ((NPC) actor).getIndex());
		}
		o.addProperty("amount", event.getHitsplat().getAmount());
		o.addProperty("type", event.getHitsplat().getHitsplatType());
		session.add(o);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (session != null)
		{
			JsonObject o = line("open");
			o.addProperty("interface", event.getGroupId());
			session.add(o);
		}
		if (Privacy.isPinKeypad(event.getGroupId()))
		{
			setPinPause(true);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (Privacy.isPinKeypad(event.getGroupId()))
		{
			setPinPause(false);
		}
		if (session != null)
		{
			JsonObject o = line("close");
			o.addProperty("interface", event.getGroupId());
			session.add(o);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// While the bank PIN keypad shows, a click says which button was pressed, and a screenshot shows which
		// digit is on it: together they give the PIN away. Keypresses that enter a PIN can arrive as clicks too.
		if (session == null || pinPause)
		{
			return;
		}
		JsonObject o = line("click");
		o.addProperty("option", event.getMenuOption() == null ? "" : Text.removeTags(event.getMenuOption()));
		// A click on another player names them; leave the target out.
		String target = event.getMenuTarget() == null || Privacy.targetsPlayer(event.getMenuAction())
			? "" : scrub(Text.removeTags(event.getMenuTarget()));
		o.addProperty("target", target);
		o.addProperty("action", event.getMenuAction() == null ? "" : event.getMenuAction().name());
		o.addProperty("id", event.getId());
		o.addProperty("itemId", event.getItemId());
		o.addProperty("param0", event.getParam0());
		o.addProperty("param1", event.getParam1());
		session.add(o);
	}

	@Subscribe
	public void onNotificationFired(NotificationFired event)
	{
		if (session == null)
		{
			return;
		}
		JsonObject o = line("notify");
		o.addProperty("text", scrub(event.getMessage()));
		session.add(o);
		if (config.shotOnNotification())
		{
			shoot("notify");
		}
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		if (session == null)
		{
			return;
		}
		// Which plugins talked to each other, not what they said.
		JsonObject o = line("pluginMessage");
		o.addProperty("namespace", event.getNamespace());
		o.addProperty("name", event.getName());
		session.add(o);
	}

	/** The hotkey: a mark in the recording, and a screenshot when screenshots are on. */
	private void mark()
	{
		if (session == null)
		{
			return;
		}
		session.add(line("mark"));
		if (config.shotEverySeconds() > 0 || config.shotOnNotification())
		{
			shoot("mark");
		}
	}

	private void object(String kind, GameObject object)
	{
		if (session == null || object == null)
		{
			return;
		}
		JsonObject o = line(kind);
		o.addProperty("id", object.getId());
		o.addProperty("hash", object.getHash());
		worldView(o, object.getWorldView());
		point(o, "", object.getWorldLocation());
		session.add(o);
	}

	private void npc(String kind, NPC npc)
	{
		if (session == null || npc == null)
		{
			return;
		}
		JsonObject o = line(kind);
		o.addProperty("npc", npc.getId());
		o.addProperty("index", npc.getIndex());
		o.addProperty("name", scrub(npc.getName()));
		worldView(o, npc.getWorldView());
		session.add(o);
	}

	private void worldEntity(String kind, WorldEntity entity)
	{
		if (session == null || entity == null)
		{
			return;
		}
		JsonObject o = line(kind);
		worldView(o, entity.getWorldView());
		o.addProperty("config", entity.getConfig() == null ? -1 : entity.getConfig().getId());
		o.addProperty("owner", entity.getOwnerType());
		session.add(o);
	}

	// ---- The bank PIN ----

	/** Whether the bank PIN keypad is on screen. Client thread only. */
	private boolean pinShowing()
	{
		Widget keypad = client.getWidget(InterfaceID.BankpinKeypad.UNIVERSE);
		return keypad != null && !keypad.isHidden();
	}

	/** Starts or ends the pause, writing a line when it changes. Client thread only. */
	private void setPinPause(boolean paused)
	{
		if (paused == pinPause)
		{
			return;
		}
		pinPause = paused;
		if (session != null)
		{
			pinLine(session, paused ? "pause" : "resume", System.currentTimeMillis());
		}
	}

	private void pinLine(SessionLog target, String kind, long now)
	{
		JsonObject o = target.line(kind, client.getTickCount(), now);
		o.addProperty("why", "bank PIN");
		target.add(o);
	}

	// ---- Screenshots ----

	/**
	 * Saves the next frame of the game view, overlays included, shrunk and with the chat box blacked out when
	 * that is on. One at a time: a request while one is waiting is dropped, unless that one has waited too long.
	 */
	private void shoot(String reason)
	{
		SessionLog target = session;
		long now = System.currentTimeMillis();
		if (target == null || pinPause || (shotPending && now - shotRequestedAt < SHOT_GIVE_UP_MILLIS))
		{
			return;
		}
		shotPending = true;
		shotRequestedAt = now;
		lastShotAt = now;
		String file = String.format("%05d-tick%d-%s.jpg", ++shotCount, client.getTickCount(), reason);
		JsonObject o = line("shot");
		o.addProperty("file", file);
		o.addProperty("reason", reason);
		target.add(o);
		Rectangle chat = config.hideChat() ? chatBounds() : null;
		int canvasWidth = client.getCanvasWidth();
		int maxWidth = config.shotWidth();
		drawManager.requestNextFrameListener(image ->
		{
			try
			{
				// The keypad may have opened between the request and this frame, or the player logged out (the
				// recording then ended and the frame may show the login screen): such a frame is never saved.
				boolean keep = !pinPause && session == target && Privacy.mayKeepFrame(client.getGameState());
				BufferedImage small = keep ? shrink(image, maxWidth, chat, canvasWidth) : null;
				if (small != null)
				{
					executor.execute(() -> writeShot(target, file, small));
				}
			}
			finally
			{
				shotPending = false;
			}
		});
	}

	/** Where the chat box is on the canvas, or null when it is not showing. */
	private Rectangle chatBounds()
	{
		Widget chat = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
		return chat == null || chat.isHidden() ? null : chat.getBounds();
	}

	/** The frame at no more than {@code maxWidth} across, with the chat box painted black. */
	static BufferedImage shrink(Image image, int maxWidth, Rectangle chat, int canvasWidth)
	{
		int width = image.getWidth(null);
		int height = image.getHeight(null);
		if (width <= 0 || height <= 0)
		{
			return null;
		}
		double scale = Math.min(1.0, maxWidth / (double) width);
		int w = Math.max(1, (int) Math.round(width * scale));
		int h = Math.max(1, (int) Math.round(height * scale));
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(image, 0, 0, w, h, null);
			if (chat != null && canvasWidth > 0)
			{
				// Widget bounds are in game pixels; the frame may be stretched, then it is shrunk here.
				double k = (width / (double) canvasWidth) * scale;
				g.setColor(Color.BLACK);
				g.fillRect((int) Math.floor(chat.x * k), (int) Math.floor(chat.y * k),
					(int) Math.ceil(chat.width * k) + 1, (int) Math.ceil(chat.height * k) + 1);
			}
		}
		finally
		{
			g.dispose();
		}
		return out;
	}

	private static void writeShot(SessionLog target, String file, BufferedImage image)
	{
		try (OutputStream out = target.shot(file).openOutputStream())
		{
			ImageIO.write(image, "jpg", out);
		}
		catch (IOException e)
		{
			log.warn("Black Box could not save screenshot {}", file, e);
		}
	}

	// ---- Writing and cleaning up ----

	private void flushQuietly()
	{
		SessionLog current = session;
		if (current != null)
		{
			flush(current);
		}
	}

	private static void flush(SessionLog target)
	{
		try
		{
			target.flush();
		}
		catch (IOException e)
		{
			log.warn("Black Box could not write {}", target.getName(), e);
		}
	}

	/** Deletes old recordings by the Cleanup settings. Executor thread only. */
	private void cleanUp(Filepath root, String current)
	{
		List<Retention.Session> found = new ArrayList<>();
		try (Stream<Filepath> entries = root.walk(1))
		{
			for (Filepath dir : entries.filter(p -> !p.equals(root) && p.isDirectory()).collect(Collectors.toList()))
			{
				found.add(measure(dir));
			}
		}
		catch (IOException e)
		{
			log.warn("Black Box could not list old recordings", e);
			return;
		}
		List<String> doomed = Retention.toDelete(found, current, System.currentTimeMillis(), config.keepDays(),
			config.maxMegabytes() * BYTES_PER_MEGABYTE);
		for (String name : doomed)
		{
			try
			{
				root.join(name).deleteRecursively();
				log.debug("Black Box deleted old recording {}", name);
			}
			catch (IOException e)
			{
				log.warn("Black Box could not delete {}", name, e);
			}
		}
	}

	/** A recording folder's size and the time of its newest file. */
	private static Retention.Session measure(Filepath dir) throws IOException
	{
		long bytes = 0;
		long newest = dir.getLastModifiedTime().toMillis();
		try (Stream<Filepath> files = dir.walk())
		{
			for (Filepath file : files.filter(Filepath::isFile).collect(Collectors.toList()))
			{
				bytes += file.size();
				newest = Math.max(newest, file.getLastModifiedTime().toMillis());
			}
		}
		return new Retention.Session(dir.getFileName(), newest, bytes);
	}

	// ---- Helpers ----

	private JsonObject line(String kind)
	{
		return session.line(kind, client.getTickCount(), System.currentTimeMillis());
	}

	private String scrub(String text)
	{
		Player me = client.getLocalPlayer();
		return Privacy.scrub(text, me == null ? null : me.getName());
	}

	private WorldEntity boatOf(WorldView view)
	{
		WorldView top = client.getTopLevelWorldView();
		return top == null ? null : top.worldEntities().byIndex(view.getId());
	}

	private static void worldView(JsonObject o, WorldView view)
	{
		o.addProperty("wv", view == null ? -1 : view.getId());
	}

	private static void point(JsonObject o, String prefix, WorldPoint point)
	{
		if (point == null)
		{
			return;
		}
		o.addProperty(prefix + (prefix.isEmpty() ? "x" : "X"), point.getX());
		o.addProperty(prefix + (prefix.isEmpty() ? "y" : "Y"), point.getY());
		o.addProperty(prefix + (prefix.isEmpty() ? "plane" : "Plane"), point.getPlane());
	}
}
