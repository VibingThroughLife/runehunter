# Virtual OSRS — design and numbers

*Drafted 31 July 2026. Foundations built and compile-verified; the systems on top are unbuilt.*

Your creatures train while you play. You chop a real yew; about one action in ten,
RuneHunter fires a **virtual** proc — your creatures found a bundle of logs, you gain
virtual Woodcutting xp, sometimes you level. Gathered resources feed Smithing, which
forges the armour your creatures already wear in battle.

---

## 0. Ship this AFTER the Hub merge, not before

This is the recommendation I'd argue for even though you didn't ask.

You are 1–2 weeks from submitting to the Plugin Hub. Virtual OSRS is a second progression
system — persistence, xp hooks, notifications, a crafting economy, new UI. Adding it before
submission does two bad things: it delays the PR by weeks, and it enlarges the surface a
**human** reviewer has to read on a plugin that's already 15k lines.

The asymmetry is the whole argument:

| | Review path | Realistic wait |
|---|---|---|
| **In v1, pre-merge** | New plugin, human reviewer, bigger diff | 2–6 weeks, plus whatever this adds |
| **As v1.1, post-merge** | Update, auto-approval bot | Often days |

Since April 2026 RuneLite runs a bot that auto-approves most plugin updates. Getting the
first merge done is what unlocks fast iteration forever after. Build this now, hold the
commit, ship it the week after you're accepted — and it becomes a launch-week content drop
that gives people a reason to come back, instead of three more weeks of nobody playing it.

Nothing below changes if you disagree. It's purely about ordering.

---

## 1. The numbers

### Real actions per hour

| Method | Actions/hr | Procs/hr at 1/10 |
|---|---|---|
| Woodcutting (willows/teaks) | 900–1,400 | 90–140 |
| Fishing (barb/harpoon) | 600–1,000 | 60–100 |
| Mining (iron/granite) | 800–1,300 | 80–130 |
| Slayer (mid tasks) | 300–800 | 30–80 |
| Cooking/Fletching | 1,200–2,400 | 120–240 |

**Working figure: ~107 procs/hour** at a flat 1/10.

### Time to 99 (13,034,431 xp)

| Avg xp/proc | Hours to 99 | |
|---|---|---|
| 250 | 487h | absurd |
| 1,000 | 122h | authentic but punishing |
| 2,000 | 61h | a real grind |
| **~3,000** | **~42h** | **recommended** |
| 6,000 | 20h | no prestige |

### The recommended curve — flat bundle of 10

XP per proc rises only when you unlock a better resource, which reproduces the OSRS shape:

| Band | Resource | XP/proc | Hours | Cumulative |
|---|---|---|---|---|
| 1–15 | Logs | 250 | 0.1h | 0.1h |
| 15–30 | Oak | 370 | 0.3h | 0.4h |
| 30–45 | Willow | 670 | 0.7h | 1.1h |
| 45–60 | Maple | 1,000 | 2.0h | 3.1h |
| 60–75 | Yew | 1,750 | 5.1h | 8.2h |
| 75–90 | Magic | 2,500 | 15.5h | 23.7h |
| 90–99 | Redwood | 3,800 | 18.9h | **42.6h** |

**Level 60 arrives in 3 hours. The last nine levels take 19.** That ratio is the thing —
it's why an OSRS player will feel this curve is correct without being told why.

And these aren't 42 extra hours. It's 42 hours of skilling you were doing anyway.

### Tuning

`BUNDLE_SIZE` is the single dial. Hours to 99 scale inversely:

| Bundle | Hours to 99 |
|---|---|
| 6 | ~71h |
| 8 | ~53h |
| **10** | **~43h** |
| 12 | ~35h |

Change one constant in `VirtualSkill.java` and the whole curve moves.

### One guard rail

XP drops vary far more than the averages above — 2-tick teaks or blast-furnace runs fire
several times faster than the model. **Cap procs at ~200/hour** so a degenerate method
can't trivialise the curve. It won't affect normal play at all; the ceiling sits well above
the 107 typical figure.

---

## 2. Design

### Skills

Six, each mirroring a real OSRS skill and driven by its xp drops.

| Virtual skill | Driven by | Kind | Yields |
|---|---|---|---|
| Woodcutting | Woodcutting | gathering | logs |
| Fishing | Fishing | gathering | fish |
| Mining | Mining | gathering | ore |
| Hunter | Hunter | gathering | creature materials |
| Slayer | Slayer | gathering | essence |
| **Smithing** | Smithing | **artisan** | **gear** |

Hunter is the thematically obvious one for a creature-collecting game, and Slayer's "essence"
gives the Secret Dex and higher-tier catches something to consume later.

