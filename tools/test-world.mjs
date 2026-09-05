import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createSimulation, findPath } from '../docs/assets/world/simulation.js';
import { WORLD, CREATURES, isWalkable } from '../docs/assets/world/world-data.js';

const EPSILON = 1e-6;
const separation = (a, b) => Math.hypot(a.x - b.x, a.z - b.z);

function assertSafeRoute(start, path, walkable = isWalkable) {
  assert.ok(Array.isArray(path), 'Expected a reachable route');
  let previous = start;
  for (const point of path) {
    assert.ok(Math.abs(point.x - previous.x) < EPSILON || Math.abs(point.z - previous.z) < EPSILON, 'Route cut a diagonal corner');
    const steps = Math.max(1, Math.ceil(separation(previous, point) * 20));
    for (let i = 0; i <= steps; i += 1) {
      const t = i / steps;
      assert.ok(walkable(previous.x + (point.x - previous.x) * t, previous.z + (point.z - previous.z) * t), 'Route crosses a blocked segment');
    }
    previous = point;
  }
}

function finishWalking(simulation, { checkFollower = false } = {}) {
  for (let tick = 0; tick < 12_000 && simulation.state.path.length; tick += 1) {
    const before = { ...simulation.state.player };
    simulation.update(1 / 60);
    assert.ok(isWalkable(simulation.state.player.x, simulation.state.player.z), 'Player entered blocked ground');
    assert.ok(separation(before, simulation.state.player) <= 4 / 60 + EPSILON, 'Player moved faster than the movement budget');
    if (checkFollower && simulation.state.follower) {
      const follower = simulation.state.followerPosition;
      assert.ok(isWalkable(follower.x, follower.z), 'Follower cut through a wall or water');
    }
  }
  assert.equal(simulation.state.path.length, 0, 'Movement did not complete');
  assert.equal(simulation.state.player.moving, false, 'Player did not stop at the destination');
}

function catchOne(simulation, id) {
  assert.deepEqual(simulation.selectCreature(id), { ok: true });
  assertSafeRoute(simulation.state.player, simulation.state.path);
  finishWalking(simulation, { checkFollower: true });
  assert.equal(simulation.state.phase, 'ready');
  assert.deepEqual(simulation.catchCreature(), { ok: true });
  simulation.update(1.8);
  assert.equal(simulation.state.phase, 'caught');
  assert.ok(simulation.state.caught.includes(id));
}

test('A* follows the bridge rather than crossing open water', () => {
  const destination = { x: 7, z: 20 };
  const path = findPath(WORLD.spawn, destination);
  assertSafeRoute(WORLD.spawn, path);
  assert.deepEqual(path.at(-1), destination);
  const overRiver = path.filter((p) => p.x >= WORLD.river.minX && p.x <= WORLD.river.maxX);
  assert.ok(overRiver.length > 0, 'Route did not cross the river');
  assert.ok(overRiver.every((p) => p.z > WORLD.bridge.minZ && p.z < WORLD.bridge.maxZ), 'River crossing left the safe bridge deck');
  assert.equal(findPath(WORLD.spawn, { x: 15, z: 0 }), null, 'Water must not be a destination');
});

test('A* rejects blocked, invalid, outside, and unreachable destinations', () => {
  assert.equal(findPath(WORLD.spawn, { x: -18, z: -12 }), null, 'The keep is solid');
  assert.equal(findPath(WORLD.spawn, { x: WORLD.bounds.maxX + 1, z: 0 }), null);
  assert.equal(findPath({ x: NaN, z: 0 }, WORLD.spawn), null);
  assert.equal(findPath(WORLD.spawn, { x: Infinity, z: 0 }), null);
  assert.deepEqual(findPath(WORLD.spawn, WORLD.spawn), []);
  const bounds = { minX: 0, maxX: 2, minZ: 0, maxZ: 2 };
  const splitGrid = (x) => x !== 1;
  assert.equal(findPath({ x: 0, z: 0 }, { x: 2, z: 2 }, splitGrid, bounds), null);
  const thinBarrier = (_x, z) => z < 0.4 || z > 0.6;
  assert.equal(findPath({ x: 0, z: 0 }, { x: 0, z: 1 }, thinBarrier, bounds), null, 'Checking tile centres alone misses thin barriers');
});

