# Black Box

**Black Box records what happens in your game client, so a plugin's developer can see a bug you hit and they
cannot reproduce.** It keeps the last days of play in a folder on your computer. When something goes wrong, you
press Export and send the developer one zip of the last few minutes. It sends nothing anywhere by itself, and it
never touches the game: it only watches.

For developers it is more than a bug report: the recording holds what the game sent and what the client did,
tick by tick, and a plugin's own tests can play it back through the plugin's logic (see [Replay](#replay)).

## If a developer asked you to use Black Box

1. **Install Black Box.** It is not on the Plugin Hub yet: until it is, build it from source (see
   [Building and running from source](#building-and-running-from-source)). It starts recording by itself the
   next time you log in; there is nothing to switch on.
2. **Play until the problem happens.** When it does, open Black Box's sidebar (the black box icon) and press
   **Mark**. Say in a few words what you saw, and press **Save flag**.
3. **Press Export…** and pick **The last 10 minutes** (or whatever the developer asked for). Black Box writes one
   zip, uploads nothing, and copies its path to your clipboard; **Open export folder** shows it.
4. **Attach the zip** to the GitHub issue, or send it where the developer asked. The zip's `README.txt` says what
   it holds. You can open any file in it with a text editor before you send it.

That is all. If you would rather Black Box stopped recording afterwards, turn **Record** off in its settings, or
turn the plugin off.

## What is in the zip, and what never is

**In it:**

- What the game showed you and your plugins: game messages, your items, interfaces opening and closing, game
  values, XP, your position and animation, NPCs and objects around you, your clicks and menus, sounds.
- Every installed plugin, whether it was on, and its settings. Text you typed into a plugin's settings
  (highlight lists, names, links) is **left out** unless you tick it when you export (**With text settings…**).
- The client's own log lines for those minutes, with your name and home folder taken out.
- Your client's details: RuneLite and Java versions, operating system, window size, frames per second.
- Pictures of your screen **only if you turn on Pictures in clips**. They show whatever was on screen; turn on
  **Hide the chat box** to black out the chat box and split private chat in them.

**Never in the recording itself (the text files), in any setting:**

- **Other players.** Public, private, clan, friends and trade chat, broadcasts, other players' overhead text, hits
  on other players and names on clicks aimed at players are not recorded. Your own name is written as `<you>`.
- **Your bank PIN.** While the PIN keypad shows, nothing is recorded: no clicks, no game values, no pictures.
  Black Box never listens to your keyboard beyond its own Mark hotkey, so a PIN typed on the number pad is never
  seen either.
- **Secrets in settings.** A setting a plugin marks secret, and anything that looks like a webhook, key, password
  or token, is always withheld.
- **Your account.** No login name, no account id, no password. The login screen and the welcome screen after
  logging in are never in a picture.
- **Anything outside Black Box's own folder.** It never reads RuneLite's log file or any other file.

**Pictures are different:** they show your screen as it was. With **Hide the chat box** off, that includes the chat
box, so other players' messages and your own name can be in them. Pictures go into a zip only with **Pictures in
clips** on, and the zip's `README.txt` says whether the chat was blacked out. Look at them before you send.

## What it never does

- It never sends anything to the game: no clicks, no keys, no menu actions.
- It never talks to the internet.
- It never changes another plugin's settings.

## Where it keeps recordings

Each login starts a recording in `.runelite/plugin-data/black-box/<date and time>/`, and exports go into the
`clips` folder beside them. **Open folder** in the sidebar opens it. Recordings older than 7 days are deleted, and
the oldest go first once the folder passes 500 MB; both are settings. The recording in progress is never deleted.

## The sidebar

The **Recorder** page:

- **The card at the top** shows that it is recording, since when, and how big the recording is. A red line warns
  when an export's pictures would show your chat (Pictures in clips on, Hide the chat box off).
- **Export…** saves the last 2, 10 or 30 minutes, a number of minutes you type, or all of today, as one zip, with
  typed text settings left out. **With text settings…** has the same choices and first lets you pick which typed
  settings to include, grouped by plugin, none ticked. An export reaches back across recordings, so a bug just
  before a logout is still in it.
- **Mark** saves the moment at once and opens a small form to say what happened; **Save flag** keeps it,
  **Cancel** drops it. A **Mark hotkey** in the settings does the same without the form.
- **Frames per second**: the current reading and a small chart. Click the chart for the **Black Box: FPS window**.
- **Recordings**: each with **Zip**, **Open** and **Delete**.

The **Debug** page holds extra tests a developer may ask for (below).

## Settings

| Setting | Default | What it does |
|---|---|---|
| Record | on | Records while you are logged in. |
| Mark hotkey | none | Marks the moment at once, with a picture, without the form. |
| Sound effects | on | Records the id of each sound effect the game plays, while the game's sound effects are on. |
| Screenshots: Every | 0 (off) | A picture this often; 0 leaves pictures to Mark and notifications. |
| Screenshots: On notifications | on | A picture whenever any plugin sends a notification. |
| Screenshots: Pictures in clips | off | Put the exported minutes' pictures into the zip. |
| Screenshots: Hide the chat box | off | Blacks out the chat box, and the split private chat lines above it, in every picture. |
| Screenshots: Width | 960 pixels | Pictures are shrunk to at most this width. |
| Cleanup: Keep for (days) | 7 | Recordings older than this are deleted. |
| Cleanup: Size limit (MB) | 500 | Then the oldest are deleted until the rest fit. |
| Debug: Also turn off at logout | off | Debug switches off at logout, not only when the client closes. |

---

# For plugin developers

## Asking a player for a recording

Paste this into the issue:

> Install Black Box (it is not on the Plugin Hub yet: build it from source, the README says how) and open its
> sidebar (the black box icon). It records on its own. Make the problem happen, press Mark, say what you saw and
> press Save flag, then press Export…, pick "The last 10
> minutes", and attach the zip. Its text files hold no chat from other players, no bank PIN and no typed text
> settings. Pictures go in only if you turn them on, and then show your screen as it was; the README inside says
> exactly what it holds.

There is no live link between you and the player, and nothing in the zip needs the game open to read.

## Reading a clip

The zip holds `events.jsonl` (one JSON object per line, in the order things happened), `client-log.txt`,
`names.txt` (a name for every varbit, interface, sound, animation, NPC, object and item id the lines mention) and a
`README.txt` that sums up the warnings and errors by where they start, and the frames per second: low, mean, high,
and the lowest moments with the last thing that happened before each.

- [`READING.md`](READING.md): the short guide. The questions a developer asks (what was installed, how was my
  plugin set, what happened around the mark, were there errors, what did the game send against what my plugin
  should have done, where did the frames go) and where each answer is, plus a paragraph to hand an AI assistant
  along with a clip.
- [`FORMAT.md`](FORMAT.md): every line kind and every field, by format version. A test keeps it complete: every
  line the plugin can write has a section there.

A few lines a bug report usually turns on:

- `plugins` and `settings` at the start of each recording, and `setting` whenever one changes, so you see how
  your plugin was set at the moment of the bug.
- `menu` (each right-click menu after every plugin had its turn, with `before` when a plugin changed it),
  `overlays` and `infoboxes`, for "it shows for me and not for them".
- `npcSay` with `shown`, and `npcShown` a client tick later, for two plugins fighting over the same overhead text:
  the change is seen, never traced to a plugin (that would take reflection, which the Plugin Hub forbids), and the
  `plugins` line says which plugin only the affected player had on.
- `tick` lines carry `fps`, `frames`, `longestFrameMs` and how long work waited on the Swing and client threads,
  next to `plugin` and `setting` lines saying what was toggled when.

**Open a clip…** in the FPS window shows a player's zip as a chart: frame rate, plugin and setting changes and
marks, with a slider; the file is only read. Click and drag across the chart to zoom in; **Reset zoom** goes back.

## The Debug page

For a bug that needs more than a normal recording, ask the player to tick the tests you need on the **Debug** page
(or **Select all**) and switch **Debug** on. The switch is never saved, so it is off whenever the client starts; the
ticks are saved. Tests that are off cost nothing; two are marked as able to cost frames. A `debug` line says when
Debug was on and which tests ran, so "test off" and "nothing happened" read differently. None of them write while the
bank PIN keypad shows.

| Test | What it adds |
|---|---|
| Projectiles | Each projectile: what fired it at what, from where to where, when it lands. None to or from other players. |
| NPC details | An NPC's make-up (what it turns into, combat level, size, options), its health bar, what it is fighting. |
| Interface contents (can cost frames) | What the open interfaces show, when it changes. Chat, friends, clan, trade and duel screens are left out; text is kept only on the player's own screens, with every name the client knows as `<player>`. |
| Game scripts (can cost frames) | How many times each game script ran, per tick; never what was passed to it. |
| Client values | The client's varc numbers when they change; never the text ones, which hold what is typed. |
| Mouse position | Where the mouse is over the game, once a tick when it moved. |
| Tick and frame timing | How long all plugins' tick handlers took together, the slowest client tick, how long frames took to draw. |

## Replay

A recording is the game's side of a session: what your plugin would have seen, tick by tick. A replay test feeds it
through your plugin's own logic and checks what the logic decided against something the recording itself can vouch
for. It is a unit test, so it runs wherever your tests run (`./gradlew test`, IntelliJ), with no client open.
Nothing from Black Box is needed at build time.

1. **Copy `src/test/java/com/blackbox/replay/Recording.java`** into your plugin's test sources and change its
   package. It needs only Gson, which RuneLite's client already brings onto the test classpath. How to credit it is
   in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
2. **Keep recordings out of git.** Make a `recordings/` folder, add it to `.gitignore`, and copy each recording's
   `events.jsonl` there, never the pictures. Let the test skip itself when the folder is missing, so CI and the
   Plugin Hub's build stay green without recordings.
3. **Write a translator**: a small test-only class that walks the recording and, for each line, makes the call
   your plugin would have made in game, then asks your logic what it thinks.
4. **Check against the recording, not against yourself.** Pick something the game states outright now and then,
   which your logic only estimates in between, and compare the two every time the game states it.

[`REPLAY.md`](REPLAY.md) has the steps in full, a minimal test, and a paragraph to hand an AI assistant to add replay
to a plugin.

### A worked example: the cargo hold count in AFK Shipwrecks

Black Box was made to help develop [AFK Shipwrecks](https://runelite.net/plugin-hub/show/cargo-hold-alert), which counts
what is in a ship's cargo hold while its window is closed. The hold's real contents show only when its window
opens, so each opening is ground truth: the `items` line the game sends then must equal the count, or the count has
drifted. The replay test plays every recording through the same code the plugin runs in game and fails on any drift.

Over one afternoon the test played 21 recordings with 149 hold openings. The two recordings made that day began with
29 drifting openings; by the end all 149 matched. Each miss turned out to be an order of clicks within a single game
tick, readable because `click` lines are kept in millisecond order next to the `items` lines:

- **Deposit-all with another click in the same tick** (Walk here, a crew command, a Drop). The code treated the
  second click as cancelling the deposit; the game had already carried it out.
- **Drop clicked before Deposit-all** in the same tick: one inventory change covers both, the hold one short of the
  delta. Clicked after: either the deposit lands and the drop finds nothing, or the drop goes through and the
  deposit never happens, never both.
- **Deposit-all twice in one tick**, the second as the first landed: the second click reset the first before it had
  been counted.

The recordings also refused rules that looked right. Of seven candidate rules for the drop case, three failed on
recorded sequences before they shipped. The rule that survived shipped with those sequences as unit tests. Two of
the misses were the translator's own, which is the other lesson: a translator is code too, and the recording checks
it as well.

What a recording cannot give you: your plugin's internal state, and whatever the game shows that Black Box does not
record yet.

## Building and running from source

`./gradlew test build` builds and runs the tests; `./gradlew run` starts a developer client with only Black Box.
Never add Black Box to another plugin's launcher or build: that plugin would then need Black Box to build at all.

## License

BSD 2-Clause, like RuneLite. See [`LICENSE`](LICENSE). The parts that come from elsewhere (the id names table, from
RuneLite's API, and the Gradle wrapper) are in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md), which is also
inside the plugin JAR.
