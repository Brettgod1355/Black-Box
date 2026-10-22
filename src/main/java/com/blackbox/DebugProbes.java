/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.blackbox;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Nameable;
import net.runelite.api.NameableContainer;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.Projectile;
import net.runelite.api.WorldView;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.Text;

/**
 * The Debug page's extra tests (format 10). Each ticked test is its own small subscriber, registered on the event
 * bus only while the Debug switch is on, so a test that is off costs nothing at all, not even an event call. They
 * read only, like the rest of Black Box, and write nothing while the bank PIN keypad shows or nothing is recording
 * ({@link Sink#writing}). Client thread throughout, except where a method says otherwise.
 */
class DebugProbes
{
	/** Where the probes write: the recording, through the plugin's own privacy rules. */
	interface Sink
	{
		/** Whether lines may be written now: a recording is running and the bank PIN keypad is not showing. */
		boolean writing();

		/** A new line of this kind, stamped with the tick and time. */
		JsonObject line(String kind);

		void add(JsonObject line);

		/** The player's own name replaced, as everywhere else in a recording. */
		String scrub(String text);
	}

	/** A probe that also does something once per game tick, or starts over with a new recording. */
	interface Probe
	{
		default void tick()
		{
		}

		default void reset()
		{
		}
	}

	private final Client client;
	private final EventBus eventBus;
	private final DrawManager drawManager;
	private final Sink sink;
	private final Map<DebugTest, Probe> running = new EnumMap<>(DebugTest.class);

	DebugProbes(Client client, EventBus eventBus, DrawManager drawManager, Sink sink)
	{
		this.client = client;
		this.eventBus = eventBus;
		this.drawManager = drawManager;
		this.sink = sink;
	}

	/** Runs exactly these tests: starts the ones not yet running, stops the rest. */
	void run(Set<DebugTest> tests)
	{
		for (DebugTest test : DebugTest.values())
		{
			Probe probe = running.get(test);
			if (tests.contains(test) && probe == null)
			{
				probe = create(test);
				eventBus.register(probe);
				if (probe instanceof Timing)
				{
					((Timing) probe).subscribe();
				}
				running.put(test, probe);
			}
			else if (!tests.contains(test) && probe != null)
			{
				eventBus.unregister(probe);
				if (probe instanceof Timing)
				{
					((Timing) probe).unsubscribe();
				}
				running.remove(test);
			}
		}
	}

	void stopAll()
	{
		run(EnumSet.noneOf(DebugTest.class));
	}

	/** A new recording: every probe writes its state again from the start. */
	void reset()
	{
		running.values().forEach(Probe::reset);
	}

	/** Once per game tick while recording, after the tick line. */
	void tick()
	{
		running.values().forEach(Probe::tick);
	}

	private Probe create(DebugTest test)
	{
		switch (test)
		{
			case PROJECTILES:
				return new Projectiles();
			case NPC_DETAILS:
				return new NpcDetails();
			case WIDGETS:
				return new Widgets();
			case SCRIPTS:
				return new Scripts();
			case VARCS:
				return new Varcs();
			case MOUSE:
				return new Mouse();
			case TIMING:
				return new Timing();
			default:
				throw new IllegalArgumentException(test.name());
		}
	}

	private boolean isOtherPlayer(Actor actor)
	{
		return actor instanceof Player && actor != client.getLocalPlayer();
	}

	/**
	 * Who an actor is, as the rest of a recording says it: {@code <you>}, an NPC's id and index, or {@code player}
	 * for anyone else (never a name).
	 */
	private void actor(JsonObject o, String prefix, Actor actor)
	{
		if (actor == null)
		{
			return;
		}
		if (actor == client.getLocalPlayer())
		{
			o.addProperty(prefix, Privacy.YOU);
		}
		else if (actor instanceof NPC)
		{
			o.addProperty(prefix + "Npc", ((NPC) actor).getId());
			o.addProperty(prefix + "Index", ((NPC) actor).getIndex());
		}
		else
		{
			o.addProperty(prefix, "player");
		}
	}

