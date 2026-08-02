# RuneHunter test plan, before the Hub

*Written 1 Aug 2026. Covers the v8 features that were never playtested, plus everything the dev-tooling strip touched.*

> **Don't tick these by hand.** Run `python3 runehunter-dash.py` from the repo root.
> It reads this file, gives you clickable checkboxes that remember your progress,
> shows which branch and save namespace you're on, and has buttons for the git and
> gradle steps below. Editing this file changes the dashboard's list.

Work top to bottom. Sections 2 and 4 are different builds, so don't mix them up.

---

## 0. Before anything else

- [ ] `git add -A && git commit -m "v8 WIP: virtual skills, death stakes, site + Discord"`
- [ ] `git branch dev-tools`
- [ ] Confirm `git branch -v` shows both `main` and `dev-tools`

Nothing below is safe until this is done. The PokeDev console and your tuned `SecretIds` currently exist only as uncommitted files.

---

## ⚠️ Read this before you start switching branches

**Your save changes when you switch.** On `dev-tools` launched with `--developer-mode` you are on the `dev_` save namespace. On stripped `main` you are always on the main save. So your dev collection will look **empty** when you first run `main`, and your main collection will look empty on `dev-tools`.

That is correct behaviour, not a bug. Don't chase it.

---

## 1. The site (5 minutes, no client needed)

- [ ] `runehunter.gg` loads over HTTPS with no certificate warning
- [ ] `runehunter.gg/discord` redirects to the Discord and the invite still works
- [ ] The version badge reads **v0.8.0 beta** in the header, hero and footer
- [ ] Read the page on your phone and check the hero and status block don't overflow
- [ ] Nobody has em dashes left: `grep -c '—' docs/*.md docs/*.html README.md` should be 0 everywhere

---

## 2. On `dev-tools`: the v8 things nobody has played yet

`git checkout dev-tools` then `./gradlew runClient`. You have PokeDev here.

### The six secret models (highest risk on the whole project)

Use `secret <name>` or the Toolkit buttons. For each: does it look like the thing it's meant to be, at a sane size, animating?

- [ ] **3rd Age Mage**, where the item-merge is the single most likely thing to be broken
- [ ] **3rd Age Ranger**
- [ ] **3rd Age Warrior**
- [ ] If the 3rd Age items float, sink, or sit at the wrong scale: tune `THIRD_AGE_ITEM_SCALE` / `THIRD_AGE_ITEM_LIFT` in `SecretIds.java`, then `secret reload`. If it still looks wrong after two attempts, drop `.mergeItems` and ship them as recolours. A clean recolour beats a broken merge.
- [ ] **Jimmothy**: if the tail rings land on his face, flip the Z slab from `0.55` to `1.00` down to `0.00` to `0.45`
- [ ] **Zanik**: if she is pose-locked, try NPC `11261` instead of `11260`
- [ ] **Marcus**: confirm the name scan grabbed a sensible crocodile variant
- [ ] Catch one secret and confirm the gold **SECRET** section appears in the GoDex showing "1 found" (never "1/6")
- [ ] Confirm the radar and the GoDex do **not** reveal uncaught secrets

### Battle pace

- [ ] Fight a common on **RELAXED** (the default). Does it feel too slow now?
- [ ] Same fight on **STANDARD** and **FAST**
- [ ] Confirm the telegraph gives you enough time to actually flick on RELAXED
- [ ] Rare smites your prayer off; epic hits through it

### Trading and duels: the least-tested path in the project

Needs two clients.

- [ ] `duel practice` against your own companion's mirror works solo
- [ ] Two clients in the same RuneLite party can see each other in the Trophy Room party strip
- [ ] Trade: offer, both accept, both collections update correctly
- [ ] Trade: change the offer mid-way and confirm **both accepts reset**
- [ ] Trade a creature with gear equipped and confirm the gear travels with it
- [ ] Duel: both clients agree on who won, and neither creature faints
- [ ] Kill one client mid-duel and confirm the other aborts cleanly within about 10s

### Your new uncommitted work

I don't know what state these are in. You do. Add what they need:

- [ ] Virtual skills
- [ ] Death stakes
- [ ] Loot piles

---

## 3. Apply the strip

- [ ] `git checkout main`
- [ ] Tell me to write the stripped files, or apply `strip-dev-tooling.patch` yourself
- [ ] `./gradlew build` succeeds
- [ ] `git diff --stat` looks like 14 files, ~1,666 lines removed

---

## 4. On stripped `main`: regression pass

This is the build that ships. Launch it **without** `--developer-mode` so you see exactly what a player sees.

### Nothing dev-shaped is visible

- [ ] No **PokeDev** button on the panel, just one full-width Trophy Room button
- [ ] `::rh` and `::rgo` do nothing at all in chat
- [ ] RuneLite settings for RuneHunter show **14 options and no `[Dev]` entries**
- [ ] Right-clicking a creature shows **no "Report bug"** option
- [ ] No spawn tile markers, names or clickboxes drawn in the world

### The new panel footer

- [ ] Bottom of the panel shows a **Feedback & Discord** button and `RuneHunter v0.8.0 (beta)`
- [ ] Clicking it opens your browser at the Discord (it should go via `runehunter.gg/discord`)
- [ ] Panel still scrolls properly with the footer present, and the GoDex list isn't squashed

### Rates I changed, so play a normal session and sanity-check

The strip removed the dev boosts, so these paths now run their real numbers for the first time.

- [ ] Creatures still spawn at a reasonable pace during ordinary play
- [ ] Legendaries appear occasionally but not constantly (now a flat 1/8 per region window)
- [ ] Shinies feel rare (now always 1/512, so you will probably not see one, which is the point)
- [ ] **Don't expect to see a secret.** They are 1/1400 per region per window on `main`. Verify secrets on `dev-tools`, not here.

### Things the strip touched indirectly

- [ ] Wild battles still trigger on their own during play
- [ ] Attack telegraphs still vary between melee / ranged / magic (I removed the forced-style branch from `rollTelegraph`)
- [ ] Throw an orb and catch something, and the cinematic still plays
- [ ] Out of orbs: the "kill monsters to earn more" message still appears on right-click
- [ ] Trophy Room opens, and its **Trade / Duel / Practice duel** buttons still work (these were the only remaining route after I removed the name-based party commands)
- [ ] Trophy Room still hides itself during a battle and comes back after
- [ ] Log out and back in: collection, orbs and companion all persist

---

## 5. Housekeeping before submitting

- [ ] Reconcile the version numbers: `runelite-plugin.properties` says `0.8.0`, `build.gradle` says `0.1.0-SNAPSHOT`
- [ ] Reword the README opener from "Pokemon GO inside Old School RuneScape" to an "inspired by" framing, matching the FAQ's discipline
- [ ] Decide whether **GoDex** stays. It's the most likely thing to draw a trademark objection from a human reviewer, and there'd be no rule to appeal to
- [ ] Move `src/tools/java` to its own repo, since it pulls `net.runelite:cache` which the plugin doesn't need
- [ ] Batch the party messages so trading and duelling don't chat per-tick
- [ ] GoDaddy: WHOIS privacy, auto-renew, `hello@runehunter.gg` forwarding
- [ ] Seed the Discord: a pinned "what this is", channels, a bug-report format

---

## 6. Then, and only then

- [ ] Fork `runelite/plugin-hub`
- [ ] Create `plugins/runehunter` containing exactly `repository=` and `commit=` (full 40-char sha)
- [ ] Open the PR and mention up front that the only networking is RuneLite's own Party service. Don't let a reviewer discover it
- [ ] Expect a merge in hours rather than weeks, and keep every fix in the same PR
