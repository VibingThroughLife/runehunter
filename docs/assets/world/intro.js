/** Pure scroll choreography. World units and the +z-facing actor convention match scene.js. */
const TAU = Math.PI * 2;
const CATCH_SECONDS = 3;
const ARRIVAL_END = 0.2;
const JOURNEY_END = 0.65;
const ROUTE = [[8, 12], [8, 21], [-7, 22], [-27, 22], [-33, 12], [-33, 1]];
const clamp = (value, low = 0, high = 1) => Math.min(high, Math.max(low, value));
const mix = (a, b, t) => a + (b - a) * t;
const mixPoint = (a, b, t) => a.map((value, index) => mix(value, b[index], t));
const smooth = (value) => { const t = clamp(value); return t * t * (3 - 2 * t); };
const distance = (a, b) => Math.hypot(b[0] - a[0], b[1] - a[1]);
const angleMix = (a, b, t) => a + Math.atan2(Math.sin(b - a), Math.cos(b - a)) * t;

// Round each route corner inside its adjacent segments. Quadratic control hulls
// stay on the safe exterior ground; the sampled arc lengths keep footsteps even.
function buildRoute() {
  const samples = [{ point: ROUTE[0], length: 0 }];
  const append = (point) => {
    const previous = samples.at(-1);
    samples.push({ point, length: previous.length + distance(previous.point, point) });
  };
  for (let index = 1; index < ROUTE.length - 1; index += 1) {
    const before = ROUTE[index - 1], corner = ROUTE[index], after = ROUTE[index + 1];
    const trim = Math.min(2, distance(before, corner) / 3, distance(corner, after) / 3);
    const enter = mixPoint(corner, before, trim / distance(corner, before));
    const leave = mixPoint(corner, after, trim / distance(corner, after));
    append(enter);
    for (let sample = 1; sample <= 32; sample += 1) {
      const t = sample / 32;
      append(mixPoint(mixPoint(enter, corner, t), mixPoint(corner, leave, t), t));
    }
  }
  append(ROUTE.at(-1));
  return samples;
}

const ROUTE_SAMPLES = buildRoute();
const ROUTE_LENGTH = ROUTE_SAMPLES.at(-1).length;

function routePoint(length) {
  const travelled = clamp(length, 0, ROUTE_LENGTH);
  let low = 1, high = ROUTE_SAMPLES.length - 1;
  while (low < high) {
    const middle = (low + high) >> 1;
    if (ROUTE_SAMPLES[middle].length < travelled) low = middle + 1;
    else high = middle;
  }
  const before = ROUTE_SAMPLES[low - 1], after = ROUTE_SAMPLES[low];
  return mixPoint(before.point, after.point, (travelled - before.length) / (after.length - before.length));
}

const CAMERA_KEYS = [
  { p: 0, position: [62, 42, 65], target: [-7, 3, 0], phone: [69, 49, 69], phoneTarget: [-4, 4, 3] },
  { p: 0.2, position: [58.55, 40.05, 61.75], target: [-7, 3, 0], phone: [65.35, 46.75, 65.7], phoneTarget: [-4, 4, 3] },
  { p: 0.3, position: [22, 26, 39], target: [1, 2, 12], phone: [28, 31, 47], phoneTarget: [0, 3, 12] },
  { p: 0.42, position: [-12, 22, 44], target: [-12, 2, 14], phone: [-2, 31, 50], phoneTarget: [-12, 3, 14] },
  { p: 0.55, position: [-43, 17, 29], target: [-27, 2, 11], phone: [-46, 22, 35], phoneTarget: [-27, 3, 12] },
  { p: 0.65, position: [-44, 9, 12], target: [-32, 1, -1], phone: [-45, 12, 18], phoneTarget: [-32, 1.5, -0.5] },
];

