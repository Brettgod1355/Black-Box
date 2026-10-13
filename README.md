# Black Box

A developer tool for RuneLite plugin authors. While you play, Black Box records what the game shows your
plugins: game messages, item changes, objects and NPCs coming and going, game values, XP, your position and
animation, interfaces opening and closing, your clicks, and every plugin's notifications. It can also save
screenshots of the game view. Later, a plugin's tests can replay the recording through the plugin's own
logic and check what it decided, without playing the same session again.

It only loads in RuneLite's developer mode. It is not meant for the Plugin Hub.

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

## Using it

1. Start a developer client with Black Box loaded: `./gradlew run` in this repository starts a client with
   only Black Box. Never add Black Box to another plugin's launcher or build: that plugin would then need
   Black Box to build at all.
2. In Black Box's settings, turn on **Record**. A new recording starts each time you log in, in
   `.runelite/plugin-data/black-box/<date and time>/`.
3. Play as you normally would. Set a **Mark hotkey** to flag a moment worth a look later.
4. Turn **Record** off, or log out, to finish.

## Settings

| Setting | Default | What it does |
|---|---|---|
| Record | off | Records while you are logged in. |
| Mark hotkey | none | Marks the moment, and takes a screenshot if screenshots are on. |
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
{"e":"session","tick":100,"ms":0,"format":1,"blackBox":"0.1","runelite":"1.13.1","world":444,"started":"2026-10-13_10-00-00"}
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
| `open`, `close` | `interface` |
| `click` | `option`, `target`, `action`, `id`, `itemId`, `param0`, `param1` |
| `notify` | `text` |
| `pluginMessage` | `namespace`, `name` |
| `state` | `state` |
| `shot` | `file`, `reason` (`timer`, `notify` or `mark`) |
| `mark` | |
| `pause`, `resume` | `why` (`bank PIN`): no `click`, `var` or `shot` lines in between. A `shot` just before a `pause` may have no file: its frame came after the keypad opened |
| `end` | `why` |

## Replaying in another plugin's tests

Copy `src/test/java/com/blackbox/replay/Recording.java` into the other plugin's test sources and change its
package. It needs only Gson, which RuneLite's client already brings onto the test classpath, so the plugin
never depends on Black Box. Then a test reads a recording, walks it tick by tick with `byTick()`, turns each
line into whatever the plugin's logic takes, and checks what the plugin decided.

## License

BSD 2-Clause, like RuneLite. See `LICENSE`.
