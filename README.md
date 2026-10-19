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
- It never records the login screen. A recording starts once you are logged in and ends when you log out,
  and a screenshot whose frame is drawn after that, on the login screen, while logging in, hopping worlds
  or with the connection lost, is thrown away.

## Using it

1. Start a developer client with Black Box loaded: `./gradlew run` in this repository starts a client with
   only Black Box. Never add Black Box to another plugin's launcher or build: that plugin would then need
   Black Box to build at all.
2. In Black Box's settings, turn on **Record**. A new recording starts each time you log in, in
   `.runelite/plugin-data/black-box/<date and time>/`.
3. Play as you normally would. Set a **Mark hotkey** to flag a moment worth a look later.
4. Turn **Record** off, or log out, to finish.

## Frames per second

Black Box keeps the client's frames per second from the moment it loads, recording or not, and marks the moments
worth lining up against it: a plugin switched on or off, a setting changed (which one, never its value), a mark, a
notification, a login or logout. The **sidebar** (the chart icon) shows the current reading, the last minute's low,
mean and high, and a small chart. **Click the chart** to open it in its own window: a larger chart, a time range
from one minute to everything, every marked moment listed on the right (click one to jump to it), and the choice of
any past recording in the Black Box folder, with a slider to scrub through it. That is how to see what a plugin or a
setting does to the frame rate: toggle it while watching, or record a session and read it back later.

In a recording, every `tick` line carries `fps`, and `plugin` and `setting` lines say what was toggled when.

## Settings

| Setting | Default | What it does |
|---|---|---|
| Record | off | Records while you are logged in. |
| Mark hotkey | none | Marks the moment, and takes a screenshot if screenshots are on. |
| Sound effects | on | Records the id of each sound effect the game plays, while the game's sound effects are on. |
| Screenshots: Every | 5 seconds | A screenshot this often; 0 turns the timer off. |
| Screenshots: On notifications | on | A screenshot whenever any plugin sends a notification. |
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
| `npcAdd`, `npcGone` | `npc`, `index`, `name`, `wv` |
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

BSD 2-Clause, like RuneLite. See `LICENSE`.
