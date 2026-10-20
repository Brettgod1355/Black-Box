# Replay: running a recording through your plugin

## What it is for

A recording is what the game showed the client during a real session. Replay runs that recording through your
plugin's own logic, tick by tick, inside a unit test, and checks what the logic decided. It turns "it happened
once on someone's client" into a test you can run a hundred times: the bug reproduces on your machine without
playing, a fix is checked against the same session, and the test stays as a guard against the bug coming back.

Replay is not a video player. It does not drive a client, draw overlays or press anything. It feeds the lines of
`events.jsonl` to the part of your plugin that makes decisions, and asserts on the decisions.

## Where it lives

In **your plugin's test sources**, not in Black Box. Nothing in your plugin's main code or build depends on Black
Box, so the Plugin Hub builds your plugin without it. Two things go into your repository:

- `src/test/java/<your package>/replay/Recording.java`: a copy of Black Box's reader (this repository,
  `src/test/java/com/blackbox/replay/Recording.java`), with only the package line changed. It needs nothing but
  Gson, which RuneLite's client already puts on the test classpath.
- Your replay test: a class that reads a recording, turns its lines into what your logic expects, and asserts.

Recordings themselves stay out of the repository. Put them in a git-ignored `recordings/` folder in your clone
(`recordings/<any name>/events.jsonl`); the test skips when the folder is missing, so the Hub's build and a
fresh clone pass without them.

## What a developer does, step by step

1. Copy `Recording.java` into your test sources and change its package.
2. Add `recordings/` to `.gitignore`.
3. Find the part of your plugin that decides things. Replay works when that logic is a plain class or method
   with plain inputs: "given these varbit values, is the boat protected?", "given this chat line, which hazard
   fired?", "given this item container, how many are in the hold?". If your decisions live inside
   `@Subscribe` methods that read the `Client` directly, move the decision into a method that takes values and
   returns an answer; the event handler then calls it. That refactor is the real work of making a plugin
   replayable, and it makes the plugin easier to test in every other way too.
4. Write the translator: walk `recording.byTick()`; for each tick, feed the lines your logic cares about
   (`var` for varbit values, `chat` for game messages, `items` for containers, `npcAdd`/`npcMove` for NPCs,
   `tick` for the player's or the hull's position; see `FORMAT.md`) into that logic, and record what it decided.
5. Assert. Two shapes work well: a report (write what the logic decided on every tick to `build/replay/<name>.txt`
   and read it yourself) while you are exploring, and hard assertions ("the warning fired before tick 405",
   "the count never drifted from the real container") once you know what right looks like.
6. Keep the test skipping without recordings (`Assume.assumeTrue(Files.isDirectory(RECORDINGS))`), so it costs
   nothing on a machine that has none.

## A minimal replay test

```java
public class MyReplay
{
	private static final Path RECORDINGS = Paths.get("recordings");

	@Test
	public void theWarningFiresBeforeTheGameDoes() throws IOException
	{
		Assume.assumeTrue(Files.isDirectory(RECORDINGS));
		for (Path file : eventsFiles(RECORDINGS))
		{
			Recording recording = Recording.read(file);
			Map<Integer, Integer> varbits = new HashMap<>();   // the latest value of every varbit
			MyLogic logic = new MyLogic();
			for (Map.Entry<Integer, List<Recording.Line>> tick : recording.byTick().entrySet())
			{
				for (Recording.Line line : tick.getValue())
				{
					switch (line.kind())
					{
						case "var":
							varbits.put(line.number("varbit", -1), line.number("value", 0));
							logic.onVarbits(varbits);
							break;
						case "chat":
							logic.onGameMessage(line.text("text"));
							break;
						default:
							break;
					}
				}
				logic.onTick(tick.getKey());
			}
			assertTrue(file + ": no warning", logic.warnedAtTick() >= 0);
		}
	}
}
```

The two plugins this was built for show the two shapes. AFK Shipwrecks' `HoldCountReplay` feeds `items`,
`sound` and `click` lines to the hold counter and compares its count with the real container on every tick
(that replay found and fixed a count that drifted in 29 places). Safe Sailing's `HazardEdgeReplay` feeds `var`
lines to the protection reader and the `chat` lines to the hazard matcher, and writes a report of where each
warning fired against where the game's red line came.

## What a recording must hold for your replay

Only what your logic reads. Check `FORMAT.md` for the line kinds; if your plugin decides on something Black Box
does not record, that is a feature request for Black Box, not a reason to read the client in the test. A
recording from a player is enough as long as it covers the moment: ask them to Mark it, and export at least the
minutes before.

## For an AI assistant

To add replay to a plugin, or to use one, give the assistant this file and `FORMAT.md`, the plugin's source, and
one recording, and say:

> This is a RuneLite plugin and a Black Box recording. Read REPLAY.md and FORMAT.md. Find the plugin's decision
> logic and, if it reads the Client inside event handlers, propose the smallest refactor that moves each decision
> into a method with plain inputs. Then write a replay test in the plugin's test sources, modeled on the minimal
> test in REPLAY.md, that feeds the recording's lines to that logic tick by tick and asserts <what should have
> happened>. The recordings folder is git-ignored and the test must skip when it is missing. Do not add any
> dependency on Black Box.

To read what a replay found, give it the report the test wrote (`build/replay/<name>.txt`) and the recording,
and ask it to line the report's ticks up with the `chat`, `var` and `mark` lines around them.
