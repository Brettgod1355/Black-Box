# Reading a recording

A Black Box export is plain text. You need a text editor, or `grep`, or an AI assistant with this page and
`FORMAT.md` in front of it. The five questions a developer asks, and where the answers are.

## 1. What was the player running?

The first line of `events.jsonl` (`"e":"session"`): client version and commit, launcher, Java, operating
system, window size and mode. The `plugins` line right after it: every installed plugin, on or off, with the
Plugin Hub version. (Formats 4 to 7 also had a `watch` line naming the plugin the player picked.)

    grep -m1 '"session"' events.jsonl
    grep -m1 '"plugins"' events.jsonl

## 2. How was my plugin set?

The `settings` line whose `plugin` is yours: every setting as the client stored it. Then every `setting` line
with your `group`: each change, with its new value and when. Free text is as the player typed it, so a block
list or a highlight list is readable; webhooks, keys and passwords are withheld.

    grep '"settings".*"plugin":"Safe Sailing"' events.jsonl
    grep '"setting".*"group":"safesailing"' events.jsonl

## 3. What happened at the moment the player flagged?

Find the `mark` line (its `text` is what the player wrote), take its `tick`, and read the lines of the ticks
before it: `chat` (what the game said), `var` (what changed; `names.txt` names each varbit), `items`, `open`,
`click`, `npcAdd`, `npcTarget`, `hit`. The `tick` lines give the player's position and, aboard, the hull's
position and heading. If pictures were included, `shots/` has one named with the same tick.

    grep '"mark"' events.jsonl
    grep '"tick":1234,' events.jsonl

## 4. Did anything go wrong in the client?

Start with `README.txt`: it sums up every warning and error in `client-log.txt`, grouped by level, logger,
message and the code the stack trace starts in, biggest group first, so you see at once whether any are your
plugin's. A log after a login or a death is mostly other plugins' null pointers on a player that is not there
yet. Then `client-log.txt` itself: search for `WARN` and `ERROR`. Every one from the whole recording is in an
export, with the first ten frames of the stack trace. A plugin's own log lines, at INFO and above, are there too.

    grep -n 'ERROR\|WARN' client-log.txt

## 5. Was it the game or the plugin?

Compare what the game sent (`chat`, `var`, `npcAdd`, `items`) with what your plugin should have done with it.
If your plugin has a replay test (see the README), run the recording through it: the test shows what your
logic decided on every tick of that session, and a fix can be checked against the same file.

From format 7, an `npcSay` line's `shown` is what the NPC showed once every other plugin had handled the line, and
an `npcShown` line what it showed a client tick later when that changed again: a crewmate's line blanked by one
plugin and written back by another reads as those two, and the `plugins` line says which plugins could have.

## Handing it to an AI assistant

Give the assistant the export's files and say:

> This is a Black Box recording from RuneLite. Read FORMAT.md for the line kinds and fields, and READING.md for
> how to read it. The player reported: "<what they said>". Find the `mark` line, reconstruct what the game and
> the plugin <name> did in the minute before it, note any WARN or ERROR in client-log.txt, and tell me what the
> plugin saw and what it should have done.

With the two documents in front of it, an assistant can name varbits from `names.txt`, line up the client log
with the ticks (the `session` line's `startedAt` plus any line's `ms` is its wall-clock moment), and read the
settings as the player had them.
