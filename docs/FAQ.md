# RuneHunter FAQ

*A creature-collecting layer for Old School RuneScape, built as a RuneLite plugin.*

---

## The basics

### What is RuneHunter?

Miniature versions of OSRS monsters (Barrows brothers, metal dragons, slayer mobs, GWD generals) spawn hidden in the 3D world around you. Behind the Varrock bank. Around the corner in a dungeon. Tucked against a wall where you'd never look unless you were looking.

You walk over, you throw an orb, you try to catch it. Rarer creatures are harder to catch and harder to find. Each of the 87 public creatures has a shiny variant; hidden Secret Dex creatures do not. You fill out a collection log (the "GoDex"), and you can set any creature you've caught as a companion that follows you around.

Orbs, the catch currency, drop from normal NPC kills. Your regular grind *is* the RuneHunter grind.

### Can I install it yet?

**Not on the Plugin Hub yet.** The public source is v0.8.0 beta. The core game is implemented, with hardening and playtesting still in progress. RuneHunter has not been submitted to the Plugin Hub, and there is no confirmed release date.

You can inspect or build the [source on GitHub](https://github.com/VibingThroughLife/runehunter). Release announcements will appear in the [Discord](https://runehunter.gg/discord). The website's V1 release does not change the plugin's availability.

### Is this a separate game?

No. It's a RuneLite plugin that runs on top of your normal OSRS client. You're still playing OSRS. Nothing about your account, your XP, your bank, or your gameplay changes.

### Does it cost anything?

No. It's free, it's open source, and there is nothing to buy. Orbs come from playing the game. There is no currency, no store, no premium tier, and no plan to ever add one. A catch-rate meta-game with real money attached would be a gambling product, and that's not what this is.

### Does it work on ironman / HCIM / group ironman?

Yes. Collections are stored per-account locally, and nothing RuneHunter does touches your actual account state. Your HCIM is exactly as safe (or not) as it was before you installed a plugin. See the honest answer below.

---

## Is this allowed?

### Will this get me banned?

RuneHunter is designed from the ground up to sit inside Jagex's third-party client rules:

- **No gameplay advantage.** It doesn't tell you where real NPCs are, doesn't help you fight anything, doesn't reveal information the game doesn't already give you. The creatures it renders are not real game entities.
- **No automation.** It never clicks, moves, or acts for you. Every catch is you walking there and clicking.
- **No RuneHunter server or telemetry.** Your collection is stored locally. Party presence, trading and duelling use RuneLite's built-in Party service when you join a party. See below.
- **Cosmetic and additive only.** It adds a visual collection layer. It removes nothing and trivializes nothing.

RuneHunter is being prepared for submission to the RuneLite **Plugin Hub**. It has not been submitted, reviewed or approved. These design intentions are not a guarantee of approval.

To be straight with you: no plugin author can *promise* you won't be banned, and anyone who does is lying. Jagex sets the rules and can change them. What we can tell you is what the plugin actually does, which is the list above, and that the source is public so you can verify it yourself.

### Does being on the Plugin Hub mean a plugin is guaranteed safe?

Hub plugins are third-party code. RuneHunter is not currently listed, and a future listing would not be a guarantee from Jagex or RuneLite. Read the [Plugin Hub information](https://github.com/runelite/runelite/wiki/Information-about-the-Plugin-Hub) for RuneLite's explanation.

### Isn't this a Pokémon ripoff?

It's obviously Pokémon-inspired, and we're not going to pretend otherwise. But it's built entirely from OSRS's own assets: every creature is a real OSRS NPC model, loaded from your own game cache, shrunk down. There's no Pokémon art, no Pokémon names, no Pokémon code. Orbs, not poké balls. GoDex, not Pokédex. RuneHunter, not RuneGo.

---

## Who made this and why

### Who are you?

One OSRS player, **VibingThroughLife**. Not a studio, not a company. Someone who has played this game for years and wanted a reason to walk around parts of Gielinor that stopped mattering a long time ago.

RuneHunter started as "what if a tiny Dharok was hiding behind Lumbridge castle" and turned into 87 creatures, real-time prayer-flick battles, and a trophy room.

### Why did you make it?

**Because OSRS is better with more to do in it.** Not more efficient. More *to do*. There are hundreds of hours of content most players never touch because there's no reason to go there. RuneHunter puts a reason on those tiles.

**Because the community keeps proving it wants this.** The OSRS TCG plugin went viral within days of hitting the Plugin Hub with nothing but a booster-pack loop. The appetite for collection meta-games on top of OSRS is real and it's not being served.

**Because content creators need new formats.** Streamers and YouTubers make OSRS content in a dozen different ways: speedruns, ironman progression, PvP, lore, snowflake accounts. Every one of those formats had to be invented by somebody. RuneHunter is another one: shiny hunts, dex-completion races, first-to-catch-Jad, "find the legendary before the window closes." It's inherently clippable, because the good moment is *visual*. You spin the camera and there's a shiny mini-Vorkath behind a wall.

Nobody needs permission to make content with it, monetize it, or build a series around it. That's the point.

**Because the world deserved to be the game board.** The design rule that mattered most: the collection lives in the 3D world, not in a menu. Menus are not clippable. Walking around a corner and finding something is.

---

## How it works

### How do I get orbs?

They drop from NPC kills during normal play. Four tiers, from the basic **unpowered orb** up to the near-guaranteed **eldritch orb**, with drop rates scaling to NPC combat level, so bossing pays better, but a chicken-killing ironman still progresses.

There's also the **species lure**: killing a real NPC temporarily raises the local spawn chance of its RuneHunter counterpart. On an abyssal demon task? Mini abyssal demons start showing up around you.

### How do spawns work? Do I see the same creatures as my friends?

Spawns use a seed based on your world, region and a 30-minute time window. Personal species lures, local spawn limits and placement can affect what appears, so identical encounters are not guaranteed between players. No RuneHunter server is needed.

Different worlds roll differently, which quietly makes world-hopping a hunting mechanic.

### How do battles work?

Real-time, on the OSRS tick, built around prayer flicking. Wild creatures telegraph attacks with a style icon; you select the matching protection prayer in RuneHunter's battle panel to deflect. The default Relaxed pace gives seven ticks of warning. Standard and Fast shorten the window. Correct protection at impact deflects ordinary hits and builds special-attack energy. RuneHunter prayer points drain while lit.

Rare-and-above creatures smite your prayer off. Epic-and-above throw skull-marked unavoidable hits that punch through prayer. It's OSRS combat literacy, applied to a collection game.

### Where is my collection saved?

Locally, in your RuneLite config directory, scoped to your account profile. There is no account, no login, and no RuneHunter server that knows you exist.

### So does anything touch the network?

When you join a RuneLite Party, RuneHunter uses RuneLite's built-in **Party service**. It periodically shares your OSRS display name, current companion and companion level with party members. Trade and duel messages also carry creature XP and gear plus combat state such as hitpoints, prayer and special attacks. Messages are relayed to the party and filtered for the intended player locally.

There is no RuneHunter server or telemetry. If you never join a party, RuneHunter itself makes no network requests. RuneLite and OSRS still use their own normal network connections.

### How does trading work?

Join the same RuneLite party as a friend, then use the Party strip in the Trophy Room. Trades use the OSRS two-screen flow, and any change resets both accepts, the anti-scam behaviour you already know. Level and equipped gear travel with the creature.

Shinies can't leave by accident: a creature is only tradeable while you still own a spare plain copy of it.

### And duels?

Real-time prayer combat against a friend's companion. The challenger's client runs the simulation, and stakes are friendly: win/loss record only, nobody's creature faints. There's a **practice duel** against your own companion's mirror if you want to learn the flicking solo.

### Can I lose my collection?

If you wipe your RuneLite config, yes. Back up your `.runelite` directory if you care about it. We'll add an export button.

---

## For developers and creators

### Can I build on top of this?

An integration API is planned, but **it does not ship in v0.8.0**. The repository contains a design draft for other RuneLite plugins to react to creature spawns, catches, battles and dex updates.

Possible uses include sound packs, shiny alerts, stream overlays and personal stat trackers. No broadcaster, state files or copy-paste consumer stub ship yet, so the examples cannot currently connect to RuneHunter.

The proposed API uses local channels. Its payloads and stability policy are proposals, not a released contract. See the [integration API draft](integration-api.md).

### Can I stream it / make videos / monetize that content?

Yes, unconditionally. No permission needed, no revenue share, no attribution required (though a link is appreciated). If you're doing something interesting with it, tell us. We'd rather signal-boost you than restrict you.

### Is the source available?

Yes, public repository, BSD 2-Clause licensed, which is what the Plugin Hub requires. Read it, fork it, verify the claims on this page yourself.

### How do I report a bug or request a creature?

The [Discord](https://runehunter.gg/discord) is the fastest route, and GitHub issues work just as well if you'd rather not use Discord. Include your RuneLite version, what you were doing, and a screenshot if it's visual. Creature requests are genuinely welcome, and the roster expands in waves.

---

## Roadmap

**Implemented in the v0.8.0 beta source:** 87 creatures across five tiers plus shinies, hidden 3D spawns, orb drops and species lure, real-time prayer-flick battles, companions, GoDex, trophy room, a handful of secret creatures, and trading + duels over RuneLite's Party service. Hardening and playtesting remain in progress; this is not a Plugin Hub release.

**Next:** friend activity feed, essence spending, regional dex rewards.

**Later:** new creature waves (DT2 bosses, raids minis, Araxxor, Nightmare), seasonal events, community-run competitions.

Each wave is a reason to come back, and a reason for someone to make a video about it.

---

*RuneHunter is an unofficial third-party plugin. Not affiliated with or endorsed by Jagex Ltd. Old School RuneScape is a trademark of Jagex Ltd. Not affiliated with or endorsed by Nintendo, Game Freak, or The Pokémon Company.*
