# A small hunt in a living Lumbridge

Historical release notes. The walkable demo was superseded on 12 September 2026
by [Scroll into Lumbridge](SCROLL-INTO-LUMBRIDGE.md).

Completed and reviewed on 5 September 2026, ahead of the 8 AM Chicago handoff.
Branch: `codex/lumbridge-adventure`, based on completed hero `21fa6bf`.
The website is ready for owner review; production publication remains pending.
The original checkout's unfinished plugin changes were preserved.

## Experience

The homepage now opens onto one animated, walkable Lumbridge: a castle courtyard,
twin gate towers with blue banners, a stone bridge, the River Lum, and a riverbank
farm. Explore Lumbridge opens a focused adventure. Chicken, Goblin, and miniature
Dharok can each be examined, approached, and caught with an orb. The latest catch
follows the player along the same walkable route.

Click/tap destinations and keyboard movement respect the tile grid, walls, water,
trees, and bridge. The field journal provides equivalent HTML discovery actions.
Catch outcomes are deterministic, and overlapping or duplicate catches are blocked.
The demo collection survives Exit and re-entry in memory; Replay clears it and
reloading starts fresh. It does not connect to real plugin progress.

The scene includes articulated walking, creature reactions, orb flight, a brief
catch celebration, villagers, chickens, waving flags, water, and trees. Camera
rotation is bounded and zoom has explicit controls. Pause freezes active walking
and throws without jumping forward. Reduced motion uses discrete actions. Rendering
suspends offscreen and when hidden. Escape exits and restores opener focus.

Illustrated fallback artwork remains available while loading, with JavaScript
disabled, or after WebGL failure. The HTML journal remains usable when graphics
fail, and a restored WebGL context retains collection progress. The page identifies
the adventure as a website showcase. The 87-creature roster, useful website sections,
v0.8.0 beta availability, Plugin Hub preparation, and API/Party disclosures remain.

## Source and runtime

`docs/index.html` and `docs/assets/` are canonical. Run `node tools/sync-site.mjs`
after edits to update the root preview mirrors (`runehunter-index.html`, `assets/`).
There is no build step or dependency installation:

```sh
python3 -m http.server 4175 --bind 127.0.0.1 --directory docs
node tools/test-world.mjs
node tools/test-site.mjs
node tools/test-browser.mjs
```

The browser harness locates installed Playwright and Chrome. Alternatively set
`PLAYWRIGHT_PACKAGE_PATH` and `PLAYWRIGHT_CHROMIUM_EXECUTABLE` to local installations.
`RUNEHUNTER_QA_DIR` selects its output directory. It starts a loopback server,
allows verified same-origin modules, and rejects third-party runtime requests.

Local modules separate world data/collision, procedural geometry and actors,
simulation/pathfinding, WebGL rendering, and HTML/input/lifecycle controls. Three.js
0.185.1 is vendored locally with its MIT license. The WebGL2 renderer uses a capped
pixel ratio and reduces effects for smaller/slower devices. `?debug` enables read-only
state, frame counters, renderer counts, and frame-interval traces for QA.

No backend, accounts, multiplayer, plugin API changes, analytics, external runtime
requests, persistent storage, or automatic audio were added.

## Validation

- 15 simulation tests passed: movement, collision, bridge routing, unreachable
  destinations, all catches, duplicate prevention, follower routing, replay,
  reduced motion, and pending-action preservation.
- Static acceptance passed: identical root/Pages mirrors, seven local modules,
  vendor license, embedded fonts, accessible controls, roster and release facts.
- Complete independent Chrome 152 browser acceptance passed at
  `2026-09-05T06:23:18.766Z`: 1440x900, 1200x850, 390x844, and 320x740.
  Desktop and touch-emulated phone playthroughs caught all three creatures.
- Pointer, keyboard, touch emulation, camera controls, collection/replay,
  repeated entry/exit, focus restoration, reduced motion, pause during a walk
  and throw, JavaScript-disabled reading, failed WebGL initialization, and real
  `WEBGL_lose_context` recovery passed. Pause checks use simulation/frame counters,
  positions and screenshots, not discarded canvas-buffer equality.
- Independent desktop/phone visual review passed after fixing Dharok's tower
  occlusion, encounter spacing/facing, and narrow-screen pause access. Castle,
  bridge, creatures and controls remain readable without horizontal overflow.
- Browser runs recorded no console/page errors, failed requests, or third-party
  runtime requests. Repository guard and whitespace checks passed.

Measured review frames contained 23,016–23,588 triangles and 23–38 draw calls,
below the 50,000/75 targets. The nine world/vendor files total 2,180,495 bytes raw
and 440,321 bytes when individually gzip-compressed at level 9; this is a local
compression measurement, not an observed CDN transfer. Both fallback WebP images
add approximately 217 KB. The compressed world asset budget is below 2 MB.

Recorded animation-frame interval p95 was 16.7–16.8 ms. Traces include automated
interactions and screenshot capture, exclude gaps of 250 ms or more, and are not
GPU render timings. These are desktop Chrome measurements and mobile browser
emulation, not physical-device or Safari evidence.

## Evidence and provenance

The final browser report and full screenshots were saved to
`/private/tmp/runehunter-adventure-qa-final`. Delivery copies (hero, adventure,
completion, phone screenshots, report, and an actual browser walkthrough WebM) are
in the task's persistent visualization directory:
`/Users/matthewvaldez/.codex/visualizations/2026/09/05/01a06f66-f079-7930-882a-f6c6691cd766`.

The 3D scenery and models are original procedural fan geometry. Existing game fonts
are CC0 RuneStar recreations. Fallback illustration provenance and its original
generation prompt are preserved in [the hero handoff](HERO-V1-RELEASE.md).
`docs/og.jpg` is a 1200x630 screenshot of the completed 3D homepage with secondary
UI hidden for the social crop. The walkthrough records this website showcase,
not an OSRS client or plugin gameplay. No captured media is loaded by the site.

## Release and recovery

GitHub Pages serves `main:/docs` at `https://runehunter.gg`; `docs/CNAME` retains
the domain. Review `main...codex/lumbridge-adventure`, then merge through the existing
GitHub review process when the owner is authenticated. Verify the Pages build,
served HTML, local modules and license, static fallback, and one live catch after
publication. The branch includes the prior website recovery/hero work and this
adventure, with no plugin edits.

SSH can push the website branch as VibingThroughLife. The currently authenticated
API/browser account lacks repository write permission; PR creation previously
failed with `must be a collaborator`. Owner sign-in as VibingThroughLife is the
remaining publication dependency. Do not retry unchanged credentials in a loop or
push directly to main. This handoff does not claim a PR, merge, or live deployment.

The previous public source is `8528b5f788dc3f3de2ca990f508ba2282488cc03`; the
recovered website base is `512acfd542a57942552f81adef48c7a823b6accf`. If rollback is
needed, revert the website release through the same review process and verify
Pages again. To return only the adventure to its preceding hero, revert the
adventure commit while retaining hero `21fa6bf`.

The overnight continuation is paused at completed handoff. Resume this branch
when owner authentication is available; there is no pending feature pass or
unresolved acceptance defect. Additional regions remain deferred.
