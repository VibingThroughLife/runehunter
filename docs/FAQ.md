# RuneHunter — FAQ

*A creature-collecting layer for Old School RuneScape, built as a RuneLite plugin.*

---

## The basics

### What is RuneHunter?

Miniature versions of OSRS monsters — Barrows brothers, metal dragons, slayer mobs, GWD generals — spawn hidden in the 3D world around you. Behind the Varrock bank. Around the corner in a dungeon. Tucked against a wall where you'd never look unless you were looking.

You walk over, you throw an orb, you try to catch it. Rarer creatures are harder to catch and harder to find. Every creature has a shiny variant. You fill out a collection log (the "GoDex"), and you can set any creature you've caught as a companion that follows you around.

Orbs — the catch currency — drop from normal NPC kills. Your regular grind *is* the RuneHunter grind.

### Is this a separate game?

No. It's a RuneLite plugin that runs on top of your normal OSRS client. You're still playing OSRS. Nothing about your account, your XP, your bank, or your gameplay changes.

### Does it cost anything?

No. It's free, it's open source, and there is nothing to buy. Orbs come from playing the game. There is no currency, no store, no premium tier, and no plan to ever add one. A catch-rate meta-game with real money attached would be a gambling product, and that's not what this is.

### Does it work on ironman / HCIM / group ironman?

Yes. Collections are stored per-account locally, and nothing RuneHunter does touches your actual account state. Your HCIM is exactly as safe (or not) as it was before you installed a plugin — see the honest answer below.

---

## Is this allowed?

### Will this get me banned?

RuneHunter is designed from the ground up to sit inside Jagex's third-party client rules:

- **No gameplay advantage.** It doesn't tell you where real NPCs are, doesn't help you fight anything, doesn't reveal information the game doesn't already give you. The creatures it renders are not real game entities.
- **No automation.** It never clicks, moves, or acts for you. Every catch is you walking there and clicking.
- **No RuneHunter server.** There isn't one. Your collection lives in a file on your own computer. The only networking anywhere in the plugin is trading and duelling, which run over RuneLite's own built-in Party service — see below.
- **Cosmetic and additive only.** It adds a visual collection layer. It removes nothing and trivializes nothing.

That's the strongest compliance posture a plugin can have. It's also why RuneHunter is submitted to the official RuneLite **Plugin Hub**, where RuneLite maintainers review submissions for exactly these things.

To be straight with you: no plugin author can *promise* you won't be banned, and anyone who does is lying. Jagex sets the rules and can change them. What we can tell you is what the plugin actually does, which is the list above, and that the source is public so you can verify it yourself.

### Why does the Plugin Hub show a warning?

RuneLite shows a warning on all Hub plugins because they're third-party code that RuneLite doesn't guarantee. That's a standard notice on every plugin in the Hub, not a flag on this one.

### Isn't this a Pokémon ripoff?

It's obviously Pokémon-inspired, and we're not going to pretend otherwise. But it's built entirely from OSRS's own assets — every creature is a real OSRS NPC model, loaded from your own game cache, shrunk down. There's no Pokémon art, no Pokémon names, no Pokémon code. Orbs, not poké balls. GoDex, not Pokédex. RuneHunter, not RuneGo.

---

## Who made this and why

### Who are you?

One OSRS player, **VibingThroughLife**. Not a studio, not a company. Someone who has played this game for years and wanted a reason to walk around parts of Gielinor that stopped mattering a long time ago.

RuneHunter started as "what if a tiny Dharok was hiding behind Lumbridge castle" and turned into 87 creatures, real-time prayer-flick battles, and a trophy room.

### Why did you make it?

**Because OSRS is better with more to do in it.** Not more efficient — more *to do*. There are hundreds of hours of content most players never touch because there's no reason to go there. RuneHunter puts a reason on those tiles.

**Because the community keeps proving it wants this.** The OSRS TCG plugin went viral within days of hitting the Plugin Hub with nothing but a booster-pack loop. The appetite for collection meta-games on top of OSRS is real and it's not being served.

**Because content creators need new formats.** Streamers and YouTubers make OSRS content in a dozen different ways — speedruns, ironman progression, PvP, lore, snowflake accounts. Every one of those formats had to be invented by somebody. RuneHunter is another one: shiny hunts, dex-completion races, first-to-catch-Jad, "find the legendary before the window closes." It's inherently clippable, because the good moment is *visual* — you spin the camera and there's a shiny mini-Vorkath behind a wall.

Nobody needs permission to make content with it, monetize it, or build a series around it. That's the point.

**Because the world deserved to be the game board.** The design rule that mattered most: the collection lives in the 3D world, not in a menu. Menus are not clippable. Walking around a corner and finding something is.

