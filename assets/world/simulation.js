import { WORLD, CREATURES, isWalkable } from './world-data.js';

const SPEED = 4;
const THROW_SECONDS = 1.8;
const APPROACH_DISTANCE = 2;
const FOLLOW_DISTANCE = 1.2;
const EPSILON = 1e-7;
const DIRECTIONS = [[1, 0], [0, 1], [-1, 0], [0, -1]];
const distance = (a, b) => Math.hypot(b.x - a.x, b.z - a.z);
const key = (point) => `${point.x},${point.z}`;
const tile = (point) => ({ x: Math.round(point.x), z: Math.round(point.z) });
const finitePoint = (point) => point && Number.isFinite(point.x) && Number.isFinite(point.z);

function segmentWalkable(a, b, walkable) {
  if (Math.abs(a.x - b.x) > EPSILON && Math.abs(a.z - b.z) > EPSILON) return false;
  const samples = Math.max(1, Math.ceil(distance(a, b) * 10));
  for (let i = 0; i <= samples; i += 1) {
    const t = i / samples;
    if (!walkable(a.x + (b.x - a.x) * t, a.z + (b.z - a.z) * t)) return false;
  }
  return true;
}

class Frontier {
  items = [];
  before(a, b) { return a.g + a.h < b.g + b.h || (a.g + a.h === b.g + b.h && (a.h < b.h || (a.h === b.h && a.order < b.order))); }
  push(node) {
    const items = this.items;
    items.push(node);
    let i = items.length - 1;
    while (i > 0) {
      const parent = (i - 1) >> 1;
      if (!this.before(items[i], items[parent])) break;
      [items[i], items[parent]] = [items[parent], items[i]];
      i = parent;
    }
  }
  pop() {
    const items = this.items, first = items[0], last = items.pop();
    if (items.length) {
      items[0] = last;
      let i = 0;
      while (i * 2 + 1 < items.length) {
        let child = i * 2 + 1;
        if (child + 1 < items.length && this.before(items[child + 1], items[child])) child += 1;
        if (!this.before(items[child], items[i])) break;
        [items[i], items[child]] = [items[child], items[i]];
        i = child;
      }
    }
    return first;
  }
}

/** Deterministic four-neighbour A*. Excludes start, includes end; null means unreachable.
 * A moving actor may start partway along a cardinal edge. Both safe endpoints
 * are considered, so retargeting never cuts diagonally across a corner.
 */
export function findPath(start, end, walkable = isWalkable, bounds = WORLD.bounds) {
  if (!finitePoint(start) || !finitePoint(end)) return null;
  const target = tile(end);
  const within = (p) => p.x >= bounds.minX && p.x <= bounds.maxX && p.z >= bounds.minZ && p.z <= bounds.maxZ;
  if (!within(start) || !within(target) || !walkable(start.x, start.z) || !walkable(target.x, target.z)) return null;
  if (distance(start, target) < EPSILON) return [];
  const frontier = new Frontier(), costs = new Map(), parents = new Map(), points = new Map();
  let order = 0;
  const heuristic = (p) => Math.abs(p.x - target.x) + Math.abs(p.z - target.z);
  for (const x of new Set([Math.floor(start.x), Math.ceil(start.x)])) {
    for (const z of new Set([Math.floor(start.z), Math.ceil(start.z)])) {
      const point = { x, z };
      if (!within(point) || !segmentWalkable(start, point, walkable)) continue;
      const id = key(point), g = distance(start, point);
      costs.set(id, g); parents.set(id, null); points.set(id, point);
      frontier.push({ ...point, g, h: heuristic(point), order: order++ });
    }
  }
  while (frontier.items.length) {
    const current = frontier.pop(), currentKey = key(current);
    if (current.g !== costs.get(currentKey)) continue;
    if (current.x === target.x && current.z === target.z) {
      const route = [];
      for (let id = currentKey; id !== null; id = parents.get(id)) route.push({ ...points.get(id) });
      route.reverse();
      if (route.length && distance(start, route[0]) < EPSILON) route.shift();
      return route;
    }
    for (const [dx, dz] of DIRECTIONS) {
      const next = { x: current.x + dx, z: current.z + dz }, id = key(next), g = current.g + 1;
      if (!within(next) || g >= (costs.get(id) ?? Infinity) || !segmentWalkable(current, next, walkable)) continue;
      costs.set(id, g); parents.set(id, currentKey); points.set(id, next);
      frontier.push({ ...next, g, h: heuristic(next), order: order++ });
    }
  }
  return null;
}

