# The recording format

A recording is a folder named by its start, `2026-10-20_18-02-11`, holding `events.jsonl` (one JSON object per
line, in the order things happened), `client-log.txt` (the client's own log for the same time) and `shots/`
(pictures). An export is a zip of the same, cut to a window, with a `README.txt` and a `names.txt`.

Every line in `events.jsonl` has three fields: `e`, the kind of line; `tick`, the game tick it happened on
(the client counts about 1.67 a second; a login resets nothing, the count keeps rising); and `ms`, milliseconds
since the recording began. A tool reads lines by `e` and ignores kinds it does not know.

The first line's `format` says which version of this document the file follows. This is format **6**.

Privacy holds on every line: other players never appear, the player's own name is written as `<you>`, nothing
is written while the bank PIN keypad shows except the `pause` and `resume` lines that bracket it.

## The recording itself

### `session`
The first line. `format` (this document's version), `blackBox` (the plugin's version), `runelite`,
`runeliteCommit`, `launcher`, `pluginHub` (the Plugin Hub manifest version), `java` (version and vendor),
`os` (name, version, architecture), `canvas` (width x height), `resizable`, `stretched`, `world`, `started`
(the folder name) and `startedAt` (wall clock, milliseconds since 1970; add `ms` to it for any line's moment).

### `end`
The last line. `why`: `logged out`, `recording switched off` or `plugin stopped`. A recording without one
ended with the client.

### `state`
The game state changed: `state` is RuneLite's name (`LOGGED_IN`, `LOGIN_SCREEN`, `HOPPING`, `LOADING`,
`CONNECTION_LOST`).

### `pause` and `resume`
The bank PIN keypad opened or closed; `why` is `bank PIN`. Between them no `click`, `var`, `shot` or `sound`
line is written.

## What the client is doing

### `tick`
Once per game tick. `fps` (the client's frames per second), `wv` (the world view the player stands in; `-1`
is the main world, another number a boat), `x`, `y`, `plane` (the player's tile in that world view), `anim`
(the player's animation id, `-1` for none). Aboard a boat also `topX`, `topY`, `topPlane` (the player in the
main world), `boatX`, `boatY`, `boatPlane` (the hull), `boatConfig` (the boat's world entity config), `boatOwner`,
`boatDir` (0 to 2047, 0 south, 1024 north). `overlay` and `underlay` are the scene's ids of the tile under the
player, or under the hull aboard: water kinds and ground types, as the game's own map data has them; `-1` when
not loaded.

### `plugin`
A plugin was switched on or off: `name`, `on`.

### `setting`
A setting changed: `group` (the plugin's config group), `key`, `plugin` (the plugin's name, when known),
`value` (the new value, withheld as `<secret>` or `<withheld>` under the rules in `settings`).

### `plugins`
Written at the start and whenever the "reporting on" list changes: `plugins`, every installed plugin as
`{name, on, version}` (`version` only for Plugin Hub plugins).

### `watch`
The plugins the player is reporting on: `plugins` as `{name, on, version}`. Their setting changes are the
markers on the frames-per-second chart.

### `settings`
One per plugin that has settings, at the start: `plugin`, `group`, `values` (key to value, as the client stores
them). A setting the plugin marks secret is `<secret>`; a hidden one is left out; free text that is a webhook,
key, password or token, by its key name or by what it looks like, is `<withheld>`. Everything else is as typed.

### `mark`
The player flagged a moment: from the sidebar's Mark, with `text` when they wrote something; from the hotkey
without. The `tick` and `ms` are the moment of the press, not of the save.

### `note`
A note by the player in their own words: `text`.

### `shot`
A picture was taken: `file` (its name in `shots/`, which carries the tick and the reason), `reason` (`mark`,
`notify`, `timer`).

### `notify`
A plugin sent a notification: `text`.

### `pluginMessage`
One plugin sent another a message: `namespace`, `name`; never the content.

## What the game told the player

### `chat`
A game message to the player: `type` (RuneLite's chat type; only game messages, never other players' chat),
`text`. Hazard warnings, skill messages and plugin chat lines are here.

### `npcSay`
An NPC's overhead text: `npc` (the NPC's id), `index` (its slot in the scene), `name`, `text`, `wv`.

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
`itemId`, `param0`, `param1`. Never while the bank PIN keypad shows.

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

### `collision`
Once per region per recording, the first tick the whole region is loaded: `region`, `plane`, `x`, `y` (the
region's south-west corner), `flags` as 64 rows of 64 numbers, `[x][y]`, the client's movement flags for each
tile: what a boat or a walker can and cannot cross.

## `client-log.txt`

The client's own log, caught inside the client as it is written: every INFO, WARN and ERROR line of RuneLite
and its plugins, as `yyyy-MM-dd HH:mm:ss zzz [thread] LEVEL logger - message`, with an exception's first ten
frames per cause under it. The player's name is `<you>` and the home folder `<home>`. The first lines are from
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