	private static void point(JsonObject o, String prefix, WorldPoint point)
	{
		if (point != null)
		{
			o.addProperty(prefix + "X", point.getX());
			o.addProperty(prefix + "Y", point.getY());
			o.addProperty(prefix + "Plane", point.getPlane());
		}
	}

	/** Each projectile once, when the game first places it; none to or from another player. */
	class Projectiles implements Probe
	{
		private final Set<Projectile> seen = Collections.newSetFromMap(new IdentityHashMap<>());

		@Subscribe
		public void onProjectileMoved(ProjectileMoved event)
		{
			Projectile p = event.getProjectile();
			if (p == null || !sink.writing() || !seen.add(p) || isOtherPlayer(p.getSourceActor())
				|| isOtherPlayer(p.getTargetActor()))
			{
				return;
			}
			JsonObject o = sink.line("projectile");
			o.addProperty("id", p.getId());
			actor(o, "from", p.getSourceActor());
			actor(o, "to", p.getTargetActor());
			point(o, "from", p.getSourcePoint());
			point(o, "to", p.getTargetPoint());
			o.addProperty("cycle", client.getGameCycle());
			o.addProperty("startCycle", p.getStartCycle());
			o.addProperty("endCycle", p.getEndCycle());
			o.addProperty("startHeight", p.getStartHeight());
			o.addProperty("endHeight", p.getEndHeight());
			o.addProperty("slope", p.getSlope());
			sink.add(o);
		}

		@Override
		public void tick()
		{
			seen.removeIf(p -> p.getRemainingCycles() <= 0);
		}

		@Override
		public void reset()
		{
			seen.clear();
		}
	}

	/** NPC make-up when an NPC appears or turns into something else, health bars and fights each tick. */
	class NpcDetails implements Probe
	{
		private final Map<Integer, String> health = new HashMap<>();
		private boolean described;

		@Subscribe
		public void onNpcSpawned(NpcSpawned event)
		{
			describe(event.getNpc());
		}

		@Subscribe
		public void onNpcChanged(NpcChanged event)
		{
			describe(event.getNpc());
		}

		@Subscribe
		public void onInteractingChanged(InteractingChanged event)
		{
			if (!(event.getSource() instanceof NPC) || !sink.writing())
			{
				return;
			}
			NPC npc = (NPC) event.getSource();
			JsonObject o = sink.line("npcFight");
			o.addProperty("npc", npc.getId());
			o.addProperty("index", npc.getIndex());
			if (event.getTarget() == null)
			{
				o.addProperty("target", "none");
			}
			else
			{
				actor(o, "target", event.getTarget());
			}
			sink.add(o);
		}

		private void describe(NPC npc)
		{
			if (npc == null || !sink.writing())
			{
				return;
			}
			JsonObject o = sink.line("npcInfo");
			o.addProperty("npc", npc.getId());
			o.addProperty("index", npc.getIndex());
			NPCComposition base = npc.getComposition();
			NPCComposition now = npc.getTransformedComposition();
			if (now != null && base != null && now.getId() != base.getId())
			{
				o.addProperty("becomes", now.getId());
			}
			NPCComposition shown = now != null ? now : base;
			if (shown != null)
			{
				o.addProperty("name", shown.getName());
				o.addProperty("combat", shown.getCombatLevel());
				o.addProperty("size", shown.getSize());
				JsonArray actions = new JsonArray();
				if (shown.getActions() != null)
				{
					for (String action : shown.getActions())
					{
						actions.add(action == null ? "" : Text.removeTags(action));
					}
				}
				o.add("actions", actions);
				o.addProperty("interactible", shown.isInteractible());
				o.addProperty("minimap", shown.isMinimapVisible());
				if (shown.isFollower())
				{
					o.addProperty("follower", true);
				}
			}
			sink.add(o);
		}

