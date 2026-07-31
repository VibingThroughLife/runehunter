# RuneHunter — Generative Art Spec (Catch Cutscene + Trophy Room)

Drop finished files into `src/main/resources/com/runehunter/` with these EXACT
filenames — the cutscene engine picks them up automatically, no code changes.

**Global style prompt (prepend to every generation):**
"2004-era Old School RuneScape low-poly game art style, muted earthy palette,
slightly chunky pixel-friendly shapes, no modern gradients, no text,
transparent background, PNG"

---

## 1. cine_backdrop.png — 512 x 288
The cutscene stage background (the only opaque image).
Prompt: "side view of a dark underground chasm in 2004 RuneScape style, sheer
rock wall face spanning the middle, rocky ledge platforms at left and right
edges, black abyss below, faint teal crystal glow from the Temple of Light,
no characters, no pegs, banded stepped shading, vignette at edges"

The Man character (same guy in all five strips): a plain RuneScape "Man" —
brown hair, tan shirt, brown trousers. Side view. Do NOT include pegs or
scenery in the Man strips; the engine draws the wall and pegs itself.

## 2. cine_man_stand.png — 256 x 128 (horizontal strip, 2 frames of 128x128)
Standing idle on a ledge: 1) neutral stance 2) slight breathing bob / glance.

## 3. cine_man_jump.png — 512 x 128 (horizontal strip, 4 frames of 128x128)
Mid-leap between handholds: 1) crouched anticipation 2) launched, arms
forward 3) apex, legs trailing 4) reaching for the grab.

## 4. cine_man_hang.png — 512 x 128 (horizontal strip, 4 frames of 128x128)
Hanging from a peg by both hands (peg NOT included — hands at top-center of
frame): 1) steady hang 2) slight sway left 3) steady 4) slight sway right.
Feet dangling, knuckles tight, mildly panicked face.

## 5. cine_man_fall.png — 512 x 128 (horizontal strip, 4 frames of 128x128)
Losing his grip — the MEP2 falling meme: 1) one hand slipping 2) both arms
windmilling 3) free-fall, limbs flailing, mouth open 4) mid-tumble, upside
down.

## 6. cine_man_cheer.png — 512 x 128 (horizontal strip, 4 frames of 128x128)
Celebrating the catch: 1) fists coming up 2) arms fully raised V-shape jump
3) airborne, knees tucked 4) landed, one fist up.

## 7. Trophy Room float animations — pod_<archetype>.png (12 files)

The Trophy Room podium cycles these automatically at ~6fps with a floating
bob. Until a file exists the podium falls back to the flat silhouette, so you
can deliver these one at a time in any order.

**Format:** horizontal strip of SQUARE frames, 4 frames of 256x256 →
1024 x 256 PNG, transparent background. (Any square frame size works — the
engine derives the frame count from width/height — but 256 looks best on the
podium.) Creature centered, same scale and baseline in every frame; the
4 frames are a gentle idle loop: 1) rest pose 2) slight rise / inhale
3) apex, small feature movement (wing lift, tail sway, glow pulse)
4) settling back down. No pedestal, no shadow — the engine draws both.

Filenames + subject prompts (append to the global style prompt above,
plus: "majestic trophy showcase pose, three-quarter front view, single
creature, gentle idle animation frames"):

- `pod_dragon.png` — "a proud low-poly chromatic dragon, wings half-folded,
  long neck raised, tail curled around its feet"
- `pod_demon.png` — "a hulking horned demon, wide stance, small bat wings,
  smoldering fists"
- `pod_humanoid.png` — "an armored warrior figure with weapon shouldered,
  cape hanging, helmet plume"
- `pod_beast.png` — "a four-legged beast standing alert, thick fur, claws,
  head held high"
- `pod_bird.png` — "a large bird with folded wings, chest puffed, sharp
  beak, tail feathers fanned"
- `pod_bug.png` — "a chitinous insectoid with segmented shell, pincers
  raised, antennae up"
- `pod_aquatic.png` — "a many-tentacled kraken rising from a small pool of
  water, tentacles curling"
- `pod_slime.png` — "a glistening amorphous slime blob with a wobbling
  peak, drips at the base"
- `pod_ghost.png` — "a hooded spectral wraith hovering, tattered robe
  trailing into wisps of smoke"
- `pod_spider.png` — "a great spider with banded legs raised in threat
  pose, fangs visible"
- `pod_giant.png` — "a towering giant leaning on a wooden club, heavy
  brow, ragged hide clothes"
- `pod_serpent.png` — "a coiled serpent rearing up, hood flared, fanged
  mouth slightly open"

## 8. Later / optional
- cine_sparkle.png — 1024 x 128 strip, 8 frames of 128: gold four-point
  sparkle burst appearing → expanding → fading (engine currently reuses the
  small sparkle icon; a strip upgrade slots in later)
- Replacement brand art, same filenames: banner.png 220x70, icon.png 24x24,
  logo48.png 48x48, orb_unpowered/elemental/crystal/eldritch.png 32x32,
  sparkle.png 16x16, sil_*.png 24x24 silhouettes (12 archetypes)

## Rules that make or break it
- Transparent backgrounds on everything except cine_backdrop.png
- Horizontal strips: frames evenly spaced, SAME character scale and baseline
  in every frame (the engine cuts the strip into equal-width frames)
- The Man must be the same character across all three strips
- No text baked into any image
