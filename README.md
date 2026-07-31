# RuneHunter

**Miniature OSRS monsters, hidden in the world you already play in.**

Barrows brothers, metal dragons, slayer mobs, GWD generals — shrunk down and tucked
into real tiles across Gielinor. Behind the Varrock bank. Around a corner in a dungeon.
Against a wall where you'd never look unless you were looking.

Walk over. Throw an orb. Fill your GoDex.

A free, open-source RuneLite plugin. Inspired by creature-collecting games, built
entirely out of OSRS's own assets.

---

## What it is

- **87 creatures** across five rarity tiers, plus shiny variants and a hidden Secret Dex
- **Spawns are seeded**, not random — from world + region + a 30-minute window, so every
  player on your world sees the same creatures in the same places. Shared hunts, zero servers.
- **Orbs drop from normal kills.** Your regular grind *is* the RuneHunter grind. Killing a
  real NPC also lures its miniature counterpart to you.
- **Battles are prayer flicking.** Real-time, on the tick, with telegraphed attacks and
  overhead protection prayers. Rares smite your prayer off; epics punch through it.
- **Companions, Trophy Room, trading and friendly duels** over RuneLite's Party service.

No gameplay advantage. No automation. Nothing that tells you anything about the real game
you couldn't already see.

---

## Who's building this

I've spent over ten years in IT, working in systems infrastructure with a systems and
security focus. That's my day job and it's how I think.

I've been playing RuneScape on and off since 2006. This game has been part of my life for
most of my life, and the nostalgia is most of why I'm still here.

But the thing I actually love about OSRS isn't the game — it's what the community keeps
building on top of it. Snowflake accounts. Self-imposed rulesets nobody asked for. Ironman
progression series. Locked-region runs. Escape-room style challenges built out of ordinary
game systems. People invent entire genres inside a twenty-year-old MMO, and then other
people watch it for hours.

RuneHunter is me adding one more thing to that pile. Not more efficient — more *to do*.
There are enormous parts of Gielinor nobody visits because there's no reason to go. This
puts a reason on those tiles.

---

## On security

This is where my day job and my hobby collide, and it's the part I actually worry about.

A RuneLite plugin runs code inside your game client. It can see what you see. The Plugin Hub
is reviewed and RuneLite's maintainers do good work, but they have said publicly that the
volume of submitted code now exceeds what humans can review by hand. At the same time,
generating a plausible-looking plugin has never been easier.

That is a supply-chain problem wearing a friendly hat. People are about to install a lot more
third-party code into a client attached to accounts worth real money, and most of them will
never read a line of it.

So this plugin is built to be boring in all the ways that matter:

| | |
|---|---|
| **No third-party dependencies** | Nothing but RuneLite itself. Nothing to compromise upstream. |
| **No reflection, no native code** | Both are Plugin Hub violations, and both are how plugins get sneaky. Audited clean. |
| **No RuneHunter server** | There isn't one. No account, no login, no telemetry, nothing that knows you exist. |
| **No HTTP calls of its own** | The only networking is RuneLite's own Party service, and only when you deliberately join a party. |
| **Local storage only** | Your collection lives in your own `.runelite` directory. |
| **Public source, BSD 2-Clause** | Read it. Fork it. Verify the table above yourself. |

If you take one thing from this README, take this: **read what you install, or install things
whose authors show their work.** That standard applies to me too.

---

## On AI

I use AI to build this, and I'd rather say so here than have someone find out and conclude I
was hiding it.

The reason it's worth being direct about: RuneLite's maintainers have already stated publicly
that most plugin code submitted to them is no longer written by humans, and that they can't
fund human review at that scale. That's not a forecast, it's the current state of the Plugin
Hub, described by the people who run it. So the useful question isn't whether AI-assisted
plugins exist — it's whether they're careful or careless.

Every design decision here is mine: the tiers, the catch rates, the prayer-flick combat, the
spawn seeding, which creatures made the roster, which easter eggs are funny and why. AI is
the fastest typist I've ever worked with. It isn't the thing deciding what this game is.
Nothing ships that I haven't compiled and played.

Slop is what you get when nobody is accountable for the output. I'm accountable for this one.
The source is public and the licence is BSD 2-Clause, so every claim on this page is something
you can check rather than take my word for. If it's bad, that's on me — tell me in the issues.

---

## On Jagex

I want this community to keep the freedom it has, which means not abusing it.

RuneHunter is built to sit inside Jagex's third-party client rules on purpose, not by accident:
no gameplay advantage, no automation, no information the game doesn't already give you, nothing
that trivialises existing content. The creatures it renders are not real game entities and never
touch your account state. Every model comes from the game's own cache, rendered client-side in
your own client.

The freedom to build things like this exists because people have mostly not taken the piss with
it. I'd like it to still exist in ten years.

---

## Build on it

RuneHunter ships a **public integration API**. Other plugin authors can react to spawns, catches,
shinies, battles and dex progress — custom highlights, sound packs, TTS shiny alerts, OBS
overlays, Discord webhooks, flick-accuracy trackers.

You don't need my jar, a build change, or my permission. Copy one self-contained file into your
plugin and you're done.

Full spec: [`docs/integration-api.md`](docs/integration-api.md)

This is deliberate. Somebody building an escape-room series shouldn't have to fork this plugin —
they should be able to react to it from their own. The community inventing new genres on top of
old systems is the best thing about OSRS, and an API is how you get out of its way.

If you want to make content with this — streams, videos, series, whatever — go ahead. No
permission, no revenue share, no attribution required. A link is appreciated and that's it.

---

## Install

RuneLite → wrench icon → Plugin Hub → search **RuneHunter** → Install.

*(Pending Plugin Hub review. Until then, clone and run locally.)*

---

## Links

- **Site:** [runehunter.gg](https://runehunter.gg)
- **FAQ:** [`docs/FAQ.md`](docs/FAQ.md)
- **Integration API:** [`docs/integration-api.md`](docs/integration-api.md)
- **Bugs and creature requests:** GitHub issues

---

*An unofficial third-party plugin. Not affiliated with or endorsed by Jagex Ltd. Old School
RuneScape is a trademark of Jagex Ltd. Not affiliated with or endorsed by Nintendo, Game Freak,
or The Pokémon Company.*
