/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.SwingUtilities;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.Scene;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.SoundEffectPlayed;
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
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
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
	static final String VERSION = "0.2";

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

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private Gson gson;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ConfigManager configManager;

	/** Every plugin's config group, for the setting lines; client thread. */
	private final Map<String, Plugin> groups = new HashMap<>();
	/** The watched plugins' config groups: their setting changes become chart markers; client thread. */
	private final Map<String, Plugin> watchedGroups = new HashMap<>();
	/** A Mark pressed in the sidebar and not yet saved: its tick, its time and its picture; client thread. */
	private int pendingMarkTick = -1;
	private long pendingMarkAt;
	private BufferedImage pendingMarkImage;
	/** The client's log, caught inside the client; attached while the plugin runs. */
	private final ClientLogTap logTap = new ClientLogTap(System.getProperty("user.home"));
	/** Regions whose collision flags this recording already holds; client thread. */
	private final Set<Integer> collisionDone = new HashSet<>();
	/** NPC indexes that are targeting the player right now, so the end of it gets a line too; client thread. */
	private final Set<Integer> targetingMe = new HashSet<>();
	private WatchPanel watchPanel;

	/** Frames per second since the plugin started, recording or not: what the sidebar and the window show live. */
	private final FpsSeries live = new FpsSeries("Live", FpsSeries.LIVE_CAPACITY);
	private BlackBoxPanel panel;
	private NavigationButton navButton;
	private FpsWindow window;

	private SessionLog session;
	private ScheduledFuture<?> flushTask;
	private long lastShotAt;
	private int shotCount;
	/** For {@link #npcMoves()}. Client thread only. */
	private final NpcTiles npcTiles = new NpcTiles();
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
		logTap.attach();
		flushTask = executor.scheduleWithFixedDelay(this::flushQuietly, 1, 1, TimeUnit.SECONDS);
		watchPanel = new WatchPanel(new Watching());
		Office office = new Office();
		RecorderSection recorder = new RecorderSection(office);
		panel = new BlackBoxPanel(recorder, new FpsSection(live, this::openFpsWindow), new RecordingsSection(office, recorder), watchPanel);
		clientThread.invoke(this::rebuildWatchedGroups);
		navButton = NavigationButton.builder()
			.tooltip("Black Box")
			.icon(ImageUtil.loadImageResource(getClass(), "sidebar.png"))
			.priority(10)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		clientThread.invoke(this::startIfWanted);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(markListener);
		logTap.detach();
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (panel != null)
		{
			panel.onDeactivate();
			panel = null;
		}
		SwingUtilities.invokeLater(() ->
		{
			if (window != null)
			{
				window.dispose();
				window = null;
			}
		});
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
		collisionDone.clear();
		targetingMe.clear();
		shotCount = 0;
		lastShotAt = 0;
		npcTiles.clear();
		pinPause = pinShowing();
		JsonObject header = started.line("session", client.getTickCount(), now);
		header.addProperty("format", SessionLog.FORMAT);
		header.addProperty("blackBox", VERSION);
		header.addProperty("runelite", RuneLiteProperties.getVersion());
		header.addProperty("world", client.getWorld());
		header.addProperty("started", name);
		header.addProperty("startedAt", now);
		Environment.describe(header, client);
		started.add(header);
		if (pinPause)
		{
			pinLine(started, "pause", now);
		}
		writeWatch(started, now);
		Player me = client.getLocalPlayer();
		logTap.setOwnName(me == null ? null : me.getName());
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
			// The client's log from here on, and what it logged since the client started, beside the events.
			logTap.startSending(started::addLogLine);
			cleanUp(root, name);
		});
		log.debug("Black Box recording {}", name);
		panelChanged();
	}

	/** Tells the sidebar a recording started or ended. Any thread. */
	private void panelChanged()
	{
		BlackBoxPanel current = panel;
		if (current != null)
		{
			SwingUtilities.invokeLater(current::recordingChanged);
		}
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
		logTap.stopSending();
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
			panelChanged();
		});
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// Which setting changed, never its value: a value can be a webhook link or an API key (WORKING_RULES §4).
		// Not a game value, so the bank PIN pause does not apply; a setting says nothing about the keypad.
		String group = event.getGroup();
		String key = event.getKey();
		String newValue = event.getNewValue();
		long now = System.currentTimeMillis();
		clientThread.invoke(() ->
		{
			Plugin plugin = groups.get(group);
			// Every plugin's value, through the same withholding as a snapshot (secrets, webhooks and tokens never).
			String value = plugin == null ? null : valueFor(plugin, key, newValue);
			if (watchedGroups.containsKey(group) || BlackBoxConfig.GROUP.equals(group))
			{
				// Chart markers only for the plugins being watched, so the chart is not buried under every toggle.
				live.note(now, "setting", group + "." + key + (value == null ? "" : " = " + value));
			}
			if (session != null)
			{
				JsonObject o = session.line("setting", client.getTickCount(), now);
				o.addProperty("group", group);
				o.addProperty("key", key);
				if (plugin != null)
				{
					o.addProperty("plugin", plugin.getName());
					if (value != null)
					{
						o.addProperty("value", value);
					}
				}
				session.add(o);
			}
			if (BlackBoxConfig.GROUP.equals(group) && "record".equals(key))
			{
				if (config.record())
				{
					startIfWanted();
				}
				else
				{
					stop("recording switched off");
				}
			}
		});
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		// A plugin switched on or off: the moment to line up against the frame rate. Not a game value either.
		String name = event.getPlugin() == null ? "?" : event.getPlugin().getName();
		boolean on = event.isLoaded();
		long now = System.currentTimeMillis();
		live.note(now, "plugin", name + (on ? " on" : " off"));
		clientThread.invoke(() ->
		{
			if (session != null)
			{
				JsonObject o = session.line("plugin", client.getTickCount(), now);
				o.addProperty("name", name);
				o.addProperty("on", on);
				session.add(o);
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		live.note(System.currentTimeMillis(), "state", state.name());
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
		int fps = client.getFPS();
		live.add(System.currentTimeMillis(), fps);
		if (session == null)
		{
			return;
		}
		// The keypad's load and close events cover this; a tick re-check catches anything they miss.
		setPinPause(pinShowing());
		JsonObject o = line("tick");
		o.addProperty("fps", fps);
		Player me = client.getLocalPlayer();
		if (me != null)
		{
			WorldView view = me.getWorldView();
			o.addProperty("wv", view == null ? -1 : view.getId());
			point(o, "", me.getWorldLocation());
			o.addProperty("anim", me.getAnimation());
			WorldEntity boat = view == null || view.isTopLevel() ? null : boatOf(view);
			LocalPoint ground = me.getLocalLocation();
			if (boat != null)
			{
				// Aboard a boat: where the player and the boat are in the top-level world too.
				LocalPoint top = boat.transformToMainWorld(me.getLocalLocation());
				point(o, "top", top == null ? null : WorldPoint.fromLocal(client, top));
				LocalPoint hull = boat.getLocalLocation();
				point(o, "boat", hull == null ? null : WorldPoint.fromLocal(client, hull));
				o.addProperty("boatConfig", boat.getConfig() == null ? -1 : boat.getConfig().getId());
				o.addProperty("boatOwner", boat.getOwnerType());
				// Which way the hull points (0 to 2047, 0 south), for anything that depends on heading.
				o.addProperty("boatDir", boat.getOrientation());
				ground = hull;
			}
			// The ground, or water, under the player (ashore) or under the hull (aboard): the scene's overlay and
			// underlay ids, the same ids a map built from the game's files uses. World data, not about anyone.
			WorldView top = client.getTopLevelWorldView();
			if (top != null && ground != null && (boat != null || view == top))
			{
				Scene scene = top.getScene();
				int plane = top.getPlane();
				short[][][] overlays = scene == null ? null : scene.getOverlayIds();
				short[][][] underlays = scene == null ? null : scene.getUnderlayIds();
				o.addProperty("overlay", overlays == null || plane >= overlays.length ? -1 : SceneData.at(overlays[plane], ground.getSceneX(), ground.getSceneY()));
				o.addProperty("underlay", underlays == null || plane >= underlays.length ? -1 : SceneData.at(underlays[plane], ground.getSceneX(), ground.getSceneY()));
				collision(top, WorldPoint.fromLocal(client, ground));
			}
		}
		session.add(o);
		npcMoves();

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
	public void onSoundEffectPlayed(SoundEffectPlayed event)
	{
		// The client only raises this while the game's sound effects are on. A sound id says nothing about
		// anyone, but while the keypad shows nothing is recorded at all.
		if (session == null || pinPause || !config.recordSounds())
		{
			return;
		}
		JsonObject o = line("sound");
		o.addProperty("id", event.getSoundId());
		o.addProperty("delay", event.getDelay());
		Actor source = event.getSource();
		if (source != null && source == client.getLocalPlayer())
		{
			o.addProperty("from", Privacy.YOU);
		}
		else if (source instanceof NPC)
		{
			o.addProperty("npc", ((NPC) source).getId());
			o.addProperty("index", ((NPC) source).getIndex());
		}
		// Another player as the source is left out, like everything else about other players.
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
		String text = scrub(event.getMessage());
		o.addProperty("text", text);
		live.note(System.currentTimeMillis(), "notify", text);
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

	/** A note typed in the sidebar: what happened, in the player's words, at this moment. Client thread. */
	private void note(String text)
	{
		live.note(System.currentTimeMillis(), "note", text);
		if (session == null)
		{
			return;
		}
		JsonObject o = line("note");
		o.addProperty("text", scrub(text));
		session.add(o);
	}

	/** The hotkey: a mark in the recording, and a screenshot when screenshots are on. */
	private void mark()
	{
		live.note(System.currentTimeMillis(), "mark", "Mark");
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
		// Where it is in the main world (format 5), unless the bank PIN keypad shows.
		WorldPoint at = pinPause ? null : mainWorldTile(npc);
		point(o, "", at);
		if (at != null && "npcAdd".equals(kind) && isTopLevel(npc))
		{
			npcTiles.moved(npc.getIndex(), at);
		}
		else if ("npcGone".equals(kind))
		{
			npcTiles.forget(npc.getIndex());
		}
		session.add(o);
	}

	/**
	 * One {@code npcMove} line per tick (format 5): {@code at}, an array of {@code [index, x, y, plane]} for every
	 * NPC in the main world whose tile changed since the last tick. NPCs aboard a boat (crew) are left out: they
	 * move with the boat every tick. Nothing while the bank PIN keypad shows.
	 */
	private void npcMoves()
	{
		WorldView top = client.getTopLevelWorldView();
		if (session == null || pinPause || top == null)
		{
			return;
		}
		JsonArray moved = new JsonArray();
		for (NPC npc : top.npcs())
		{
			WorldPoint at = npc == null ? null : npc.getWorldLocation();
			if (at == null)
			{
				continue;
			}
			if (npcTiles.moved(npc.getIndex(), at))
			{
				JsonArray entry = new JsonArray();
				entry.add(npc.getIndex());
				entry.add(at.getX());
				entry.add(at.getY());
				entry.add(at.getPlane());
				moved.add(entry);
			}
		}
		if (moved.size() > 0)
		{
			JsonObject o = line("npcMove");
			o.add("at", moved);
			session.add(o);
		}
	}

	private static boolean isTopLevel(NPC npc)
	{
		WorldView view = npc.getWorldView();
		return view != null && view.isTopLevel();
	}

	/** The NPC's tile in the main world: its own tile there, or, aboard a boat, the boat's main-world tile under it. */
	private WorldPoint mainWorldTile(NPC npc)
	{
		WorldView view = npc.getWorldView();
		if (view == null)
		{
			return null;
		}
		if (view.isTopLevel())
		{
			return npc.getWorldLocation();
		}
		WorldEntity boat = boatOf(view);
		LocalPoint local = npc.getLocalLocation();
		LocalPoint top = boat == null || local == null ? null : boat.transformToMainWorld(local);
		return top == null ? null : WorldPoint.fromLocal(client, top);
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

	// ---- The sidebar's recorder and recordings list ----

	/** What the recorder card and the recordings list do; Swing thread in, workers out. */
	private final class Office implements Desk
	{
		@Override
		public boolean isRecording()
		{
			return session != null;
		}

		@Override
		public String currentName()
		{
			SessionLog current = session;
			return current == null ? null : current.getName();
		}

		@Override
		public long startedAt()
		{
			SessionLog current = session;
			return current == null ? 0 : current.getStartedAt();
		}

		@Override
		public long bytesSoFar()
		{
			SessionLog current = session;
			return current == null ? 0 : current.getBytes();
		}

		@Override
		public boolean isRecordingEnabled()
		{
			return config.record();
		}

		@Override
		public void beginMark(Consumer<String> then)
		{
			clientThread.invoke(() ->
			{
				pendingMarkTick = client.getTickCount();
				pendingMarkAt = System.currentTimeMillis();
				pendingMarkImage = null;
				capture(image -> pendingMarkImage = image);
				String when = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(pendingMarkAt));
				SwingUtilities.invokeLater(() -> then.accept(when));
			});
		}

		@Override
		public void saveMark(String text)
		{
			clientThread.invoke(() ->
			{
				if (pendingMarkTick < 0)
				{
					return;
				}
				int tick = pendingMarkTick;
				long at = pendingMarkAt;
				BufferedImage image = pendingMarkImage;
				pendingMarkTick = -1;
				pendingMarkImage = null;
				live.note(at, "mark", text.isEmpty() ? "Mark" : text);
				SessionLog target = session;
				if (target == null)
				{
					return;
				}
				JsonObject o = target.line("mark", tick, at);
				if (!text.isEmpty())
				{
					o.addProperty("text", scrub(text));
				}
				target.add(o);
				if (image != null && !pinPause)
				{
					String file = String.format("%05d-tick%d-mark.jpg", ++shotCount, tick);
					JsonObject shot = target.line("shot", tick, at);
					shot.addProperty("file", file);
					shot.addProperty("reason", "mark");
					target.add(shot);
					executor.execute(() -> writeShot(target, file, image));
				}
			});
		}

		@Override
		public void cancelMark()
		{
			clientThread.invoke(() ->
			{
				pendingMarkTick = -1;
				pendingMarkImage = null;
			});
		}

		@Override
		public void note(String text)
		{
			clientThread.invoke(() -> BlackBoxPlugin.this.note(text));
		}

		@Override
		public void recordings(Consumer<List<Entry>> then)
		{
			executor.execute(() ->
			{
				List<Entry> entries = new ArrayList<>();
				try
				{
					Filepath root = getPluginDirectory();
					String current = currentName();
					try (Stream<Filepath> dirs = root.walk(1))
					{
						for (Filepath dir : dirs.filter(p -> !p.equals(root) && p.isDirectory()).collect(Collectors.toList()))
						{
							if (Names.parse(dir.getFileName()) != null)
							{
								entries.add(new Entry(dir.getFileName(), measure(dir).bytes, dir.getFileName().equals(current)));
							}
						}
					}
				}
				catch (IOException e)
				{
					log.warn("Black Box could not list recordings", e);
				}
				entries.sort((a, b) -> b.name.compareTo(a.name));
				SwingUtilities.invokeLater(() -> then.accept(entries));
			});
		}

		@Override
		public void clip(int lastMinutes, Report report)
		{
			long now = System.currentTimeMillis();
			bundle(report, now - lastMinutes * 60_000L, now, false,
				Names.FOLDER.format(LocalDateTime.now()) + "-last" + lastMinutes + "min.zip");
		}

		@Override
		public void exportToday(Report report)
		{
			long now = System.currentTimeMillis();
			long midnight = LocalDateTime.now().toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
			bundle(report, midnight, now, true, LocalDateTime.now().toLocalDate() + "-today.zip");
		}

		@Override
		public void zipRecording(String name, Report report)
		{
			executor.execute(() ->
			{
				try
				{
					Filepath root = getPluginDirectory();
					SessionLog current = session;
					long now = System.currentTimeMillis();
					List<ClipBundle.Source> sources = new ArrayList<>();
					for (ClipBundle.Source source : ClipBundle.sources(gson, root, current == null ? null : current.getName(), current == null ? 0 : current.getStartedAt(), now))
					{
						if (source.name.equals(name))
						{
							sources.add(source);
						}
					}
					if (sources.isEmpty())
					{
						say(report, "Not a recording: " + name);
						return;
					}
					if (current != null && name.equals(current.getName()))
					{
						current.flush();
					}
					Filepath zip = ClipBundle.write(gson, root, sources, 0, config.picturesInClips(), name + ".zip");
					done(report, zip);
				}
				catch (IOException e)
				{
					log.warn("Black Box could not write the zip", e);
					say(report, "Could not write the zip: " + e.getMessage());
				}
			});
		}

		/** Writes the recordings overlapping a window, whole when {@code whole}, else from {@code since}. */
		private void bundle(Report report, long since, long until, boolean whole, String fileName)
		{
			executor.execute(() ->
			{
				try
				{
					Filepath root = getPluginDirectory();
					SessionLog current = session;
					if (current != null)
					{
						current.flush(); // so the clip holds the current recording's lines up to now
					}
					List<ClipBundle.Source> all = ClipBundle.sources(gson, root, current == null ? null : current.getName(), current == null ? 0 : current.getStartedAt(), until);
					List<ClipBundle.Source> covering = ClipBundle.covering(all, since, until);
					if (covering.isEmpty())
					{
						say(report, "Nothing recorded in that time.");
						return;
					}
					Filepath zip = ClipBundle.write(gson, root, covering, whole ? 0 : since, config.picturesInClips(), fileName);
					done(report, zip);
				}
				catch (IOException e)
				{
					log.warn("Black Box could not write the clip", e);
					say(report, "Could not write the clip: " + e.getMessage());
				}
			});
		}

		private void done(Report report, Filepath zip)
		{
			String where = zip.toString();
			boolean copied = RecorderSection.copy(where);
			say(report, "Ready to send: " + where + (copied ? " (path copied to the clipboard)" : ""));
		}

		@Override
		public void openFolder(Report report)
		{
			try
			{
				LinkBrowser.open(getPluginDirectory().toString());
			}
			catch (IOException e)
			{
				say(report, "Could not find the recordings folder: " + e.getMessage());
			}
		}

		@Override
		public void delete(String name, Report report)
		{
			executor.execute(() ->
			{
				try
				{
					Filepath dir = getPluginDirectory().join(name);
					if (Names.parse(name) == null || !dir.isDirectory())
					{
						say(report, "Not a recording: " + name);
						return;
					}
					dir.deleteRecursively();
					say(report, "Deleted " + name + ".");
				}
				catch (IOException e)
				{
					say(report, "Could not delete " + name + ": " + e.getMessage());
				}
			});
		}

		private void say(Report report, String text)
		{
			SwingUtilities.invokeLater(() -> report.say(text));
		}
	}

	// ---- Watched plugins ----

	/** What the sidebar's "Watched plugins" section reads and writes: Black Box's own configuration. Swing thread. */
	private final class Watching implements WatchPanel.Model
	{
		@Override
		public List<String> installed()
		{
			List<String> names = new ArrayList<>();
			for (Plugin plugin : pluginManager.getPlugins())
			{
				if (plugin.getName() != null)
				{
					names.add(plugin.getName());
				}
			}
			Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
			return names;
		}

		@Override
		public List<String> watched()
		{
			return WatchList.decode(configManager.getConfiguration(BlackBoxConfig.GROUP, WatchList.WATCHED_KEY));
		}

		@Override
		public void setWatched(List<String> names)
		{
			if (names.isEmpty())
			{
				configManager.unsetConfiguration(BlackBoxConfig.GROUP, WatchList.WATCHED_KEY);
			}
			else
			{
				configManager.setConfiguration(BlackBoxConfig.GROUP, WatchList.WATCHED_KEY, WatchList.encode(names));
			}
			clientThread.invoke(() ->
			{
				rebuildWatchedGroups();
				if (session != null)
				{
					writeWatch(session, System.currentTimeMillis());
				}
			});
		}

		@Override
		public boolean isOn(String name)
		{
			Plugin plugin = pluginByName(name);
			return plugin != null && pluginManager.isPluginEnabled(plugin);
		}

		@Override
		public List<String> sets()
		{
			String prefix = BlackBoxConfig.GROUP + "." + WatchList.SET_PREFIX;
			List<String> names = new ArrayList<>();
			for (String key : configManager.getConfigurationKeys(prefix))
			{
				if (key.startsWith(prefix))
				{
					names.add(key.substring(prefix.length()));
				}
			}
			Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
			return names;
		}

		@Override
		public List<String> loadSet(String name)
		{
			return WatchList.decode(configManager.getConfiguration(BlackBoxConfig.GROUP, WatchList.SET_PREFIX + name));
		}

		@Override
		public void saveSet(String name, List<String> names)
		{
			configManager.setConfiguration(BlackBoxConfig.GROUP, WatchList.SET_PREFIX + name, WatchList.encode(names));
		}

		@Override
		public void deleteSet(String name)
		{
			configManager.unsetConfiguration(BlackBoxConfig.GROUP, WatchList.SET_PREFIX + name);
		}
	}

	private Plugin pluginByName(String name)
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (name.equals(plugin.getName()))
			{
				return plugin;
			}
		}
		return null;
	}

	/** The watched plugins in the stored order, those that are installed. Any thread. */
	private List<Plugin> watchedPlugins()
	{
		List<Plugin> plugins = new ArrayList<>();
		for (String name : WatchList.decode(configManager.getConfiguration(BlackBoxConfig.GROUP, WatchList.WATCHED_KEY)))
		{
			Plugin plugin = pluginByName(name);
			if (plugin != null)
			{
				plugins.add(plugin);
			}
		}
		return plugins;
	}

	/** A watched plugin's config descriptor, or null when it has no configuration. */
	private ConfigDescriptor descriptorOf(Plugin plugin)
	{
		Config proxy = pluginManager.getPluginConfigProxy(plugin);
		return proxy == null ? null : configManager.getConfigDescriptor(proxy);
	}

	/** Client thread. */
	private void rebuildWatchedGroups()
	{
		groups.clear();
		for (Plugin plugin : pluginManager.getPlugins())
		{
			ConfigDescriptor descriptor = descriptorOf(plugin);
			if (descriptor != null)
			{
				groups.put(descriptor.getGroup().value(), plugin);
			}
		}
		watchedGroups.clear();
		for (Plugin plugin : watchedPlugins())
		{
			ConfigDescriptor descriptor = descriptorOf(plugin);
			if (descriptor != null)
			{
				watchedGroups.put(descriptor.getGroup().value(), plugin);
			}
		}
	}

	/**
	 * A {@code watch} line naming the watched plugins (the ones the player is reporting on), a {@code plugins} line
	 * naming every installed plugin with on or off, then a {@code settings} line per plugin that has settings, with
	 * every current value (secrets, webhooks and tokens withheld, see {@link SettingsSnapshot}). Client thread.
	 */
	private void writeWatch(SessionLog target, long now)
	{
		List<Plugin> plugins = watchedPlugins();
		JsonObject watch = target.line("watch", client.getTickCount(), now);
		JsonArray list = new JsonArray();
		for (Plugin plugin : plugins)
		{
			JsonObject one = new JsonObject();
			one.addProperty("name", plugin.getName());
			one.addProperty("on", pluginManager.isPluginEnabled(plugin));
			String version = Environment.versionOf(plugin);
			if (version != null)
			{
				one.addProperty("version", version);
			}
			list.add(one);
		}
		watch.add("plugins", list);
		target.add(watch);
		List<Plugin> all = new ArrayList<>(pluginManager.getPlugins());
		all.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(a.getName()), String.valueOf(b.getName())));
		JsonObject installed = target.line("plugins", client.getTickCount(), now);
		JsonArray names = new JsonArray();
		for (Plugin plugin : all)
		{
			JsonObject one = new JsonObject();
			one.addProperty("name", plugin.getName());
			one.addProperty("on", pluginManager.isPluginEnabled(plugin));
			String version = Environment.versionOf(plugin);
			if (version != null)
			{
				one.addProperty("version", version);
			}
			names.add(one);
		}
		installed.add("plugins", names);
		target.add(installed);
		for (Plugin plugin : all)
		{
			ConfigDescriptor descriptor = descriptorOf(plugin);
			if (descriptor == null)
			{
				continue;
			}
			String group = descriptor.getGroup().value();
			JsonObject o = target.line("settings", client.getTickCount(), now);
			o.addProperty("plugin", plugin.getName());
			o.addProperty("group", group);
			o.add("values", SettingsSnapshot.of(descriptor.getItems(), key -> configManager.getConfiguration(group, key)));
			target.add(o);
		}
	}

	/** A changed setting's value, withheld like a snapshot would; null when it is hidden or unset. */
	private String valueFor(Plugin plugin, String key, String newValue)
	{
		if (newValue == null)
		{
			return null;
		}
		ConfigDescriptor descriptor = descriptorOf(plugin);
		if (descriptor == null)
		{
			return null;
		}
		for (ConfigItemDescriptor item : descriptor.getItems())
		{
			if (!key.equals(item.key()))
			{
				continue;
			}
			if (item.getItem().hidden())
			{
				return null;
			}
			if (item.getItem().secret())
			{
				return SettingsSnapshot.SECRET;
			}
			return item.getType() == String.class ? SettingsSnapshot.text(key, newValue) : newValue;
		}
		return null;
	}

	// ---- Frames per second ----

	/** Opens (or raises) the chart window. Any thread. */
	private void openFpsWindow()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (window == null)
			{
				window = new FpsWindow(live, new RecordingSource(), executor);
			}
			// Hiding first makes a window that is already open come to the front without raising it ourselves,
			// which the Hub's window rule forbids even for a plugin's own frame (this repository stays Hub-ready).
			if (window.isVisible())
			{
				window.setVisible(false);
			}
			window.setVisible(true);
		});
	}

	/** Past recordings in the plugin's own folder, newest first, for the window. Executor thread. */
	private final class RecordingSource implements FpsWindow.Source
	{
		@Override
		public List<String> recordings() throws IOException
		{
			Filepath root = getPluginDirectory();
			List<String> names = new ArrayList<>();
			try (Stream<Filepath> entries = root.walk(1))
			{
				for (Filepath dir : entries.filter(p -> !p.equals(root) && p.isDirectory()).collect(Collectors.toList()))
				{
					if (dir.join(SessionLog.EVENTS_FILE).isFile())
					{
						names.add(dir.getFileName());
					}
				}
			}
			names.sort(java.util.Comparator.reverseOrder());
			return names;
		}

		@Override
		public FpsSeries load(String name) throws IOException
		{
			try (BufferedReader in = getPluginDirectory().join(name, SessionLog.EVENTS_FILE).openBufferedReader())
			{
				return FpsSeries.fromRecording(name, gson, in);
			}
		}
	}

	// ---- The world around the boat ----

	/**
	 * A region's collision flags, once per recording, the first time the whole region is inside the loaded scene:
	 * what a boat can and cannot sail through, and what a walker can and cannot cross. World data, not about
	 * anyone; what Shortest Path reads live. About 4,096 numbers per region. Client thread.
	 */
	private void collision(WorldView top, WorldPoint at)
	{
		if (at == null || session == null)
		{
			return;
		}
		int region = at.getRegionID();
		if (collisionDone.contains(region))
		{
			return;
		}
		CollisionData[] maps = top.getCollisionMaps();
		int plane = top.getPlane();
		if (maps == null || plane >= maps.length || maps[plane] == null)
		{
			return;
		}
		int[][] flags = SceneData.regionFlags(maps[plane].getFlags(), top.getBaseX(), top.getBaseY(), (region >> 8) * 64, (region & 0xff) * 64);
		if (flags == null)
		{
			return; // not fully loaded yet; try again next tick
		}
		collisionDone.add(region);
		JsonObject o = line("collision");
		o.addProperty("region", region);
		o.addProperty("plane", plane);
		o.addProperty("x", (region >> 8) * 64);
		o.addProperty("y", (region & 0xff) * 64);
		JsonArray rows = new JsonArray();
		for (int[] column : flags)
		{
			JsonArray row = new JsonArray();
			for (int flag : column)
			{
				row.add(flag);
			}
			rows.add(row);
		}
		o.add("flags", rows);
		session.add(o);
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		// When an NPC starts or stops targeting you: the aggro moment. Another player as the target is never
		// recorded; a stop line names no one.
		Actor source = event.getSource();
		if (session == null || !(source instanceof NPC))
		{
			return;
		}
		NPC npc = (NPC) source;
		boolean me = event.getTarget() != null && event.getTarget() == client.getLocalPlayer();
		boolean was = targetingMe.contains(npc.getIndex());
		if (me == was)
		{
			return;
		}
		if (me)
		{
			targetingMe.add(npc.getIndex());
		}
		else
		{
			targetingMe.remove(npc.getIndex());
		}
		JsonObject o = line("npcTarget");
		o.addProperty("npc", npc.getId());
		o.addProperty("index", npc.getIndex());
		o.addProperty("name", scrub(npc.getName()));
		o.addProperty("on", me);
		worldView(o, npc.getWorldView());
		session.add(o);
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		// NPCs only (the player's own animation is on every tick line; other players are never recorded).
		Actor actor = event.getActor();
		if (session == null || !(actor instanceof NPC))
		{
			return;
		}
		NPC npc = (NPC) actor;
		JsonObject o = line("npcAnim");
		o.addProperty("npc", npc.getId());
		o.addProperty("index", npc.getIndex());
		o.addProperty("anim", npc.getAnimation());
		worldView(o, npc.getWorldView());
		session.add(o);
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		// Your own spot animations: being set alight, frozen or diseased shows here, timed against the warnings.
		Actor actor = event.getActor();
		if (session == null || actor == null || actor != client.getLocalPlayer())
		{
			return;
		}
		JsonObject o = line("graphic");
		JsonArray ids = new JsonArray();
		for (ActorSpotAnim spot : actor.getSpotAnims())
		{
			ids.add(spot.getId());
		}
		o.add("ids", ids);
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

	/**
	 * Keeps the next frame, shrunk and with the chat box blacked out, for a mark that may be saved later. The
	 * keypad and the game state are checked when the frame arrives, as for any screenshot. Client thread.
	 */
	private void capture(java.util.function.Consumer<BufferedImage> then)
	{
		if (pinPause)
		{
			return;
		}
		Rectangle chat = config.hideChat() ? chatBounds() : null;
		int canvasWidth = client.getCanvasWidth();
		int maxWidth = config.shotWidth();
		drawManager.requestNextFrameListener(image ->
		{
			boolean keep = !pinPause && Privacy.mayKeepFrame(client.getGameState());
			BufferedImage small = keep ? shrink(image, maxWidth, chat, canvasWidth) : null;
			if (small != null)
			{
				clientThread.invoke(() -> then.accept(small));
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