---

## How it works

### How do I get orbs?

They drop from NPC kills during normal play. Four tiers, from the basic **unpowered orb** up to the near-guaranteed **eldritch orb**, with drop rates scaling to NPC combat level — so bossing pays better, but a chicken-killing ironman still progresses.

There's also the **species lure**: killing a real NPC temporarily raises the local spawn chance of its RuneHunter counterpart. On an abyssal demon task? Mini abyssal demons start showing up around you.

### How do spawns work? Do I see the same creatures as my friends?

Yes, if you're on the same world. Spawns are seeded deterministically from `(world, region, 30-minute time window)`, so everyone on your world sees the same creature in the same spot during the same window. That's what makes "it's over here!" work — and it needs no server at all.

Different worlds roll differently, which quietly makes world-hopping a hunting mechanic.

### How do battles work?

Real-time, on the OSRS tick, built around prayer flicking. Wild creatures telegraph attacks three ticks ahead with a style icon; you flick the correct overhead protection prayer to deflect. Correct prayer at impact = zero damage plus special-attack energy. Prayer points drain while lit, so flicking properly is the skilled play.

Rare-and-above creatures smite your prayer off. Epic-and-above throw skull-marked unavoidable hits that punch through prayer. It's OSRS combat literacy, applied to a collection game.

### Where is my collection saved?

Locally, in your RuneLite config directory, scoped to your account profile. There is no account, no login, and no RuneHunter server that knows you exist.

### So does anything touch the network?

One thing: **trading and duelling**, which run over RuneLite's own built-in **Party service** — the same mechanism the vanilla Party plugin and the OSRS TCG plugin use. It only does anything when you deliberately join a party with someone, and it only carries the creature being traded and the duel state. No RuneHunter infrastructure is involved, because none exists.

If you never join a party, RuneHunter never touches the network at all.

### How does trading work?

Join the same RuneLite party as a friend, then use the Party strip in the Trophy Room. Trades use the OSRS two-screen flow, and any change resets both accepts — the anti-scam behaviour you already know. Level and equipped gear travel with the creature.

Shinies can't leave by accident: a creature is only tradeable while you still own a spare plain copy of it.

### And duels?

Real-time prayer combat against a friend's companion. The challenger's client runs the simulation, and stakes are friendly — win/loss record only, nobody's creature faints. There's a **practice duel** against your own companion's mirror if you want to learn the flicking solo.

### Can I lose my collection?

If you wipe your RuneLite config, yes. Back up your `.runelite` directory if you care about it. We'll add an export button.

---

## For developers and creators

### Can I build on top of this?

Yes — that's an explicit design goal. RuneHunter ships a **public integration API** so other RuneLite plugin authors can build on it without touching our code.

Think about how popular NPC-highlight tooling is for things like imp hunting. Same idea here: RuneHunter broadcasts what's happening — creature spawned, catch attempted, shiny found, battle won, dex updated — and anyone can write a plugin that reacts to it. Custom highlights. Sound packs. TTS shiny alerts. OBS overlays that show your live dex progress on stream. Discord webhooks. Personal stat trackers. Things we haven't thought of.

The API is local-only, requires no permission from us, and has a documented stability policy. See `docs/integration-api.md`.

### Can I stream it / make videos / monetize that content?

Yes, unconditionally. No permission needed, no revenue share, no attribution required (though a link is appreciated). If you're doing something interesting with it, tell us — we'd rather signal-boost you than restrict you.

### Is the source available?

Yes, public repository, BSD 2-Clause licensed, which is what the Plugin Hub requires. Read it, fork it, verify the claims on this page yourself.

### How do I report a bug or request a creature?

GitHub issues. Include your RuneLite version, what you were doing, and a screenshot if it's visual. Creature requests are welcome — the roster expands in waves.

---

## Roadmap

**Now:** 87 creatures across five tiers plus shinies, hidden 3D spawns, orb drops and species lure, real-time prayer-flick battles, companions, GoDex, trophy room, a handful of secret creatures, and trading + duels over RuneLite's Party service.

**Next:** friend activity feed, essence spending, regional dex rewards.

**Later:** new creature waves (DT2 bosses, raids minis, Araxxor, Nightmare), seasonal events, community-run competitions.

Each wave is a reason to come back, and a reason for someone to make a video about it.

---

*RuneHunter is an unofficial third-party plugin. Not affiliated with or endorsed by Jagex Ltd. Old School RuneScape is a trademark of Jagex Ltd. Not affiliated with or endorsed by Nintendo, Game Freak, or The Pokémon Company.*
