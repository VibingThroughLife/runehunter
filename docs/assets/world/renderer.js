import * as THREE from '../vendor/three.module.js';
import { createWorld } from './scene.js';
import { WORLD, CREATURES } from './world-data.js';

const clamp = (n, a, b) => Math.min(b, Math.max(a, n));
const creatureMap = new Map(CREATURES.map(c => [c.id, c]));

/** One renderer moves between the establishing shot and the adventure viewport. */
export function createWorldRenderer(canvas, { onLost, onRestored } = {}) {
  let renderer;
  try {
    const context = canvas.getContext('webgl2', { alpha: false, antialias: true, powerPreference: 'low-power' });
    if (!context) return null;
    renderer = new THREE.WebGLRenderer({ canvas, context, antialias: true, alpha: false });
  } catch { return null; }

  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.16;
  const scene = new THREE.Scene();
  scene.background = new THREE.Color('#b7ccbf');
  scene.fog = new THREE.Fog('#b7ccbf', 85, 190);
  scene.add(new THREE.HemisphereLight(0xf4f1e1, 0x3b5442, 1.7));
  const sunlight = new THREE.DirectionalLight(0xffedce, 2.2);
  sunlight.position.set(-32, 60, 26);
  scene.add(sunlight);
  const world = createWorld();
  scene.add(world.group);
  const camera = new THREE.PerspectiveCamera(40, 1, .1, 240);
  const raycaster = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  let active = false, lost = false, low = innerWidth < 760, width = 1, height = 1;
  let azimuth = .77, targetAzimuth = .77, radius = 40, targetRadius = 40;
  let focus = new THREE.Vector3(WORLD.spawn.x - 4, 0, WORLD.spawn.z - 1);
  let frames = 0, lastFrameTime = null, frameTimes = [], qualitySample = [], slowFrames = 0;
  let lastCaught = 0, celebrationAt = -100, celebrationPosition = new THREE.Vector3();
  let latestState = null, latestPaused = false;
  let cameraDirty = true, lastPlayerX = null, lastPlayerZ = null;
  let framedCreature = null;
  const vector = new THREE.Vector3();

  const selection = new THREE.Mesh(new THREE.RingGeometry(.85, .95, 32), new THREE.MeshBasicMaterial({ color: 0xffe092, transparent: true, opacity: .85, depthWrite: false, side: THREE.DoubleSide }));
  selection.rotation.x = -Math.PI / 2;
  selection.position.y = .09;
  selection.visible = false;
  scene.add(selection);
  const destination = new THREE.Mesh(new THREE.RingGeometry(.32, .43, 16), new THREE.MeshBasicMaterial({ color: 0xf4d975, transparent: true, opacity: .9, depthWrite: false, side: THREE.DoubleSide }));
  destination.rotation.x = -Math.PI / 2;
  destination.position.y = .11;
  destination.visible = false;
  scene.add(destination);
  const pathGeometry = new THREE.BufferGeometry();
  const pathPositions = new Float32Array(15000);
  pathGeometry.setAttribute('position', new THREE.BufferAttribute(pathPositions, 3));
  pathGeometry.setDrawRange(0, 0);
  const pathLine = new THREE.Line(pathGeometry, new THREE.LineBasicMaterial({ color: 0xf5d886, transparent: true, opacity: .45, depthWrite: false }));
  pathLine.frustumCulled = false;
  scene.add(pathLine);

  const orb = new THREE.Group();
  orb.add(new THREE.Mesh(new THREE.IcosahedronGeometry(.26, 1), new THREE.MeshStandardMaterial({ color: 0xf8ce69, emissive: 0xb16d16, emissiveIntensity: .5, roughness: .45, metalness: .2 })));
  const orbBand = new THREE.Mesh(new THREE.TorusGeometry(.265, .038, 4, 16), new THREE.MeshBasicMaterial({ color: 0xfff5c1 }));
  orb.add(orbBand); orb.visible = false; scene.add(orb);
  const sparkleGeometry = new THREE.IcosahedronGeometry(.085, 0);
  const sparkles = new THREE.InstancedMesh(sparkleGeometry, new THREE.MeshBasicMaterial({ color: 0xffdf8a, transparent: true, opacity: 1 }), 28);
  sparkles.visible = false; sparkles.frustumCulled = false; scene.add(sparkles);
  const dummy = new THREE.Object3D();

  const targetHitAreas = CREATURES.map(c => {
    const mesh = new THREE.Mesh(new THREE.SphereGeometry(1.5, 6, 4), new THREE.MeshBasicMaterial({ visible: false }));
    mesh.position.set(c.x, 1.3, c.z); mesh.userData.creatureId = c.id; scene.add(mesh); return mesh;
  });

  function setQuality(value) {
    low = value;
    renderer.setPixelRatio(Math.min(devicePixelRatio || 1, low ? 1 : 1.5));
    world.setQuality?.(low);
    renderer.setSize(width, height, false);
  }
  function resize() {
    const rect = canvas.parentElement.getBoundingClientRect();
    width = Math.max(1, Math.round(rect.width)); height = Math.max(1, Math.round(rect.height));
    camera.aspect = width / height;
    renderer.setPixelRatio(Math.min(devicePixelRatio || 1, low ? 1 : 1.5));
    renderer.setSize(width, height, false);
    camera.updateProjectionMatrix();
  }
  function setActive(value, state) {
    active = value;
    cameraDirty = true;
    framedCreature = null;
    if (active) {
      focus.set(state.player.x - 2, 0, state.player.z - 1);
      radius = targetRadius = width < 760 ? 40 : 36;
      azimuth = targetAzimuth = .77;
    }
    lastFrameTime = null; resize();
  }
  function rotate(amount) { targetAzimuth = clamp(targetAzimuth + amount, -.4, 1.7); cameraDirty = true; }
  function zoom(amount) { targetRadius = clamp(targetRadius + amount, 20, 62); cameraDirty = true; }

  function setCamera(state, paused, dt) {
    camera.clearViewOffset();
    if (!active) {
      const mobile = width < 621;
      const target = new THREE.Vector3(mobile ? -1 : -2, 1.2, mobile ? -2 : -2);
      const distance = mobile ? 94 : 102;
      camera.position.set(target.x + distance * .66, mobile ? 54 : 58, target.z + distance * .67);
      camera.lookAt(target);
      if (!mobile) camera.setViewOffset(width, height, -width * .19, -height * .01, width, height);
    } else {
      if (state.selected !== framedCreature) {
        framedCreature = state.selected;
        if (state.selected && !state.caught.includes(state.selected)) {
          targetAzimuth = state.selected === 'dharok' ? -.4 : .77;
          targetRadius = state.selected === 'chicken' ? 28 : state.selected === 'goblin' ? 30 : 27;
          cameraDirty = true;
        }
      }
      const follow = new THREE.Vector3(state.player.x - 2, 0, state.player.z - 1);
      if (state.selected && ['ready', 'throwing', 'caught'].includes(state.phase) && (!state.caught.includes(state.selected) || state.selected === state.follower)) {
        const subject = state.follower === state.selected && state.phase === 'caught' ? state.followerPosition : creatureMap.get(state.selected);
        if (subject) follow.set((state.player.x + subject.x) / 2, 0, (state.player.z + subject.z) / 2);
      }
      const discreteInput = cameraDirty || state.player.x !== lastPlayerX || state.player.z !== lastPlayerZ;
      focus.lerp(follow, paused ? (discreteInput ? 1 : 0) : 1 - Math.exp(-dt * 3));
      const ease = paused ? (discreteInput ? 1 : 0) : 1 - Math.exp(-dt * 8);
      azimuth += (targetAzimuth - azimuth) * ease;
      radius += (targetRadius - radius) * ease;
      const pitch = .76;
      camera.position.set(focus.x + Math.sin(azimuth) * radius * Math.cos(pitch), radius * Math.sin(pitch), focus.z + Math.cos(azimuth) * radius * Math.cos(pitch));
      camera.lookAt(focus.x, 0, focus.z);
    }
    camera.updateProjectionMatrix(); camera.updateMatrixWorld();
    cameraDirty = false; lastPlayerX = state.player.x; lastPlayerZ = state.player.z;
  }

  function render(state, { paused = false, dt = 1 / 60, now = performance.now(), ambientTime = state.time } = {}) {
    if (lost) return;
    latestState = state; latestPaused = paused;
    if (state.caught.length > lastCaught) {
      celebrationAt = state.time;
      celebrationPosition.set(state.followerPosition.x, 1.3, state.followerPosition.z);
    }
    if (state.caught.length < lastCaught) celebrationAt = -100;
    lastCaught = state.caught.length;
    world.animate(ambientTime, state);
    for (const [id, actor] of Object.entries(world.actors)) {
      let position;
      if (id === 'player') position = state.player;
      else if (state.follower === id) position = state.followerPosition;
      else position = creatureMap.get(id);
      if (!position) continue;
      const captured = state.caught.includes(id);
      actor.group.visible = id === 'player' || !captured || state.follower === id;
      const shrinking = state.phase === 'throwing' && state.selected === id;
      const scale = shrinking ? 1 - clamp((state.throwProgress - .48) / .3, 0, 1) * .94 : 1;
      actor.group.scale.setScalar(scale * (actor.baseScale ?? 1));
      actor.group.position.set(position.x, 0, position.z);
      if (position.rotation !== undefined) actor.group.rotation.y = position.rotation;
      actor.group.userData.moving = active && Boolean(position.moving);
      actor.group.userData.reaction = state.selected === id ? (state.phase === 'ready' ? 'noticed' : state.phase === 'throwing' ? 'anticipating' : state.phase === 'caught' && state.time - celebrationAt < 1.8 ? 'celebrate' : '') : '';
      actor.animate(ambientTime, active && Boolean(position.moving));
    }

    const selected = creatureMap.get(state.selected);
    selection.visible = active && Boolean(selected) && !state.caught.includes(state.selected);
    if (selected) {
      selection.position.set(selected.x, .1, selected.z);
      const pulse = paused ? 1 : 1 + Math.sin(state.time * 3) * .06;
      selection.scale.setScalar(pulse);
    }
    destination.visible = active && state.path.length > 0;
    if (destination.visible) {
      const last = state.path.at(-1);
      destination.position.set(last.x, .11, last.z);
      destination.rotation.z = state.time * .6;
    }
    const route = active ? [state.player, ...state.path] : [];
    const count = Math.min(route.length, 4999);
    for (let i = 0; i < count; i++) { pathPositions[i * 3] = route[i].x; pathPositions[i * 3 + 1] = .08; pathPositions[i * 3 + 2] = route[i].z; }
    pathGeometry.attributes.position.needsUpdate = true; pathGeometry.setDrawRange(0, count > 1 ? count : 0);
    orb.visible = active && state.phase === 'throwing' && Boolean(selected);
    if (orb.visible) {
      const t = clamp(state.throwProgress / .77, 0, 1);
      orb.position.set(THREE.MathUtils.lerp(state.player.x, selected.x, t), .8 + Math.sin(t * Math.PI) * 3.5, THREE.MathUtils.lerp(state.player.z, selected.z, t));
      orb.rotation.set(state.time * 3, state.time * 5, 0);
    }
    const elapsed = state.time - celebrationAt;
    sparkles.visible = active && !paused && elapsed >= 0 && elapsed < 1.8;
    if (sparkles.visible) {
      const p = elapsed / 1.8;
      for (let i = 0; i < 28; i++) {
        const a = i * 2.39996;
        const spread = (.7 + (i % 5) / 4) * p * 2.8;
        dummy.position.set(celebrationPosition.x + Math.cos(a) * spread, Math.max(.2, celebrationPosition.y + 2.8 * p - 2.5 * p * p + Math.sin(i * 4) * p), celebrationPosition.z + Math.sin(a) * spread);
        dummy.scale.setScalar(1 - p * .9); dummy.rotation.set(i + p * 5, i, p * 6); dummy.updateMatrix(); sparkles.setMatrixAt(i, dummy.matrix);
      }
      sparkles.material.opacity = 1 - p; sparkles.instanceMatrix.needsUpdate = true;
    }
    targetHitAreas.forEach((mesh, i) => { const c = CREATURES[i]; mesh.visible = !state.caught.includes(c.id); });
    setCamera(state, paused, dt);
    renderer.render(scene, camera);
    frames++;
    if (!paused && lastFrameTime !== null) {
      const duration = now - lastFrameTime;
      if (duration > 0 && duration < 250) {
        frameTimes.push(duration); if (frameTimes.length > 1800) frameTimes.shift();
        qualitySample.push(duration); if (qualitySample.length > 90) qualitySample.shift();
        if (qualitySample.length === 90 && frames % 90 === 0) {
          const average = qualitySample.reduce((a, b) => a + b, 0) / 90;
          slowFrames = average > 34 ? slowFrames + 1 : 0;
          if (slowFrames >= 2 && !low) setQuality(true);
        }
      }
    }
    lastFrameTime = paused ? null : now;
  }

  function pick(clientX, clientY) {
    if (lost || !active) return null;
    const rect = canvas.getBoundingClientRect();
    pointer.set((clientX - rect.left) / rect.width * 2 - 1, -(clientY - rect.top) / rect.height * 2 + 1);
    raycaster.setFromCamera(pointer, camera);
    // Test the environment as well as creatures so a hidden creature cannot be selected through a wall.
    const hits = raycaster.intersectObjects([world.group, ...targetHitAreas], true);
    for (const hit of hits) {
      let ancestor = hit.object, hidden = false;
      while (ancestor) { if (!ancestor.visible) { hidden = true; break; } ancestor = ancestor.parent; }
      if (hidden) continue;
      let object = hit.object, id;
      while (object && !id) { id = object.userData.creatureId; object = object.parent; }
      if (id && !latestState?.caught.includes(id)) return { creature: id };
      if (hit.object === world.ground || hit.object.userData.walkSurface) return { x: hit.point.x, z: hit.point.z };
      if (hit.object.userData.ignorePick || hit.object.material?.transparent) continue;
      // A roof/wall click maps to that world tile; collision supplies useful feedback.
      return { x: hit.point.x, z: hit.point.z };
    }
    const groundPlane = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0);
    const point = raycaster.ray.intersectPlane(groundPlane, new THREE.Vector3());
    return point ? { x: point.x, z: point.z } : null;
  }
  function projectPoint(x, y, z) {
    const rect = canvas.getBoundingClientRect();
    vector.set(x, y, z).project(camera);
    return { x: rect.left + (vector.x + 1) / 2 * rect.width, y: rect.top + (1 - vector.y) / 2 * rect.height, visible: vector.z > -1 && vector.z < 1 && Math.abs(vector.x) < 1 && Math.abs(vector.y) < 1 };
  }
  function screenDirection(dx, dz) {
    const x = dx * Math.cos(azimuth) + dz * Math.sin(azimuth);
    const z = -dx * Math.sin(azimuth) + dz * Math.cos(azimuth);
    return Math.abs(x) > Math.abs(z) ? [Math.sign(x), 0] : [0, Math.sign(z)];
  }
  const lostHandler = event => { event.preventDefault(); lost = true; onLost?.(); };
  const restoredHandler = () => { lost = false; resize(); if (latestState) render(latestState, { paused: latestPaused }); onRestored?.(); };
  canvas.addEventListener('webglcontextlost', lostHandler);
  canvas.addEventListener('webglcontextrestored', restoredHandler);
  setQuality(low); resize();
  return {
    render, resize, setActive, rotate, zoom, pick, projectPoint, screenDirection,
    info: () => ({ mode: 'webgl', contextLost: lost, frames, triangles: renderer.info.render.triangles, drawCalls: renderer.info.render.calls, quality: low ? 'low' : 'high', frameTimes: [...frameTimes] }),
    dispose() { canvas.removeEventListener('webglcontextlost', lostHandler); canvas.removeEventListener('webglcontextrestored', restoredHandler); world.dispose(); scene.traverse(object => { object.geometry?.dispose(); if (object.material && !Array.isArray(object.material)) object.material.dispose(); }); renderer.dispose(); },
  };
}