test('A* is deterministic, shortest on a simple grid, and uses cardinal edges', () => {
  const bounds = { minX: -5, maxX: 5, minZ: -5, maxZ: 5 };
  const start = { x: 0, z: 0 }, end = { x: 3, z: 2 };
  const first = findPath(start, end, () => true, bounds);
  assert.equal(first.length, 5);
  assertSafeRoute(start, first, () => true);
  assert.deepEqual(findPath(start, end, () => true, bounds), first);
  const obstacle = (x, z) => !(x >= 0.9 && x <= 1.1 && z >= -0.1 && z <= 1.1);
  const detour = findPath(start, { x: 2, z: 0 }, obstacle, bounds);
  assertSafeRoute(start, detour, obstacle);
  assert.equal(detour.length, 4);
});

test('Movement consumes its budget, stops exactly, and can retarget between tiles', () => {
  const simulation = createSimulation();
  assert.deepEqual(simulation.moveTo(30, 12), { ok: true });
  simulation.update(0.125);
  assert.equal(simulation.state.player.x, 27.5);
  assert.equal(simulation.state.player.z, 12);
  assert.equal(simulation.state.player.moving, true);
  const start = { ...simulation.state.player };
  assert.deepEqual(simulation.moveTo(27, 16), { ok: true });
  assertSafeRoute(start, simulation.state.path);
  simulation.update(1_000);
  assert.equal(simulation.state.player.x, 27);
  assert.equal(simulation.state.player.z, 16);
  assert.equal(simulation.state.path.length, 0);
  assert.equal(simulation.state.player.moving, false);
  assert.equal(simulation.state.time, 1_000.125);
});

test('Manual movement cancels an approach and blocked actions explain themselves', () => {
  const simulation = createSimulation();
  simulation.selectCreature('dharok');
  assert.equal(simulation.state.phase, 'approaching');
  assert.equal(simulation.catchCreature().ok, false, 'Catching from a distance must fail');
  simulation.moveTo(28, 12);
  assert.equal(simulation.state.selected, null);
  assert.equal(simulation.state.phase, 'exploring');
  assert.equal(simulation.catchCreature().ok, false);
  const failure = simulation.moveTo(15, 0);
  assert.equal(failure.ok, false);
  assert.match(failure.reason, /blocked|bridge/i);
  assert.equal(simulation.selectCreature('missing').ok, false);
  assert.equal(simulation.stepDirection(1, 1).ok, false);
  assert.equal(simulation.stepDirection(0, 0).ok, false);
});

test('Keyboard steps select adjacent cardinal tiles', () => {
  const simulation = createSimulation();
  assert.deepEqual(simulation.stepDirection(0, 1), { ok: true });
  simulation.update(0.25);
  assert.equal(simulation.state.player.x, WORLD.spawn.x);
  assert.equal(simulation.state.player.z, WORLD.spawn.z + 1);
  assert.equal(simulation.state.player.moving, false);
});

test('Approaches stop two tiles away and face the creature in both motion modes', () => {
  for (const reducedMotion of [false, true]) {
    const simulation = createSimulation();
    simulation.setReducedMotion(reducedMotion);
    // Reach Chicken from the side so arrival must turn away from the final step.
    assert.equal(simulation.moveTo(26, 2).ok, true);
    finishWalking(simulation);
    for (const creature of CREATURES) {
      assert.equal(simulation.selectCreature(creature.id).ok, true);
      assertSafeRoute(simulation.state.player, simulation.state.path);
      finishWalking(simulation);
      const player = simulation.state.player;
      assert.equal(simulation.state.phase, 'ready');
      assert.ok(Math.abs(separation(player, creature) - 2) < EPSILON, 'Leave two tiles of space for the throw');
      assertSafeRoute(player, [{ x: creature.x, z: creature.z }]);
      assert.ok(Math.abs(Math.sin(player.rotation) - (creature.x - player.x) / 2) < EPSILON, 'Player must face the target on the x axis');
      assert.ok(Math.abs(Math.cos(player.rotation) - (creature.z - player.z) / 2) < EPSILON, 'Player must face the target on the z axis');
    }
  }
});

