import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createIntroState, evaluateIntro } from '../docs/assets/world/intro.js';

const near = (actual, expected, tolerance = 1e-8) => assert.ok(Math.abs(actual - expected) < tolerance, `${actual} differs from ${expected}`);
const outsideCastle = (x, z) => x < -25 || x > 5 || z < -22 || z > 16;
const poseOnly = ({ progress: _progress, ...pose }) => pose;
const pointDistance = (a, b) => Math.hypot(...a.map((value, index) => value - b[index]));

test('Progress clamps, invalid input stays finite, and chapter boundaries are explicit', () => {
  for (const [input, expected] of [[-2, 0], [2, 1], [Infinity, 1], [-Infinity, 0], [NaN, 0], [undefined, 0], ['bad', 0]]) {
    const result = evaluateIntro(input);
    assert.equal(result.progress, expected);
    for (const value of [...result.camera.position, ...result.camera.target, ...Object.values(result.player)]) assert.ok(Number.isFinite(value));
  }
  assert.equal(evaluateIntro(0.19999).chapter, 'arrival');
  assert.equal(evaluateIntro(0.2).chapter, 'journey');
  assert.equal(evaluateIntro(0.64999).chapter, 'journey');
  assert.equal(evaluateIntro(0.65).chapter, 'encounter');
});

test('Forward, reverse, and random direct evaluation produce the same scene without shared mutation', () => {
  const progress = Array.from({ length: 101 }, (_, index) => index / 100);
  const forward = progress.map((p) => evaluateIntro(p));
  for (const index of [...progress.keys()].reverse()) assert.deepEqual(evaluateIntro(progress[index]), forward[index]);
  for (let index = 0; index < 101; index += 1) {
    const shuffled = (index * 37) % 101;
    assert.deepEqual(evaluateIntro(progress[shuffled]), forward[shuffled]);
  }
  const mutable = evaluateIntro(0.4);
  mutable.camera.position[0] = 999;
  mutable.player.x = 999;
  assert.deepEqual(evaluateIntro(0.4), forward[40]);
});

test('Player travels continuously outside the castle and water, and stops facing Dharok', () => {
  const start = evaluateIntro(0).player;
  assert.deepEqual([start.x, start.z], [8, 12]);
  let previous = evaluateIntro(0.2).player;
  let travelled = 0;
  for (let index = 1; index <= 9000; index += 1) {
    const player = evaluateIntro(0.2 + index * 0.45 / 9000).player;
    assert.ok(outsideCastle(player.x, player.z), 'Actor enters the castle footprint');
    assert.ok(player.x < 12, 'Actor enters the river');
    const step = Math.hypot(player.x - previous.x, player.z - previous.z);
    assert.ok(step < 0.012, 'Actor teleports at a route corner');
    travelled += step;
    previous = player;
  }
  assert.ok(travelled > 64 && travelled < 69, `Unexpected route length ${travelled}`);
  const end = evaluateIntro(1).player;
  near(end.x, -33); near(end.z, 1);
  near(Math.sin(end.rotation), Math.sin(Math.atan2(1, -4.4)));
  near(Math.cos(end.rotation), Math.cos(Math.atan2(1, -4.4)));
  assert.equal(start.walkWeight, 0);
  assert.equal(end.walkWeight, 0);
});

test('Footstep phase follows distance and heading does not snap at rounded corners', () => {
  let previous = evaluateIntro(0.2).player;
  let phaseStep;
  for (let index = 1; index <= 4500; index += 1) {
    const player = evaluateIntro(0.2 + index / 10000).player;
    const angularStep = Math.atan2(Math.sin(player.rotation - previous.rotation), Math.cos(player.rotation - previous.rotation));
    assert.ok(Math.abs(angularStep) < 0.035, `Heading snapped ${angularStep}`);
    if (phaseStep === undefined) phaseStep = player.walkPhase - previous.walkPhase;
    near(player.walkPhase - previous.walkPhase, phaseStep, 1e-9);
    previous = player;
  }
});

test('Desktop and phone camera tracks remain continuous and outside the castle', () => {
  for (const mobile of [false, true]) {
    let previous = evaluateIntro(0, { mobile }).camera;
    for (let index = 1; index <= 10000; index += 1) {
      const camera = evaluateIntro(index / 10000, { mobile }).camera;
      assert.ok(outsideCastle(camera.position[0], camera.position[2]), 'Camera enters the castle footprint');
      assert.ok(camera.position[1] >= 9, 'Camera sinks below the intended view');
      assert.ok(pointDistance(previous.position, camera.position) < 0.1, 'Camera position jumps');
      assert.ok(pointDistance(previous.target, camera.target) < 0.04, 'Camera target jumps');
      previous = camera;
    }
  }
  assert.notDeepEqual(evaluateIntro(1).camera, evaluateIntro(1, { mobile: true }).camera);
});

