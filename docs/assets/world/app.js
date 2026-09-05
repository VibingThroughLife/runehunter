import { createSimulation } from './simulation.js';
import { createWorldRenderer } from './renderer.js';
import { WORLD, CREATURES } from './world-data.js';

const $ = id => document.getElementById(id);
const hero = document.querySelector('.hero');
const stage = $('world-stage'), canvas = $('world-canvas');
const dialog = $('adventure-dialog'), viewport = $('adventure-viewport');
const simulation = createSimulation();
const media = matchMedia('(prefers-reduced-motion: reduce)');
let paused = media.matches, active = false, heroVisible = true, dirty = true;
let pendingFrame = 0, lastNow = performance.now(), lastOpener = $('explore-world');
let keyHeld = null, pointerDown = null, graphics = null, contextLost = false, initializationError = null;
let uiStamp = '', lastNotice = '', ambientTime = 0;
const label = $('world-label');
const cameraButtons = ['world-rotate-left', 'world-rotate-right', 'world-zoom-in', 'world-zoom-out'].map($);

function useDiscreteMotion() { return paused || !graphics || contextLost; }
function requestFrame() {
  dirty = true;
  if (!pendingFrame) pendingFrame = requestAnimationFrame(frame);
}
function markFallback(value) {
  contextLost = value && Boolean(graphics);
  viewport.classList.toggle('simple-view', value);
  $('world-fallback-message').hidden = !value;
  cameraButtons.forEach(button => { button.disabled = value; });
  stage.classList.toggle('ready', !value);
  stage.setAttribute('aria-hidden', String(value || !active));
  canvas.tabIndex = active && !value ? 0 : -1;
  if (value) {
    simulation.update(0, { reducedMotion: true });
    if (active && document.activeElement === canvas) document.querySelector('[data-creature="chicken"]').focus({ preventScroll: true });
  } else simulation.setReducedMotion(useDiscreteMotion());
  syncUI(); requestFrame();
}
try {
  graphics = createWorldRenderer(canvas, {
    onLost: () => markFallback(true),
    onRestored: () => { contextLost = false; markFallback(false); },
  });
} catch (error) {
  // A safe, usable journal remains if graphics initialization fails.
  initializationError = error.message;
  graphics = null;
}
markFallback(!graphics);

function syncMotion() {
  document.body.classList.toggle('motion-paused', paused);
  for (const id of ['motion-toggle', 'world-pause']) {
    const button = $(id);
    button.setAttribute('aria-pressed', String(paused));
    button.textContent = paused ? 'Resume motion' : 'Pause motion';
  }
  $('world-pause-quick').setAttribute('aria-pressed', String(paused));
  $('world-pause-quick').setAttribute('aria-label', paused ? 'Resume motion' : 'Pause motion');
  $('world-pause-quick').textContent = paused ? 'Resume' : 'Pause';
  simulation.setReducedMotion(useDiscreteMotion());
  lastNow = performance.now();
  syncUI(); requestFrame();
}
$('motion-toggle').addEventListener('click', () => { paused = !paused; syncMotion(); });
$('world-pause').addEventListener('click', () => { paused = !paused; syncMotion(); });
$('world-pause-quick').addEventListener('click', () => { paused = !paused; syncMotion(); });
media.addEventListener('change', event => { paused = event.matches; syncMotion(); });
syncMotion();

function openAdventure(event) {
  event?.preventDefault();
  if (active) return;
  lastOpener = event?.currentTarget ?? $('explore-world');
  active = true;
  dialog.showModal();
  document.body.classList.add('adventure-open');
  viewport.appendChild(stage);
  stage.setAttribute('aria-hidden', String(!graphics || contextLost));
  canvas.tabIndex = graphics && !contextLost ? 0 : -1;
  canvas.setAttribute('aria-describedby', 'world-walk-hint');
  graphics?.setActive(true, simulation.state);
  lastNow = performance.now();
  if (graphics && !contextLost) canvas.focus({ preventScroll: true });
  else document.querySelector('[data-creature="chicken"]').focus({ preventScroll: true });
  requestFrame(); syncUI();
}
function closeAdventure() {
  if (dialog.open) dialog.close();
}
dialog.addEventListener('cancel', event => { event.preventDefault(); closeAdventure(); });
dialog.addEventListener('close', () => {
  active = false; keyHeld = null; pointerDown = null;
  document.body.classList.remove('adventure-open');
  hero.insertBefore(stage, hero.querySelector('.hero-art'));
  stage.setAttribute('aria-hidden', 'true'); canvas.tabIndex = -1;
  graphics?.setActive(false, simulation.state);
  label.style.display = 'none';
  lastOpener?.focus({ preventScroll: true });
  lastNow = performance.now(); requestFrame();
});
$('exit-world').addEventListener('click', closeAdventure);
document.querySelectorAll('#explore-world,[data-open-world]').forEach(button => button.addEventListener('click', openAdventure));

