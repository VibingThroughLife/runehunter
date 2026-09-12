# RuneHunter Integration API v1: design draft

*A proposal for RuneLite plugins that react to RuneHunter.*

**Not implemented in v0.8.0.** RuneHunter does not currently ship the broadcaster, JSON state files, consumer helper or API configuration described here. Code examples illustrate the proposed design and do not connect to the current plugin. API v1 is separate from the website's V1 release.

All channels, payloads, filenames and compatibility promises below are proposals that may change before implementation. There is no stable integration surface or release date yet.

---

## 1. Why this exists, and why it looks the way it does

RuneHunter wants a plugin ecosystem the way NPC-highlight tooling has one: someone should be able to ship "TTS shouts when a shiny spawns" or "OBS overlay of my live dex" in an afternoon, without asking us for anything and without us reviewing their code.

There is one hard constraint that shapes the entire design:

> **RuneLite loads every Plugin Hub plugin in its own `PluginHubClassLoader`.**
> ([source](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/externalplugins/ExternalPluginManager.java))

That means your plugin and RuneHunter do **not** share class identity. If RuneHunter posts an instance of `com.runehunter.api.CreatureSpawned` on the shared `EventBus`, and your plugin subscribes to *its own* copy of that class, the EventBus keys the two on different `Class` objects and your handler never fires. This is the trap every "just post an event" integration falls into. Reflection would route around it, and reflection is **forbidden** by RuneLite's plugin rules, so that door is closed too.

The proposed API would use channels whose types live in **RuneLite core**, which every plugin shares:

| Channel | Carries | Mechanism |
|---|---|---|
| **Event channel** | Discrete things that just happened | `ConfigChanged`, a core RuneLite event with `String` group/key/value |
| **State channel** | Bulk current state and static data | JSON files under `.runelite/runehunter/` |

Both proposed channels are **local-only**. Any consumer that sends data elsewhere would be responsible for its own consent, privacy disclosure and review.

---

## 2. Proposed consumer example

The intended design includes a self-contained `RuneHunterApi.java` consumer helper. That file does not exist yet. This example sketches how a future helper might be used; it is not a working quick start:

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

The design goal is to avoid a dependency on RuneHunter's jar. The example requires the unimplemented helper and broadcaster before it can work.

**Proposed availability detection:** consumers would listen for events and validate the version of a future state file. The current plugin does not create `.runelite/runehunter/state.json`; its absence cannot be used to determine whether v0.8.0 is installed.

---

## 3. Proposed event channel

### Wire format

The proposal uses a single config key. Each event would overwrite it, rather than accumulate a history. The current plugin does not emit these events.

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

### Illustrative raw consumer

This sketch would need validation and lifecycle handling before production use. No events arrive from the current plugin.

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

`ConfigChanged` is posted on whatever thread called `setConfiguration`. The proposed broadcaster would emit from the **client thread**, so consumers would need to keep handlers non-blocking. I/O would run on an executor; access to the client would return through `clientThread.invoke()`. This threading contract has not been implemented or verified.

### Proposed event types (v1)

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

## 4. Proposed state channel

The proposal places files under `~/.runelite/runehunter/` and uses atomic replacement (temp file + rename) to avoid partial reads. Consumers would read on a `dex.updated` / `catch.success` signal rather than poll. **None of these API files are currently written.**

| File | Contents | Rewritten when |
|---|---|---|
| `roster.json` | Static definitions for all creatures: id, display name, tier, habitats, source NPC id, model scale, stand/walk animation ids | On plugin start / version change |
| `state.json` | `apiVersion`, plugin version, orb inventory, current companion, lifetime stats, currently active spawns | On any change, debounced to ≥1s |
| `dex.json` | Per creature: `caught`, `count`, `shiny`, `firstCaughtAt` | On dex change |

The proposed `roster.json` would let consumers read the creature roster and tiers without maintaining a separate copy. The following values are illustrative, including `rosterVersion`:

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

## 5. Proposed scope

**The proposed API is read-only.** It would not register custom creatures, inject spawns or override catch rolls:

- Roster integrity is the collection's whole value. If any plugin can mint creatures, a completed dex means nothing.
- It keeps our Plugin Hub review surface small. "Broadcasts local state" is trivially auditable; "executes third-party creature definitions" is not.
- Read-and-react already covers the overwhelming majority of what people actually want to build.

**Possible future direction:** a declarative creature-pack format and a separate community dex. This is an idea for discussion, not committed v2 functionality or a promise of approval.

To inform the design, open an issue describing the plugin you want to build and the data it would need.

---

## 6. Proposed stability policy

These are design targets for a future release. They do not currently guarantee compatibility:

- **`v` only increments on a breaking change.** We'd rather not.
- **Adding a new event type, or a new field to an existing payload, would not be breaking.** Consumers would ignore unknown event types and fields; a future helper would handle this.
- **Removing or retyping a field would be breaking** and bump `v`. A transition window emitting both versions is proposed; its duration remains undecided.
- The proposed config group is `runehunterapi`; it is not a released contract.
- An API configuration toggle and its default are still design decisions. No *Third-party plugin API* setting exists in v0.8.0.

---

## 7. Things worth building

Ideas that could use a future implemented API:

- **Shiny TTS / sound pack**: audio alert on a shiny spawn, with per-tier sounds
- **Stream overlay bridge**: write dex progress and last-catch to a file OBS reads as a text/browser source
- **Flick coach**: grade prayer-flick accuracy over time from `battle.ended`, chart it
- **Discord webhook**: post rare catches to a clan server
- **Hunt router**: combine `creature.spawned` with the world map to plot a route through active spawns
- **Auto-screenshot**: trigger RuneLite's screenshot on `catch.success` where `newDexEntry` is true
- **Dex-race scoreboard**: a clan-run tracker fed by opt-in exports

Feedback on these ideas is welcome while the API is being designed.

---

*Discuss the draft in [GitHub issues](https://github.com/VibingThroughLife/runehunter/issues).*