function cameraAt(progress, mobile, player) {
  let index = CAMERA_KEYS.findIndex((key) => key.p >= progress);
  if (index < 0) index = CAMERA_KEYS.length - 1;
  const before = CAMERA_KEYS[Math.max(0, index - 1)], after = CAMERA_KEYS[index];
  const t = before === after ? 0 : smooth((progress - before.p) / (after.p - before.p));
  const position = mixPoint(mobile ? before.phone : before.position, mobile ? after.phone : after.position, t);
  const authoredTarget = mixPoint(mobile ? before.phoneTarget : before.target, mobile ? after.phoneTarget : after.target, t);
  // During travel the adventurer is the foreground anchor. A target two tiles
  // beyond their feet leaves the castle corner in the upper background without
  // looking so far into the courtyard that the player falls below the screen.
  const dx = player[0] - position[0], dz = player[1] - position[2], distance = Math.hypot(dx, dz);
  const followTarget = [player[0] + dx / distance * 2, 2.3, player[1] + dz / distance * 2];
  const followWeight = smooth((progress - 0.2) / 0.11) * (1 - smooth((progress - 0.59) / 0.06));
  return { position, target: mixPoint(authoredTarget, followTarget, followWeight) };
}

/** Evaluate any scroll position independently; reversing or jumping never accumulates drift. */
export function evaluateIntro(progress, { mobile = false, reducedMotion = false, caught = false } = {}) {
  const normalized = Number.isFinite(progress) ? clamp(progress) : progress === Infinity ? 1 : 0;
  const chapter = normalized < ARRIVAL_END ? 'arrival' : normalized < JOURNEY_END ? 'journey' : 'encounter';
  const sample = reducedMotion ? { arrival: 0, journey: 0.51, encounter: 0.78 }[chapter] : normalized;
  const travel = clamp((sample - ARRIVAL_END) / (JOURNEY_END - ARRIVAL_END));
  const length = ROUTE_LENGTH * travel;
  const [x, z] = routePoint(length);
  const before = routePoint(length - 0.15), after = routePoint(length + 0.15);
  const heading = Math.atan2(after[0] - before[0], after[1] - before[1]);
  const faceDharok = Math.atan2(-32 - x, -3.4 - z);
  return {
    progress: normalized,
    chapter,
    camera: cameraAt(sample, mobile, [x, z]),
    player: {
      x, z,
      rotation: angleMix(heading, faceDharok, smooth((length - ROUTE_LENGTH + 3) / 3)),
      walkPhase: reducedMotion ? 0 : length * TAU / 2.4,
      walkWeight: reducedMotion ? 0 : smooth(travel / 0.025) * smooth((1 - travel) / 0.04),
    },
    dharok: {
      x: -32, z: -3.4,
      rotation: Math.atan2(x + 32, z + 3.4),
      peek: smooth((sample - 0.54) / 0.11),
      noticed: sample >= JOURNEY_END && !caught,
    },
  };
}

/** In-memory catch state. Callers pause by withholding update; scroll is separate. */
export function createIntroState() {
  const state = { phase: 'ready', elapsed: 0, catchCount: 0 };
  const finish = () => {
    if (state.phase !== 'throwing') return false;
    state.elapsed = CATCH_SECONDS;
    state.phase = 'caught';
    state.catchCount += 1;
    return true;
  };
  return {
    state,
    throwOrb({ discrete = false } = {}) {
      if (state.phase !== 'ready') return false;
      state.phase = 'throwing';
      state.elapsed = 0;
      if (discrete) finish();
      return true;
    },
    update(dt) {
      if (state.phase !== 'throwing' || !Number.isFinite(dt) || dt <= 0) return;
      state.elapsed = Math.min(CATCH_SECONDS, state.elapsed + dt);
      if (state.elapsed >= CATCH_SECONDS - 1e-9) finish();
    },
    leaveEncounter: finish,
    replay() {
      Object.assign(state, { phase: 'ready', elapsed: 0, catchCount: 0 });
    },
  };
}
