# RuneHunter → RuneLite Plugin Hub: process, checklist, and realistic ETA

*Compiled July 31, 2026 against the current Plugin Hub rules.*

---

## 1. How approval actually works

There is no app-store portal. The Plugin Hub is a GitHub repo, and "approval" is **a maintainer merging your pull request**.

```
your public plugin repo (BSD-2, runelite-plugin.properties)
        │  git commit hash
        ▼
fork runelite/plugin-hub → add one file → open PR
        │
        ▼
review: (a) security  (b) Jagex third-party-client compliance
        │  changes requested → push new commit → update hash in the PR
        ▼
merged → hub build runs → plugin appears in the in-client Plugin Hub
```

**What reviewers check — only two things:**

1. **Security.** That the plugin isn't malicious: no credential theft, no malware. Enforced by rules banning reflection and native code, limiting dependencies, and automated code scanning.
2. **Jagex rule compliance.** Best-effort, and explicitly acknowledged as subjective. The operative sentence: *"If it is difficult for us to ensure the plugin isn't against the rules we will not merge it."*

**What they explicitly do NOT check:** whether your plugin is good, useful, performant, or compatible with anything else. Nobody is grading the game design. That is entirely on you.

**The 2026 wrinkle that matters for your ETA:** since April 2026, RuneLite runs an automated review bot that can approve plugin *updates* without a human. Their stated reason is blunt — most plugin code is no longer human-written and they can't fund human review at that scale. This is good news for your future updates and **irrelevant to your first submission**: a brand-new plugin, especially one this size, gets human eyes.

---

## 2. Pre-submission checklist

### 2.1 Repository requirements (hard blockers)

- [ ] **Public GitHub repo** for the plugin itself
- [ ] **BSD 2-Clause "Simplified" license** as `LICENSE` in the repo root — required, not optional
- [ ] **`runelite-plugin.properties`** in the repo root:
      ```properties
      displayName=RuneHunter
      author=<your handle>
      description=Catch miniature OSRS creatures hidden in the 3D world
      tags=collection,creatures,minigame,companion,pets,catch
      plugins=com.runehunter.RuneHunterPlugin
      version=1.0.0
      build=standard
      ```
- [ ] **`build=standard`** — this makes RuneLite replace your `build.gradle` at submission time and **expedites review**. Use it. It only works if you have zero non-transitive dependencies (see below).
- [ ] **`icon.png`**, max 48×72 px, repo root. Optional, but a plugin with no icon looks abandoned in the Hub list.
- [ ] **README.md** with screenshots/GIFs — the Hub surfaces it, and it's your only storefront.
- [ ] Java 11 target.

### 2.2 Dependencies — read this before anything else

> "New dependencies significantly extend review time" and every non-transitive dependency needs Gradle cryptographic hash verification plus **manual maintainer verification**.

- [ ] **Audit `build.gradle` for any dependency that isn't already transitive from `runelite-client`.** Gson, OkHttp, Guava, Lombok, and the RuneLite API are already there — use the injected instances, don't re-declare them.
- [ ] If you find one, ask whether you can delete it. Almost always yes. **Every dependency you remove is days off your review.**

### 2.3 Code rules that will get you rejected

- [ ] **No Java reflection. Anywhere.** This is strictly forbidden and the code scanner looks for it. ⚠️ **Audit your `dumpAnims` tooling and `AnimationLearner` specifically** — animation/model introspection code is exactly where reflection sneaks in.
- [ ] **No native code.**
- [ ] **No `Thread.sleep()`**, ever.
- [ ] **No `executor.awaitTermination()`** and nothing that blocks shutdown.
- [ ] **File I/O only inside `.runelite/`** subdirectories. (`ApiStateWriter` already complies — it writes under `RuneLite.RUNELITE_DIR`.)
- [ ] **Open URLs with `LinkBrowser`**, never `java.awt.Desktop`.
- [ ] **Resources via `getResourceAsStream()`**, not `getResource()` — otherwise your art breaks once it's inside a jar. ⚠️ Check every `pod_*.png` / cutscene strip load path.
- [ ] **No `log.info` on per-tick or per-frame paths** — debug level only.
- [ ] **Don't rescan the whole scene every tick.** Maintain your own collections off events.
- [ ] **Inject `Gson` and `OkHttpClient`**, don't construct them.
- [ ] **All client API access on the client thread** (`clientThread.invoke`). You already fixed the AWT-thread crash in `decline()` — do one more sweep for the same class of bug, it's the single most common cause of a "works on my machine" report.
- [ ] **Clean `shutDown()`**: every `RuneLiteObject` set inactive, every overlay deregistered, every scheduled future cancelled, every listener unregistered. A plugin that leaves objects in the scene after being disabled is a fast rejection.

