# RuneHunter — 30-second trailer

**Two documents in one: the creative brief (for you) and the Codex prompt (§4, hand it over verbatim).**

---

## 0. The one rule

**No generated footage. Not one frame.**

An OSRS audience will identify AI-generated "RuneScape" video in under two seconds, and they will be correct, and that is the end of the conversation about your plugin. The community's tolerance for this is zero and their eye for it is excellent.

You don't need it. **The plugin's real output is already the most striking thing about it** — a miniature Dharok standing behind the Varrock bank wall is a shot nothing else in OSRS produces. The scarcity isn't footage, it's *clean* footage: no chatbox, no minimap, no health bars, a camera that moves deliberately instead of like someone dragging a mouse.

So Codex's job is **not** to make a video. It's to build the capture tooling that lets you film the real thing properly. That's the anti-slop answer, and it's also the one that produces a better trailer.

---

## 1. What the trailer has to do in 30 seconds

One job: make a viewer think *"there's a tiny Dharok hiding behind that wall and I want to find one."*

Not explain the systems. Not list features. One idea, landed hard.

The emotional beat is **discovery** — the half-second where an ordinary bit of Gielinor you've walked past ten thousand times turns out to have something in it.

---

## 2. Shot list

Timings are targets. Every shot is captured in-client.

| # | Time | Shot | Note |
|---|---|---|---|
| 1 | 0:00–0:03 | **Varrock, ordinary.** Slow dolly past the bank. Players walking. Nothing unusual. | Establish the familiar. The viewer must think they know this place. |
| 2 | 0:03–0:06 | Camera keeps moving, drifts behind the wall — **a miniature Dharok is standing there.** Hold. Don't cut. | The whole trailer is this shot. Everything else is support. |
| 3 | 0:06–0:09 | Hard cut: three quick reveals — mini abyssal demon in a dungeon corner, mini kraken on the Fremennik coast, mini Zulrah in reeds. ~1s each. | Establishes it's a world, not a gimmick. |
| 4 | 0:09–0:12 | An orb arcs from the player. Catch cinematic. Verdict splash. | The verb. Show it once, clearly. |
| 5 | 0:12–0:15 | **A shiny.** Recoloured, sparkling, half-occluded behind a tree — camera finds it slowly. | Rarity as a feeling, not a number. |
| 6 | 0:15–0:20 | Battle: telegraph icon, prayer flick, deflect flash, hitsplat. Tight and fast. | Prove it isn't a menu game. Cut on impacts. |
| 7 | 0:20–0:24 | Wide: player running through Lumbridge with a **mini-Jad following**. Other players visible. | The flex. This is the screenshot people will post. |
| 8 | 0:24–0:27 | Quick montage: GoDex filling, Trophy Room podium, a legendary spawn window. | Depth, implied not explained. |
| 9 | 0:27–0:30 | Cut to black. Title card: **RuneHunter** / *Free RuneLite plugin* / `runehunter.gg` | Three lines. Nothing else. |

**Total on-screen text: four lines.** If you're tempted to add a fifth, cut a shot instead.

---

## 3. Craft notes

**Camera.** The single biggest difference between a trailer and a screen recording is that trailer cameras move *slowly and on purpose*. Every shot should be a dolly, an orbit, or locked off — never a hand-dragged spin. This is the main thing the capture tool exists to provide.

**UI.** Everything off. No chatbox, minimap, inventory, XP drops, or plugin overlays — except the two moments where the overlay *is* the shot (shots 4 and 6).

**Cutting.** Shots 1–2 are slow and long. From shot 3 the pace doubles. Shot 7 breathes again. Slow, fast, slow is what makes 30 seconds feel composed rather than rushed.

**Colour.** Grade lightly. OSRS's palette is already distinctive; crushing the blacks and lifting saturation slightly is enough. Heavy grading reads as trying too hard.

**Sound.** The trap. Do **not** use OSRS's soundtrack — it's Jagex's, and using it on a monetised or promoted video invites a claim. Do not use anything Pokémon-adjacent. Options, cheapest first: a royalty-free licence from a library like Artlist or Epidemic; or commission an original 30-second cue from a composer for roughly $100–300. Brief them with "2004 MIDI nostalgia, modern production" and they'll know what you mean.

Diegetic sound matters more than music anyway: the orb throw, the deflect, the catch confirm. Get those clean and the music can be sparse.

---

## 4. Codex prompt — hand this over verbatim