test('A catch has a deterministic duration and cannot overlap another action', () => {
  const simulation = createSimulation();
  simulation.selectCreature('chicken');
  finishWalking(simulation);
  assert.equal(simulation.state.phase, 'ready');
  simulation.catchCreature();
  assert.equal(simulation.state.phase, 'throwing');
  simulation.update(0.9);
  assert.ok(Math.abs(simulation.state.throwProgress - 0.5) < EPSILON);
  assert.equal(simulation.state.caught.length, 0);
  const position = { ...simulation.state.player };
  assert.equal(simulation.catchCreature().ok, false);
  assert.equal(simulation.selectCreature('goblin').ok, false);
  assert.equal(simulation.moveTo(27, 12).ok, false);
  assert.deepEqual(simulation.state.player, position);
  simulation.update(0.899);
  assert.equal(simulation.state.phase, 'throwing');
  simulation.update(0.001);
  assert.equal(simulation.state.phase, 'caught');
  assert.equal(simulation.state.throwProgress, 1);
  assert.deepEqual(simulation.state.caught, ['chicken']);
  assert.equal(simulation.state.follower, 'chicken');
});

test('All three creatures are reachable, collectible once, and replace the active follower', () => {
  const simulation = createSimulation();
  for (const creature of CREATURES) {
    catchOne(simulation, creature.id);
    assert.equal(simulation.state.follower, creature.id);
  }
  const caught = [...simulation.state.caught];
  simulation.selectCreature('chicken');
  assert.equal(simulation.state.phase, 'caught');
  assert.equal(simulation.catchCreature().ok, false);
  assert.deepEqual(simulation.state.caught, caught);
  assert.equal(simulation.state.follower, CREATURES.at(-1).id, 'Examining a caught creature should not create a second follower');
});

test('Follower keeps a 1.2-tile trail and follows turns over the bridge and around the castle', () => {
  const simulation = createSimulation();
  catchOne(simulation, 'chicken');
  simulation.moveTo(30, 1);
  finishWalking(simulation, { checkFollower: true });
  assert.ok(Math.abs(separation(simulation.state.player, simulation.state.followerPosition) - 1.2) < EPSILON);
  for (const destination of [{ x: 7, z: 20 }, { x: -2, z: -2 }, { x: -20, z: 1 }, { x: 27, z: 12 }]) {
    assert.equal(simulation.moveTo(destination.x, destination.z).ok, true);
    finishWalking(simulation, { checkFollower: true });
  }
  simulation.update(1);
  assert.equal(simulation.state.followerPosition.moving, false, 'Follower should settle when the player stops');
});

test('Reduced motion snaps along valid routes and finishes catches without advancing time', () => {
  const simulation = createSimulation();
  simulation.update(0, { reducedMotion: true });
  simulation.moveTo(7, 20);
  assert.equal(simulation.state.player.x, 7);
  assert.equal(simulation.state.player.z, 20);
  assert.equal(simulation.state.path.length, 0);
  assert.equal(simulation.state.time, 0);
  for (const creature of CREATURES) {
    assert.equal(simulation.selectCreature(creature.id).ok, true);
    assert.equal(simulation.state.phase, 'ready');
    assert.equal(simulation.state.player.moving, false);
    assert.equal(simulation.catchCreature().ok, true);
    assert.equal(simulation.state.phase, 'caught');
    assert.ok(isWalkable(simulation.state.followerPosition.x, simulation.state.followerPosition.z));
  }
  assert.equal(simulation.state.time, 0);
  assert.equal(simulation.state.caught.length, 3);
  simulation.moveTo(27, 12);
  assert.ok(isWalkable(simulation.state.followerPosition.x, simulation.state.followerPosition.z));
  assert.equal(simulation.state.followerPosition.moving, false);
});

