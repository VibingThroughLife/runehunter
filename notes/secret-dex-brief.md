# RuneHunter — Secret Dex Design Brief

A hidden roster of easter-egg creatures that never appears in the GoDex until
you catch one. OSRS is famous for grindy, absurdly rare chase items (3rd age
from clues, pets at 1/5000) — the Secret Dex is our version of that: things
players stumble on, screenshot, and post to Reddit.

## The characters

1. **Zanik** — the beloved Dorgeshuun cave goblin from the quest series.
   Real OSRS NPC: use her actual cache model (several Zanik NPC ids exist in
   `net.runelite.api.gameval.NpcID` — pick a clean overworld variant with a
   proper idle/walk rig). Our first "named character" secret.

2. **Jimmothy** — the internet-famous raccoon. OSRS has no raccoon, so
   kitbash one: base model with a gray-body recolor, dark bandit mask and
   ringed tail via targeted palette recolors. Candidate bases: the giant
   squirrel pet, a wolf pup, or Giant rat — whichever silhouette reads most
   "raccoon" at mini scale. Name shown exactly as "Jimmothy".

3. **Marcus** — the VRChat legend. Closest OSRS read is a crocodile
   (desert crocodile model) at mini scale, maybe with a friendly recolor.
   Display name "Marcus".

4. **3rd Age Mage / 3rd Age Ranger / 3rd Age Warrior** — three humanoid
   secrets wearing 3rd age gear, OSRS's rarest armor. Two sourcing routes,
   in order of preference: (a) existing NPCs already wearing 3rd age (some
   clue/holiday NPCs qualify — scan the cache by name/appearance), or
   (b) kitbash: merge a Man base model with the 3rd age item models from the
   cache (`client.loadModelData(itemModelId)` + `mergeModels`), since item
   models exist for every 3rd age piece.

## Rarity (grindy on purpose)

- Secrets roll on their own channel, independent of the normal spawn table:
  roughly **1 in 350 spawn windows** rolls ONE secret somewhere in the loaded
  region (windows are 30 min, deterministic per world+region seed — same as
  normal spawns). Net effect: most players will go weeks without seeing one.
- Zanik/Jimmothy/Marcus equal weight; the three 3rd Age are rarer still
  (about a third of the others' weight each).
- Secrets are Legendary-difficulty catches (Legendary catch odds) and always
  flee-capable. No shiny variants — the secret IS the flex.
- Dev boost config also boosts secrets (testing must be possible), and the
  dev console gets a way to force-spawn each one.

## Presentation rules

- **Not listed in the GoDex at all** until caught — no "???" row, no count
  change, nothing that reveals they exist.
- Once caught, a new **"SECRET"** section appears at the bottom of the GoDex
  showing only the ones you own (gold section header, sparkly).
- The Nearby radar does NOT show secrets (walking past one unaware is the
  point); the catch cutscene, companion following, Trophy Room, battles and
  trading all treat a caught secret like any Legendary.
- Catch chat message gets special fanfare ("You feel like you've found
  something you weren't supposed to...").

## Engineering integration points

- `CreatureDef` gains a `secret` flag (or subclass) + display-name override
  decoupled from the cache NPC name (Jimmothy's base NPC isn't named that).
- `CreatureRoster.SECRET` list, excluded from `ALL` (so every existing
  "iterate ALL" site — GoDex, dex bar totals, radar, lineup, battle
  wild-rolls — ignores secrets for free). Catch/store/companion paths look
  up defs through a combined byKey that includes secrets.
- `NpcModelCache` needs per-def custom build support (recolor recipes and
  multi-source model merges), not just "NPC id → model".
- `SpawnManager` gets the secret roll channel in `reseed()`.
- `CollectionStore` keys work as-is (`col_<key>` etc.).
- Generative-art slots: `sil_secret.png` (a gold "?" star silhouette for the
  SECRET section) — optional, procedural fallback fine.

## Future secrets (parking lot)

Wise Old Man, Evil Chicken, the Mime, Cabbage (yes, just a cabbage),
Gnome child. Keep the system data-driven so adding one is a single roster
entry + optional recolor recipe.
