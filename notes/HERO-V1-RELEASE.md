# RuneHunter website V1 hero

The website release is separate from the plugin release. The public plugin is
v0.8.0 beta; RuneHunter is not listed on the RuneLite Plugin Hub as checked on
4 September 2026. Do not promote the uncommitted v0.9 plugin work with this site.

## What changed

- Clear Lumbridge establishing artwork, readable game-font title, concise
  invitation to hunt, working tour/community actions and explicit release status.
- Native, keyboard-accessible Examine Dharok disclosure linked to the collection log.
- Original scrolling canvas world, minimap, 87-creature sample collection and
  prayer demo retained. The minimap appears with the tour, while renderer diagnostics
  are available with `?debug`.
- Motion control pauses artwork, camera/creature motion and battle demo; honors OS
  reduced-motion changes. Rendering stops while the hero fully covers the world,
  while the page is hidden, and while a paused frame is unchanged.
- Responsive phone framing, four visible mobile navigation links, focus targets,
  JavaScript-free reading/FAQ/artwork and forced-colors styling.
- Corrected Plugin Hub/API status, Party disclosure, battle pacing/recovery,
  personal spawn variation and public-creature shiny wording.
- Updated 1200 by 630 social preview from the actual rendered page.

## Source and preview

`runehunter-index.html` is the editable source mirror; `docs/index.html` is the
identical GitHub Pages entry point. `assets/` and `docs/assets/` contain identical
image copies so either entry point can be previewed with its own relative paths.
Keep those mirrors in sync; `tools/test-site.mjs` checks them. There is no build step,
framework, third-party runtime request, analytics or dependency installation.

From this checkout:

```sh
python3 -m http.server 4173 --bind 127.0.0.1 --directory docs
node tools/test-site.mjs
node tools/test-browser.mjs
```

Browser checks use installed Playwright. Set `PLAYWRIGHT_PACKAGE_PATH` and
`PLAYWRIGHT_CHROMIUM_EXECUTABLE` if they are not discoverable. Optional
`RUNEHUNTER_QA_DIR` selects the screenshot/report directory. The harness starts its
own loopback-only server and blocks external runtime requests.

## Artwork provenance

Built-in image generation tool, generated for this website on 4 September 2026.
Original: `exec-64a758df-f903-4772-9c99-147e002ff766.png`, 1536 by 1024.
Delivered as `docs/assets/lumbridge-hero.webp` (1536 by 1024, approximately 154 KB)
and `docs/assets/lumbridge-hero-mobile.webp` (960 by 640, approximately 63 KB).
The page labels this as concept artwork, never as a plugin screenshot. Image
compression/resizing used Sharp. The social card is a Chrome screenshot of the
finished HTML, with navigation/secondary content hidden for the 1200 by 630 crop.
Game fonts are the previously supplied CC0 RuneStar recreations.

Generation prompt:

> Use case: stylized-concept. Asset type: wide website hero background for RuneHunter, a creature collecting RuneLite plugin for Old School RuneScape. Create a beautiful nostalgic Old School RuneScape Lumbridge landscape rendered in the unmistakable 2007 game's low polygon flat-shaded art language, with a more thoughtfully lit handcrafted diorama composition. Wide 3:2 landscape image. Camera from the east bank of the River Lum looking west toward the iconic Lumbridge castle. Recognizable Lumbridge: warm grey/beige castle, two squat crenellated cylindrical gate towers with blue vertical banners flanking the open eastern courtyard entrance; square three-storey main keep behind the courtyard to the left, flag atop. Stone arched bridge crossing the blue River Lum toward the gate from lower right; blocky oak and willow trees; coarse green grass, small brown paths, a tiny goblin beside the river, a distant windmill. A miniature Dharok in dark olive/bronze Barrows armor and horned full helmet, holding his unmistakably oversized double-bladed greataxe, hides beside a low stone wall in the bottom right, visible and charming but still recognizable as OSRS Dharok. He is much smaller than a normal player. Orange late afternoon sun, warm stone, soft ambient atmospheric depth, restrained charming detail. Faithful chunky angular 2007 game geometry, broad flat colors, no photorealism, no high definition realistic textures, no modern fantasy palace, no Disney, no voxel Minecraft cubes. The castle is a strong clear silhouette in the right-center of the image, occupying the middle/right two-thirds. LEFT THIRD is a quiet shaded green bank with trees, dark enough to sit under website text; keep primary landmark and tiny warrior clear of this left region. Some blue-grey sky, mostly ground and castle. Composition must be premium editorial game key art and immediately feel like Lumbridge to long-time OSRS players. No text, no interface, no watermarks, no logos. This is original fan concept artwork, not a fake gameplay screenshot.

## Release and rollback

GitHub Pages is configured for `main:/docs`, with HTTPS and `docs/CNAME` set to
`runehunter.gg`. A website branch merge publishes the site. Confirm the Pages
build succeeds and compare the served HTML and each image to the release commit.
Do not push unrelated plugin changes. Rollback is a revert of the website release
commit through the same branch/review process, followed by Pages verification.

The previous live source is `8528b5f788dc3f3de2ca990f508ba2282488cc03`.
The recovered site base is `512acfd542a57942552f81adef48c7a823b6accf`.

Automated browser evidence covers 1440x900, 1200x850, 390x844, 320x740,
reduced-motion and JavaScript-disabled views. This is desktop browser emulation,
not evidence from physical mobile devices or an OSRS client. No plugin release,
Plugin Hub approval or live gameplay capture is claimed by this website release.