		@Override
		public void tick()
		{
			WorldView top = client.getTopLevelWorldView();
			if (top == null || !sink.writing())
			{
				// NPCs that appear meanwhile (the bank PIN keypad, not recording) are described once writing resumes.
				described = false;
				return;
			}
			if (!described)
			{
				// NPCs already in view when the test started (or the recording began).
				described = true;
				for (NPC npc : top.npcs())
				{
					describe(npc);
				}
			}
			JsonArray changed = new JsonArray();
			Set<Integer> present = new HashSet<>();
			for (NPC npc : top.npcs())
			{
				present.add(npc.getIndex());
				String now = npc.getHealthRatio() + "/" + npc.getHealthScale();
				String before = health.put(npc.getIndex(), now);
				// A bar that appears, changes or goes away; never "no bar" to "no bar".
				boolean hadBar = before != null && !before.endsWith("/-1") && !before.endsWith("/0");
				if (!now.equals(before) && (npc.getHealthScale() > 0 || hadBar))
				{
					changed.add(new JsonArray());
					JsonArray one = changed.get(changed.size() - 1).getAsJsonArray();
					one.add(npc.getIndex());
					one.add(npc.getHealthRatio());
					one.add(npc.getHealthScale());
				}
			}
			health.keySet().retainAll(present);
			if (changed.size() > 0)
			{
				JsonObject o = sink.line("npcHealth");
				o.add("at", changed);
				sink.add(o);
			}
		}

		@Override
		public void reset()
		{
			health.clear();
			described = false;
		}
	}

	/**
	 * Interfaces about other players: chat, private messages, friends, ignores, chat channels and clans, group
	 * ironman, trade, the duel arena, reports, hiscores and display names. Left out whole (an interface nested inside
	 * one, such as a dialog in the chat box, is still read on its own terms); the bank PIN keypad too.
	 */
	static final Set<Integer> SOCIAL_INTERFACES = new HashSet<>(Arrays.asList(
		InterfaceID.CHATBOX, InterfaceID.PM_CHAT, InterfaceID.CHAT_BOTH, InterfaceID.CHATCHANNEL_CURRENT, InterfaceID.CHATCHANNEL_SETUP, InterfaceID.SIDE_CHANNELS,
		InterfaceID.SIDE_CHANNELS_LARGE, InterfaceID.FRIENDS, InterfaceID.IGNORE, InterfaceID.TRADEMAIN,
		InterfaceID.TRADECONFIRM, InterfaceID.TRADESIDE, InterfaceID.BUGREPORT, InterfaceID.REPORTABUSE,
		InterfaceID.HISCORES, InterfaceID.DISPLAYNAME, InterfaceID.TUTORIAL_DISPLAYNAME, InterfaceID.GROUPING,
		InterfaceID.BARBASSAULT_OVER_RECRUIT_PLAYER_NAMES, InterfaceID.DEADMAN_TOURNAMENT_VIEWER,
		InterfaceID.BR_TOURNAMENT_VIEWER, InterfaceID.BANKPIN_KEYPAD,
		InterfaceID.CLANS_BANNED, InterfaceID.CLANS_INFO, InterfaceID.CLANS_INTERESTS, InterfaceID.CLANS_HALL,
		InterfaceID.CLANS_MEMBERS, InterfaceID.CLANS_OUTFIT, InterfaceID.CLANS_RANKTITLES, InterfaceID.CLANS_STORAGE_MAIN,
		InterfaceID.CLANS_STORAGE_SIDE, InterfaceID.CLANS_APPLICANTS, InterfaceID.CLANS_BOARD, InterfaceID.CLANS_SIDEPANEL,
		InterfaceID.CLANS_GUEST_SIDEPANEL, InterfaceID.CLANS_EVENTS, InterfaceID.CLANS_EVENTS_CREATE,
		InterfaceID.CLANS_CREATION_SIDEPANEL, InterfaceID.CLANS_PERMISSIONS,
		InterfaceID.GIM_RAFFLE, InterfaceID.GIM_LIMITED_SIDEPANEL, InterfaceID.GIM_LEAVING, InterfaceID.GIM_CREATION_SIDEPANEL,
		InterfaceID.GIM_SIDEPANEL, InterfaceID.GIM_SETTINGS, InterfaceID.GIM_SHARED_BANK_UNLOCKS, InterfaceID.GIM_OPTIONS,
		InterfaceID.PVP_ARENA_LEGACYDUEL_OPTIONS, InterfaceID.PVP_ARENA_LEGACYDUEL_CONFIRM, InterfaceID.PVP_ARENA_UNRANKEDDUEL,
		InterfaceID.PVP_ARENA_APPLICANTS, InterfaceID.PVP_ARENA_HUD, InterfaceID.PVP_ARENA_SIDEPANEL,
		InterfaceID.PVP_ARENA_SPECTATOR, InterfaceID.PVP_ARENA_BOARD, InterfaceID.PVP_ARENA_SCOREBOARD,
		InterfaceID.PVP_ARENA_1V1_INFO, InterfaceID.PVP_ARENA_STAGINGAREA_HUD));

