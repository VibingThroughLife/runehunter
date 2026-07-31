# RuneHunter — Handoff (v8, July 31 2026)

Everything below is committed to your repo and compiles clean. Restart the dev
client (`./gradlew runClient`) and it's all live.

## What shipped in v8

**Battle pacing** — new config: RuneHunter → "Battle pace". Default is
**Relaxed** (~2.2x slower): a common wild attacks every ~9s with a ~4.2s
telegraph, prayer drains 3x slower, everything scales. Standard (~1.6x) and
Fast (= the old speed) are there if Relaxed feels too chill. Applies from the
next battle after changing it.

**PokeDev Toolkit tab** — open PokeDev (button on the plugin panel), second
tab "Toolkit": every command is now a button. Load orb pack / big pack, gear,
status, reseed, swarm, despawn all, all lineup pages, start battle, practice
duel, force next attack style (melee/ranged/magic/disable/skull), all six
secret spawns + secret status/reload, Trophy Room, bug reports. Output prints
to the Console tab.

**Secret Dex** — six hidden easter eggs that NEVER show in the GoDex until
caught (then a gold SECRET section appears showing only what you own — "2
found", never "2/6"): **Zanik**, **Jimmothy** (raccoon kitbashed from the
giant squirrel: desaturate + bandit mask + tail rings), **Marcus** (crocodile),
and **3rd Age Mage / Ranger / Warrior** (Man + real 3rd age treasure-trail
item models merged on). Roughly 1 in 350 spawn windows (~177 hours of play per
secret — properly OSRS-grindy), radar never shows them, no shinies. Full
design doc: `docs/secret-dex-brief.md`. All cache ids live in ONE file —
`data/SecretIds.java` — swap ids there if something looks off in game.

**Trading + Duels** (RuneLite Party service, no backend): join the same
vanilla RuneLite party as a friend, then the Trophy Room's new Party strip
shows members with Trade/Duel buttons. Trade = OSRS-style two-screen flow
(any change resets accepts — anti-scam), level + equipped gear travel with the
creature, shinies can never leave by accident (only tradeable while you own a
spare plain copy). Duel = real-time prayer combat vs their companion,
challenger's client is authoritative, friendly stakes (W/L record only, no
faint). **Practice duel** button fights your own companion's mirror so you can
test solo.

## Test drive checklist (in order)

1. `Battle pace` config on Relaxed → PokeDev Toolkit → "Start battle" — is
   the speed right now? (Also try "Next: skull" / "Next: disable" mid-fight.)
2. Toolkit → Secret Dex buttons → spawn each of the six, eyeball the models:
   - **3rd Age trio is the riskiest** — item models are icon models, they may
     sit wrong on the body. Knobs: `THIRD_AGE_ITEM_SCALE` / `_LIFT` in
     `SecretIds.java`; worst case remove `.mergeItems(...)` and the white/gold
     recolor alone still reads as 3rd age.
   - **Jimmothy**: if the tail rings landed on his face, flip the Z slab in
     SecretIds from `0.55–1.00` to `0.00–0.45`.
   - **Zanik** (id 11260): if stiff/pose-locked, try 11261 (follower variant).
   - **Marcus**: confirm he's the desert croc, not a quest variant.
   - After each SecretIds edit: Toolkit → "Reload models" → respawn.
3. Trophy Room → "Practice duel" — full duel flow solo.
4. With a friend (or two clients): same RuneLite party → Trophy Room party
   strip → Trade and Duel for real. (Two-client sync is the least-tested
   path — expect rough edges, report what breaks.)

## Next actions (next session picks this up from claude/dev-status.md)

1. Fix whatever the v8 test drive turns up (SecretIds tuning, duel sync).
2. Generative art: cutscene strips + 12 Trophy Room `pod_*.png` floats
   (specs in `docs/art-spec.md`) — feed the prompts to your image model.
3. Domain registration (runehunter.gg was the pick) + FAQ site.
4. Friend feed on the Party Hello beacons (Activity tab's "Friends" section
   is still a placeholder — the presence data now exists to fill it).
5. Pre-Hub cleanup: gate/strip ALL dev tooling (PokeDev, dev configs, secret
   force-spawns), package rename decision (com.runehunter), GitHub repo + push,
   Plugin Hub submission review (client-side only, no external calls — the
   Party service usage follows the same pattern as the official party plugin).

## Where things live

- Dev commands: `::rh <cmd>` in chat or PokeDev console — status, orbs,
  swarm, lineup N, here <name> [shiny], anim/step/despawn, battle, style,
  duel [name|practice], trade <name>, party, secret <name|status|reload>,
  trophy, gear, report, reseed, prop.
- Secret ids/recipes: `data/SecretIds.java` (one file, heavily commented).
- Party protocol: `party/` package; trade rules in `TradeLedger` javadoc.
- Pacing numbers: `BattleManager` (scaled by `RuneHunterConfig.BattlePace`).