### 2.4 RuneHunter-specific hardening

*Status as of the July 31 repo audit — ✅ = verified done, ⬜ = outstanding.*

- ✅ **Package renamed** to `com.runehunter.*`, classes `RuneHunter*`, all 40 package declarations matching their directories, zero residual `runego` in the tree.
- ✅ **Config group locked** at `runehunter` (`@ConfigGroup`, `CollectionStore`, `TradeLedger`, `AnimationLearner`). Frozen from here — renaming after the first release silently wipes every user's collection.
- ✅ **Reflection audit: clean.** No `java.lang.reflect`, no `setAccessible`, no `Class.forName`, no `newInstance`. The only `getClass()` in the codebase is `e.getClass().getSimpleName()` for an error string. `dumpAnims` and `AnimationLearner` — the two files flagged as most likely to hide reflection — are both clear.
- ✅ **No `Thread.sleep`, no `awaitTermination`, no `java.awt.Desktop`, no `getResource()`.**
- ✅ **`log.info` count: 2**, both one-shot (NPC scan complete, reseed summary). Neither is on a per-tick or per-frame path. Consider dropping the reseed one to `debug` since it fires every window and scene load.
- ✅ **LICENSE** is BSD 2-Clause; **`icon.png`** is 48×48 (limit is 48×72).
- ✅ **Zero non-transitive dependencies** — `build.gradle` declares only `net.runelite:client` (compileOnly) and junit. `build=standard` is now set.
- ⬜ **Strip every dev tool from the shipped build:** `DevConsole`, the PokeDev Toolkit tab, `::rh` chat commands, the `style <melee|ranged|magic|disable|skull>` forcer, the six secret force-spawn buttons, and the PokeDev launcher button on the plugin panel. Reviewers read developer backdoors as unreviewed surface area, and a button that forces combat outcomes or spawns hidden content reads badly at a glance even though it's harmless. Cleanest: strip from the release build entirely; second best: gate behind `RuneLite.isDevMode()` **and** remove the panel button. Touches `DevConsole.java`, `RuneHunterPanel.java`, `RuneHunterPlugin.java`, `TrophyRoom.java`, `CatchManager.java`, `SecretIds.java`.
- ⬜ **Decide what to do with `src/tools/java`.** The cache-dump tooling (`LocDump`, `NpcAnimDump`) pulls `net.runelite:cache`, a dependency the plugin itself doesn't need. Under `build=standard` it never reaches the built artifact — but it's still visible in the repo a reviewer reads. Cleanest is to move it to a separate `runehunter-tools` repo and keep the submission repo to plugin code only.
- ⬜ **Optional: config migration shim.** The group rename means collections stored under `runego.*` are no longer read. That's only your own dev data, and the Toolkit can regrant it — but if you'd rather keep it, a one-time copy from the old group on first startup is ~30 lines.
- ⬜ **README rewrite.** The current one is from v0.1 and describes a much smaller plugin.
- [ ] **Export/backup button for the collection.** Not required, but the first support request you get will be "I reinstalled and lost 40 hours."
- [ ] **Performance sanity pass**: the ~8-concurrent-object cap, model caching per creature+shiny, lazy loading. Reviewers don't check performance, but the Hub listing's reviews do.
- [ ] Art is **not** a blocker. Podium strips fall back to silhouettes; ship without them and add art in an update (which the bot can auto-approve).

### 2.5 Write the PR description to do the compliance work for you

This is the highest-leverage 20 minutes in the whole process. The reviewer's job is to convince themselves your plugin isn't against the rules; if that's hard, they don't merge. Make it easy. State plainly:

- **No RuneHunter server, and no third-party server of any kind.** No telemetry, no account data, no external API. The plugin's own code makes zero HTTP calls — audited and confirmed, the only `java.net` usage in the codebase is `URLEncoder`/`URLDecoder` for string escaping.
- **The one networking path is RuneLite's own Party service**, used for creature trading and friendly duels — the same mechanism the vanilla Party plugin and the OSRS TCG plugin use. It is inert unless the user deliberately joins a party, and it carries only the creature being traded and duel state. Say this explicitly and say it early; it is much better coming from you than discovered by a reviewer. (See the JebScape PR, where nearly the whole thread was privacy disclosure for a third-party server — Party avoids that conversation, but only if you frame it yourself.)
- **No gameplay advantage.** Renders only its own `RuneLiteObject`s. Surfaces no information about real NPCs, players, or drops that the game doesn't already show.
- **No automation.** Never sends input, never acts for the player.
- **Purely additive and cosmetic.** Removes nothing, trivializes no existing content.
- **Nothing crowdsourced.** No data about other players is collected or shared — this is an explicitly rejected category, so say the words.
- **All models come from the player's own game cache**, scaled and recoloured client-side.
- **Precedent:** point at the OSRS TCG plugin (collection meta-game, merged) and JebScape (custom rendered entities in-scene, reviewed and discussed openly).
- **Integration API:** describe it in one sentence as *local-only broadcast via config keys and files under `.runelite`* so nobody has to reverse-engineer why you're writing a config group.

### 2.6 Submission steps

1. Finalise and push your plugin repo. Copy the **40-character** commit hash.
2. Fork `runelite/plugin-hub`.
3. New branch. Create `plugins/runehunter` (no extension) containing exactly:
   ```
   repository=https://github.com/<you>/runehunter.git
   commit=<40-char hash>
   ```
4. Commit, push, open a PR against `runelite/plugin-hub` master.
5. Paste the §2.5 description into the PR body.
6. On requested changes: push to your plugin repo, then **update the hash in the same PR**. Don't open a new one.
7. Be patient and don't bump the thread. Maintainers have stated that **updates to existing plugins are prioritised over new submissions** — that's the queue you're in.

---

## 3. ETA

RuneLite publishes **no SLA and no target turnaround**. Anyone quoting you a firm number is guessing. What we can anchor on: the review guidance's own emphasis on patience, the stated prioritisation of updates over new plugins, and observed precedent — JebScape's submission sat over a month before its first maintainer pass.

Working from today, **July 31, 2026**:

| Phase | Work | Duration |
|---|---|---|
| **Hardening** | Package rename, dev-tool stripping, reflection audit, shutdown sweep, resource-loading fix, dependency audit | **1–2 weeks** |
| **Packaging** | LICENSE, properties file, icon, README with GIFs, FAQ, API docs | **2–4 days** (parallel) |
| **Integration API** | Wire `ApiBroadcaster` + `ApiStateWriter` into existing systems, config toggle | **2–4 days** (parallel) |
| **Playtest + fix** | Battles, animation spot-check, a clean multi-hour session with no exceptions in the log | **3–5 days** |
| → **Submit PR** | | **~mid-to-late August 2026** |
| **Review queue** | New plugin, human reviewer, non-trivial size | **2–6 weeks**, realistically |
| **Change requests** | Assume at least one round | **+3–7 days** |
| → **Live in Hub** | | **late September – mid October 2026** |

**Best case** if review is quick and clean: ~3 weeks from submission, so early September.
**Worst realistic case:** a reviewer wants meaningful changes to how battles or spawns work, adding a month or more.

**Three things that would blow this up, all avoidable:**

1. **Adding any third-party dependency.** Explicitly called out as significantly extending review. Don't.
2. **Adding a backend/server before launch.** Instantly imports the entire privacy-disclosure review conversation. Ship v1 fully local; add a leaderboard in v1.2 when you have users to justify it.
3. **Leaving dev commands in.** Cheap to fix now, expensive to argue about mid-review.

**After the first merge, everything gets faster.** Updates route through the auto-approval bot, so creature waves and art drops can ship in days rather than weeks. Getting v1 through the door is the whole game — that's the argument for shipping the 87 creatures you have rather than waiting on art, trading, and duels.

---

## Sources

- [runelite/plugin-hub README](https://github.com/runelite/plugin-hub)
- [Plugin Hub Review policy](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review)
- [Information about the Plugin Hub](https://github.com/runelite/runelite/wiki/Information-about-the-Plugin-Hub)
- [Rejected or Rolled Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- [runelite/example-plugin AGENTS.md](https://github.com/runelite/example-plugin/blob/master/AGENTS.md)
- [JebScape submission PR #3814](https://github.com/runelite/plugin-hub/pull/3814)
- [ExternalPluginManager.java](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/externalplugins/ExternalPluginManager.java)