	/**
	 * Interfaces whose text is kept: the player's own screens, which never name anyone else (side panels, bank,
	 * equipment, shops, the Grand Exchange offers, skill menus, dialogs, the collection log, Sailing's own panels).
	 * Everywhere else text is {@code <withheld>}: raid and minigame lobbies, party boards and scoreboards list
	 * strangers, and new content brings new ones, so a list of what to hide could never be complete. Add an
	 * interface here when a bug needs its text and it shows no other player.
	 */
	static final Set<Integer> TEXT_INTERFACES = new HashSet<>(Arrays.asList(
		InterfaceID.TOPLEVEL, InterfaceID.TOPLEVEL_OSRS_STRETCH, InterfaceID.TOPLEVEL_PRE_EOC, InterfaceID.TOPLEVEL_DISPLAY,
		InterfaceID.TOPLEVEL_OSM, InterfaceID.ORBS, InterfaceID.ORBS_NOMAP, InterfaceID.ORBS_OSM, InterfaceID.ORBS_OSM_NOMAP,
		InterfaceID.XP_DROPS, InterfaceID.BUFF_BAR, InterfaceID.HPBAR_HUD, InterfaceID.STAT_BOOSTS_HUD,
		InterfaceID.INVENTORY, InterfaceID.INVENTORY_NOOPS, InterfaceID.WORNITEMS, InterfaceID.STATS, InterfaceID.QUESTLIST,
		InterfaceID.PRAYERBOOK, InterfaceID.MAGIC_SPELLBOOK, InterfaceID.COMBAT_INTERFACE, InterfaceID.SETTINGS_SIDE,
		InterfaceID.MUSIC, InterfaceID.EMOTE, InterfaceID.BANKMAIN, InterfaceID.BANKSIDE, InterfaceID.EQUIPMENT,
		InterfaceID.EQUIPMENT_SIDE, InterfaceID.SETTINGS, InterfaceID.QUESTJOURNAL, InterfaceID.QUESTJOURNAL_OVERVIEW,
		InterfaceID.SKILL_GUIDE, InterfaceID.SKILL_GUIDE_V2, InterfaceID.GE_OFFERS, InterfaceID.GE_OFFERS_SIDE,
		InterfaceID.GE_HISTORY, InterfaceID.SHOPMAIN, InterfaceID.SHOPSIDE, InterfaceID.SKILLMULTI, InterfaceID.SMITHING,
		InterfaceID.SEED_VAULT, InterfaceID.SEED_VAULT_DEPOSIT, InterfaceID.COLLECTION, InterfaceID.COLLECTION_OVERVIEW,
		InterfaceID.CHAT_LEFT, InterfaceID.CHAT_RIGHT, InterfaceID.CHATMENU, InterfaceID.MESSAGEBOX,
		InterfaceID.MESSAGEBOX_TITLED, InterfaceID.OBJECTBOX, InterfaceID.OBJECTBOX_DOUBLE, InterfaceID.LEVELUP_DISPLAY,
		InterfaceID.MENU, InterfaceID.MENU_NEW,
		InterfaceID.SAILING_MENU, InterfaceID.SAILING_INTRO_HUD, InterfaceID.SAILING_BT_HUD, InterfaceID.SAILING_BT_SELECTION,
		InterfaceID.SAILING_BOAT_SELECTION, InterfaceID.SAILING_LOG, InterfaceID.SAILING_SIDEPANEL, InterfaceID.SAILING_CREW,
		InterfaceID.SAILING_CUSTOMISATION, InterfaceID.SAILING_BOAT_CARGOHOLD, InterfaceID.SAILING_BOAT_CARGOHOLD_SIDE));