test('Arrival preserves the opening composition and gently pushes in before the journey', () => {
  for (const mobile of [false, true]) {
    const first = evaluateIntro(0, { mobile }).camera;
    const middle = evaluateIntro(0.1, { mobile }).camera;
    const last = evaluateIntro(0.2, { mobile }).camera;
    assert.deepEqual(first.position, mobile ? [69, 49, 69] : [62, 42, 65]);
    assert.deepEqual(first.target, last.target);
    near(pointDistance(last.position, last.target) / pointDistance(first.position, first.target), 0.95);
    assert.ok(pointDistance(middle.position, first.target) < pointDistance(first.position, first.target));
    assert.ok(pointDistance(middle.position, first.target) > pointDistance(last.position, first.target));
    assert.deepEqual(evaluateIntro(0.19, { mobile, reducedMotion: true }).camera, first);
  }
});

test('Reduced motion has one fixed composition per chapter with no walking cycle', () => {
  for (const mobile of [false, true]) {
    for (const [start, end] of [[0, 0.199], [0.2, 0.649], [0.65, 1]]) {
      const first = evaluateIntro(start, { reducedMotion: true, mobile });
      const last = evaluateIntro(end, { reducedMotion: true, mobile });
      assert.deepEqual(poseOnly(first), poseOnly(last));
      assert.equal(first.player.walkPhase, 0);
      assert.equal(first.player.walkWeight, 0);
    }
  }
  assert.equal(evaluateIntro(0).dharok.peek, 0);
  assert.equal(evaluateIntro(0.65).dharok.peek, 1);
  assert.equal(evaluateIntro(0.65).dharok.noticed, true);
  assert.equal(evaluateIntro(0.65, { caught: true }).dharok.noticed, false);
});

test('Catch lasts three seconds, rejects repeated throws, and counts completion exactly once', () => {
  const intro = createIntroState();
  const stable = intro.state;
  assert.deepEqual(stable, { phase: 'ready', elapsed: 0, catchCount: 0 });
  assert.equal(intro.throwOrb(), true);
  for (let tick = 0; tick < 179; tick += 1) {
    assert.equal(intro.throwOrb(), false);
    intro.update(1 / 60);
  }
  assert.equal(stable.phase, 'throwing');
  assert.equal(stable.catchCount, 0);
  intro.update(1 / 60);
  assert.deepEqual(stable, { phase: 'caught', elapsed: 3, catchCount: 1 });
  assert.equal(intro.throwOrb(), false);
  assert.equal(intro.leaveEncounter(), false);
  intro.update(1000);
  assert.equal(intro.state, stable);
  assert.equal(stable.catchCount, 1);
});

test('Elapsed time is stable when paused and ignores invalid deltas', () => {
  const intro = createIntroState();
  intro.throwOrb();
  intro.update(0.72);
  const before = { ...intro.state };
  for (const dt of [0, -1, NaN, Infinity, undefined]) intro.update(dt);
  for (let index = 0; index < 50; index += 1) evaluateIntro(index / 49);
  assert.deepEqual(intro.state, before, 'Scroll evaluation or invalid time advances a paused throw');
  intro.update(2.28);
  assert.equal(intro.state.phase, 'caught');
});

test('Leaving mid-throw settles once; leaving a ready encounter does not catch', () => {
  const intro = createIntroState();
  assert.equal(intro.leaveEncounter(), false);
  assert.equal(intro.state.phase, 'ready');
  intro.throwOrb(); intro.update(0.25);
  assert.equal(intro.leaveEncounter(), true);
  assert.deepEqual(intro.state, { phase: 'caught', elapsed: 3, catchCount: 1 });
  assert.equal(intro.leaveEncounter(), false);
  assert.equal(intro.throwOrb(), false);
});

test('Discrete catching finishes immediately, replay resets stably, and a new instance starts fresh', () => {
  const intro = createIntroState();
  const stable = intro.state;
  intro.throwOrb({ discrete: true });
  assert.deepEqual(stable, { phase: 'caught', elapsed: 3, catchCount: 1 });
  intro.replay();
  assert.equal(intro.state, stable);
  assert.deepEqual(stable, { phase: 'ready', elapsed: 0, catchCount: 0 });
  intro.throwOrb(); intro.update(1); intro.replay(); intro.update(99);
  assert.deepEqual(stable, { phase: 'ready', elapsed: 0, catchCount: 0 });
  intro.throwOrb(); intro.update(99);
  assert.deepEqual(stable, { phase: 'caught', elapsed: 3, catchCount: 1 });
  assert.deepEqual(createIntroState().state, { phase: 'ready', elapsed: 0, catchCount: 0 });
});