test('Changing to reduced motion settles an active route or throw immediately', () => {
  const simulation = createSimulation();
  simulation.selectCreature('dharok');
  simulation.update(0.1);
  simulation.update(0, { reducedMotion: true });
  assert.equal(simulation.state.phase, 'ready');
  assert.equal(simulation.state.time, 0.1);
  simulation.update(0, { reducedMotion: false });
  simulation.catchCreature();
  simulation.update(0.1);
  simulation.update(0, { reducedMotion: true });
  assert.equal(simulation.state.phase, 'caught');
  assert.equal(simulation.state.time, 0.2);
});

test('Configuring discrete actions for an explicit pause preserves an in-flight route and throw exactly', () => {
  const simulation = createSimulation();
  simulation.selectCreature('chicken');
  simulation.update(0.1);
  const walking = structuredClone(simulation.state);
  assert.deepEqual(simulation.setReducedMotion(true), { ok: true });
  assert.deepEqual(simulation.state, walking, 'Pause configuration must not move the actor or revise the pending route');
  simulation.setReducedMotion(false);
  finishWalking(simulation);
  simulation.catchCreature();
  simulation.update(0.4);
  const throwing = structuredClone(simulation.state);
  simulation.setReducedMotion(true);
  assert.deepEqual(simulation.state, throwing, 'Pause configuration must not complete an in-flight catch');
  assert.equal(simulation.catchCreature().ok, false, 'Clicking again while paused must not complete a pending throw');
  assert.equal(simulation.state.phase, 'throwing');
  assert.equal(simulation.state.throwProgress, throwing.throwProgress);
  simulation.setReducedMotion(false);
  simulation.update(1.4);
  assert.equal(simulation.state.phase, 'caught');
  assert.deepEqual(simulation.state.caught, ['chicken']);
  simulation.reset();
  simulation.setReducedMotion(true);
  simulation.selectCreature('goblin');
  assert.equal(simulation.state.phase, 'ready', 'New explicit actions can still use discrete movement');
  simulation.catchCreature();
  assert.equal(simulation.state.phase, 'caught');
  assert.equal(simulation.state.time, 0);
});

test('Reset creates a fresh memory-only adventure while preserving the state reference and preference', () => {
  const simulation = createSimulation(), reference = simulation.state;
  simulation.update(0, { reducedMotion: true });
  simulation.selectCreature('chicken'); simulation.catchCreature(); simulation.update(4);
  const revision = simulation.state.revision;
  assert.deepEqual(simulation.reset(), { ok: true });
  assert.equal(simulation.state, reference);
  assert.equal(simulation.state.time, 0);
  assert.equal(simulation.state.revision, revision + 1);
  assert.deepEqual(simulation.state.caught, []);
  assert.equal(simulation.state.selected, null);
  assert.equal(simulation.state.follower, null);
  assert.equal(simulation.state.phase, 'exploring');
  assert.deepEqual(simulation.state.path, []);
  assert.deepEqual(simulation.state.player, { ...WORLD.spawn, rotation: 0, moving: false });
  simulation.selectCreature('goblin');
  assert.equal(simulation.state.phase, 'ready', 'Replay should keep the motion preference');
  simulation.reset();
  assert.deepEqual(createSimulation().state.caught, [], 'A second instance must not inherit catches');
});

test('Time only advances through update, state is serializable, and repeated inputs are deterministic', () => {
  function play() {
    const simulation = createSimulation();
    simulation.moveTo(28, 12);
    assert.equal(simulation.state.time, 0);
    assert.equal(simulation.update(-1).ok, false);
    assert.equal(simulation.update(NaN).ok, false);
    assert.equal(simulation.state.time, 0);
    simulation.update(0.25);
    simulation.selectCreature('chicken');
    simulation.update(20); simulation.catchCreature(); simulation.update(1.8);
    simulation.moveTo(7, 20); simulation.update(2);
    return simulation.state;
  }
  const first = play();
  assert.deepEqual(play(), first);
  assert.deepEqual(JSON.parse(JSON.stringify(first)), first);
});