function act(method, ...args) {
  simulation.setReducedMotion(useDiscreteMotion());
  const result = simulation[method](...args);
  syncUI(); requestFrame();
  return result;
}
document.querySelectorAll('[data-creature]').forEach(button => {
  button.addEventListener('click', () => { keyHeld = null; act('selectCreature', button.dataset.creature); });
});
$('world-catch').addEventListener('click', () => { keyHeld = null; act('catchCreature'); });
$('world-replay').addEventListener('click', () => {
  keyHeld = null; act('reset'); graphics?.setActive(active, simulation.state);
  $('world-notice').textContent = 'A fresh little adventure. Who will you find first?';
});
$('world-rotate-left').addEventListener('click', () => { graphics?.rotate(-.23); requestFrame(); });
$('world-rotate-right').addEventListener('click', () => { graphics?.rotate(.23); requestFrame(); });
$('world-zoom-in').addEventListener('click', () => { graphics?.zoom(-5); requestFrame(); });
$('world-zoom-out').addEventListener('click', () => { graphics?.zoom(5); requestFrame(); });

canvas.addEventListener('pointerdown', event => {
  if (!active || !graphics || contextLost || event.button !== 0 || !event.isPrimary) return;
  canvas.focus({ preventScroll: true });
  pointerDown = { id: event.pointerId, x: event.clientX, y: event.clientY, lastX: event.clientX, moved: false };
  canvas.setPointerCapture(event.pointerId);
});
canvas.addEventListener('pointermove', event => {
  if (!active || !graphics || contextLost) return;
  if (pointerDown?.id === event.pointerId) {
    const distance = Math.hypot(event.clientX - pointerDown.x, event.clientY - pointerDown.y);
    if (distance > 7) pointerDown.moved = true;
    if (pointerDown.moved) {
      graphics.rotate(-(event.clientX - pointerDown.lastX) * .005);
      requestFrame();
    }
    pointerDown.lastX = event.clientX;
  }
});
canvas.addEventListener('pointerup', event => {
  if (!pointerDown || pointerDown.id !== event.pointerId) return;
  const click = !pointerDown.moved;
  pointerDown = null;
  if (canvas.hasPointerCapture(event.pointerId)) canvas.releasePointerCapture(event.pointerId);
  if (!click) return;
  const hit = graphics?.pick(event.clientX, event.clientY);
  keyHeld = null;
  if (hit?.creature) act('selectCreature', hit.creature);
  else if (hit) act('moveTo', hit.x, hit.z);
});
canvas.addEventListener('pointercancel', () => { pointerDown = null; });
canvas.addEventListener('contextmenu', event => { if (active) event.preventDefault(); });
const directions = { ArrowUp: [0, -1], w: [0, -1], W: [0, -1], ArrowDown: [0, 1], s: [0, 1], S: [0, 1], ArrowLeft: [-1, 0], a: [-1, 0], A: [-1, 0], ArrowRight: [1, 0], d: [1, 0], D: [1, 0] };
function moveKey(key) {
  const direction = directions[key];
  if (!direction) return;
  const step = graphics?.screenDirection(...direction) ?? direction;
  act('stepDirection', ...step);
}
canvas.addEventListener('keydown', event => {
  if (!active || !directions[event.key]) return;
  event.preventDefault();
  if (!event.repeat) { keyHeld = event.key; moveKey(event.key); }
});
canvas.addEventListener('keyup', event => { if (event.key === keyHeld) keyHeld = null; });
canvas.addEventListener('blur', () => { keyHeld = null; });

