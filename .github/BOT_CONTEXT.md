# Notes for the helper bot

Helper-Bot (https://github.com/Brettgod1355/Helper-Bot) adds this file to Claude's instructions for the PR audit
here. The README, `FORMAT.md`, `READING.md` and `REPLAY.md` are the full guide.

## What the plugin is

Black Box is a RuneLite plugin for troubleshooting other plugins. It records what the game sent and what the client
did while the player is logged in (game messages, items, interfaces, values, positions, every plugin's settings, the
client's log lines, frame rate, optional screenshots) into its own folder on the player's computer. The player clips
the minutes around a bug and sends the zip to a plugin's developer, who reads `events.jsonl` (often with an AI
assistant) or replays it in their own tests. It never sends anything anywhere.

## Privacy rules a change must never break

Flag any change that weakens one of these, even when it looks harmless:

- **Other players are never recorded:** their chat, overhead text, hits, names in menu targets or click targets.
  The player's own name becomes `<you>`. Friend login notices keep their moment, never the name.
- **The bank PIN is never recorded.** While the keypad (interface 213) shows, no clicks, game values, positions or
  screenshots, and a frame that arrives after it opens is dropped. No key listener beyond the Mark hotkey. Any new
  event kind must respect the keypad pause.
- **No login screen or welcome screen in a screenshot.** Frames are kept only while logged in or loading, and not
  while the welcome screen after login shows.
- **Hide the chat box** (on by default) blacks out the chat box and split private chat lines in screenshots.
- **Settings:** an item marked `secret`, and any value or key that looks like a webhook, key, password or token, is
  always withheld. Typed (String) settings are left out of every export unless the player ticks them.
  Undeclared keys and odd hidden values are written as length and hash only.
- **Nothing outside its own folder:** files only through `Filepath`; RuneLite's log file is never read (log lines
  come from an appender inside the client, with the player's name and home folder replaced).

## What the plugin does not do, on purpose

- No input of any kind, no network, no reflection and no stack walk (not even to name a plugin), no writes to
  another plugin's config. A plugin conflict is seen in what was shown, never traced to its caller.
- Never shipped inside another plugin, and no other plugin refers to it.

## Format

- `SessionLog.FORMAT` goes up whenever a line's shape changes, and `FORMAT.md` plus `replay/Recording.java` must
  keep reading every older format. Flag a changed line without a format bump or a `FORMAT.md` entry.
- Event handlers are named exactly `on<Event>`; a second handler for one event is a lambda registered and
  unregistered by hand.