> You are working in a Java 11 RuneLite plugin called RuneHunter, at `~/Workspace/Git/runego`, package `com.runehunter`. It spawns miniature OSRS creatures as `RuneLiteObject`s at real world tiles, and renders battles through a full-screen `Overlay`.
>
> **Build a cinematic capture mode for recording a trailer.** You are not generating any video or images — you are building in-client tooling so a human can film real gameplay cleanly. Everything must be gated behind RuneLite's developer mode and must not ship to normal users.
>
> **Existing context you must read first:** `RuneHunterPlugin.java` (plugin lifecycle, `@Inject @Named("developerMode")`, the `syncWindowsToBattle` window-hiding pattern), `SpawnManager.java` (how objects are placed at tiles), `ui/DevConsole.java` and the PokeDev Toolkit tab (how existing dev tools are registered), `ui/BattleOverlay.java` (full-screen overlay rendering).
>
> **Deliver these, in this order:**
>
> **1. `com.runehunter.cinematic.CameraRig`** — programmatic camera control using RuneLite's camera APIs (`client.setCameraPitchTarget`, `setCameraYawTarget`, and the free-camera/`setCameraMode` facilities where available). Support three move types, each taking a duration in client ticks and easing in and out with a smoothstep curve:
> - `orbit(WorldPoint centre, int radiusTiles, int startYaw, int endYaw, int pitch, int ticks)`
> - `dolly(WorldPoint from, WorldPoint to, int height, int pitch, int ticks)`
> - `hold(WorldPoint at, int yaw, int pitch, int ticks)`
>
> Moves must be queueable so several run back to back as one continuous take. Restore the player's original camera state on stop, and on plugin shutdown.
>
> **2. `com.runehunter.cinematic.CleanScreen`** — hide RuneLite and game UI for the duration of a take: chatbox, minimap, inventory, XP tracker, and all RuneHunter overlays. Take a bitmask so a shot can keep specific overlays (the battle overlay and catch cinematic need to stay visible in some shots). Restore exactly what was hidden, and be safe against a take being aborted mid-way.
>
> **3. `com.runehunter.cinematic.Shot`** — a declarative shot: camera moves, which overlays stay visible, an optional creature to force-spawn at a given tile with a chosen animation, and a countdown before capture begins so the operator can get in position.
>
> **4. Shot definitions** for the nine shots described in §2 of `notes/trailer-brief.md`, as a `TrailerShots` class of static factory methods. Take the world coordinates as parameters — do not hardcode locations.
>
> **5. A Toolkit tab section** in the PokeDev console: one button per shot, plus Stop, plus a "restore everything" panic button. Follow the existing Toolkit button registration pattern exactly.
>
> **6. `tools/assemble-trailer.sh`** — an ffmpeg script that takes numbered clips from a directory, applies a light grade (mild contrast curve, +8% saturation), cuts them to the timings in the brief, crossfades where the brief says to, overlays a title card PNG for the last 3 seconds, and outputs 1080p60 H.264 plus a 4K variant. It must fail loudly with a clear message if a clip is missing or a duration doesn't match, rather than silently producing a short video.
>
> **Hard constraints:**
> - Java 11. No new dependencies — RuneLite's transitive set only. This is a Plugin Hub plugin and every added dependency lengthens review.
> - No reflection. It is a Plugin Hub violation and an automatic rejection.
> - All client API access on the client thread via `ClientThread.invoke`.
> - Everything gated on the injected `developerMode` flag, matching how `DevConsole` and the `::rh` commands are already gated. Nothing in this feature may be reachable in a normal client.
> - Never leave the player's camera or UI in a modified state — restore on stop, on abort, and on plugin shutdown.
> - Verify with: `javac -encoding UTF-8 --release 11 -cp "libs/*" -d out $(find src/main/java -name "*.java")`. It must compile with zero errors before you report done.
>
> Ask me before changing anything outside `com.runehunter.cinematic`, `ui/DevConsole.java`, and `tools/`.

---

## 5. Getting it in front of people

**Don't pay a streamer.** A paid placement gets read as an ad and converts badly in this community. What actually works is a plugin good enough that someone covers it because it's interesting — which is how the OSRS TCG plugin went viral.

Order of operations:

1. **Ship to the Plugin Hub first.** "Available now in the Plugin Hub" is a video someone can make. "Coming soon, DM me for a build" is not.
2. **Post the trailer to r/2007scape yourself**, on launch day, as the developer, with a plain title. That subreddit surfaces plugins reliably and creators watch it for material.
3. **Then approach creators**, with the trailer and a working install link. One clip of a shiny mini-Vorkath behind a wall does more than any pitch email.
4. **Have `runehunter.gg` live before any of it.** Every view you get is worth more when it lands somewhere real.

The trailer's job is to make step 3 unnecessary.