	/** Text that is there but not kept, outside {@link #TEXT_INTERFACES}. */
	static final String TEXT_WITHHELD = Privacy.WITHHELD;

	/** Deeper than any real interface; a guard against a widget that lists itself. */
	private static final int MAX_DEPTH = 40;

	/**
	 * What the open interfaces show, written as the parts that changed since the last tick. Only parts with
	 * something in them are kept (text, an item, a picture, or hidden), social interfaces are skipped whole, text is
	 * kept only in {@link #TEXT_INTERFACES}, and there it has the player's name and every other name the client knows
	 * (friends, ignores, channel and clan members, players in view and aboard boats) replaced.
	 */
	class Widgets implements Probe
	{
		private Map<String, String> last = new HashMap<>();
		private String namesKey = "";
		private Pattern names;
		/** Raw text to what is written for it, kept while the names stay the same: most text does not change. */
		private final Map<String, String> cleaned = new HashMap<>();
		private static final int CLEANED_MAX = 20_000;

		@Override
		public void tick()
		{
			if (!sink.writing())
			{
				return;
			}
			refreshNames();
			Map<String, String> now = new HashMap<>();
			Map<String, JsonArray> parts = new HashMap<>();
			Widget[] roots = client.getWidgetRoots();
			if (roots != null)
			{
				for (Widget root : roots)
				{
					visit(root, false, now, parts, 0);
				}
			}
			JsonArray changed = new JsonArray();
			for (String key : new TreeSet<>(now.keySet()))
			{
				if (!now.get(key).equals(last.get(key)))
				{
					changed.add(parts.get(key));
				}
			}
			JsonArray gone = new JsonArray();
			for (String key : new TreeSet<>(last.keySet()))
			{
				if (!now.containsKey(key))
				{
					gone.add(key);
				}
			}
			last = now;
			if (changed.size() > 0 || gone.size() > 0)
			{
				JsonObject o = sink.line("widgets");
				o.add("changed", changed);
				o.add("gone", gone);
				sink.add(o);
			}
		}

		private void visit(Widget w, boolean dynamic, Map<String, String> now, Map<String, JsonArray> parts, int depth)
		{
			if (w == null || depth > MAX_DEPTH)
			{
				return;
			}
			int group = w.getId() >>> 16;
			if (SOCIAL_INTERFACES.contains(group))
			{
				// Nothing of this interface; an interface nested in it (a dialog in the chat box) has its own group.
				visitAll(w.getNestedChildren(), false, now, parts, depth);
				return;
			}
			String key = (w.getId() >>> 16) + "." + (w.getId() & 0xFFFF) + (dynamic ? "[" + w.getIndex() + "]" : "");
			boolean hidden = w.isSelfHidden();
			String text = text(group, w.getText());
			int item = w.getItemId();
			int sprite = w.getSpriteId();
			if (hidden || !text.isEmpty() || item >= 0 || sprite >= 0)
			{
				JsonArray part = new JsonArray();
				part.add(key);
				part.add(w.getType());
				part.add(text);
				part.add(item);
				part.add(item >= 0 ? w.getItemQuantity() : 0);
				part.add(sprite);
				part.add(hidden ? 1 : 0);
				now.put(key, part.toString());
				parts.put(key, part);
			}
			if (hidden)
			{
				// Nothing under a hidden part shows; its children would only be noise.
				return;
			}
			// Dynamic children (made by a script under their parent) share the parent's id; their index tells them apart.
			visitAll(w.getStaticChildren(), false, now, parts, depth);
			visitAll(w.getDynamicChildren(), true, now, parts, depth);
			visitAll(w.getNestedChildren(), false, now, parts, depth);
		}

