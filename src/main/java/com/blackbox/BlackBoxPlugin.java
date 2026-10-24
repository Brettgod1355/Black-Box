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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
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
import net.runelite.api.ChatMessageType;
import net.runelite.api.KeyCode;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
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
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.CanvasSizeChanged;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.PlayerMenuOptionsChanged;
import net.runelite.api.events.ResizeableChanged;
import net.runelite.api.events.WorldViewLoaded;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
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
import net.runelite.client.config.Notification;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.externalplugins.PluginHubManifest;
import net.runelite.client.game.ItemStack;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.events.UserJoin;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBox;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Black Box: records what the game shows a plugin while you play, so a plugin's logic can be tested against
 * a real session later without playing again. A tool for developers and the players who help them, built to the Plugin Hub rules.
 * <p>
 * It only reads what RuneLite already exposes. It sends nothing to the game and nothing over the network,
 * and writes only to its own folder. Other players' names and chat are left out, and the player's own name
 * is replaced (see {@link Privacy}).
 */
@PluginDescriptor(
	name = "Black Box",
	description = "Developer tool: records game events and screenshots while you play, for testing plugins against later",
	tags = {"developer", "testing", "recorder", "replay", "screenshot"},
	internalName = "black-box"
)
public class BlackBoxPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(BlackBoxPlugin.class);

	/** The version written into every recording's first line. */
	static final String VERSION = "0.3";

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

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private PartyService partyService;

	@Inject
	private ExternalPluginManager externalPluginManager;

	@Inject
	private EventBus eventBus;

	/** The Debug switch: never saved, so off whenever the client starts. Any thread reads; set from the page. */
	private volatile boolean debugOn;
	/** The Debug page's tests, registered only while Debug is on; client thread. */
	private DebugProbes probes;
	/** {@link #menuOpenedFirst}, the second MenuOpened subscriber. */
	private EventBus.Subscriber menuFirst;

	/** Every plugin's config group, for the setting lines; client thread. */
	private final Map<String, Plugin> groups = new HashMap<>();
	/** A Mark pressed in the sidebar and not yet saved: its tick, its time and its picture; client thread. */
	private int pendingMarkTick = -1;
	private long pendingMarkAt;
	private BufferedImage pendingMarkImage;
	/** The names behind the numbers, loaded the first time an export needs them; executor thread. */
	private IdNames idNames;
	/** The client's log, caught inside the client; attached while the plugin runs. */
	private final ClientLogTap logTap = new ClientLogTap(System.getProperty("user.home"));
	/** Regions whose collision flags this recording already holds; client thread. */
	private final Set<Integer> collisionDone = new HashSet<>();
	/** NPC indexes that are targeting the player right now, so the end of it gets a line too; client thread. */
	private final Set<Integer> targetingMe = new HashSet<>();

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
	/** The welcome screen after login is showing: no screenshot is saved. Read by the frame listener. */
	private volatile boolean welcomeShowing;
	/**
	 * After every other subscriber: RuneLite calls higher priorities first, and this is far below any a plugin
	 * uses, so by the time the overhead text handler runs the other plugins have had their turn at the same event.
	 */
	static final float LAST = -1000f;
	/** Before every other subscriber, to see an event as the game posted it. */
	static final float FIRST = 1000f;
	/** The overlays, infoboxes and hint arrow last written, so a line comes only on a change (format 9); client thread. */
	private String overlaysSeen = "";
	private String infoBoxesSeen = "";
	private String hintSeen = "";
	/** Whether this recording holds its varp snapshot yet; client thread. */
	private boolean varpsDone;
	/** Party members by a number that holds for one recording, never their id or name; client thread. */
	private final Map<Long, Integer> partyMembers = new HashMap<>();
	/** Frames since the last game tick and the longest gap between them; client thread. */
	private final FrameClock frameClock = new FrameClock();
	/** Menu entries added since the last game tick; client thread. */
	private int menuAdds;
	/** How long the last probe waited on the Swing thread and on the client thread, in ms; -1 before the first. */
	private volatile double edtWaitMs = -1;
	private volatile boolean edtWaiting;
	private volatile long edtPostedAt;
	private volatile double clientWaitMs = -1;
	/** Whether the window had focus at the last FocusChanged; null before the first. */
	private Boolean focused;
	/** The menu as the game opened it, before other plugins' handlers; client thread. */
	private JsonArray menuBefore;
	/**
	 * NPCs whose overhead text changed this client cycle, by scene index, with the text the other subscribers
	 * left on them. The cycle's own ClientTick comes a moment after the event (both inside the same client
	 * cycle), so each entry is held over to the next one, by which time the game tick, the queued client-thread
	 * calls and every plugin's tick handler that follow the event have run. Client thread only.
	 */
	private final Map<Integer, Said> saidThisTick = new HashMap<>();
	private final Map<Integer, Said> saidLastTick = new HashMap<>();
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
		menuFirst = eventBus.register(MenuOpened.class, this::menuOpenedFirst, FIRST);
		logTap.attach();
		flushTask = executor.scheduleWithFixedDelay(this::flushQuietly, 1, 1, TimeUnit.SECONDS);
		Office office = new Office();
		RecorderSection recorder = new RecorderSection(office);
		debugOn = false;
		probes = new DebugProbes(client, eventBus, drawManager, new DebugProbes.Sink()
		{
			@Override
			public boolean writing()
			{
				return session != null && !pinPause;
			}

			@Override
			public JsonObject line(String kind)
			{
				// The session as it is now: shutDown may clear it from the Swing thread between writing() and here.
				SessionLog now = session;
				return now == null ? new JsonObject() : now.line(kind, client.getTickCount(), System.currentTimeMillis());
			}

			@Override
			public void add(JsonObject line)
			{
				SessionLog now = session;
				if (now != null)
				{
					now.add(line);
				}
			}

			@Override
			public String scrub(String text)
			{
				return BlackBoxPlugin.this.scrub(text);
			}
		});
		DebugSection debug = new DebugSection(new DebugSection.Controls()
		{
			@Override
			public boolean debugOn()
			{
				return debugOn;
			}

			@Override
			public void setDebugOn(boolean on)
			{
				debugOn = on;
				live.note(System.currentTimeMillis(), "setting", on ? "Debug on" : "Debug off");
				if (panel != null)
				{
					panel.debugChanged();
				}
				clientThread.invoke(BlackBoxPlugin.this::applyDebug);
			}

			@Override
			public Set<DebugTest> tests()
			{
				return DebugTest.parse(config.debugTests());
			}

			@Override
			public void setTests(Set<DebugTest> tests)
			{
				configManager.setConfiguration(BlackBoxConfig.GROUP, "debugTests", DebugTest.format(tests));
				clientThread.invoke(BlackBoxPlugin.this::applyDebug);
			}
		});
		panel = new BlackBoxPanel(recorder, new FpsSection(live, this::openFpsWindow), new RecordingsSection(office, recorder),
			debug);
		clientThread.invoke(this::rebuildGroups);
		forgetReportingOn();
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
		if (menuFirst != null)
		{
			eventBus.unregister(menuFirst);
			menuFirst = null;
		}
		debugOn = false;
		DebugProbes stopping = probes;
		if (stopping != null)
		{
			clientThread.invoke(stopping::stopAll);
		}
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

	/** Tries to make a recording's folder: true if it was made, false if one of that name is already there. */
	interface FolderClaim
	{
		boolean create(String name) throws IOException;
	}

	/**
	 * The recording's folder name: the start time, or with "-2", "-3" and so on when a folder of that name is already
	 * there. Two clients sharing one RuneLite folder that log in within the same second would otherwise write into
	 * one recording (owner, 2026-10-22). Making the folder is the claim, so the two cannot both win. When the folder
	 * cannot be made at all, the plain name is used and opening the recording reports the error.
	 */
	static String claimName(String base, FolderClaim claim)
	{
		for (int n = 1; n < 100; n++)
		{
			String name = n == 1 ? base : base + "-" + n;
			try
			{
				if (claim.create(name))
				{
					return name;
				}
			}
			catch (IOException e)
			{
				return base;
			}
		}
		return base;
	}

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
		String name = claimName(LocalDateTime.now().format(SESSION_NAME), n ->
		{
			Filepath dir = root.join(n);
			if (dir.exists())
			{
				return false;
			}
			try
			{
				dir.createDirectory();
				return true;
			}
			catch (IOException e)
			{
				// Made by the other client between the look and the making: that one is theirs.
				if (dir.exists())
				{
					return false;
				}
				throw e;
			}
		});
		SessionLog started = new SessionLog(name, root.join(name), now);
		session = started;
		collisionDone.clear();
		targetingMe.clear();
		shotCount = 0;
		lastShotAt = 0;
		npcTiles.clear();
		varpsDone = false;
		overlaysSeen = "";
		infoBoxesSeen = "";
		hintSeen = "";
		partyMembers.clear();
		// Frames and menu entries from the login screen are not this recording's.
		frameClock.reset();
		menuAdds = 0;
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
		writePlugins(started, now);
		writeProfile(started, now, null);
		writePlayerOptions(started, now);
		probes.reset();
		if (debugOn)
		{
			writeDebug(started, now);
		}
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
			String value = plugin == null
				? (RUNELITE_GROUP.equals(group) && newValue != null ? scrub(SettingsSnapshot.other(key, newValue)) : null)
				: valueFor(plugin, key, newValue);
			if ((plugin != null && shown(plugin, key)) || BlackBoxConfig.GROUP.equals(group))
			{
				// Chart markers for settings a player can see and change; hidden ones are where plugins keep their own
				// state, and would bury the chart.
				live.note(now, "setting", group + "." + key + (value == null ? "" : " = " + value));
			}
			if (session != null)
			{
				JsonObject o = session.line("setting", client.getTickCount(), now);
				o.addProperty("group", group);
				o.addProperty("key", keyFor(plugin == null ? null : descriptorOf(plugin), group, key));
				if (plugin != null)
				{
					o.addProperty("plugin", plugin.getName());
					if (!shown(plugin, key))
					{
						// Where the plugin keeps its own state: kept, but not a change the player made (format 12).
						o.addProperty("hidden", true);
					}
				}
				if (value != null)
				{
					o.addProperty("value", value);
					if (plugin != null && isTextItem(plugin, key))
					{
						o.addProperty("text", true);
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
				// A plugin's right-click options on players come and go with it.
				writePlayerOptions(session, now);
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		live.state(System.currentTimeMillis(), state.name());
		if (session != null)
		{
			JsonObject o = line("state");
			o.addProperty("state", state.name());
			session.add(o);
		}
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			// The NPCs held for an npcShown check are gone with the scene.
			saidThisTick.clear();
			saidLastTick.clear();
		}
		if (state == GameState.LOGGED_IN)
		{
			startIfWanted();
		}
		else if (state == GameState.LOGIN_SCREEN)
		{
			if (debugOn && config.debugOffAtLogout())
			{
				// Before the recording ends, so its last debug line says Debug went off.
				debugOn = false;
				live.note(System.currentTimeMillis(), "setting", "Debug off (logout)");
				applyDebug();
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.debugChanged();
					}
				});
			}
			stop("logged out");
		}
	}

	// ---- What gets recorded ----

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int fps = client.getFPS();
		live.add(System.currentTimeMillis(), fps);
		// The welcome screen's load and close events cover this; a tick re-check catches anything they miss.
		welcomeShowing = welcomeOnScreen();
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
		// Format 9: the player's own pace and input, and how smoothly frames came since the last tick.
		o.addProperty("cycle", client.getGameCycle());
		if (!pinPause)
		{
			// Input counters say when keys were pressed; not while the keypad shows.
			o.addProperty("idleMouse", client.getMouseIdleTicks());
			o.addProperty("idleKeys", client.getKeyboardIdleTicks());
		}
		o.addProperty("energy", client.getEnergy());
		o.addProperty("weight", client.getWeight());
		o.addProperty("camYaw", client.getCameraYaw());
		if (me != null)
		{
			o.addProperty("facing", me.getOrientation());
			WorldView here = me.getWorldView();
			if (here != null && here.isInstance())
			{
				o.addProperty("instance", true);
			}
		}
		o.addProperty("frames", frameClock.frames());
		o.addProperty("longestFrameMs", frameClock.longestGapMs());
		frameClock.reset();
		if (!pinPause)
		{
			o.addProperty("menuAdds", menuAdds);
		}
		menuAdds = 0;
		if (edtWaiting)
		{
			// The Swing thread has not run the last probe yet: a freeze in progress, not the last wait.
			o.addProperty("edtPendingMs", msSince(edtPostedAt));
		}
		else if (edtWaitMs >= 0)
		{
			o.addProperty("edtWaitMs", edtWaitMs);
		}
		if (clientWaitMs >= 0)
		{
			o.addProperty("clientWaitMs", clientWaitMs);
		}
		session.add(o);
		npcMoves();
		probe();
		hintArrow();
		if (!pinPause)
		{
			// Overlay bounds move with what the keypad draws; none while it shows.
			overlays();
		}
		infoBoxes();
		varps();
		if (debugOn)
		{
			probes.tick();
		}

		int every = config.shotEverySeconds();
		if (every > 0 && !pinPause && System.currentTimeMillis() - lastShotAt >= every * 1000L)
		{
			shoot("timer");
		}
	}

	@Subscribe(priority = LAST)
	public void onChatMessage(ChatMessage event)
	{
		if (session == null)
		{
			return;
		}
		// A friend logging in or out, or a friends chat notice, is worth its moment, never its name (format 9).
		boolean friendNotice = event.getType() == ChatMessageType.LOGINLOGOUTNOTIFICATION
			|| event.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION;
		if (!friendNotice && !Privacy.recordsChat(event.getType()))
		{
			return;
		}
		JsonObject o = line("chat");
		o.addProperty("type", event.getType().name());
		o.addProperty("text", friendNotice ? Privacy.WITHHELD : scrub(event.getMessage()));
		// After every other plugin (LAST): a plugin that rewrote the line in the chat box leaves it here. RuneLite's
		// chat formatting writes the shown text beside the value, so that one wins when set.
		if (!friendNotice && event.getMessageNode() != null)
		{
			String formatted = event.getMessageNode().getRuneLiteFormatMessage();
			String shown = formatted != null ? formatted : event.getMessageNode().getValue();
			if (!Text.removeTags(plain(shown)).equals(Text.removeTags(plain(event.getMessage()))))
			{
				o.addProperty("shown", scrub(shown));
			}
		}
		session.add(o);
	}

	@Subscribe(priority = LAST)
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
		// The game set the text and posted this event; every other subscriber has run by now (LAST). What the NPC
		// shows at this point is what they left: a plugin's setOverheadText fires no event of its own (a bare field
		// write in the injected client, checked 2026-10-21), so a blanked or rewritten line can only be seen, not
		// caught, and never traced to its plugin (the Hub forbids reflection, a stack walk included; format 7).
		String shown = npc.getOverheadText();
		if (!plain(shown).equals(plain(event.getOverheadText())))
		{
			o.addProperty("shown", scrub(shown));
		}
		session.add(o);
		// A new line from the game replaces whatever was held for this slot: it is not a plugin's write.
		saidLastTick.remove(npc.getIndex());
		saidThisTick.put(npc.getIndex(), new Said(npc, shown));
	}

	@Subscribe(priority = LAST)
	public void onClientTick(ClientTick event)
	{
		if (saidThisTick.isEmpty() && saidLastTick.isEmpty())
		{
			return;
		}
		if (session != null)
		{
			for (Said said : saidLastTick.values())
			{
				// A plugin that changed the text after the event, on the game tick, a queued call or its own tick
				// handler (LAST puts this after those), leaves it here: record what the NPC shows now when it is not
				// what the last npcSay said was shown.
				String shown = said.npc.getOverheadText();
				if (!plain(shown).equals(plain(said.text)))
				{
					JsonObject o = line("npcShown");
					o.addProperty("npc", said.npc.getId());
					o.addProperty("index", said.npc.getIndex());
					o.addProperty("name", scrub(said.npc.getName()));
					o.addProperty("text", scrub(shown));
					o.addProperty("cycle", said.npc.getOverheadCycle());
					worldView(o, said.npc.getWorldView());
					session.add(o);
				}
			}
		}
		saidLastTick.clear();
		saidLastTick.putAll(saidThisTick);
		saidThisTick.clear();
	}

	/** An NPC whose overhead text changed this tick, and the text the other subscribers left shown. */
	private static final class Said
	{
		private final NPC npc;
		private final String text;

		private Said(NPC npc, String text)
		{
			this.npc = npc;
			this.text = text;
		}
	}

	private static String plain(String text)
	{
		return text == null ? "" : text;
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
		if (Privacy.isWelcomeScreen(event.getGroupId()))
		{
			welcomeShowing = true;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (Privacy.isPinKeypad(event.getGroupId()))
		{
			setPinPause(false);
		}
		if (Privacy.isWelcomeScreen(event.getGroupId()))
		{
			welcomeShowing = false;
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
		// The option as the menu showed it, colors included, when a plugin colored it (format 9): an equals check
		// on the plain word fails on a colored one.
		String raw = event.getMenuOption();
		if (raw != null && !raw.equals(Text.removeTags(raw)))
		{
			o.addProperty("rawOption", scrub(raw));
		}
		// A click on another player names them, and so does one on a chat line or a friend; leave the target out.
		boolean onNpc = event.getMenuEntry() != null && event.getMenuEntry().getNpc() != null;
		String target = event.getMenuTarget() == null || !(onNpc || Privacy.keepsTarget(event.getMenuAction(), event.getItemId()))
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
		// Not always on the client thread: the Screenshot plugin's "A screenshot was saved" is raised from the thread
		// that wrote the file, and reading the chat box for the picture there fails (seen 2026-10-20, a death).
		if (!client.isClientThread())
		{
			clientThread.invoke(() -> onNotificationFired(event));
			return;
		}
		if (session == null)
		{
			return;
		}
		JsonObject o = line("notify");
		String text = scrub(event.getMessage());
		o.addProperty("text", text);
		// How the notification was set up to reach the player, and whether the window had focus (format 9). A custom
		// sound's file name is left out: it is a path on the player's computer.
		Notification how = event.getNotification();
		if (how != null)
		{
			JsonObject settings = new JsonObject();
			settings.addProperty("enabled", how.isEnabled());
			settings.addProperty("override", how.isOverride());
			settings.addProperty("tray", how.isTray());
			settings.addProperty("sound", how.getSound() == null ? null : how.getSound().name());
			settings.addProperty("flash", how.getFlash() == null ? null : how.getFlash().name());
			settings.addProperty("requestFocus", how.getRequestFocus() == null ? null : how.getRequestFocus().name());
			settings.addProperty("gameMessage", how.isGameMessage());
			settings.addProperty("whenFocused", how.isSendWhenFocused());
			o.add("how", settings);
		}
		if (focused != null)
		{
			o.addProperty("focused", focused);
		}
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

		/** The current recording's whole folder (events, client log, pictures), measured off the client thread. */
		private volatile long folderBytes;
		private volatile long folderMeasuredAt;
		private volatile SessionLog folderOf;

		@Override
		public long bytesSoFar()
		{
			SessionLog current = session;
			if (current == null)
			{
				return 0;
			}
			long now = System.currentTimeMillis();
			if (folderOf != current || now - folderMeasuredAt >= 5000)
			{
				if (folderOf != current)
				{
					folderOf = current;
					folderBytes = 0;
				}
				folderMeasuredAt = now;
				executor.execute(() ->
				{
					try
					{
						long bytes = measure(current.getDir()).bytes;
						if (folderOf == current)
						{
							folderBytes = bytes;
						}
					}
					catch (IOException e)
					{
						log.debug("Black Box could not measure {}", current.getDir(), e);
					}
				});
			}
			// Same total as the recordings list; the events written since the last measure may lead it briefly.
			return Math.max(folderBytes, current.getBytes());
		}

		@Override
		public boolean isRecordingEnabled()
		{
			return config.record();
		}

		@Override
		public boolean picturesShowChat()
		{
			return config.picturesInClips() && !config.hideChat();
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

		/** Every installed plugin's text settings that hold something now, for the export's picker. Swing thread. */
		@Override
		public List<Desk.TextSetting> textSettings()
		{
			List<Desk.TextSetting> out = new ArrayList<>();
			for (Plugin plugin : pluginManager.getPlugins())
			{
				ConfigDescriptor descriptor = descriptorOf(plugin);
				if (descriptor == null)
				{
					continue;
				}
				String group = descriptor.getGroup().value();
				for (ConfigItemDescriptor item : descriptor.getItems())
				{
					if (item.getType() != String.class || item.getItem().hidden() || item.getItem().secret())
					{
						continue;
					}
					String value = configManager.getConfiguration(group, item.key());
					// A webhook, key or token is withheld whatever the player picks, so it is not offered.
					if (value == null || value.isEmpty() || SettingsSnapshot.WITHHELD.equals(SettingsSnapshot.text(item.key(), value)))
					{
						continue;
					}
					out.add(new Desk.TextSetting(group + "." + item.key(), plugin.getName(), item.name(), value));
				}
			}
			out.sort(java.util.Comparator.comparing((Desk.TextSetting t) -> t.plugin).thenComparing(t -> t.name));
			return out;
		}

		@Override
		public void clip(int lastMinutes, Set<String> includeText, Report report)
		{
			long now = System.currentTimeMillis();
			bundle(report, now - lastMinutes * 60_000L, now, false,
				Names.FOLDER.format(LocalDateTime.now()) + "-last" + lastMinutes + "min.zip", includeText, lastMinutes);
		}

		@Override
		public void exportToday(Set<String> includeText, Report report)
		{
			long now = System.currentTimeMillis();
			long midnight = LocalDateTime.now().toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
			bundle(report, midnight, now, true, LocalDateTime.now().toLocalDate() + "-today.zip", includeText, 0);
		}

		@Override
		public void zipRecording(String name, Set<String> includeText, Report report)
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
					Filepath zip = ClipBundle.write(gson, root, sources, 0, config.picturesInClips(), config.hideChat(), name + ".zip", names(),
						includeText);
					done(report, zip);
				}
				catch (IOException e)
				{
					log.warn("Black Box could not write the zip", e);
					say(report, "Could not write the zip: " + e.getMessage());
				}
			});
		}

		/**
		 * Writes the recordings overlapping a window, whole when {@code whole}, else from {@code since}. With
		 * {@code lastMinutes} above 0 and nothing recorded in the window, it takes the last that many minutes of the most
		 * recent recording instead, and says so.
		 */
		private void bundle(Report report, long since, long until, boolean whole, String fileName, Set<String> includeText,
			int lastMinutes)
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
					long from = since;
					String file = fileName;
					String instead = null;
					ClipBundle.Source latest = ClipBundle.latest(all);
					if (covering.isEmpty() && lastMinutes > 0 && latest != null)
					{
						long end = latest.endsAt();
						from = end - lastMinutes * 60_000L;
						covering = ClipBundle.covering(all, from, end);
						file = latest.name + "-last" + lastMinutes + "min.zip";
						instead = "Nothing was recorded in the last " + lastMinutes + " minutes, so this is the last " + lastMinutes
							+ " minutes of the recording that ended at "
							+ java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(
								java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault()))
							+ ". ";
					}
					if (covering.isEmpty())
					{
						say(report, "Nothing recorded in that time.");
						return;
					}
					Filepath zip = ClipBundle.write(gson, root, covering, whole ? 0 : from, config.picturesInClips(), config.hideChat(), file,
						names(), includeText);
					done(report, zip, instead);
				}
				catch (IOException e)
				{
					log.warn("Black Box could not write the clip", e);
					say(report, "Could not write the clip: " + e.getMessage());
				}
			});
		}

		/** The bundled names table, loaded once; null when it cannot be read (the export then has no names.txt). */
		private IdNames names()
		{
			if (idNames == null)
			{
				try
				{
					idNames = IdNames.load();
				}
				catch (IOException e)
				{
					log.warn("Black Box could not load its names table", e);
				}
			}
			return idNames;
		}

		private void done(Report report, Filepath zip)
		{
			done(report, zip, null);
		}

		/** Reports a written zip; {@code before}, when given, says first what was exported instead. */
		private void done(Report report, Filepath zip, String before)
		{
			String where = zip.toString();
			boolean copied = RecorderSection.copy(where);
			say(report, (before == null ? "" : before) + RecorderSection.EXPORTED + where
				+ (copied ? " (path copied to the clipboard)" : ""));
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
		public void openExports(Report report)
		{
			try
			{
				Filepath clips = getPluginDirectory().join(ClipBundle.CLIPS_DIR);
				clips.createDirectories();
				LinkBrowser.open(clips.toString());
			}
			catch (IOException e)
			{
				say(report, "Could not find the export folder: " + e.getMessage());
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

	// ---- Every plugin's settings ----

	/** A plugin's config descriptor, or null when it has no configuration. */
	private ConfigDescriptor descriptorOf(Plugin plugin)
	{
		Config proxy = pluginManager.getPluginConfigProxy(plugin);
		return proxy == null ? null : configManager.getConfigDescriptor(proxy);
	}

	/** Client thread. */
	private void rebuildGroups()
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
	}

	/**
	 * Clears what the sidebar's "Reporting on" section stored (the plugin list under {@code watched} and named sets
	 * under {@code set.}): recordings cover every plugin and nothing is picked any more (owner, 2026-10-22).
	 */
	private void forgetReportingOn()
	{
		configManager.unsetConfiguration(BlackBoxConfig.GROUP, "watched");
		String prefix = BlackBoxConfig.GROUP + ".set.";
		for (String key : configManager.getConfigurationKeys(prefix))
		{
			if (key.startsWith(prefix))
			{
				configManager.unsetConfiguration(BlackBoxConfig.GROUP, key.substring(BlackBoxConfig.GROUP.length() + 1));
			}
		}
	}

	/** True when the key is a setting the player can see in the plugin's panel, not a hidden one. */
	private boolean shown(Plugin plugin, String key)
	{
		ConfigDescriptor descriptor = descriptorOf(plugin);
		if (descriptor == null)
		{
			return false;
		}
		for (ConfigItemDescriptor item : descriptor.getItems())
		{
			if (key.equals(item.key()))
			{
				return !item.getItem().hidden();
			}
		}
		return false;
	}

	/**
	 * A {@code plugins} line naming every installed plugin with on or off, then a {@code settings} line per plugin
	 * that has settings, with every current value (secrets, webhooks and tokens withheld, see
	 * {@link SettingsSnapshot}). Client thread.
	 */
	private void writePlugins(SessionLog target, long now)
	{
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
			// Format 9: switched on but not running (a start-up that failed), and which Hub build it is.
			one.addProperty("active", pluginManager.isPluginActive(plugin));
			PluginHubManifest.JarData jar = ExternalPluginManager.getJarData(plugin.getClass());
			if (jar != null)
			{
				one.addProperty("jarHash", jar.getJarHash());
			}
			PluginHubManifest.DisplayData display = ExternalPluginManager.getDisplayData(plugin.getClass());
			if (display != null)
			{
				one.addProperty("updatedAt", display.getLastUpdatedAt());
			}
			names.add(one);
		}
		installed.add("plugins", names);
		// Hub plugins the player installed that did not load at all (format 9).
		Set<String> loaded = new HashSet<>();
		for (Plugin plugin : all)
		{
			String internal = ExternalPluginManager.getInternalName(plugin.getClass());
			if (internal != null)
			{
				loaded.add(internal);
			}
		}
		JsonArray notLoaded = new JsonArray();
		for (String internal : externalPluginManager.getInstalledExternalPlugins())
		{
			if (!loaded.contains(internal))
			{
				notLoaded.add(internal);
			}
		}
		if (notLoaded.size() > 0)
		{
			installed.add("notLoaded", notLoaded);
		}
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
			// Which of them are typed text (format 11): an export leaves those out unless the player picks them.
			JsonArray textKeys = SettingsSnapshot.textKeys(descriptor.getItems());
			if (textKeys.size() > 0)
			{
				o.add("text", textKeys);
			}
			JsonObject other = otherValues(group, descriptor);
			if (other.size() > 0)
			{
				o.add("other", other);
			}
			target.add(o);
		}
		// RuneLite's own settings: overlay positions, notification defaults, the installed Hub plugins (format 9).
		JsonObject core = target.line("settings", client.getTickCount(), now);
		core.addProperty("plugin", "RuneLite");
		core.addProperty("group", RUNELITE_GROUP);
		core.add("values", new JsonObject());
		core.add("other", otherValues(RUNELITE_GROUP, null));
		target.add(core);
	}

	/** Whether a plugin's setting is typed text (a visible, non-secret String item): left out of exports by default. */
	private boolean isTextItem(Plugin plugin, String key)
	{
		ConfigDescriptor descriptor = descriptorOf(plugin);
		if (descriptor == null)
		{
			return false;
		}
		for (ConfigItemDescriptor item : descriptor.getItems())
		{
			if (key.equals(item.key()))
			{
				return item.getType() == String.class && !item.getItem().hidden() && !item.getItem().secret();
			}
		}
		return false;
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
			if (item.getItem().secret())
			{
				return SettingsSnapshot.SECRET;
			}
			if (item.getItem().hidden())
			{
				// Where a plugin keeps its own state (format 9): written like any value it keeps outside its settings.
				return scrub(SettingsSnapshot.other(key, newValue));
			}
			return item.getType() == String.class ? SettingsSnapshot.text(key, newValue) : newValue;
		}
		return scrub(SettingsSnapshot.other(key, newValue));
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

		/** RuneLite's own file chooser (the Hub's rule: never a JFileChooser), opening in the clips folder. */
		@Override
		public Filepath chooseClip(java.awt.Component parent)
		{
			Filepath.Chooser chooser = new Filepath.Chooser()
				.setIsOpen()
				.setAcceptsFiles()
				.setDialogTitle("Open a Black Box clip")
				.addExtensionFilter("Black Box clip (.zip) or events file (.jsonl)", "zip", "jsonl");
			try
			{
				Filepath clips = getPluginDirectory().join(ClipBundle.CLIPS_DIR);
				if (clips.isDirectory())
				{
					chooser.setCurrentDirectory(clips);
				}
			}
			catch (IOException e)
			{
				// No folder yet: the chooser opens where it likes.
			}
			List<Filepath> chosen = chooser.showDialog(parent);
			return chosen == null || chosen.isEmpty() ? null : chosen.get(0);
		}

		@Override
		public FpsSeries loadClip(Filepath file) throws IOException
		{
			return ClipBundle.readSeries(gson, file);
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

	/** Whether the welcome screen after login is on screen. Client thread only. */
	private boolean welcomeOnScreen()
	{
		Widget welcome = client.getWidget(InterfaceID.WelcomeScreen.UNIVERSE);
		return welcome != null && !welcome.isHidden();
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
		if (target == null || pinPause || welcomeShowing || (shotPending && now - shotRequestedAt < SHOT_GIVE_UP_MILLIS))
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
		List<Rectangle> hide = config.hideChat() ? hiddenAreas() : Collections.emptyList();
		int canvasWidth = client.getCanvasWidth();
		int maxWidth = config.shotWidth();
		drawManager.requestNextFrameListener(image ->
		{
			try
			{
				// The keypad or the welcome screen may have opened between the request and this frame, or the
				// player logged out (the recording then ended and the frame may show the login screen): such a
				// frame is never saved.
				boolean keep = !pinPause && !welcomeShowing && session == target
					&& Privacy.mayKeepFrame(client.getGameState());
				BufferedImage small = keep ? shrink(image, maxWidth, hide, canvasWidth) : null;
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
		if (pinPause || welcomeShowing)
		{
			return;
		}
		List<Rectangle> hide = config.hideChat() ? hiddenAreas() : Collections.emptyList();
		int canvasWidth = client.getCanvasWidth();
		int maxWidth = config.shotWidth();
		drawManager.requestNextFrameListener(image ->
		{
			boolean keep = !pinPause && !welcomeShowing && Privacy.mayKeepFrame(client.getGameState());
			BufferedImage small = keep ? shrink(image, maxWidth, hide, canvasWidth) : null;
			if (small != null)
			{
				clientThread.invoke(() -> then.accept(small));
			}
		});
	}

	/**
	 * What Hide the chat box blacks out, on the canvas: the chat box, and each line of split private chat drawn
	 * above it (private messages and friends logging in; owner, 2026-10-24: "do it but only if the hide the chat
	 * box is enabled"). Only lines with text, so an empty split-chat layer covers nothing. Client thread only.
	 */
	private List<Rectangle> hiddenAreas()
	{
		List<Rectangle> areas = new ArrayList<>();
		Widget chat = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
		if (chat != null && !chat.isHidden())
		{
			areas.add(chat.getBounds());
		}
		Widget split = client.getWidget(InterfaceID.PmChat.CONTAINER);
		if (split != null && !split.isHidden())
		{
			addTextLines(split, areas);
		}
		return areas;
	}

	/** Game pixels added around each split-chat line, so no part of a letter shows. */
	private static final int LINE_PAD = 4;

	/** The bounds of every showing widget under this one that holds text, a little larger. */
	private static void addTextLines(Widget parent, List<Rectangle> areas)
	{
		for (Widget[] children : new Widget[][]{parent.getStaticChildren(), parent.getDynamicChildren(),
			parent.getNestedChildren()})
		{
			if (children == null)
			{
				continue;
			}
			for (Widget w : children)
			{
				if (w == null || w.isHidden())
				{
					continue;
				}
				String text = w.getText();
				if (text != null && !text.isEmpty())
				{
					// Letters hang past a line's bounds (descenders showed below it in a live picture, 2026-10-24).
					Rectangle line = w.getBounds();
					line.grow(LINE_PAD, LINE_PAD);
					areas.add(line);
				}
				addTextLines(w, areas);
			}
		}
	}

	/** The frame at no more than {@code maxWidth} across, with the given areas painted black. */
	static BufferedImage shrink(Image image, int maxWidth, List<Rectangle> hide, int canvasWidth)
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
			if (canvasWidth > 0)
			{
				// Widget bounds are in game pixels; the frame may be stretched, then it is shrunk here.
				double k = (width / (double) canvasWidth) * scale;
				g.setColor(Color.BLACK);
				for (Rectangle r : hide)
				{
					g.fillRect((int) Math.floor(r.x * k), (int) Math.floor(r.y * k),
						(int) Math.ceil(r.width * k) + 1, (int) Math.ceil(r.height * k) + 1);
				}
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

	// ---- The Debug page (format 10) ----

	/** Runs the ticked tests while Debug is on, none while off, and says so in the recording. Client thread. */
	private void applyDebug()
	{
		probes.run(debugOn ? DebugTest.parse(config.debugTests()) : EnumSet.noneOf(DebugTest.class));
		if (session != null)
		{
			writeDebug(session, System.currentTimeMillis());
		}
	}

	/** Whether Debug is on and which tests are ticked, so a missing line reads as "test off", not "nothing happened". */
	private void writeDebug(SessionLog target, long now)
	{
		JsonObject o = target.line("debug", client.getTickCount(), now);
		o.addProperty("on", debugOn);
		JsonArray tests = new JsonArray();
		for (DebugTest test : DebugTest.parse(config.debugTests()))
		{
			tests.add(test.key);
		}
		o.add("tests", tests);
		target.add(o);
	}

	// ---- Format 9: the cheap batch from the Hub sweep (research/hub-hard-bugs-2026-10-21.md) ----

	/** RuneLite's own config group. */
	private static final String RUNELITE_GROUP = "runelite";

	/**
	 * A group's values outside its visible settings: hidden items and keys no item declares, where plugins keep
	 * their own state. Secret items stay out; the rest goes through {@link SettingsSnapshot#other}, and a key
	 * holding the RuneScape profile id has the id hashed. Client thread.
	 */
	private JsonObject otherValues(String group, ConfigDescriptor descriptor)
	{
		Set<String> skip = new HashSet<>();
		if (descriptor != null)
		{
			for (ConfigItemDescriptor item : descriptor.getItems())
			{
				if (!item.getItem().hidden() || item.getItem().secret())
				{
					skip.add(item.key());
				}
			}
		}
		JsonObject other = new JsonObject();
		for (String whole : configManager.getConfigurationKeys(group + "."))
		{
			String key = whole.substring(group.length() + 1);
			if (skip.contains(key))
			{
				continue;
			}
			String stored = configManager.getConfiguration(group, key);
			if (stored != null)
			{
				other.addProperty(keyFor(descriptor, group, key), scrub(SettingsSnapshot.other(key, stored)));
			}
		}
		return other;
	}

	/**
	 * A config key as a recording may name it. A key the plugin declares, and any key of RuneLite's own group (overlay
	 * and plugin names), as it is; a key no item declares is the plugin's own and can hold a name (Friend Notes keeps
	 * {@code note_<friend>}), so it is written as its length and hash. A RuneScape profile id in a key is hashed.
	 */
	private static String keyFor(ConfigDescriptor descriptor, String group, String key)
	{
		if (RUNELITE_GROUP.equals(group) || BlackBoxConfig.GROUP.equals(group))
		{
			return Hashes.profileKey(key);
		}
		if (descriptor != null)
		{
			for (ConfigItemDescriptor item : descriptor.getItems())
			{
				if (key.equals(item.key()))
				{
					return Hashes.profileKey(key);
				}
			}
		}
		return SettingsSnapshot.hashed(key);
	}

	/** The RuneScape profile in use, hashed (it is derived from the account), and why the line was written. */
	private void writeProfile(SessionLog target, long now, String why)
	{
		JsonObject o = target.line("profile", client.getTickCount(), now);
		o.addProperty("rsProfile", Hashes.short12(configManager.getRSProfileKey()));
		if (why != null)
		{
			o.addProperty("why", why);
		}
		target.add(o);
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		long now = System.currentTimeMillis();
		clientThread.invoke(() ->
		{
			rebuildGroups();
			if (session != null)
			{
				// Every setting may have changed with the profile: a fresh snapshot after the line.
				writeProfile(session, now, "settings profile");
				writePlugins(session, now);
			}
			// The ticked tests are a setting too: the new profile's run from now on.
			if (debugOn)
			{
				applyDebug();
			}
		});
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.debugChanged();
			}
		});
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		long now = System.currentTimeMillis();
		clientThread.invoke(() ->
		{
			if (session != null)
			{
				writeProfile(session, now, "account profile");
			}
		});
	}

	/** The eight right-click options on players, with their priority and menu type. Client thread. */
	private void writePlayerOptions(SessionLog target, long now)
	{
		String[] options = client.getPlayerOptions();
		boolean[] priorities = client.getPlayerOptionsPriorities();
		int[] types = client.getPlayerMenuTypes();
		if (options == null)
		{
			return;
		}
		JsonArray list = new JsonArray();
		for (int i = 0; i < options.length; i++)
		{
			JsonArray one = new JsonArray();
			one.add(options[i] == null ? "" : scrub(options[i]));
			one.add(priorities != null && i < priorities.length && priorities[i]);
			one.add(types != null && i < types.length ? types[i] : -1);
			list.add(one);
		}
		JsonObject o = target.line("playerOptions", client.getTickCount(), now);
		o.add("options", list);
		target.add(o);
	}

	@Subscribe
	public void onPlayerMenuOptionsChanged(PlayerMenuOptionsChanged event)
	{
		if (session != null)
		{
			writePlayerOptions(session, System.currentTimeMillis());
		}
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		frameClock.frame(System.nanoTime());
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		menuAdds++;
	}

	/** How long work posted now waits on the Swing thread and on the client thread; read on the next tick. */
	private void probe()
	{
		long posted = System.nanoTime();
		if (!edtWaiting)
		{
			// One probe at a time on the Swing thread, so a freeze shows as one growing wait, not a queue of them.
			edtWaiting = true;
			edtPostedAt = posted;
			SwingUtilities.invokeLater(() ->
			{
				edtWaitMs = msSince(posted);
				edtWaiting = false;
			});
		}
		clientThread.invokeLater(() ->
		{
			clientWaitMs = msSince(posted);
		});
	}

	private static double msSince(long nanos)
	{
		return Math.round((System.nanoTime() - nanos) / 100_000.0) / 10.0;
	}

	/**
	 * The menu as the game opened it, before any other plugin. Registered in {@link #startUp} as a second subscriber
	 * at {@link #FIRST}: the event bus only takes {@code @Subscribe} methods named exactly {@code onMenuOpened}.
	 */
	private void menuOpenedFirst(MenuOpened event)
	{
		menuBefore = session == null || pinPause ? null : menu(event.getMenuEntries());
	}

	/**
	 * The menu as the player saw it, after every other plugin's handler (LAST), and the menu the game opened with
	 * when a plugin changed it. Never while the bank PIN keypad shows. Shift is read, not listened for.
	 */
	@Subscribe(priority = LAST)
	public void onMenuOpened(MenuOpened event)
	{
		if (session == null || pinPause)
		{
			menuBefore = null;
			return;
		}
		JsonArray shown = menu(client.getMenu().getMenuEntries());
		JsonObject o = line("menu");
		o.add("entries", shown);
		o.addProperty("shift", client.isKeyPressed(KeyCode.KC_SHIFT));
		if (menuBefore != null && !menuBefore.equals(shown))
		{
			o.add("before", menuBefore);
		}
		menuBefore = null;
		session.add(o);
	}

	/** Menu entries in the order the client holds them; a target that may name a player is left empty. */
	private JsonArray menu(MenuEntry[] entries)
	{
		JsonArray list = new JsonArray();
		if (entries == null)
		{
			return list;
		}
		for (MenuEntry entry : entries)
		{
			JsonObject one = new JsonObject();
			one.addProperty("option", entry.getOption() == null ? "" : scrub(entry.getOption()));
			boolean keep = entry.getPlayer() == null
				&& (entry.getNpc() != null || Privacy.keepsTarget(entry.getType(), entry.getItemId()));
			one.addProperty("target", entry.getTarget() == null || !keep ? "" : scrub(Text.removeTags(entry.getTarget())));
			one.addProperty("type", entry.getType() == null ? "" : entry.getType().name());
			one.addProperty("id", entry.getIdentifier());
			one.addProperty("itemId", entry.getItemId());
			one.addProperty("param0", entry.getParam0());
			one.addProperty("param1", entry.getParam1());
			if (entry.isDeprioritized())
			{
				one.addProperty("deprioritized", true);
			}
			Menu sub = entry.getSubMenu();
			if (sub != null && sub.getMenuEntries() != null)
			{
				one.addProperty("submenu", sub.getMenuEntries().length);
			}
			list.add(one);
		}
		return list;
	}

	/** The hint arrow, written when it changes. The point is raw: after a clear the client leaves (0,0,0). */
	private void hintArrow()
	{
		JsonObject fields = new JsonObject();
		fields.addProperty("has", client.hasHintArrow());
		fields.addProperty("type", client.getHintArrowType());
		NPC npc = client.getHintArrowNpc();
		if (npc != null)
		{
			fields.addProperty("npc", npc.getId());
			fields.addProperty("index", npc.getIndex());
		}
		point(fields, "", client.getHintArrowPoint());
		if (client.getHintArrowPlayer() != null)
		{
			// That it points at a player, never who.
			fields.addProperty("player", true);
		}
		String seen = fields.toString();
		if (!seen.equals(hintSeen))
		{
			hintSeen = seen;
			session.add(with(line("hint"), fields));
		}
	}

	/** Every registered overlay in render order, written when the set or its placement changes. */
	private void overlays()
	{
		JsonArray list = new JsonArray();
		StringBuilder seen = new StringBuilder();
		// OverlayManager has no public list; anyMatch visits every overlay when the test never matches.
		overlayManager.anyMatch(overlay ->
		{
			JsonObject one = new JsonObject();
			one.addProperty("name", overlay.getName());
			one.addProperty("plugin", overlay.getPlugin() == null ? null : overlay.getPlugin().getName());
			one.addProperty("layer", overlay.getLayer() == null ? null : overlay.getLayer().name());
			one.addProperty("position", overlay.getPosition() == null ? null : overlay.getPosition().name());
			one.addProperty("preferred", overlay.getPreferredPosition() == null ? null : overlay.getPreferredPosition().name());
			if (overlay.getPreferredLocation() != null)
			{
				one.addProperty("at", overlay.getPreferredLocation().x + "," + overlay.getPreferredLocation().y);
			}
			one.addProperty("priority", overlay.getPriority());
			one.addProperty("movable", overlay.isMovable());
			seen.append(one).append('\n');
			if (overlay.getBounds() != null)
			{
				Rectangle b = overlay.getBounds();
				one.addProperty("bounds", b.x + "," + b.y + "," + b.width + "," + b.height);
			}
			list.add(one);
			return false;
		});
		String now = seen.toString();
		if (!now.equals(overlaysSeen))
		{
			overlaysSeen = now;
			JsonObject o = line("overlays");
			o.add("overlays", list);
			session.add(o);
		}
	}

	/** The infoboxes on screen, written when one comes or goes, with their text at that moment. */
	private void infoBoxes()
	{
		List<InfoBox> boxes = infoBoxManager.getInfoBoxes();
		StringBuilder seen = new StringBuilder();
		for (InfoBox box : boxes)
		{
			seen.append(box.getName()).append('\n');
		}
		String now = seen.toString();
		if (!now.equals(infoBoxesSeen))
		{
			infoBoxesSeen = now;
			JsonArray list = new JsonArray();
			for (InfoBox box : boxes)
			{
				JsonObject one = new JsonObject();
				one.addProperty("name", box.getName());
				String text;
				try
				{
					// Each plugin writes its own getText; one that throws must not stop the rest of the tick.
					text = scrub(box.getText());
				}
				catch (RuntimeException e)
				{
					text = "<error>";
				}
				one.addProperty("text", text);
				list.add(one);
			}
			JsonObject o = line("infoboxes");
			o.add("infoboxes", list);
			session.add(o);
		}
	}

	/** Once per recording, every varp that is not 0: the game settings that never change while it runs. */
	private void varps()
	{
		if (varpsDone || pinPause)
		{
			return;
		}
		int[] values = client.getVarps();
		if (values == null)
		{
			return;
		}
		JsonObject set = new JsonObject();
		for (int i = 0; i < values.length; i++)
		{
			if (values[i] != 0)
			{
				set.addProperty(String.valueOf(i), values[i]);
			}
		}
		JsonObject o = line("varps");
		o.add("values", set);
		session.add(o);
		varpsDone = true;
	}

	@Subscribe
	public void onWorldViewLoaded(WorldViewLoaded event)
	{
		WorldView view = event.getWorldView();
		if (session == null || view == null)
		{
			return;
		}
		JsonObject o = line("scene");
		o.addProperty("wv", view.getId());
		o.addProperty("top", view.isTopLevel());
		o.addProperty("baseX", view.getBaseX());
		o.addProperty("baseY", view.getBaseY());
		o.addProperty("plane", view.getPlane());
		o.addProperty("instance", view.isInstance());
		int[][][] chunks = view.isInstance() ? view.getInstanceTemplateChunks() : null;
		if (chunks != null)
		{
			// Only the chunks in use: [plane, x, y, the template chunk as the client packs it].
			JsonArray used = new JsonArray();
			for (int z = 0; z < chunks.length; z++)
			{
				for (int x = 0; x < chunks[z].length; x++)
				{
					for (int y = 0; y < chunks[z][x].length; y++)
					{
						if (chunks[z][x][y] != -1)
						{
							JsonArray one = new JsonArray();
							one.add(z);
							one.add(x);
							one.add(y);
							one.add(chunks[z][x][y]);
							used.add(one);
						}
					}
				}
			}
			o.add("chunks", used);
		}
		session.add(o);
	}

	@Subscribe
	public void onFocusChanged(FocusChanged event)
	{
		focused = event.isFocused();
		// Possibly the Swing thread: read the session once.
		SessionLog now = session;
		if (now != null)
		{
			JsonObject o = now.line("window", client.getTickCount(), System.currentTimeMillis());
			o.addProperty("focus", event.isFocused());
			now.add(o);
		}
	}

	@Subscribe
	public void onCanvasSizeChanged(CanvasSizeChanged event)
	{
		if (session != null)
		{
			JsonObject o = line("window");
			o.addProperty("canvas", client.getCanvasWidth() + "x" + client.getCanvasHeight());
			session.add(o);
		}
	}

	@Subscribe
	public void onResizeableChanged(ResizeableChanged event)
	{
		if (session != null)
		{
			JsonObject o = line("window");
			o.addProperty("resizable", event.isResized());
			session.add(o);
		}
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		// In a party or not; never its id or passphrase.
		boolean in = event.getPartyId() != null;
		clientThread.invoke(() ->
		{
			partyMembers.clear();
			if (session != null)
			{
				JsonObject o = line("party");
				o.addProperty("in", in);
				session.add(o);
			}
		});
	}

	@Subscribe
	public void onUserJoin(UserJoin event)
	{
		partyMember("partyJoin", event.getMemberId());
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		partyMember("partyLeave", event.getMemberId());
	}

	/** A party member as a number that holds for this recording, never their id or name. */
	private void partyMember(String kind, long memberId)
	{
		clientThread.invoke(() ->
		{
			if (session == null)
			{
				return;
			}
			Integer number = partyMembers.get(memberId);
			if (number == null)
			{
				number = partyMembers.size() + 1;
				partyMembers.put(memberId, number);
			}
			JsonObject o = line(kind);
			o.addProperty("member", number);
			o.addProperty("size", partyService.getMembers().size());
			session.add(o);
		});
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		groundItem("itemAdd", event.getItem(), event.getTile());
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		groundItem("itemGone", event.getItem(), event.getTile());
	}

	/** An item on the ground and whose it is, as a kind: the player's, another's, the group's or nobody's. */
	private void groundItem(String kind, TileItem item, Tile tile)
	{
		if (session == null || item == null)
		{
			return;
		}
		JsonObject o = line(kind);
		o.addProperty("id", item.getId());
		o.addProperty("qty", item.getQuantity());
		o.addProperty("own", item.getOwnership());
		point(o, "", tile == null ? null : tile.getWorldLocation());
		session.add(o);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (session == null)
		{
			return;
		}
		JsonObject o = line("loot");
		o.addProperty("from", "lootTracker");
		String type = event.getType() == null ? "" : event.getType().name();
		o.addProperty("type", type);
		// Loot from a player names them; the type says it was one.
		o.addProperty("name", "PLAYER".equals(type) ? "" : scrub(event.getName()));
		o.addProperty("combat", event.getCombatLevel());
		o.addProperty("amount", event.getAmount());
		o.add("items", stacks(event.getItems()));
		session.add(o);
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (session == null || event.getNpc() == null)
		{
			return;
		}
		JsonObject o = line("loot");
		o.addProperty("from", "npc");
		o.addProperty("npc", event.getNpc().getId());
		o.addProperty("name", scrub(event.getNpc().getName()));
		o.add("items", stacks(event.getItems()));
		session.add(o);
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot event)
	{
		if (session == null || event.getComposition() == null)
		{
			return;
		}
		JsonObject o = line("loot");
		o.addProperty("from", "server");
		o.addProperty("npc", event.getComposition().getId());
		o.addProperty("name", scrub(event.getComposition().getName()));
		o.add("items", stacks(event.getItems()));
		session.add(o);
	}

	@Subscribe
	public void onPlayerLootReceived(PlayerLootReceived event)
	{
		if (session == null)
		{
			return;
		}
		// The items only: the player is never named.
		JsonObject o = line("loot");
		o.addProperty("from", "player");
		o.add("items", stacks(event.getItems()));
		session.add(o);
	}

	private static JsonArray stacks(Collection<ItemStack> items)
	{
		JsonArray list = new JsonArray();
		if (items != null)
		{
			for (ItemStack stack : items)
			{
				JsonArray one = new JsonArray();
				one.add(stack.getId());
				one.add(stack.getQuantity());
				list.add(one);
			}
		}
		return list;
	}

	/** Copies fields onto a line. */
	private static JsonObject with(JsonObject line, JsonObject fields)
	{
		fields.entrySet().forEach(e -> line.add(e.getKey(), e.getValue()));
		return line;
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