### The three rules

**1. It never touches real OSRS.** It grants nothing in-game, automates nothing, and reveals
nothing the client doesn't already show. It only observes xp you were going to earn anyway.

**2. Virtual level is capped by your real level.** Your virtual Woodcutting cannot exceed your
actual Woodcutting. This is the rule that makes the layer *reward* real progression instead of
competing with it — and it means a fresh account can't idle its way to a maxed virtual profile.
`VirtualSkill.effectiveLevel(virtual, real)` enforces it.

**3. Notifications are unmistakably RuneHunter's.** Never styled as game messages. A player
must never mistake a virtual log for a real one, and a reviewer must never wonder whether the
plugin is faking game state.

### The loop

```
   real skilling xp drop
          │  ~1 in 10
          ▼
   virtual proc ──► gathering skill xp + resources banked
          │                                   │
          │ level up                          ▼
          ▼                            SMITHING consumes ore
   better resource tier                       │
                                              ▼
                                    forges GearItem for creatures
                                              │
                                              ▼
                                   equipped in Trophy Room,
                                   feeds BattleStats in duels
```

Smithing is the payoff and the reason the gathering skills matter. Gear is currently
**drop-only** from battles — this gives it a second, earned source, and connects a system
you've already built to one you haven't.

### Ore → gear mapping

The existing `GearItem` tiers map cleanly onto ore, so nothing needs redesigning:

| Gear tier | Requires | Virtual Smithing |
|---|---|---|
| Bronze | Copper ore | 1 |
| Mithril | Mithril ore | 40 |
| Rune | Runite ore | 70 |
| Dragon | Dragon fragment | 85 |

---

## 3. Compliance read

Low risk, but worth stating in the PR rather than letting a reviewer find it.

- **No gameplay advantage** — nothing crosses into real OSRS.
- **No automation** — purely observational; it reacts to xp you earned by playing.
- **Not "simulating content"** — the precedent to respect is Jagex asking for Quest Helper's
  Leviathan simulation to be removed. That was a *practice tool for a specific real encounter*,
  which trivialises real content. A parallel collection minigame isn't that, and doesn't make
  any real activity easier.
- **Watch the AFK framing.** Passive reward for real skilling is fine, but never advertise it
  as a reason to idle. Describe it as "your creatures train while you play," not "earn while
  you AFK."
- **No real-money anything**, consistent with the rest of the plugin.

---

## 4. Honest design risk

RuneHunter's hook is *hunting creatures hidden in the 3D world*. That's what makes it
clippable and what makes it different from the TCG plugin.

An idle-skiller is a different kind of fun — numbers going up while you do something else.
It'll almost certainly help retention. It could also blur what the plugin is *for*, and the
worst version of this is where the hunting becomes the side dish.

Mitigation: keep the virtual layer subordinate in the UI — a tab, not the front page — and
make its rewards flow back into hunting and battling rather than standing alone. The gear
pipeline already does exactly that, which is the strongest argument for building Smithing
first among the artisan skills.

---

## 5. What's built

Compile-verified against the real RuneLite jars, 77 classes, no errors.

**`data/SkillXp.java`** — the OSRS xp curve, exactly. Verified at runtime against six known
checkpoints: level 10 = 1,154, level 50 = 101,333, level 75 = 1,210,421, level 92 = 6,517,253,
level 99 = 13,034,431. Binary-search `levelForXp` for the hot path, plus `xpToNextLevel`,
`progressInLevel` for the xp bar, and `isLevelUp` for firing notifications.

**`data/VirtualSkill.java`** — six skills, their resource tiers with real OSRS xp values, the
`BUNDLE_SIZE` / `PROC_CHANCE` dials, tier lookup, xp-per-proc, and the real-level cap.

## 6. What's next

1. **`VirtualSkillManager`** — subscribe to `StatChanged`, roll the proc, award xp, enforce the
   hourly cap, detect level-ups. The only new event hook required.
2. **Persistence** — extend `CollectionStore`: `runehunter.virtual.<skill>.xp` and a resource
   bank, RSProfile-scoped like the rest.
3. **Notifications** — a RuneHunter-branded toast for finds and level-ups, in the existing
   2004-cutscene art language rather than a Swing dialog.
4. **Panel tab** — six skill rows with xp bars and the resource bank.
5. **Smithing bench** — spend ore, forge `GearItem`s, in the Trophy Room next to the gear
   equip UI where it belongs.
6. **API events** — `virtual.proc`, `virtual.levelup` on the integration API, so third-party
   plugins and stream overlays can react. Roughly ten lines given the API already exists.