function syncUI() {
  const state = simulation.state;
  const stamp = [state.selected, state.phase, state.caught.join(','), state.notice, paused, active].join('|');
  if (stamp === uiStamp) return;
  uiStamp = stamp;
  $('world-progress').textContent = `${state.caught.length} / 3`;
  $('world-progress').setAttribute('aria-label', `Demo collection: ${state.caught.length} of 3 creatures`);
  $('world-journal-title').textContent = state.caught.length === 3 ? 'Three new friends.' : 'A few small discoveries.';
  document.querySelector('.journal-help').textContent = state.caught.length === 3
    ? 'Your little hunting party is complete. Take your companion for a walk, or replay the adventure.'
    : 'Pick a clue to walk over, or explore at your own pace.';
  dialog.classList.toggle('adventure-complete', state.caught.length === 3);
  document.querySelectorAll('[data-creature]').forEach(button => {
    const id = button.dataset.creature, caught = state.caught.includes(id);
    button.setAttribute('aria-pressed', String(state.selected === id));
    button.classList.toggle('caught', caught);
    button.querySelector('.clue-mark').textContent = caught ? '✓' : '+';
    button.setAttribute('aria-label', `${CREATURES.find(c => c.id === id).name}${caught ? ', caught' : ', follow this clue'}`);
  });
  const creature = CREATURES.find(c => c.id === state.selected);
  $('world-creature-name').textContent = creature?.name ?? (state.caught.length === 3 ? 'A fine little hunting party.' : 'Who will you find first?');
  $('world-creature-tier').textContent = creature ? `${creature.tier} creature` : state.caught.length === 3 ? 'Three new friends' : 'A new adventure';
  $('world-creature-description').textContent = creature?.description ?? (state.caught.length === 3 ? 'You found every creature in this little corner of Lumbridge. Take your companion for a walk, or replay the adventure.' : 'Every great hunt starts with a little curiosity. Your three clues are just above.');
  const catchButton = $('world-catch');
  catchButton.disabled = state.phase !== 'ready';
  catchButton.textContent = ({ ready: 'Throw an orb', approaching: 'Walking over...', throwing: 'An orb and a little hope...', caught: 'Added to your demo collection' })[state.phase] ?? 'Choose a creature';
  const notice = state.caught.length === 3 && state.phase === 'caught'
    ? 'Three little discoveries, one lovely adventure. Your newest companion is ready to follow you.'
    : state.notice;
  if (notice !== lastNotice) { $('world-notice').textContent = notice; lastNotice = notice; }
}

