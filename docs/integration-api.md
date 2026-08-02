# RuneHunter Integration API v1

*How to build a RuneLite plugin that reacts to RuneHunter.*

Status: **v1 draft**, read-and-react. Stable surface, additive changes only (see [Stability policy](#stability-policy)).

---

## 1. Why this exists, and why it looks the way it does

RuneHunter wants a plugin ecosystem the way NPC-highlight tooling has one: someone should be able to ship "TTS shouts when a shiny spawns" or "OBS overlay of my live dex" in an afternoon, without asking us for anything and without us reviewing their code.

There is one hard constraint that shapes the entire design:

> **RuneLite loads every Plugin Hub plugin in its own `PluginHubClassLoader`.**
> ([source](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/externalplugins/ExternalPluginManager.java))

That means your plugin and RuneHunter do **not** share class identity. If RuneHunter posts an instance of `com.runehunter.api.CreatureSpawned` on the shared `EventBus`, and your plugin subscribes to *its own* copy of that class, the EventBus keys the two on different `Class` objects and your handler never fires. This is the trap every "just post an event" integration falls into. Reflection would route around it, and reflection is **forbidden** by RuneLite's plugin rules, so that door is closed too.

So the API is carried on channels whose types live in **RuneLite core**, which every plugin genuinely shares:

| Channel | Carries | Mechanism |
|---|---|---|
| **Event channel** | Discrete things that just happened | `ConfigChanged`, a core RuneLite event with `String` group/key/value |
| **State channel** | Bulk current state and static data | JSON files under `.runelite/runehunter/` |

Both are **local-only**. Nothing here touches the network, and nothing about this API changes RuneHunter's compliance posture.

---

## 2. Quick start

Copy [`RuneHunterApi.java`](../api-stubs/consumer/RuneHunterApi.java) into your plugin (single file, BSD 2-Clause, no dependencies beyond what RuneLite already gives you). Then:

```java
public class ShinyAlertPlugin extends Plugin implements RuneHunterApi.Listener
{
    @Inject private EventBus eventBus;
    @Inject private Gson gson;
    @Inject private Client client;

    private RuneHunterApi api;

    @Override
    protected void startUp()
    {
        api = new RuneHunterApi(gson, this);
        eventBus.register(api);          // api forwards ConfigChanged to your callbacks
    }

    @Override
    protected void shutDown()
    {
        eventBus.unregister(api);
        api = null;
    }

    @Override
    public void onCreatureSpawned(RuneHunterApi.CreatureSpawned e)
    {
        if (e.shiny)
        {
            client.addChatMessage(ChatMessageType.CONSOLE, "",
                "SHINY " + e.name + " at " + e.worldX + "," + e.worldY, null);
        }
    }
}
```

That's the whole integration. No dependency on RuneHunter's jar, no build changes, no coordination with us.

**Detecting whether RuneHunter is installed:** don't try. Just listen. If RuneHunter isn't running, no events arrive. If you need to branch on it, check whether `.runelite/runehunter/state.json` exists and its `apiVersion` field is one you support.

---

## 3. Event channel

### Wire format

RuneHunter writes a single config key. Every event overwrites it, so the config file does not grow.

- **Config group:** `runehunterapi`
- **Config key:** `event`
- **Value:** compact JSON envelope

```json
{
  "v": 1,
  "seq": 4127,
  "t": "creature.spawned",
  "ts": 1785000000000,
  "d": { "...event-specific payload..." }
}
```

| Field | Type | Meaning |
|---|---|---|
| `v` | int | API version. Ignore envelopes whose `v` you don't support. |
| `seq` | long | Monotonic per-session counter. Use it to drop duplicates, because RuneLite can replay a `ConfigChanged` on profile load. |
| `t` | string | Event type (table below). |
| `ts` | long | `System.currentTimeMillis()` at emission. |
| `d` | object | Payload. |

### Consuming it raw (if you don't want the helper)

```java
@Subscribe
public void onConfigChanged(ConfigChanged e)
{
    if (!"runehunterapi".equals(e.getGroup()) || !"event".equals(e.getKey())) return;
    JsonObject env = gson.fromJson(e.getNewValue(), JsonObject.class);
    if (env == null || env.get("v").getAsInt() != 1) return;
    long seq = env.get("seq").getAsLong();
    if (seq <= lastSeq) return;   // dedupe
    lastSeq = seq;
    String type = env.get("t").getAsString();
    // ...
}
```

### Threading

`ConfigChanged` is posted on whatever thread called `setConfiguration`. RuneHunter always emits from the **client thread**. Your handler therefore runs on the client thread. Do not block it. If you need to do I/O or network work (a Discord webhook, say), hand off to an executor; if you need to come back to the client, use `clientThread.invoke()`.

### Event types (v1)

#### `creature.spawned`
Fires when a creature is placed into the scene.
```json
{ "creatureId": "dharok", "name": "Dharok the Wretched", "tier": 4, "tierName": "EPIC",
  "shiny": false, "worldX": 3232, "worldY": 3218, "plane": 0,
  "regionId": 12850, "windowId": "w302-12850-1785000000", "despawnAt": 1785001800000,
  "occluded": true }
```

#### `creature.despawned`
```json
{ "creatureId": "dharok", "reason": "WINDOW_END" }
```
`reason` ∈ `WINDOW_END` · `CAUGHT` · `FLED` · `SCENE_UNLOAD`

#### `catch.attempt`
Every orb thrown, success or not.
```json
{ "creatureId": "dharok", "tier": 4, "shiny": false, "orb": "CRYSTAL",
  "result": "BROKE_OUT", "rollP": 0.625 }
```
`orb` ∈ `UNPOWERED` · `ELEMENTAL` · `CRYSTAL` · `ELDRITCH`
`result` ∈ `CAUGHT` · `BROKE_OUT` · `FLED`

#### `catch.success`
Fires in addition to `catch.attempt` when the result is `CAUGHT`. This is the one most consumers want.
```json
{ "creatureId": "dharok", "name": "Dharok the Wretched", "tier": 4, "shiny": true,
  "newDexEntry": true, "newShinyEntry": true, "totalCount": 3, "orb": "CRYSTAL" }
```

#### `orb.received`
```json
{ "orb": "ELEMENTAL", "amount": 1, "sourceNpcId": 415, "sourceNpcName": "Abyssal demon" }
```

#### `companion.changed`
```json
{ "creatureId": "kraken", "shiny": false }
```
`creatureId` is `null` when the companion is dismissed.

#### `battle.started`
```json
{ "creatureId": "vorkath", "tier": 5, "shiny": false, "wildLevel": 88,
  "companionId": "abyssal_demon", "companionLevel": 74 }
```

#### `battle.ended`
```json
{ "creatureId": "vorkath", "outcome": "VICTORY", "ticks": 214,
  "deflects": 19, "misflicks": 4, "specialsUsed": 2,
  "damageDealt": 340, "damageTaken": 96 }
```
`outcome` ∈ `VICTORY` · `DEFEAT` · `FLED` · `FLEE_FAILED_ABORT`
`deflects` / `misflicks` are the prayer-flick scorecard, a natural hook for stream overlays and "flick accuracy" trackers.

#### `dex.updated`
```json
{ "caught": 41, "total": 87, "shinyCaught": 2, "essence": 1180 }
```

#### `lure.changed`
```json
{ "creatureId": "abyssal_demon", "stacks": 3, "expiresAt": 1785000900000 }
```
Emitted when the species lure from real NPC kills changes. `creatureId` is `null` when the lure lapses.

---

## 4. State channel

Files under `~/.runelite/runehunter/`. Rewritten atomically (temp file + rename), so a reader never sees a partial file. Read them on the `dex.updated` / `catch.success` signal rather than polling.

| File | Contents | Rewritten when |
|---|---|---|
| `roster.json` | Static definitions for all creatures: id, display name, tier, habitats, source NPC id, model scale, stand/walk animation ids | On plugin start / version change |
| `state.json` | `apiVersion`, plugin version, orb inventory, current companion, lifetime stats, currently active spawns | On any change, debounced to ≥1s |
| `dex.json` | Per creature: `caught`, `count`, `shiny`, `firstCaughtAt` | On dex change |

`roster.json` is the file that makes a *good* third-party plugin possible. It's how your plugin knows all 87 creatures and their tiers without hardcoding a copy that rots.

```json
// roster.json (excerpt)
{
  "apiVersion": 1,
  "rosterVersion": "1.0.0",
  "creatures": [
    { "id": "dharok", "name": "Dharok the Wretched", "tier": 4, "tierName": "EPIC",
      "habitats": ["MORYTANIA"], "sourceNpcId": 1673, "scale": 70,
      "standAnim": 5486, "walkAnim": 5487 }
  ]
}
```

---

## 5. What v1 deliberately does not do

**You cannot write into RuneHunter.** No registering custom creatures, no injecting spawns, no overriding catch rolls. That's intentional for v1:

- Roster integrity is the collection's whole value. If any plugin can mint creatures, a completed dex means nothing.
- It keeps our Plugin Hub review surface small. "Broadcasts local state" is trivially auditable; "executes third-party creature definitions" is not.
- Read-and-react already covers the overwhelming majority of what people actually want to build.

**Planned for v2** (once v1 has real consumers and we know what they hit): a declarative creature-pack format: third parties ship a signed JSON pack of creature definitions that RuneHunter loads into a clearly-marked *community* dex, separate from the canonical one. Declarative, not executable, so the review posture holds.

If you're blocked on something v1 can't do, open an issue describing the *plugin you want to build*, not the API call you want. That's what shapes v2.

---

## 6. Stability policy

- **`v` only increments on a breaking change.** We'd rather not.
- **Adding a new event type, or a new field to an existing payload, is not breaking.** Your consumer must ignore unknown event types and unknown fields. The helper class does this for you.
- **Removing or retyping a field is breaking** and bumps `v`. RuneHunter will emit both `v` and `v+1` envelopes for at least 90 days after any bump.
- Config group `runehunterapi` will never be renamed.
- The API is on by default and can be disabled in RuneHunter's config (*Third-party plugin API*). Assume some users have it off.

---

## 7. Things worth building

Not a wishlist we're claiming. Genuinely unclaimed, and none of it needs us:

- **Shiny TTS / sound pack**: audio alert on a shiny spawn, with per-tier sounds
- **Stream overlay bridge**: write dex progress and last-catch to a file OBS reads as a text/browser source
- **Flick coach**: grade prayer-flick accuracy over time from `battle.ended`, chart it
- **Discord webhook**: post rare catches to a clan server
- **Hunt router**: combine `creature.spawned` with the world map to plot a route through active spawns
- **Auto-screenshot**: trigger RuneLite's screenshot on `catch.success` where `newDexEntry` is true
- **Dex-race scoreboard**: a clan-run tracker fed by opt-in exports

Ship it, tell us, we'll link it.

---

*Questions: GitHub issues. Breaking-change announcements: repo releases page.*
