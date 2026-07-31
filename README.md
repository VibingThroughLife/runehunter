# RuneHunter

**Pokemon GO inside Old School RuneScape.** Miniature versions of iconic
monsters — Barrows brothers, dragons, slayer monsters, bosses — spawn hidden
throughout the 3D world. Hunt them down, throw orbs to catch them, and
complete your GoDex.

## How it works

- **Hunt.** 87 creatures across 5 rarity tiers spawn at real tiles in the
  world, seeded per world + region + 30-minute window — every RuneHunter player
  on your world sees the same spawns. The nearby radar gives footstep hints,
  never the exact tile.
- **Earn orbs.** Killing NPCs rolls for orb drops (Unpowered → Elemental →
  Crystal → Eldritch). Higher-level kills roll better orbs.
- **Species lure.** Killing a creature's real counterpart boosts its mini's
  spawn chance near you for 10 minutes — your slayer task is your safari.
- **Catch.** Walk up, click the mini, throw an orb. Rarer creatures break
  out and flee more. Every spawn has a 1/512 shiny chance with a recolored
  model — shinies are seeded, so your whole world can race for one.
- **Collect.** GoDex side panel tracks your collection; duplicates convert
  to essence. Set any caught creature as a companion that follows you.

Everything is client-side and cosmetic: no gameplay advantage, no external
servers, no automation. Your collection is stored per-account in your
RuneLite profile.

## Development

Standard RuneLite external plugin. Open in IntelliJ, then run
`RuneHunterPluginTest` (in `src/test/java`) to launch RuneLite with the plugin
loaded.

```
./gradlew build
```

### Architecture

| Package | What lives there |
|---|---|
| `data` | Tiers, orb types, habitat zones, the 87-creature roster |
| `spawn` | NPC id resolution by cache scan, model shrinking/recoloring, seeded spawn manager, animation learner |
| `game` | Catch mechanics, orb drops from kills, companion follower |
| `storage` | RSProfile-scoped persistence |
| `ui` | GoDex side panel, nearby radar overlay |

Notable implementation details:

- **No hardcoded NPC ids.** The roster is keyed by exact NPC names; ids are
  resolved once per session by scanning `client.getNpcDefinition(0..16000)`
  in chunks on the client thread.
- **Model pipeline:** `loadModelData` → `mergeModels` → clone → NPC palette
  recolors → optional shiny hue-rotation → `scale(~70/128 ÷ npc size)` →
  `light()`. Built models are cached per creature+variant.
- **Animations are learned, not hardcoded.** `NPCComposition` doesn't expose
  animations, so the plugin records idle pose animations from real NPCs you
  encounter and persists them.

## Roadmap

v1.1: trading + friend companions via the RuneLite Party system, essence
shop. v1.2+: new creature waves (DT2 bosses, raids minis), seasonal shinies.

## License

BSD 2-Clause
