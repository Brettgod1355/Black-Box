# The recording format

A recording is a folder named by its start, `2026-10-20_18-02-11`, holding `events.jsonl` (one JSON object per
line, in the order things happened), `client-log.txt` (the client's own log for the same time) and `shots/`
(pictures). An export is a zip of the same, cut to a window, with a `README.txt` (what the files hold, the
log's warnings and errors summed up, and the frames per second: low, mean, high and the lowest moments with the last
thing that happened before each) and a `names.txt`.

Every line in `events.jsonl` has three fields: `e`, the kind of line; `tick`, the game tick it happened on
(the client counts about 1.67 a second; a login resets nothing, the count keeps rising); and `ms`, milliseconds
since the recording began. A tool reads lines by `e` and ignores kinds it does not know.

The first line's `format` says which version of this document the file follows. This is format **13**.

Privacy holds on every line: other players never appear, the player's own name is written as `<you>`, nothing
is written while the bank PIN keypad shows except the `pause` and `resume` lines that bracket it.

## The recording itself

### `session`
The first line. `format` (this document's version), `blackBox` (the plugin's version), `runelite`,
`runeliteCommit`, `launcher`, `pluginHub` (the Plugin Hub manifest version), `java` (version and vendor),
`os` (name, version, architecture), `canvas` (width x height), `resizable`, `stretched`, `world`, `started`
(the folder name) and `startedAt` (wall clock, milliseconds since 1970; add `ms` to it for any line's moment).
From format 9 also `revision` (the game build), `gpu` (the GPU plugin drawing), `locale`, `lookAndFeel`, `toolkit`
(the AWT toolkit's class name, which says Windows, X11 or macOS), `java2d` (only the Java2D switches that are set: `opengl`, `d3d`,
`noddraw`, `uiScale`) and `screens` (each screen's `size` and `scale`, the desktop's scaling). No JVM flags or
heap: those need `java.lang.management`, which the Plugin Hub refuses.

### `end`
The last line. `why`: `logged out`, `recording switched off` or `plugin stopped`. A recording without one
ended with the client.

### `state`
The game state changed: `state` is RuneLite's name (`LOGGED_IN`, `LOGIN_SCREEN`, `HOPPING`, `LOADING`,
`CONNECTION_LOST`).

### `pause` and `resume`
The bank PIN keypad opened or closed; `why` is `bank PIN`. Between them no `click`, `var`, `shot` or `sound`
line is written; from format 9 no `menu` or `varps` either, the tick line leaves out `idleMouse`, `idleKeys` and
`menuAdds`, and no `overlays` line is written; from format 10 none of the Debug page's tests write either.

## What the client is doing

### `tick`
Once per game tick. `fps` (the client's frames per second), `wv` (the world view the player stands in; `-1`
is the main world, another number a boat), `x`, `y`, `plane` (the player's tile in that world view), `anim`
(the player's animation id, `-1` for none). Aboard a boat also `topX`, `topY`, `topPlane` (the player in the
main world), `boatX`, `boatY`, `boatPlane` (the hull), `boatConfig` (the boat's world entity config), `boatOwner`,
`boatDir` (0 to 2047, 0 south, 1024 north). `overlay` and `underlay` are the scene's ids of the tile under the
player, or under the hull aboard: water kinds and ground types, as the game's own map data has them; `-1` when
not loaded. From format 9 also `cycle` (the client's game cycle, to order events within a tick), `idleMouse` and
`idleKeys` (client ticks since the last mouse and key input, the counters behind the idle logout; not while the
bank PIN keypad shows), `energy` (run
energy, 0 to 10000), `weight` (kg), `camYaw` (0 to 2047), `facing` (the player's own orientation, 0 to 2047),
`instance` (true inside an instanced area), `frames` and `longestFrameMs` (frames drawn since the last tick and
the longest gap between two of them, which shows a stutter the frames per second hides), `menuAdds` (menu entries
added since the last tick, by the game and plugins together), and `edtWaitMs` and `clientWaitMs` (how long work
posted at the last tick waited on the Swing thread and on the client thread; a freeze shows as a long wait). While
the Swing thread has not yet run the last probe, `edtPendingMs` says how long it has waited so far instead.
From format 13, while the game shows the Wilderness level on screen, `wildy` is that number (the HUD's "Level: N",
its first line only: RuneLite's Combat Level plugin adds a combat range below it); when the text is something else
(a PvP world's range), `wildyText` holds it instead, tags taken out.

### `plugin`
A plugin was switched on or off: `name`, `on`.

### `setting`
A setting changed: `group` (the plugin's config group), `key`, `plugin` (the plugin's name, when known),
`value` (the new value, withheld as `<secret>` or `<withheld>` under the rules in `settings`). From format 11 a setting
that is typed text has `text` `true`; in an export its value is `<text, not included>` unless the player picked it. From format 9 a hidden
or undeclared key and a key of RuneLite's own `runelite` group carry their value too, under the rules of `other`
below; a key the plugin does not declare is written as its length and hash like `other`'s keys, and a key of the
`runelite` group as it is. From format 12 a hidden or undeclared key also has `hidden` `true`: it is where the plugin keeps
its own state, not a change the player made, so the chart and a clip's frame-rate summary leave it out (in older
recordings, a key written as its length and hash).

### `plugins`
Written at the start (until format 7 also whenever the "reporting on" list changed): `plugins`, every installed plugin as
`{name, on, version}` (`version` only for Plugin Hub plugins). From format 9 each also has `active` (running: on but
not active means its start-up failed) and, for Plugin Hub plugins, `jarHash` and `updatedAt` (which build it is),
and the line has `notLoaded`: Plugin Hub plugins the player installed that did not load at all.

### `watch`
Formats 4 to 7 only: the plugins the player was reporting on, `plugins` as `{name, on, version}`. Gone from
format 8, where every recording covers every plugin and nothing is picked.

### `settings`
One per plugin that has settings, at the start: `plugin`, `group`, `values` (key to value, as the client stores
them). A setting the plugin marks secret is `<secret>`; a hidden one is left out; free text that is a webhook,
key, password or token, by its key name or by what it looks like, is `<withheld>`. Everything else is as typed.
From format 9, `other`: what the plugin keeps outside its visible settings (hidden items and keys no item declares,
where plugins store their own state; secret items stay out). These can hold the saved login name, a party
passphrase or friends' names, so only a plain value is written as it is: a number of up to ten digits, `true` or
`false`, an enum constant, a point (`12:34`) or a size (`765x503`). Any other value is `<N chars, sha256
abc123def456>`, the length and the first 12 hex characters of its SHA-256, which shows that it changed and compares
between recordings without saying what it is; a key that is a webhook, key, password or token is `<withheld>`. A
key a hidden item declares is written as it is; a key no item declares can hold a name (Friend Notes keeps
`note_<friend>`) and is written as its length and hash too. From format 11, `text`: the keys of `values` that are typed text (a plugin's String settings: lists, names, links).
They are recorded as they are, on the player's own computer; an **export** writes each as `<text, not included>`
unless the player ticked it in the export's list. An export of an older recording judges by the value instead: anything
not a plain number, flag, enum, point, size or color counts as text. A last `settings` line with `plugin` `RuneLite` and group `runelite` holds RuneLite's
own settings this way, its keys as they are (overlay and plugin names): overlay positions, notification defaults, the installed Plugin Hub plugins. It is written
again after a settings-profile switch.

### `profile`
From format 9. At the start and when a profile changes: `rsProfile` (the RuneScape profile in use, as a 12-character
hash, since it is derived from the account) and `why` (`settings profile` when the RuneLite settings profile
switched, followed by a fresh `plugins` and `settings`; `account profile` when the game account's profile did).

### `playerOptions`
From format 9. At the start, when the game changes them and when a plugin is switched on or off: `options`, the
eight right-click options on players as `[text, priority, menu type]`; a plugin's own option (a colored "Mark") is
here with its colors.

### `mark`
The player flagged a moment: from the sidebar's Mark, with `text` when they wrote something; from the hotkey
without. The `tick` and `ms` are the moment of the press, not of the save.

### `note`
A note by the player in their own words: `text`.

### `shot`
A picture was taken: `file` (its name in `shots/`, which carries the tick and the reason), `reason` (`mark`,
`notify`, `timer`).

### `notify`
A plugin sent a notification: `text`. From format 9 also `how` (how it was set up to reach the player: `enabled`,
`override`, `tray`, `sound` as a kind, never a custom sound's file, `flash`, `requestFocus`, `gameMessage`,
`whenFocused`) and `focused` (whether the window had focus at that moment, when known).

### `window`
From format 9. The window gained or lost focus (`focus`), the game's canvas changed size (`canvas`, width x
height), or the client switched between fixed and resizable (`resizable`).

### `pluginMessage`
One plugin sent another a message: `namespace`, `name`; never the content.

## What the game told the player

### `chat`
A game message to the player: `type` (RuneLite's chat type; only game messages, never other players' chat),
`text`. Hazard warnings, skill messages and plugin chat lines are here. From format 9 Black Box handles the message
after every other plugin, and `shown` is the text in the chat box by then (RuneLite's formatted text when it has
one) when a plugin rewrote it, colors aside; and a friend logging in or out (`LOGINLOGOUTNOTIFICATION`) or a friends
chat notice (`FRIENDSCHATNOTIFICATION`) is kept with `text` `<withheld>`, the moment without the name.

### `npcSay`
An NPC's overhead text as the game set it: `npc` (the NPC's id), `index` (its slot in the scene), `name`, `text`,
`wv`. From format 7, `shown`: what the NPC showed once every other plugin had handled this same event, written only
when it is not `text` (a plugin blanked or rewrote the line inside its handler; a plugin's own write fires no event,
so it can only be seen). A blanked text is `""` or `null`. Black Box handles this event after every other plugin.
The game writes the line's display count after the event, so a plugin's `setOverheadCycle` inside its handler is
overwritten; only its text survives.

### `npcShown`
From format 7. One client tick after an `npcSay`, once the game tick, the queued client-thread calls and every
plugin's tick handler that follow the event have run (Black Box's own runs last), the text the NPC really shows,
written only when it is not what that `npcSay` left (`shown`, or `text` when there was no `shown`): a plugin changed
it after the event. `npc`, `index`, `name`, `text`, `cycle` (client ticks until the game blanks the text: a new
line gets 100 and counts down one a client tick until the game clears it at 0; 0 or less stops the count, so the
text then stays until something writes it again, which is how a muter keeps its blank), `wv`. No `npcShown` line
means the NPC still showed what its last `npcSay` says a client tick later; a change after that is not recorded.

### `items`
An item container changed: `container` (the inventory id; 93 is the inventory, 95 the bank, see `names.txt`),
`items` as `[[id, quantity], ...]` in slot order; an empty slot is `[-1, 0]`.

### `var`
A varbit changed: `varbit` (the varbit id), `varp` (the varp it lives in), `value`. The game's state lives
here: boat hitpoints, resistances, facilities, the nearby sea, quest and skill flags. `names.txt` names the ids.

### `stat`
XP or a level changed: `skill`, `xp`, `level`, `boosted`.

### `hit`
A hitsplat on the player (`on` is `<you>`) or on an NPC (`npc`, `index`): `amount`, `type`. Never on other
players.

### `sound`
A sound effect played: `id`, `delay`, and when the game names a source, `from` (`<you>`) or `npc` and `index`.

### `open` and `close`
An interface loaded or closed: `interface` (the group id; `names.txt` names it).

### `click`
The player clicked a menu entry: `option`, `target` (empty when the target is a player), `action`, `id`,
`itemId`, `param0`, `param1`. Never while the bank PIN keypad shows. From format 9 also `rawOption`, the option with
the colors a plugin gave it, when it had any; and an entry on an interface (the chat box, friends and clan lists)
keeps its target only when it is about an item, since there the target is often a player's name. The same goes for
a plugin's own entry (a `RUNELITE` action, such as Friend Notes' "Add Note" on a friend), unless it is on an NPC.

### `menu`
From format 9. The player opened a right-click menu: `entries`, in the order the client holds them, each
`{option, target, type, id, itemId, param0, param1}` with `deprioritized` and `submenu` (its entry count) when set;
`option` keeps its colors; `target` follows the `click` rules, without colors. Black Box reads the menu after every other plugin has
handled the opening, so `entries` is what the player saw; `before` is the menu as the game opened it, written only
when a plugin changed it. `shift`: whether Shift was held. Never while the bank PIN keypad shows.

### `hint`
From format 9. The hint arrow, written when it changes: `has`, `type`, then `npc` and `index` for an NPC, `x`, `y`,
`plane` for a point, or `player` `true` for a player (never who). The point is the client's raw value: after a
clear the client leaves `0, 0, 0`, which a plugin may wrongly read as an arrow.

### `overlays`
From format 9. `overlays`: every registered overlay in drawing order, at the start and whenever the set or its placement
changes, each `name`, `plugin`, `layer`, `position`, `preferred` (the position the player dragged it to), `at` (its saved
spot), `priority`, `movable`, `bounds` (`x,y,width,height` when last drawn).

### `infoboxes`
From format 9. `infoboxes`: every registered infobox, drawn or not (a plugin can hide them), at the start and
whenever one comes or goes, each `name` and `text` at that moment (`<error>` when the plugin's text threw).

### `varps`
From format 9. Once per recording, on its first tick away from the bank PIN keypad: `values`, every varp that is
not 0, by number. These are the game settings and account state that do not change while the recording runs.

## The world around the player

### `objectAdd` and `objectGone`
A game object came into or left the scene: `id`, `hash`, `wv`, `x`, `y`, `plane`.

### `npcAdd` and `npcGone`
An NPC came into or left the scene: `npc` (id), `index`, `name`, `wv`, and from format 5 its tile.

### `npcMove`
From format 5: each tick, the NPCs in the main world that changed tile, as `[index, x, y, plane]`.

### `npcTarget`
An NPC started (`on` true) or stopped (`on` false) targeting the player: `npc`, `index`, `name`, `wv`. The aggro
moment. Never another player as the target.

### `npcAnim`
An NPC played an animation: `npc`, `index`, `anim` (`names.txt` names it), `wv`.

### `graphic`
The player's own spot animations changed: `ids`, the list now showing (being set alight, frozen).

### `boatAdd` and `boatGone`
A boat (world entity) came into or left the scene: `wv`, `config`, `owner`.

### `scene`
From format 9. A world view loaded (a region, an instance or a boat): `wv`, `top`, `baseX`, `baseY`, `plane`,
`instance`, and for an instance `chunks`, each `[plane, x, y, template]` with the template chunk as the client
packs it.

### `itemAdd` and `itemGone`
From format 9. An item appeared on or left the ground: `id`, `qty`, `own` (0 nobody's, 1 the player's, 2 another
player's, 3 the group's: a kind, never a name), `x`, `y`, `plane`.

### `loot`
From format 9. Loot the client reports: `from` says which report. `lootTracker` (RuneLite's Loot Tracker, only
while it is on): `type`, `name` (empty for loot from a player), `combat`, `amount`, `items`. `npc` and `server`
(an NPC's drop, with or without Loot Tracker): `npc`, `name`, `items`. `player` (a player's drop): `items` only,
never the player. `items` are `[id, quantity]`.

### `party`, `partyJoin` and `partyLeave`
From format 9. RuneLite party: `party` with `in` (in a party or not; never its id or passphrase); `partyJoin` and
`partyLeave` with `member` (a number that holds while the party lasts and starts again at a new `party` line,
never the member's id or name) and `size`.

### `collision`
Once per region per recording, the first tick the whole region is loaded: `region`, `plane`, `x`, `y` (the
region's south-west corner), `flags` as 64 rows of 64 numbers, `[x][y]`, the client's movement flags for each
tile: what a boat or a walker can and cannot cross.

## The Debug page (format 10)

The sidebar's second page has a Debug switch and tests under it. The switch is never saved (off whenever the client
starts, and at logout with the setting "Also turn off at logout"); which tests are ticked is saved. A test runs only
while Debug is on and it is ticked, and writes nothing while the bank PIN keypad shows. A missing line therefore
means "test off" only when the last `debug` line says so; read that first.

### `debug`
Debug switched on or off, a test ticked or unticked, or a recording started with Debug on: `on`, and `tests`, the keys
of the ticked tests (`projectiles`, `npcDetails`, `widgets`, `scripts`, `varcs`, `mouse`, `timing`).

### `projectile`
Test `projectiles`. A projectile the first time the game places it: `id`, who fired it (`from` `<you>`, or `fromNpc`
and `fromIndex`), at whom (`to`, or `toNpc` and `toIndex`), `fromX`/`fromY`/`fromPlane` and `toX`/`toY`/`toPlane`
when the game gives them, `cycle` (the client's cycle now), `startCycle` and `endCycle` (when it leaves and lands, in
client cycles), `startHeight`, `endHeight`, `slope`. Never one to or from another player.

### `npcInfo`
Test `npcDetails`. An NPC's make-up when it appears, when it turns into something else, and for every NPC in view when
the test starts: `npc`, `index`, `becomes` (the NPC it shows as now, when that is not `npc`), `name`, `combat`,
`size`, `actions` (its right-click options, empty strings for none), `interactible`, `minimap`, `follower` (a pet or
other follower, when true).

### `npcHealth`
Test `npcDetails`. Health bars that changed since the last tick: `at`, each `[index, ratio, scale]` (ratio out of
scale, as the game draws the bar; only NPCs that have a bar).

### `npcFight`
Test `npcDetails`. An NPC started or stopped interacting with something: `npc`, `index`, and the target as `target`
`<you>`, `targetNpc` and `targetIndex`, `target` `player` (another player, never who) or `target` `none`.

### `widgets`
Test `widgets`. What the open interfaces show, as the parts that changed since the last tick (the first line after
the test starts, or after a recording starts, has them all): `changed`, each `[key, type, text, itemId, quantity,
spriteId, hidden]`, and `gone`, the keys of parts no longer there. `key` is `group.child`, with `[index]` for a part a
script made under its parent. Only parts with something in them are kept (text, an item, a picture, or hidden), and
nothing under a hidden part. Text is kept only in the player's own screens (the HUD and orbs, side panels, bank,
equipment, shops, Grand Exchange offers, skill menus, dialogs, the collection log, Sailing's panels: the list is
`DebugProbes.TEXT_INTERFACES`); everywhere else a part with text has `text` `<withheld>`, since lobbies, party boards
and scoreboards list strangers. Kept text has its colors taken out (a line break becomes a space), the player's name as
`<you>`, and every name the client knows (friends, ignores, chat channel and clan members, players in view and aboard
boats) as `<player>`; a name that is also a common word ("Bank") is replaced there too. Interfaces about other players
are left out whole, though an interface nested in one (a dialog in the chat box) is read on its own terms: the chat
box and private messages, friends, ignores, chat channels and clans, group ironman, grouping, trade, the duel arena,
reports, hiscores, display names, tournament viewers, and the bank PIN keypad.

### `scripts`
Test `scripts`. How many times each game script ran since the last tick: `ran`, script id to count. Never what was
passed to a script, which can hold chat.

### `varcs`
Test `varcs`. The client's varc numbers that changed since the last tick: `values`, each `[index, value]` (the value
at the tick). Never the text varcs, which hold what the player types.

### `mouse`
Test `mouse`. The mouse over the game canvas, once a tick when it moved: `x`, `y`; `-1`, `-1` when it is off the
canvas.

### `timing`
Test `timing`. Once per game tick, from the last handler of the tick: `gameTickMs` (from the first plugin's game tick
handler to the last, every plugin together, Black Box's own included), `slowestClientTickMs` (the same for the slowest
client tick since the last line), `frames`, and when there were any, `meanFrameMs` and `slowestFrameMs` (from the start
of a frame to the end of its drawing, overlays included). A plugin whose handler has the same priority as Black Box's
first or last one may fall just outside the span.

## `client-log.txt`

The client's own log, caught inside the client as it is written: every INFO, WARN and ERROR line of RuneLite
and its plugins, as `yyyy-MM-dd HH:mm:ss zzz [thread] LEVEL logger - message`, with an exception's first ten
frames per cause under it. The player's name is `<you>`, the home folder `<home>`, and an account name or id other software logs
(RuneLite's "Discord RPC service is ready with user ...", a plugin's `user=...`) is `<withheld>`. The first lines are from
before the recording began, back to when the client started. In an export, every WARN and ERROR line of the whole
recording is kept and only the ordinary lines outside the window are dropped.

## `names.txt` (exports only)

`kind id NAME` for every varbit, varp, interface, sound, animation, spot animation, NPC, object, item and
inventory id the exported events mention, with RuneLite's constant name; `?` when the table does not know it.

## Versions

1. The 0.1 lines. 2. `sound`. 3. `fps` on `tick`; `plugin`, `setting`. 4. `watch`, `settings`; `value` on
`setting`. 5. NPC positions: tiles on `npcAdd`/`npcGone`, `npcMove`. 6. `boatDir`, `overlay`, `underlay` on
`tick`; `collision`, `npcTarget`, `npcAnim`, `graphic`, `note`; `plugins`; every plugin's `settings`;
`startedAt` and the environment on `session`; `version` on plugin lists; `text` on `mark`; `client-log.txt`.
7. `shown` on `npcSay`; `npcShown`. 8. No `watch` line: the sidebar's "Reporting on" is gone. 9. On `session`:
`revision`, `gpu`, `locale`, `lookAndFeel`, `toolkit`, `java2d`, `screens`; on `tick`: `cycle`, `idleMouse`,
`idleKeys`, `energy`, `weight`, `camYaw`, `facing`, `instance`, `frames`, `longestFrameMs`, `menuAdds`,
`edtWaitMs`, `clientWaitMs`; on `plugins`: `active`, `jarHash`, `updatedAt`, `notLoaded`; `other` on `settings` and
RuneLite's own `settings` line; values of hidden and undeclared keys on `setting`; `rawOption` on `click`; `shown`
and friend notices on `chat`; `how` and `focused` on `notify`; `profile`, `playerOptions`, `window`, `menu`, `hint`,
`overlays`, `infoboxes`, `varps`, `scene`, `itemAdd`, `itemGone`, `loot`, `party`, `partyJoin`, `partyLeave`.
10. The Debug page: `debug`, `projectile`, `npcInfo`, `npcHealth`, `npcFight`, `widgets`, `scripts`, `varcs`,
`mouse`, `timing`. 11. `text` on `settings` and `setting`; exports leave typed text out unless the player picks it.
12. `hidden` on `setting`. 13. `wildy` (or `wildyText`) on `tick`.
