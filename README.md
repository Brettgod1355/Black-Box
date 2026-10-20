# Black Box

A developer tool for RuneLite plugin authors. While you play, Black Box records what the game shows your
plugins: game messages, item changes, objects and NPCs coming and going, game values, XP, your position and
animation, interfaces opening and closing, your clicks, and every plugin's notifications. It can also save
screenshots of the game view. Later, a plugin's tests can replay the recording through the plugin's own
logic and check what it decided, without playing the same session again.

It only loads in RuneLite's developer mode. It is not meant for the Plugin Hub.

## Why it exists

Black Box was made to help develop [AFK Shipwrecks](https://github.com/Brettgod1355/AFK-Shipwrecks), a sailing
plugin that keeps count of what is in your cargo hold while its window is closed. That count has to follow
deposits, withdrawals and drops as they happen, a problem other plugins that count cargo share. The replay
function is what fixed it: recordings of real trips, played through the plugin's logic in a test, showed every
miss and refused the wrong fixes before they shipped. The worked example below tells that story.

## What it never does

- It never sends anything to the game: no clicks, no keys, no menu actions.
- It never talks to the internet. It writes files to its own folder and nothing else.
- It never records other players. Public, private, clan, friends and trade chat are left out, and so are
  broadcasts, other players' overhead text, hits on other players, and the names on clicks aimed at
  players. Your own name is replaced by `<you>` wherever the game writes it. Screenshots black out the chat
  box by default.
- It never records your bank PIN. While the bank PIN keypad is on screen, no clicks, game values or
  screenshots are recorded, so neither a clicked button nor a picture of the shuffled keypad ends up in a
  recording. It never listens to your keyboard either, except for the Mark hotkey, so a PIN typed on the
  number pad is not recorded.
- It never records a setting marked secret, nor a webhook, key, password or token in any setting; the rest of
  every plugin's settings are recorded, so a developer can see what was on.
- It never reads RuneLite's log file. The client's log lines in a recording are caught inside the client as they
  happen, with your name and home folder replaced.
- It never records the login screen. A recording starts once you are logged in and ends when you log out,
  and a screenshot whose frame is drawn after that, on the login screen, while logging in, hopping worlds
  or with the connection lost, is thrown away.

## How a developer uses it

There is no live link between a player and a developer, and nothing in this needs the game open to read.

1. **The player records without doing anything.** With Black Box on, the client writes what the game sent, what
   the client did, every plugin's settings and the client's own log into a folder on the player's machine, tick
   by tick. When something goes wrong they press Mark, say what happened, and press Export. That writes one zip.
   They attach it to the issue. Their part is done; the client can be closed.
2. **The developer reads the zip.** It holds text files and, if the player allowed it, a few pictures. No game, no
   connection to the player, nothing that runs. `READING.md` gives the five questions and where the answers are:
   what was installed, how was my plugin set, what happened around the mark, were there errors, what did the game
   send against what my plugin should have done. `names.txt` names every number. Most bug reports end here.
3. **Replay, when reading is not enough.** The developer drops the events file into a git-ignored `recordings/`
   folder in their plugin's clone and runs the plugin's replay test (`REPLAY.md`). The test reads the file in
   tick order and hands each line to the plugin's decision code, which answers as it would have in play: warn
   now, the hold holds 12, no hazard. The test writes those answers to a report or asserts them. The developer
   sees their plugin's decisions laid against what the game actually sent, for a session they never played;
   they fix the code and run the same file again to see the fix hold.

Replay is a unit test, so it runs wherever the plugin's tests run: IntelliJ, any other IDE, or the command line
(`./gradlew test`, the same command the Plugin Hub runs). No client is open, nothing is drawn, nothing moves.
The only pictures are the ones the player allowed, of their screen at the marked moments. The frames-per-second
window in the sidebar can open a recording and show its chart with the markers, which is the nearest thing to
watching it.

## The sidebar

Click the black box icon in the sidebar. Recording starts by itself when you log in; the card at the top shows
the red dot, since when, the time so far and the size. **Export** saves the last 2, 10 or 30 minutes, a number
of minutes you choose, or all of today, as one zip to send to a developer; **Open folder** opens the folder that
holds the recordings and the exports. **Mark**: press it the moment you see the problem. The time is saved at
once and the Mark area becomes a small form: say what happened, as much or as little as you like, and press
Save flag; Cancel drops it, and nothing is written until you save. Export afterwards. Then frames per
second, the list of recordings with Zip, Open and Delete for each, and **Reporting on**: every plugin's settings
are recorded anyway; name the plugin the problem is about, so the developer sees it and its setting changes show
on the chart.

### Sending an export to a developer

An export reaches back across recordings, so a bug just before a logout is still in it. It holds `events.jsonl`
(each recording's slice starting with its first lines, so the settings in force are always there),
`client-log.txt` for the same minutes plus every warning and error since the client started, and a `README.txt` saying what they hold and never hold. Pictures go in
only if **Pictures in clips** is on. Open any file in a text editor first if you want to read it. Attach the
zip to the GitHub issue or send it where the developer asked. Exports are written to the `clips` folder and the
path is copied to your clipboard.

### Asking a player for a recording (for developers)

Paste this into the issue: "Install Black Box from the Plugin Hub and open its sidebar (the black box icon).
It records on its own. Add *<your plugin>* under Watched plugins, make the problem happen, press Mark, say what you saw and
press Save flag, then press Export and pick the last 10 minutes, and attach the zip it names. It
holds no chat from other players, no bank PIN and no pictures unless you turn those on; the README inside lists
exactly what it holds."

## Replay

`REPLAY.md` says what replay is for, where it lives (your plugin's test sources, with a copy of the reader and
a git-ignored `recordings/` folder), what a developer does step by step, a minimal test, and the paragraph to
hand an AI assistant to add replay to a plugin or read what a replay found.

## Reading a recording

`FORMAT.md` lists every line kind and every field; `READING.md` is the short guide, the five questions a
developer asks and where the answers are, and the paragraph to hand an AI assistant along with an export. An
export's `names.txt` names every varbit, interface, sound, animation, NPC, object and item id the lines mention.
A test keeps `FORMAT.md` honest: every kind of line the plugin can write must have a section there.

## Using it

1. Start a developer client with Black Box loaded: `./gradlew run` in this repository starts a client with
   only Black Box. Never add Black Box to another plugin's launcher or build: that plugin would then need
   Black Box to build at all.
2. A new recording starts each time you log in, in `.runelite/plugin-data/black-box/<date and time>/`; turn
   **Record** off in the settings to stop that.
3. Play as you normally would. Set a **Mark hotkey**, or press Mark in the sidebar, to flag a moment worth a look
   later.
4. Log out to finish, or press Clip at any time for the last minutes.

## The world around you

Every `tick` line also carries the ground or water under you: the scene's overlay and underlay ids of the tile
under your feet, or under the hull when you are aboard (`overlay`, `underlay`), and aboard, which way the hull
points (`boatDir`, 0 to 2047, 0 south). The first time a whole region is loaded, a `collision` line holds its
movement flags, 64 by 64, the same data the client uses for pathing. An `npcTarget` line marks the moment an NPC
starts or stops targeting you, `npcAnim` each animation an NPC in view plays, and `graphic` your own spot
animations (set alight, frozen). Other players are left out of all of these, as everywhere else.

## Every plugin's settings, and the client's own log

While recording, Black Box writes a `plugins` line naming every installed plugin with on or off and a `settings`
line per plugin with every one of its current settings, free text included (highlight lists, block lists, names
you typed into a plugin are what a developer needs to see). Withheld, always: a setting the plugin marks secret,
a hidden one, and anything that is a webhook, a key, a password or a token, by its key name or by what it looks
like, written as `<secret>` or `<withheld>`. When a setting changes, the `setting` line carries the new value.

The first line of a recording says which client this was: RuneLite's version and commit, the launcher and
Plugin Hub versions, Java, the operating system, the window size and mode, and the wall-clock start time; the
`plugins` line carries each Hub plugin's version. Nothing in that says who you are.

Beside the events, `client-log.txt` holds the client's own log for the same time: every INFO, WARN and ERROR line
RuneLite and its plugins logged, in the shape of RuneLite's `client.log`, with stack traces cut short. Black Box
never reads the log file; it listens to the client's logging as it happens, keeps the last lines from before you
pressed Record, and replaces your name and your home folder in every line. A developer who would have asked for
`client.log` gets it in the share zip instead.

## Reporting on

Every plugin's settings are in every recording. Under **Reporting on**, name the plugin the problem is about:
the recording says so in a `watch` line, and that plugin's setting changes show as markers on the
frames-per-second chart. **Add a plugin** drops down a search box over everything installed (type part of a
name to narrow it), and each named plugin is a row with its on/off dot and a cross to remove it.

## Frames per second

Black Box keeps the client's frames per second from the moment it loads, recording or not, and marks the moments
worth lining up against it: a plugin switched on or off, a setting changed (which one, never its value), a mark, a
notification, a login or logout. The **sidebar** (the chart icon) shows the current reading, the low, mean and high
over the last 1, 5, 10 and 15 minutes (dimmed until the client has run that long), and a small chart of the last minute. **Click the chart** to open it in its own window: a larger chart, a time range
from one minute to everything, every marked moment listed on the right (click one to jump to it), and the choice of
any past recording in the Black Box folder, with a slider to scrub through it. That is how to see what a plugin or a
setting does to the frame rate: toggle it while watching, or record a session and read it back later.

In a recording, every `tick` line carries `fps`, and `plugin` and `setting` lines say what was toggled when.

## Settings

| Setting | Default | What it does |
|---|---|---|
| Record | on | Records while you are logged in. |
| Mark hotkey | none | Marks the moment at once, with a picture, without the form. |
| Sound effects | on | Records the id of each sound effect the game plays, while the game's sound effects are on. |
| Screenshots: Every | 0 (off) | A screenshot this often; 0 leaves pictures to Mark and notifications. |
| Screenshots: On notifications | on | A screenshot whenever any plugin sends a notification. |
| Screenshots: Pictures in clips | off | Put the clipped minutes' pictures into a clip. |
| Screenshots: Hide the chat box | on | Blacks out the chat box in every screenshot. |
| Screenshots: Width | 960 pixels | Screenshots are shrunk to at most this width. |
| Cleanup: Keep for (days) | 7 | Recordings older than this are deleted. |
| Cleanup: Size limit (MB) | 500 | Then the oldest are deleted until the rest fit. |

Cleanup runs each time a recording starts. The recording in progress is never deleted.

## What a recording looks like

Each recording is a folder with `events.jsonl` and a `shots` folder. `events.jsonl` holds one JSON object
per line, in the order things happened. Every line has `e` (what happened), `tick` (the game tick) and `ms`
(milliseconds since the recording began):

```
{"e":"session","tick":100,"ms":0,"format":2,"blackBox":"0.1","runelite":"1.13.1","world":444,"started":"2026-10-13_10-00-00"}
{"e":"tick","tick":101,"ms":600,"wv":7,"x":12,"y":9,"plane":0,"anim":-1,"topX":1801,"topY":4001,"topPlane":0}
{"e":"chat","tick":102,"ms":1200,"type":"GAMEMESSAGE","text":"<you> salvages some debris."}
{"e":"notify","tick":102,"ms":1210,"text":"Your cargo hold is full (160/160)."}
```

| `e` | Fields |
|---|---|
| `session` | `format`, `blackBox`, `runelite`, `world`, `started` |
| `tick` | `wv` (the world view you stand in), `x`, `y`, `plane`, `anim`; aboard a boat also `topX`/`topY`/`topPlane` (you in the main world), `boatX`/`boatY`/`boatPlane`, `boatConfig`, `boatOwner` |
| `chat` | `type`, `text` |
| `npcSay` | `npc`, `index`, `name`, `text`, `wv` |
| `items` | `container`, `items` as `[id, quantity]` pairs, one per slot |
| `objectAdd`, `objectGone` | `id`, `hash`, `wv`, `x`, `y`, `plane` |
| `npcAdd`, `npcGone` | `npc`, `index`, `name`, `wv`; from format 5 also `x`, `y`, `plane`, the NPC's tile in the main world (under the boat for an NPC aboard one), left out while the bank PIN keypad shows |
| `npcMove` | `at`: `[index, x, y, plane]` for each NPC in the main world whose tile changed since the last tick (crew aboard boats left out). None while the bank PIN keypad shows. Format 5 and later |
| `boatAdd`, `boatGone` | `wv`, `config`, `owner` |
| `var` | `varbit`, `varp`, `value` |
| `stat` | `skill`, `xp`, `level`, `boosted` |
| `hit` | `on` (`<you>`) or `npc` and `index`; `amount`, `type` |
| `sound` | `id`, `delay`; `from` (`<you>`) or `npc` and `index` when the game says who caused it. Only while the game's sound effects are on (the client drops them otherwise), and none while the bank PIN keypad shows. Format 2 and later |
| `open`, `close` | `interface` |
| `click` | `option`, `target`, `action`, `id`, `itemId`, `param0`, `param1` |
| `notify` | `text` |
| `pluginMessage` | `namespace`, `name` |
| `state` | `state` |
| `shot` | `file`, `reason` (`timer`, `notify` or `mark`) |
| `mark` | |
| `pause`, `resume` | `why` (`bank PIN`): no `click`, `var` or `shot` lines in between. A `shot` just before a `pause` may have no file: its frame came after the keypad opened. The same goes for a `shot` just before logging out or hopping |
| `end` | `why` |

## Replaying a recording in your tests

A recording is the game's side of a session: what your plugin would have seen, tick by tick. A replay test
feeds it through your plugin's own logic and checks what the logic decided against something the recording
itself can vouch for. Nothing from Black Box is needed at build time.

1. **Copy `src/test/java/com/blackbox/replay/Recording.java`** into your plugin's test sources and change
   its package. It needs only Gson, which RuneLite's client already brings onto the test classpath.
2. **Keep recordings out of git.** Make a `recordings/` folder in your clone, add it to `.gitignore`, and
   copy each recording's `events.jsonl` into its own folder there, never the screenshots. Let the test skip
   itself when the folder is missing (`Assume.assumeTrue(Files.isDirectory(recordings))`), so your CI and
   the Plugin Hub's build stay green without recordings.
3. **Write a translator.** A small test-only class that walks `recording.byTick()` and, for each line, makes
   the call your plugin would have made in game: an `items` line becomes the container change your handler
   reads, a `click` line your menu-option handler, `open` and `close` your interface events, `var`, `stat`,
   `chat`, `npcSay` and `sound` the rest. After each tick it asks your logic what it thinks.
4. **Check against the recording, not against yourself.** Pick something the game states outright now and
   then, which your logic only estimates in between, and compare the two every time the game states it.
   Write every comparison to a file under `build/`, so a miss can be read next to the recording's lines.

### A worked example: the cargo hold count in AFK Shipwrecks

AFK Shipwrecks counts what is in the cargo hold while its window is closed: deposits, withdrawals and items
dropped on the way move the count, and the hold's real contents show only when its window opens. That
opening is the ground truth: the `items` line the game sends then must equal the count, or the count has
drifted. Its replay test plays every recording through the same code the plugin runs in game and fails on
any drift.

Over one afternoon the test played 21 recordings with 149 hold openings. The two recordings made that day
began with 29 drifting openings; by the end all 149 matched. Each miss turned out to be an order of clicks
within a single game tick, readable in the recording because `click` lines are kept in millisecond order next
to the `items` lines:

- **Deposit-all with another click in the same tick** (Walk here, a crew command, opening something, a
  Drop). The code treated the second click as cancelling the deposit; the game had already carried it out.
- **Drop clicked before Deposit-all** in the same tick: one inventory change covers both, the hold one short
  of the delta. Clicked after: either the deposit lands and the drop finds nothing, or the drop goes through
  and the deposit never happens, never both.
- **Deposit-all twice in one tick**, the second as the first landed: the second click reset the first before
  it had been counted.

The recordings also refused rules that looked right. Of seven candidate rules for the drop case, three failed
on recorded sequences before they shipped: one would have subtracted a drop that had already landed on its
own a tick before the Deposit-all (ticks 617 and 618); another would have charged a Drop that found nothing
to a Deposit-all 25 ticks later. The rule that survived shipped with those sequences as unit tests.

Two of the misses were the translator's own. It carried the count from a recording that ended in a crash (no
`end` line) into the next one, hours later; and it took a hold read that arrived a few milliseconds before the
window's `open` line, in the same tick, for something else. A translator is code too; the recording checks it
as well.

While testing in game, copy the recording in progress (minus its last, possibly partial line) into
`recordings/` and run the test: a live miss shows within a minute.

What a recording cannot give you: your plugin's internal state, and whatever the game shows that Black Box
does not log yet. The sounds the game plays were one such gap that afternoon, which is why format 2 records
them; the translator fills the rest in your plugin's own way or leaves it out.

## License

BSD 2-Clause, like RuneLite. See `LICENSE`. The parts that come from elsewhere (the id names table, from RuneLite's
API, and the Gradle wrapper) and how to credit `Recording.java` when you copy it are in
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md), which is also inside the plugin JAR.