		private void visitAll(Widget[] children, boolean dynamic, Map<String, String> now, Map<String, JsonArray> parts, int depth)
		{
			if (children != null)
			{
				for (Widget child : children)
				{
					visit(child, dynamic, now, parts, depth + 1);
				}
			}
		}

		/** What is written for a part's text: nothing, {@code <withheld>}, or the text cleaned of names. */
		private String text(int group, String raw)
		{
			if (raw == null || raw.isEmpty())
			{
				return "";
			}
			if (!TEXT_INTERFACES.contains(group))
			{
				return TEXT_WITHHELD;
			}
			String done = cleaned.get(raw);
			if (done == null)
			{
				// A line break joins two names into one word once the tags go; it becomes a space first.
				done = Privacy.withoutNames(sink.scrub(Text.removeTags(raw.replace("<br>", " "))), names);
				if (cleaned.size() >= CLEANED_MAX)
				{
					cleaned.clear();
				}
				cleaned.put(raw, done);
			}
			return done;
		}

		/** The names to take out of text, rebuilt only when the set changes. */
		private void refreshNames()
		{
			Set<String> all = new TreeSet<>();
			addNames(all, client.getFriendContainer());
			addNames(all, client.getIgnoreContainer());
			FriendsChatManager channel = client.getFriendsChatManager();
			if (channel != null)
			{
				addNames(all, channel);
				all.add(channel.getOwner());
			}
			for (ClanChannel clan : new ClanChannel[]{client.getClanChannel(), client.getGuestClanChannel()})
			{
				if (clan != null && clan.getMembers() != null)
				{
					for (ClanChannelMember member : clan.getMembers())
					{
						all.add(member.getName());
					}
				}
			}
			WorldView top = client.getTopLevelWorldView();
			if (top != null)
			{
				addPlayers(all, top);
				for (WorldView boat : top.worldViews())
				{
					addPlayers(all, boat);
				}
			}
			all.remove(null);
			String key = String.join("\n", all);
			if (!key.equals(namesKey))
			{
				namesKey = key;
				names = Privacy.namesPattern(all);
				cleaned.clear();
			}
		}

		/** Players in a world view, the player aside: the main world, or aboard a boat. */
		private void addPlayers(Set<String> into, WorldView view)
		{
			if (view == null)
			{
				return;
			}
			for (Player player : view.players())
			{
				if (player != null && player != client.getLocalPlayer())
				{
					into.add(player.getName());
				}
			}
		}

		private void addNames(Set<String> into, NameableContainer<? extends Nameable> container)
		{
			if (container == null || container.getMembers() == null)
			{
				return;
			}
			for (Nameable member : container.getMembers())
			{
				if (member != null)
				{
					into.add(member.getName());
					into.add(member.getPrevName());
				}
			}
		}

		@Override
		public void reset()
		{
			last = new HashMap<>();
		}
	}

	/** How many times each game script ran since the last tick; never its arguments, which can hold chat. */
	class Scripts implements Probe
	{
		private final Map<Integer, Integer> counts = new TreeMap<>();

		@Subscribe
		public void onScriptPreFired(ScriptPreFired event)
		{
			if (!sink.writing())
			{
				return;
			}
			counts.merge(event.getScriptId(), 1, Integer::sum);
		}

		@Override
		public void tick()
		{
			if (sink.writing() && !counts.isEmpty())
			{
				JsonObject o = sink.line("scripts");
				JsonObject ran = new JsonObject();
				counts.forEach((id, n) -> ran.addProperty(String.valueOf(id), n));
				o.add("ran", ran);
				sink.add(o);
			}
			counts.clear();
		}

		@Override
		public void reset()
		{
			counts.clear();
		}
	}

	/** The varc numbers that changed since the last tick, with their values then. Never the text varcs. */
	class Varcs implements Probe
	{
		private final Set<Integer> changed = new TreeSet<>();

		@Subscribe
		public void onVarClientIntChanged(VarClientIntChanged event)
		{
			if (!sink.writing())
			{
				return;
			}
			changed.add(event.getIndex());
		}

