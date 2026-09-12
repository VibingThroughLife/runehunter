# Lumbridge detail pass

Follow-up to the accepted scroll introduction, completed on 12 September 2026.
Base: `1b03b07`; branch: `codex/scroll-into-lumbridge`.

## What changed

Three bounded visual passes improve the existing world without changing the
camera route, catch sequence, or website navigation:

1. Warm directional daylight gives the angular stone and characters clearer
   depth, balanced with restrained ambient light and a slightly bluer distance.
2. Meadow and woodland edges gain irregular grass clusters, low shrubs, small
   flowers, and broad subtle terrain patches. River stones and reeds form
   interrupted groups. The rear kitchen yard gains an herb bed, a barrel, and
   split logs. Courtyard flower patches sit near the fountains.
3. Screenshot review corrected detached path patches into irregular turf edges
   joined to the actual grass boundary. Sparse masonry and foundation stones
   add interest to the south curtain wall during the walking shot.

All additions use existing geometry batches and deterministic placement. No new
runtime dependencies, downloads, interaction controls, or plugin changes were
introduced. Hero text space, the walking route, and the player-to-Dharok orb lane
remain clear. The original unfinished plugin checkout is preserved.

The four loading/fallback WebPs and the social card were recaptured from the
finished 3D scene. Root-preview and Pages mirrors are synchronized. The persistent
task delivery includes a new real-browser walkthrough and final screenshots;
the previous release's captures remain available for comparison.

## Evidence

Independent visual review passed all 15 captures at 1440x900, 390x844, and
320x740: arrival, early travel, castle corner, approach, and rear encounter. The
warmer lighting was also independently compared with the prior release and kept.
No blocking landmark, text, character, control, or path-edge issue was found.

The browser harness now saves a ten-position camera sweep for each viewport,
asserting and recording triangle/draw-call budgets throughout the journey.
All ten browser scenario groups passed in Chrome 152.0.7977.83 at
`2026-09-12T22:46:33.424Z`. Eleven timeline/state tests and static acceptance also
passed. The complete browser playthrough covers forward/reverse scrolling,
restored positions, direct FAQ links, keyboard and touch emulation, catches,
duplicate prevention, replay, skipping mid-catch, pause, offscreen suspension,
reduced motion, disabled JavaScript, and WebGL failure/loss/recovery. No browser
errors, failed requests, or third-party runtime requests were recorded.

| Viewport | Peak visible triangles | Peak draw calls | p95 frame interval |
| --- | ---: | ---: | ---: |
| 1440x900 | 36,841 | 54 | 16.7 ms |
| 390x844 | 36,533 | 44 | 16.7 ms |
| 320x740 | 36,533 | 44 | 16.8 ms |

The fresh traces include local interaction and screenshot overhead; their maximum
intervals were 100.03, 50, and 66.7 ms respectively. Full samples, per-position
budget counts, and 21 screenshots are saved in
`/private/tmp/runehunter-detail-qa-final`; persistent delivery copies are attached
to the Codex task. Independent final screenshot review found no blocking defects.

The constructed scene has 37,263 triangles and 68 mesh objects before hidden
actors and view culling. Static acceptance reports 442,896 gzip bytes for world
source including Three.js; the four WebPs add 186,340 bytes. Their combined
629,236 bytes remain below the 2 MB asset target. The social card is 115,765 bytes.
The local asset check passes for seven modules, local-only runtime requests,
mirrors, the vendored license, fonts, 87-creature roster, and release disclosures.

Phone and touch checks use Chrome browser emulation. Physical phones, Safari,
and a dedicated screen-reader session are not part of this evidence. Frame
intervals measure local animation frames, not GPU render times or physical-phone
performance.

## Publishing

The user explicitly authorized publication after these passes. The existing
GitHub Pages process still uses a release PR into `main`, serving `main:/docs` at
runehunter.gg. Owner SSH authenticates as `VibingThroughLife` for the branch push;
the CLI account has read permission only, and owner browser sign-in is required
to create and merge the release PR. No direct push to main is part of this release.

For rollback after publication, revert the release merge through the same PR
process and verify the previous Pages content. The publication result must be
verified before claiming that this scene is live.