const map = $('world-map'), mapContext = map.getContext('2d');
const mapBase = document.createElement('canvas'); mapBase.width = 150; mapBase.height = 150;
const mapBaseContext = mapBase.getContext('2d');
const mapPoint = (x, z) => [(x - WORLD.bounds.minX) / (WORLD.bounds.maxX - WORLD.bounds.minX) * 130 + 10, (z - WORLD.bounds.minZ) / (WORLD.bounds.maxZ - WORLD.bounds.minZ) * 130 + 10];
function buildMap() {
  const context = mapBaseContext;
  context.fillStyle = '#506340'; context.fillRect(0, 0, 150, 150);
  const a = mapPoint(WORLD.river.minX, WORLD.bounds.minZ), b = mapPoint(WORLD.river.maxX, WORLD.bounds.maxZ);
  context.fillStyle = '#658e9b'; context.fillRect(a[0], 0, b[0] - a[0], 150);
  const bridgeA = mapPoint(WORLD.bridge.minX, WORLD.bridge.minZ), bridgeB = mapPoint(WORLD.bridge.maxX, WORLD.bridge.maxZ);
  context.fillStyle = '#bcb595'; context.fillRect(bridgeA[0], bridgeA[1], bridgeB[0] - bridgeA[0], bridgeB[1] - bridgeA[1]);
  context.fillStyle = '#b3b092';
  WORLD.obstacles.forEach(obstacle => {
    if (obstacle.type === 'circle') {
      const p = mapPoint(obstacle.x, obstacle.z); context.beginPath(); context.arc(...p, obstacle.r * 1.8, 0, Math.PI * 2); context.fill();
    } else {
      const p = mapPoint(obstacle.minX, obstacle.minZ), q = mapPoint(obstacle.maxX, obstacle.maxZ); context.fillRect(...p, q[0] - p[0], q[1] - p[1]);
    }
  });
}
buildMap();
function drawMap() {
  if (!active || !mapContext) return;
  mapContext.clearRect(0, 0, 150, 150); mapContext.save(); mapContext.beginPath(); mapContext.arc(75, 75, 75, 0, Math.PI * 2); mapContext.clip();
  mapContext.drawImage(mapBase, 0, 0);
  for (const creature of CREATURES) {
    if (simulation.state.caught.includes(creature.id)) continue;
    const p = mapPoint(creature.x, creature.z); mapContext.fillStyle = `#${creature.color.toString(16).padStart(6, '0')}`; mapContext.beginPath(); mapContext.arc(...p, 2.5, 0, Math.PI * 2); mapContext.fill();
  }
  const p = mapPoint(simulation.state.player.x, simulation.state.player.z);
  mapContext.strokeStyle = '#182b19'; mapContext.lineWidth = 2; mapContext.fillStyle = '#fff7c1'; mapContext.beginPath(); mapContext.arc(...p, 3.2, 0, Math.PI * 2); mapContext.fill(); mapContext.stroke();
  mapContext.font = 'bold 12px serif'; mapContext.fillStyle = '#fff0ba'; mapContext.textAlign = 'center'; mapContext.fillText('N', 75, 15); mapContext.restore();
}
function positionLabel() {
  const state = simulation.state;
  const creature = CREATURES.find(c => c.id === state.selected);
  if (!active || !graphics || state.phase === 'throwing' || contextLost) { label.style.display = 'none'; return; }
  const showPlayer = !creature || state.phase === 'approaching' || (state.caught.includes(creature.id) && state.follower !== creature.id);
  const position = showPlayer ? state.player : state.follower === creature.id ? state.followerPosition : creature;
  const point = graphics.projectPoint(position.x, showPlayer ? 3.2 : creature.id === 'chicken' ? 2.1 : 3.4, position.z);
  const rect = viewport.getBoundingClientRect();
  if (!point.visible || point.y < rect.top + 50 || point.x < rect.left + 40 || point.x > rect.right - 40) { label.style.display = 'none'; return; }
  label.textContent = showPlayer ? 'You' : `${state.caught.includes(creature.id) ? '✓ ' : ''}${creature.name}`;
  label.style.left = `${point.x - rect.left}px`; label.style.top = `${point.y - rect.top}px`; label.style.display = 'block';
}

function frame(now) {
  pendingFrame = 0;
  const dt = Math.min(.064, Math.max(0, (now - lastNow) / 1000)); lastNow = now;
  if (document.hidden || (!active && !heroVisible)) return;
  if (!dirty && (paused || !graphics || contextLost)) return;
  dirty = false;
  if (!useDiscreteMotion()) {
    if (active && keyHeld && simulation.state.path.length === 0 && simulation.state.phase !== 'throwing') moveKey(keyHeld);
    if (active) simulation.update(dt, { reducedMotion: false });
    else ambientTime += dt;
  }
  graphics?.render(simulation.state, { paused: useDiscreteMotion(), dt, now, ambientTime: active ? simulation.state.time : ambientTime });
  drawMap(); syncUI(); positionLabel();
  if (!paused && graphics && !contextLost && !pendingFrame) pendingFrame = requestAnimationFrame(frame);
}
const observer = new IntersectionObserver(entries => {
  heroVisible = entries[0].isIntersecting;
  document.body.classList.toggle('in-hero', heroVisible);
  lastNow = performance.now(); requestFrame();
}, { threshold: 0 });
observer.observe(hero);
const resizeObserver = new ResizeObserver(() => { graphics?.resize(); requestFrame(); });
resizeObserver.observe(stage);
document.addEventListener('visibilitychange', () => { keyHeld = null; lastNow = performance.now(); requestFrame(); });

if (new URLSearchParams(location.search).has('debug')) {
  window.__runehunterWorld = {
    getState: () => ({ ...structuredClone(simulation.state), active, paused, renderer: { ...(graphics?.info() ?? { mode: 'fallback', contextLost: false, frames: 0, triangles: 0, drawCalls: 0, quality: 'static', frameTimes: [] }), simulationTime: simulation.state.time, initializationError } }),
    projectPoint: (x, y, z) => graphics?.projectPoint(x, y, z) ?? { x: 0, y: 0, visible: false },
  };
}
document.documentElement.classList.add('world-available');
requestFrame();