		@Override
		public void tick()
		{
			if (sink.writing() && !changed.isEmpty())
			{
				JsonObject o = sink.line("varcs");
				JsonArray values = new JsonArray();
				for (int index : changed)
				{
					JsonArray one = new JsonArray();
					one.add(index);
					one.add(client.getVarcIntValue(index));
					values.add(one);
				}
				o.add("values", values);
				sink.add(o);
			}
			changed.clear();
		}

		@Override
		public void reset()
		{
			changed.clear();
		}
	}

	/** The mouse over the game canvas, once a tick when it moved. */
	class Mouse implements Probe
	{
		private Point last;

		@Override
		public void tick()
		{
			if (!sink.writing())
			{
				// Paused on the keypad or not recording: start over, so the first tick after writes the position.
				last = null;
				return;
			}
			Point now = client.getMouseCanvasPosition();
			if (now != null && !now.equals(last))
			{
				last = now;
				JsonObject o = sink.line("mouse");
				o.addProperty("x", now.getX());
				o.addProperty("y", now.getY());
				sink.add(o);
			}
		}

		@Override
		public void reset()
		{
			last = null;
		}
	}

	/**
	 * How long a game tick's handlers took, all plugins together (from the first handler to the last, Black Box's own
	 * included), the slowest client tick since the last game tick, and how long frames took from the start of
	 * drawing to the end. Written from the last game tick handler, so it covers the tick it is stamped with. Its
	 * handlers are registered as lambdas: the event bus only takes {@code @Subscribe} methods named exactly
	 * {@code on<Event>}, and this one needs two per event (first and last). A plugin at the same priority as either
	 * end may fall just outside the span (ties are broken by class name).
	 */
	class Timing implements Probe
	{
		private final List<EventBus.Subscriber> subscribers = new ArrayList<>();
		private long tickStart;
		private long clientTickStart;
		private long slowestClientTick;
		private long frameStart;
		private int frames;
		private long frameTotal;
		private long slowestFrame;

		/** The draw manager's every-frame listener: the frame is drawn, overlays included. */
		private final Runnable frameDrawn = () ->
		{
			if (frameStart != 0)
			{
				long took = System.nanoTime() - frameStart;
				frames++;
				frameTotal += took;
				slowestFrame = Math.max(slowestFrame, took);
				frameStart = 0;
			}
		};

		void subscribe()
		{
			subscribers.add(eventBus.register(GameTick.class, e -> tickStart = System.nanoTime(), BlackBoxPlugin.FIRST));
			subscribers.add(eventBus.register(GameTick.class, e -> tickDone(), BlackBoxPlugin.LAST));
			subscribers.add(eventBus.register(ClientTick.class, e -> clientTickStart = System.nanoTime(), BlackBoxPlugin.FIRST));
			subscribers.add(eventBus.register(ClientTick.class,
				e -> slowestClientTick = Math.max(slowestClientTick, System.nanoTime() - clientTickStart), BlackBoxPlugin.LAST));
			subscribers.add(eventBus.register(BeforeRender.class, e -> frameStart = System.nanoTime(), BlackBoxPlugin.FIRST));
			drawManager.registerEveryFrameListener(frameDrawn);
		}

		void unsubscribe()
		{
			subscribers.forEach(eventBus::unregister);
			subscribers.clear();
			drawManager.unregisterEveryFrameListener(frameDrawn);
		}

		private void tickDone()
		{
			long took = System.nanoTime() - tickStart;
			if (sink.writing())
			{
				JsonObject o = sink.line("timing");
				o.addProperty("gameTickMs", ms(took));
				o.addProperty("slowestClientTickMs", ms(slowestClientTick));
				o.addProperty("frames", frames);
				if (frames > 0)
				{
					o.addProperty("meanFrameMs", ms(frameTotal / frames));
					o.addProperty("slowestFrameMs", ms(slowestFrame));
				}
				sink.add(o);
			}
			reset();
		}

		@Override
		public void reset()
		{
			slowestClientTick = 0;
			frames = 0;
			frameTotal = 0;
			slowestFrame = 0;
			frameStart = 0;
		}

		private double ms(long nanos)
		{
			return Math.round(nanos / 10_000.0) / 100.0;
		}
	}
}
