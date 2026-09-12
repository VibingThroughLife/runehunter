# Scroll into Lumbridge

The subsequent [Lumbridge detail pass](LUMBRIDGE-DETAIL-PASS.md) records the latest
scenery, lighting, refreshed captures, and validation measurements.

Implemented and independently reviewed on 12 September 2026. Branch
`codex/scroll-into-lumbridge` starts from `d45c2f3` in an isolated website worktree.
The original checkout's unfinished plugin work is preserved. This replaces the
walkable adventure described in the historical `LUMBRIDGE-ADVENTURE.md` notes.

## Experience

The homepage opens at Lumbridge Castle in warm daylight. Native scrolling follows
an adventurer along the southern wall, around the southwest corner, and toward
the western rear entrance. Miniature Dharok peeks out and notices the visitor.
One click throws an orb from the adventurer's hand through a visible arc, catches
Dharok, and presents a clearly labeled website-demo result.

The introduction occupies 400svh on desktop and 360svh on phones. The first 20%
establishes the castle, 20–65% follows the journey, and the remainder presents the
encounter. Skip intro, navigation, and ordinary page scrolling remain available.
The explanatory sections, 87-creature roster, FAQ, community, release information,
v0.8.0 beta availability, Plugin Hub preparation, and API/Party disclosures remain.

The three-second catch is deterministic. Repeated activation cannot overlap
throws; leaving during a throw completes exactly one result. Backward scrolling
reverses the journey while preserving that result. Replay catch resets it, and
reload starts fresh. There is no modal, free movement, or demo collection journal.

Pause freezes animation while navigation remains available. Reduced motion uses
discrete compositions and an immediate catch result; explicitly choosing Resume
animation opts into motion. JavaScript-disabled visitors receive scene stills and
readable narrative. WebGL failure retains an equivalent HTML catch action, and
context recovery preserves encounter state. Rendering suspends offscreen or when
the document is hidden.

## Artwork and implementation

Original procedural fan geometry follows the [OSRS Lumbridge Castle reference](https://oldschool.runescape.wiki/w/Lumbridge_Castle)
and [Dharok equipment reference](https://oldschool.runescape.wiki/w/D%27haroks).
Visual review used the actual [castle image](https://oldschool.runescape.wiki/images/Lumbridge_Castle.png)
and [equipped armor image](https://oldschool.runescape.wiki/images/Dharok%27s_armour_equipped_male.png).

The castle has stepped terraces, gate towers, blue banners, two fountains and two
statues, entrance steps, a roof bank and cannons, and a modeled rear kitchen door.
Angular characters, Dharok's open-face green helmet, armor spikes, and asymmetric
greataxe establish the silhouettes. Subtle water, banners, trees, hens, and
villagers share the same simulation clock. Contact shading anchors the models.

All four desktop/phone loading and fallback WebPs, plus the social preview image,
are captures of the finished 3D scene. The generated fallback illustration is
replaced. The task delivery includes opening, corner, encounter, orb-release,
catch-result, and phone screenshots, plus an actual browser walkthrough recording.

- `intro.js`: pure normalized-scroll evaluator and separate catch state machine.
- `app.js`: native scrolling, HTML controls, accessibility, pause, and lifecycle.
- `renderer.js`: camera composition, articulated poses, hand-anchored orb, metrics.
- `scene.js`: reusable geometry, castle, terrain, actors, and ambient animation.
- `world-data.js`: shared world bounds, terrain palette, and scene layout data.
- Three.js 0.185.1 remains locally vendored with its MIT license. WebGL2 resolution
  is capped and reduced on smaller or persistently slow devices.

`docs/index.html` and `docs/assets/` are canonical. The root preview mirrors are
synchronized by `tools/sync-site.mjs`. There is no build or installation step:

```sh
node tools/sync-site.mjs
python3 -m http.server 4176 --bind 127.0.0.1 --directory docs
node tools/test-intro.mjs
node tools/test-site.mjs
node tools/test-browser.mjs
```

The browser harness uses locally installed Playwright and Chrome. Set
`PLAYWRIGHT_PACKAGE_PATH`, `PLAYWRIGHT_CHROMIUM_EXECUTABLE`, or `RUNEHUNTER_QA_DIR`
to override their locations. `?debug` exposes read-only progress, encounter state,
poses, simulation time, renderer counters, and frame-interval traces for QA.
Production URLs without that query expose no debug API.

No backend, accounts, plugin API changes, extra regions, automatic audio, analytics,
external runtime requests, persistent storage, or real collection integration were
added. This is an original website showcase, not captured RuneLite gameplay.

## Validation and evidence

- Eleven pure timeline/state tests passed, including arbitrary scroll evaluation,
  continuous camera movement, reduced motion, duplicate prevention, replay, and
  completing a throw when leaving the encounter.
- Static acceptance passed: root/Pages mirrors, seven same-origin modules,
  vendored license, embedded fonts, all 87 creatures, and release disclosures.
- Independent Chrome 152.0.7977.83 browser acceptance passed at
  `2026-09-12T22:12:17.874Z`: desktop 1440x900, phone 390x844, and narrow phone
  320x740. All ten scenario groups passed.
- Forward/reverse scrolling, rapid jumps, restored positions, direct FAQ links,
  resize, pointer and keyboard paths, native wheel/PageDown, touch emulation,
  repeated throws, replay, skipping mid-catch, pause, reduced motion, disabled
  JavaScript, WebGL initialization failure, and real context loss/recovery passed.
- Pause and offscreen suspension were checked with simulation time, frame counts,
  poses, and screenshots. Fresh frame-interval traces are retained in results.json.
- Independent visual review passed for arrival, castle corner, rear encounter,
  orb release, and catch result. The final pass corrected travel framing and the
  phone camera angle to retain the castle corner. Controls fit at all three widths
  without horizontal overflow.
- No third-party runtime requests, failed requests, or browser errors were recorded.

| Final browser sample | Visible triangles | Draw calls | p95 frame interval |
| --- | ---: | ---: | ---: |
| 1440x900 | 32,830 | 25 | 16.8 ms |
| 390x844 | 32,746 | 20 | 16.8 ms |
| 320x740 | 32,746 | 20 | 16.7 ms |

World source including the vendor is 439,589 bytes with the static test's gzip settings; the four
fallback WebPs add 166,072 bytes. This remains below the 2 MB compressed world
asset target, and the scene remains below 50,000 visible triangles and 75 draw
calls. These frame intervals are local headless Chrome measurements including
automated interaction and screenshots, not GPU render time or physical-device
performance. Phone and touch evidence is browser emulation; physical phones,
Safari, and a real assistive-technology session were not tested.

The final local evidence is in `/private/tmp/runehunter-scroll-qa-final`, with
persistent copies of results, selected screenshots, and the walkthrough attached
to the Codex task delivery.

## Publication and rollback

GitHub Pages serves `main:/docs` at runehunter.gg. Publish through the existing
reviewed PR process, without a direct main push. The fresh authentication check
found CLI user `matthewmakejoy` has read permission only; the GitHub browser also
requires owner sign-in. Owner SSH can push the review branch. Creating and merging
the release PR therefore remain pending owner authentication as `VibingThroughLife`.

Review `codex/scroll-into-lumbridge` against main. If the release needs rollback
after publication, revert its merge through the same PR process to restore the
previous Pages content. The original plugin checkout must remain untouched.
No new scheduled or overnight continuation was created for this pass.