function freshState() {
  return {
    time: 0,
    player: { ...WORLD.spawn, rotation: 0, moving: false },
    path: [], selected: null, phase: 'exploring', caught: [], follower: null,
    followerPosition: { ...WORLD.spawn, rotation: 0, moving: false },
    throwProgress: 0,
    notice: 'Click a path to explore, or choose a creature to find.',
    revision: 0,
  };
}

export function createSimulation() {
  const state = freshState();
  let reducedMotion = false, throwElapsed = 0, followerTrail = [];
  const creatureById = new Map(CREATURES.map((creature) => [creature.id, creature]));
  const changed = () => { state.revision += 1; };
  const fail = (reason) => { state.notice = reason; changed(); return { ok: false, reason }; };
  const busy = () => state.phase === 'throwing';

  function appendTrail(point) {
    if (!state.follower) return;
    const last = followerTrail.at(-1) ?? state.followerPosition;
    if (distance(last, point) < EPSILON) return;
    const before = followerTrail.length > 1 ? followerTrail.at(-2) : state.followerPosition;
    const ax = last.x - before.x, az = last.z - before.z;
    const bx = point.x - last.x, bz = point.z - last.z;
    if (followerTrail.length && Math.abs(ax * bz - az * bx) < EPSILON && ax * bx + az * bz > 0) {
      followerTrail[followerTrail.length - 1] = { ...point };
    } else followerTrail.push({ ...point });
  }

  function follow(budget) {
    const follower = state.followerPosition;
    follower.moving = false;
    if (!state.follower) return;
    let length = 0, previous = follower;
    for (const point of followerTrail) { length += distance(previous, point); previous = point; }
    budget = Math.min(budget, Math.max(0, length - FOLLOW_DISTANCE));
    while (budget > EPSILON && followerTrail.length) {
      const target = followerTrail[0], length = distance(follower, target);
      if (length < EPSILON) { followerTrail.shift(); continue; }
      const travel = Math.min(length, budget);
      follower.rotation = Math.atan2(target.x - follower.x, target.z - follower.z);
      follower.x += (target.x - follower.x) * travel / length;
      follower.z += (target.z - follower.z) * travel / length;
      follower.moving = true;
      budget -= travel;
      if (travel >= length - EPSILON) { follower.x = target.x; follower.z = target.z; followerTrail.shift(); }
    }
  }

  function arrive() {
    state.player.moving = false;
    if (state.phase === 'approaching' && state.selected) {
      const creature = creatureById.get(state.selected);
      state.player.rotation = Math.atan2(creature.x - state.player.x, creature.z - state.player.z);
      state.phase = 'ready';
      state.notice = `${creature.name} is within reach. Throw an orb!`;
    }
  }

  function travelPlayer(budget) {
    state.player.moving = false;
    while (budget > EPSILON && state.path.length) {
      const target = state.path[0], length = distance(state.player, target);
      if (length < EPSILON) { state.path.shift(); continue; }
      const travel = Math.min(length, budget);
      state.player.rotation = Math.atan2(target.x - state.player.x, target.z - state.player.z);
      state.player.x += (target.x - state.player.x) * travel / length;
      state.player.z += (target.z - state.player.z) * travel / length;
      state.player.moving = true;
      budget -= travel;
      if (travel >= length - EPSILON) { state.player.x = target.x; state.player.z = target.z; state.path.shift(); }
      appendTrail({ x: state.player.x, z: state.player.z });
    }
    if (!state.path.length) arrive();
  }

  function finishCatch() {
    const creature = creatureById.get(state.selected);
    if (!creature || state.caught.includes(creature.id)) return;
    state.caught.push(creature.id);
    state.phase = 'caught'; state.throwProgress = 1;
    state.follower = creature.id;
    state.followerPosition = { x: creature.x, z: creature.z, rotation: 0, moving: false };
    followerTrail = findPath(creature, state.player) ?? [];
    state.notice = `${creature.name} caught! Your newest companion will follow your footsteps.`;
  }

  function startRoute(route) {
    state.path = route;
    state.player.moving = route.length > 0;
    if (!route.length) arrive();
    if (reducedMotion) { travelPlayer(Infinity); follow(Infinity); state.followerPosition.moving = false; }
    changed();
    return { ok: true };
  }

  function moveTo(x, z) {
    if (busy()) return fail('Finish this throw before moving.');
    if (!Number.isFinite(x) || !Number.isFinite(z)) return fail('Choose a tile inside the clearing.');
    const target = tile({ x, z });
    if (!isWalkable(target.x, target.z)) return fail('That tile is blocked. Try a path or the bridge.');
    const route = findPath(state.player, target);
    if (!route) return fail('There is no walkable route to that tile.');
    state.selected = null; state.phase = 'exploring'; state.throwProgress = 0;
    state.notice = route.length ? 'Following the path.' : 'Choose a creature, or keep exploring.';
    return startRoute(route);
  }

  function stepDirection(dx, dz) {
    if (!Number.isFinite(dx) || !Number.isFinite(dz) || (dx === 0) === (dz === 0)) return fail('Choose one direction at a time.');
    const origin = tile(state.player);
    return moveTo(origin.x + Math.sign(dx), origin.z + Math.sign(dz));
  }

  function selectCreature(id) {
    if (busy()) return fail('Finish this throw before choosing another creature.');
    const creature = creatureById.get(id);
    if (!creature) return fail('That creature is not in this clearing.');
    if (state.caught.includes(id)) {
      state.selected = id; state.phase = 'caught'; state.path = []; state.player.moving = false;
      state.throwProgress = 1; state.notice = `${creature.name} is already in your collection.`;
      changed(); return { ok: true };
    }
    let best = null, bestLength = Infinity;
    for (const [dx, dz] of DIRECTIONS) {
      const target = { x: Math.round(creature.x) + dx * APPROACH_DISTANCE, z: Math.round(creature.z) + dz * APPROACH_DISTANCE };
      if (!segmentWalkable(creature, target, isWalkable)) continue;
      const route = findPath(state.player, target);
      if (!route) continue;
      let length = 0, previous = state.player;
      for (const point of route) { length += distance(previous, point); previous = point; }
      if (length < bestLength) { bestLength = length; best = route; }
    }
    if (!best) return fail(`There is no clear path to ${creature.name}.`);
    state.selected = id; state.phase = 'approaching'; state.throwProgress = 0;
    state.notice = `Finding a path to ${creature.name}.`;
    return startRoute(best);
  }

  function catchCreature() {
    if (busy()) return fail('An orb is already in the air.');
    if (state.selected && state.caught.includes(state.selected)) return fail('That creature is already in your collection.');
    if (state.phase !== 'ready' || !state.selected) return fail('Approach a creature before throwing an orb.');
    throwElapsed = 0; state.throwProgress = 0; state.phase = 'throwing';
    state.notice = `Throwing an orb at ${creatureById.get(state.selected).name}.`;
    if (reducedMotion) finishCatch();
    changed(); return { ok: true };
  }

  function update(dt, options = {}) {
    if (!Number.isFinite(dt) || dt < 0) return fail('Simulation time must be a non-negative number.');
    const modeChanged = typeof options.reducedMotion === 'boolean' && reducedMotion !== options.reducedMotion;
    if (typeof options.reducedMotion === 'boolean') reducedMotion = options.reducedMotion;
    state.time += dt;
    if (state.path.length) travelPlayer(reducedMotion ? Infinity : SPEED * dt);
    else state.player.moving = false;
    if (state.phase === 'throwing') {
      throwElapsed += dt;
      state.throwProgress = Math.min(1, throwElapsed / THROW_SECONDS);
      if (reducedMotion || throwElapsed >= THROW_SECONDS - EPSILON) finishCatch();
    }
    follow(reducedMotion ? Infinity : SPEED * dt);
    if (reducedMotion) state.followerPosition.moving = false;
    if (dt > 0 || modeChanged) changed();
    return { ok: true };
  }

  // Configure future actions without turning a pause into a completed journey
  // or catch. Actual preference changes can still settle through update(0, ...).
  function setReducedMotion(value) {
    if (typeof value !== 'boolean') return fail('Motion preference must be true or false.');
    reducedMotion = value;
    return { ok: true };
  }

  function reset() {
    const revision = state.revision + 1;
    Object.assign(state, freshState(), { revision });
    throwElapsed = 0; followerTrail = [];
    return { ok: true };
  }

  return { state, moveTo, stepDirection, selectCreature, catchCreature, update, setReducedMotion, reset };
}
