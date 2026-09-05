import * as THREE from '../vendor/three.module.js';
import { WORLD, CREATURES, TREES } from './world-data.js';

// A small original geometry kit, adapted from RuneHunter's earlier triangle models.
// Static surfaces share vertex-colour batches. Lighting is supplied by the renderer.
const C = { stone: 0xa9aa9c, edge: 0xc4c4af, darkStone: 0x858b80, slit: 0x343d3e,
  blue: 0x345d9e, paleBlue: 0x97bdd8, gold: 0xe3bd65, wood: 0x805c38,
  bark: 0x765336, leaf: 0x698b49, roof: 0xa66143, skin: 0xd7ae82,
  eye: 0x292a29, white: 0xf0e6c7, boot: 0x554533 };
const hash = (x, z) => { const n = Math.sin(x * 127.1 + z * 311.7) * 43758.5453; return n - Math.floor(n); };

class Batch {
  constructor() { this.p = []; this.c = []; }
  add(g, color, x = 0, y = 0, z = 0, sx = 1, sy = 1, sz = 1, ry = 0) {
    let flat = g.index ? g.toNonIndexed() : g;
    const p = flat.attributes.position, c = new THREE.Color(color), co = Math.cos(ry), si = Math.sin(ry);
    for (let i = 0; i < p.count; i++) {
      const px = p.getX(i) * sx, py = p.getY(i) * sy, pz = p.getZ(i) * sz;
      this.p.push(x + px * co + pz * si, y + py, z - px * si + pz * co);
      this.c.push(c.r, c.g, c.b);
    }
    if (flat !== g) flat.dispose(); g.dispose(); return this;
  }
  box(x, y, z, w, h, d, c, rot = 0) { return this.add(new THREE.BoxGeometry(w, h, d), c, x, y, z, 1, 1, 1, rot); }
  drum(x, y, z, r, h, c, n = 12, top = r) { return this.add(new THREE.CylinderGeometry(top, r, h, n, 1), c, x, y + h / 2, z); }
  rock(x, y, z, rx, ry, rz, c) { return this.add(new THREE.IcosahedronGeometry(1, 0), c, x, y, z, rx, ry, rz); }
  tri(a, b, c, color) { const rgb = new THREE.Color(color); this.p.push(...a, ...b, ...c); for (let i = 0; i < 3; i++) this.c.push(rgb.r, rgb.g, rgb.b); }
  quad(a, b, c, d, color) { this.tri(a, b, c, color); this.tri(a, c, d, color); }
  geometry() {
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(this.p, 3));
    g.setAttribute('color', new THREE.Float32BufferAttribute(this.c, 3));
    g.computeVertexNormals(); g.computeBoundingSphere(); return g;
  }
  mesh(material) { const mesh = new THREE.Mesh(this.geometry(), material); mesh.receiveShadow = true; return mesh; }
}

function roof(b, x, y, z, w, h, d, color) {
  const a = [x - w / 2, y, z - d / 2], q = [x + w / 2, y, z - d / 2];
  const c = [x + w / 2, y, z + d / 2], e = [x - w / 2, y, z + d / 2];
  const f = [x, y + h, z - d / 2], g = [x, y + h, z + d / 2];
  b.tri(a, f, q, color); b.tri(c, g, e, color);
  b.quad(q, f, g, c, color); b.quad(e, g, f, a, color);
}
function greataxe(b) {
  const outline = [[.13,.11],[.41,.12],[.50,.28],[.48,.50],[.62,.66],[.82,.45],[.95,.16],
    [.97,0],[.95,-.16],[.82,-.45],[.62,-.66],[.48,-.50],[.50,-.28],[.41,-.12],[.13,-.11]];
  for (const side of [-1, 1]) {
    const shape = new THREE.Shape();
    outline.forEach(([x,y],i) => i ? shape.lineTo(x*side,y) : shape.moveTo(x*side,y));
    shape.closePath();
    b.add(new THREE.ExtrudeGeometry(shape, {depth:.14,bevelEnabled:false,steps:1}), 0x859187, .25,.23,.03);
    // A pale cutting edge follows the broad faceted crescent on both sides.
    for (let i=4;i<10;i++) {
      const a=outline[i],q=outline[i+1];
      const p=(v,inset,z)=>[.25+(v[0]-inset)*side,.23+v[1],z];
      const front=[p(a,0,.184),p(q,0,.184),p(q,.075,.184),p(a,.075,.184)];
      const back=[p(a,0,.016),p(q,0,.016),p(q,.075,.016),p(a,.075,.016)];
      if(side>0){front.reverse();}else{back.reverse();}
      b.quad(...front,0xbdc8bb);b.quad(...back,0xbdc8bb);
    }
  }
  b.box(.25,-.37,.1,.10,1.7,.1,C.wood);
  b.box(.25,.23,.1,.2,.32,.2,0x5d6a61);
  b.box(.25,-1.19,.1,.16,.13,.16,C.gold);
}
function crenellations(b, x, y, z, w, d) {
  for (let q = -w / 2 + 0.5; q < w / 2; q += 1.6) {
    b.box(x + q, y, z - d / 2, 0.82, 0.9, 0.72, C.edge);
    b.box(x + q, y, z + d / 2, 0.82, 0.9, 0.72, C.edge);
  }
  for (let q = -d / 2 + 0.6; q < d / 2; q += 1.6) {
    b.box(x - w / 2, y, z + q, 0.72, 0.9, 0.82, C.edge);
    b.box(x + w / 2, y, z + q, 0.72, 0.9, 0.82, C.edge);
  }
}
function windMaterial(mode) {
  const mat = new THREE.MeshLambertMaterial({ vertexColors: true, side: THREE.DoubleSide });
  const clock = { value: 0 }; mat.userData.clock = clock;
  mat.onBeforeCompile = shader => {
    shader.uniforms.sceneTime = clock;
    shader.vertexShader = 'uniform float sceneTime;\n' + shader.vertexShader;
    const transform = mode === 'flags'
      ? 'transformed.x += (0.10 + sin(position.y * 1.7 + sceneTime * 2.2) * 0.085); transformed.z += sin(position.y * 1.5 + sceneTime * 2.0) * 0.10;'
      : mode === 'water'
        ? 'transformed.y += sin(position.z * 0.6 + position.x * 0.7 + sceneTime * 1.2) * 0.025;'
        : 'transformed.x += sin(position.x * 0.2 + position.z * 0.3 + sceneTime * 0.9) * max(0.0, position.y - 1.8) * 0.017;';
    shader.vertexShader = shader.vertexShader.replace('#include <begin_vertex>', '#include <begin_vertex>\n' + transform);
  };
  mat.customProgramCacheKey = () => `lumbridge-${mode}`;
  return mat;
}

export function createWorld() {
  const group = new THREE.Group(); group.name = 'Living Lumbridge';
  const solid = new THREE.MeshLambertMaterial({ vertexColors: true, flatShading: true });
  const foliageMaterial = windMaterial('leaves'), flagMaterial = windMaterial('flags'), waterMaterial = windMaterial('water');
  const groundBatch = new Batch(), stones = new Batch(), props = new Batch(), leaves = new Batch(), flags = new Batch(), water = new Batch();
  const grass = [0x607a4d, 0x637d50, 0x678053, 0x60794c, 0x647c4e];
  const path = [0xa09988, 0xa29b89, 0x9e9888, 0xa19a88];
  for (let x = -46; x < 46; x += 2) for (let z = -42; z < 48; z += 2) {
    const river = x >= 12 && x < 20;
    const courtyard = x > -24 && x < 4 && z > -22 && z < 16;
    const road = (z >= 6 && z <= 10 && x > -12 && x < 34) || (x >= -4 && x <= -2 && z > -14 && z < 10)
      || (x > -12 && x < -2 && z > -14 && z < -10) || (x >= 6 && x <= 8 && z > 10 && z < 25)
      || (x >= 24 && x <= 26 && z > -6 && z < 12);
    let color = river ? 0x617f70 : road ? path[Math.floor(hash(x, z) * path.length)] : grass[Math.floor(hash(x, z) * grass.length)];
    if (courtyard && !road) color = hash(x, z) > 0.6 ? 0x77846b : 0x708065;
    const y = river ? -0.42 : 0;
    groundBatch.quad([x, y, z], [x, y, z + 2], [x + 2, y, z + 2], [x + 2, y, z], color);
    if (x === 18) groundBatch.quad([19, 0, z], [19, 0, z + 2], [20, 0, z + 2], [20, 0, z], grass[Math.floor(hash(x, z) * grass.length)]);
  }
  const ground = groundBatch.mesh(solid); ground.name = 'Walkable terrain'; group.add(ground);

  // Water stays below the walkable bridge; short pale facets imply flowing ripples.
  for (let z = -42; z < 48; z += 2) for (let x = 12; x < 19; x += 1) {
    water.quad([x, -0.21, z], [x, -0.21, z + 2], [x + 1, -0.21, z + 2], [x + 1, -0.21, z],
      [0x4f8695, 0x578d98, 0x538a95][Math.floor(hash(x, z) * 3)]);
    if (hash(x + 5, z) > 0.72) water.quad([x + 0.2, -0.18, z + 0.3], [x + 0.2, -0.18, z + 1.2], [x + 0.32, -0.18, z + 1.2], [x + 0.32, -0.18, z + 0.3], 0xa9c7bf);
  }
  group.add(water.mesh(waterMaterial));
  for (let z = -39; z < 47; z += 3) for (const x of [11.65, 19.3]) {
    if (z > 4 && z < 13) continue;
    props.rock(x, -0.03, z, 0.6, 0.34, 0.9, hash(x, z) > 0.5 ? 0x9a9a7d : 0xa4a688);
    for (let n = 0; n < 3; n++) props.box(x + n * 0.17, 0.33 + n * 0.06, z + 0.5, 0.05, 0.75, 0.06, 0x718e4b, n * 0.4);
  }

  // A flat bridge is deliberate: walkable ground and render height agree.
  stones.box(15.5, -0.28, 8.5, 15, 0.62, 5, C.darkStone);
  stones.box(15.5, 0.035, 8.5, 15, 0.07, 4.8, 0xb1ae96);
  for (const z of [5.55, 11.45]) {
    stones.box(15.5, 0.48, z, 13, 0.88, 0.72, C.stone);
    stones.box(15.5, 0.96, z, 13.4, 0.16, 0.88, C.edge);
    for (let x = 9.2; x <= 22; x += 2.1) stones.box(x, 0.52, z + 0.37, 0.07, 0.78, 0.015, C.darkStone);
  }
  for (const x of [12.7, 18.3]) stones.box(x, -0.62, 8.5, 1.2, 1.4, 5.4, C.darkStone);

  // Lumbridge's unmistakable blue-bannered gate, round towers and square keep.
  const wall = (x, z, w, d) => {
    stones.box(x, 2.25, z, w, 4.5, d, C.stone);
    stones.box(x, 4.55, z, w + 0.2, 0.3, d + 0.2, C.darkStone);
    crenellations(stones, x, 5.1, z, w, d);
    stones.box(x, 0.22, z, w + 0.15, 0.44, d + 0.15, C.darkStone);
  };
  wall(-25, -3, 1.5, 38); wall(-10, -22, 30, 1.5); wall(-10, 16, 30, 1.5);
  wall(5, -10.5, 1.5, 23); wall(5, 15.5, 1.5, 1);
  function tower(x, z, height = 8) {
    stones.drum(x, 0, z, 2.35, height, C.stone, 12, 2.24);
    stones.drum(x, 0, z, 2.5, 0.55, C.darkStone, 12);
    stones.drum(x, height - 0.2, z, 2.6, 0.45, C.edge, 12);
    for (let i = 0; i < 12; i++) { const a = i * Math.PI / 6;
      stones.box(x + Math.cos(a) * 2.35, height + 0.55, z + Math.sin(a) * 2.35, 0.72, 1.1, 0.7, C.edge, -a);
    }
    for (const y of [2.9, 5.6]) {
      stones.box(x + 2.26, y, z, 0.07, 1.05, 0.25, C.slit);
      stones.box(x + 2.27, y + 0.24, z, 0.08, 0.2, 0.63, C.slit);
      stones.box(x, y, z + 2.26, 0.25, 1.05, 0.08, C.slit);
    }
    for (let row = 1; row < 7; row += 1.35) for (let i = 0; i < 6; i++) {
      const a = i * Math.PI / 3 + row * 0.24;
      stones.box(x + Math.cos(a) * 2.3, row, z + Math.sin(a) * 2.3, 0.54, 0.19, 0.12, hash(i, row) > 0.5 ? C.darkStone : C.edge, -a + Math.PI / 2);
    }
  }
  tower(5, 3); tower(5, 13);
  // Open arched gate: no dark rectangle across the traversable doorway.
  for (let i = 0; i < 10; i++) {
    const a = i / 10 * Math.PI, b = (i + 1) / 10 * Math.PI;
    const p = r => [8 + Math.cos(r) * 2.35, 2.6 + Math.sin(r) * 2.35];
    const q = r => [8 + Math.cos(r) * 3.05, 2.6 + Math.sin(r) * 3.05];
    const pa = p(a), pb = p(b), qa = q(a), qb = q(b);
    const v = (x, c) => [x, c[1], c[0]];
    stones.quad(v(6.15, pa), v(6.15, pb), v(6.15, qb), v(6.15, qa), i % 2 ? C.stone : C.edge);
    stones.quad(v(3.9, pb), v(3.9, pa), v(3.9, qa), v(3.9, qb), C.darkStone);
    stones.quad(v(3.9, pa), v(3.9, pb), v(6.15, pb), v(6.15, pa), C.darkStone);
  }
  stones.box(5, 5.8, 8, 2.3, 1.0, 5.5, C.stone);
  crenellations(stones, 5, 6.75, 8, 2.3, 5.5);
  for (const z of [5.4, 10.6]) {
    stones.box(4.0, 1.3, z, 0.25, 2.6, 0.34, C.wood);
    for (let y = 0.4; y < 2.6; y += 0.8) stones.box(3.83, y, z, 0.08, 0.09, 0.4, C.darkStone);
  }
  stones.box(-17.4, 4.65, -11, 12.6, 9.3, 15.2, C.stone);
  for (const z of [-18.25, -3.75]) stones.box(-11, 4.6, z, 0.35, 9.2, 0.55, 0xb3b7a7);
  stones.box(-17.4, 9.4, -11, 13.1, 0.5, 15.7, C.darkStone);
  crenellations(stones, -17.4, 10.1, -11, 13, 15.5);
  stones.box(-17.4, 10.8, -12, 8.5, 2.5, 10, C.stone);
  stones.box(-17.4, 12.2, -12, 8.9, 0.4, 10.4, C.edge);
  crenellations(stones, -17.4, 12.85, -12, 8.9, 10.4);
  for (const z of [-16.5, -5.5]) for (const y of [3, 6.5]) {
    stones.box(-11.06, y, z, 0.08, 1.5, 0.55, C.slit);
    stones.box(-10.98, y - 0.85, z, 0.3, 0.18, 0.88, C.edge);
  }
  stones.box(-11.03, 1.65, -10.5, 0.13, 3.3, 2.4, C.wood);
  stones.box(-10.93, 1.65, -10.5, 0.05, 3.3, 0.08, C.slit);
  stones.box(-10.92, 1.4, -10.12, 0.06, 0.2, 0.2, C.gold);
  // Broad, separated stones avoid the former thin coplanar lines across the door.
  for (let row = 0; row < 4; row++) for (let i = 0; i < 5; i++) {
    const y = 1.35 + row * 2.05, z = -17.5 + i * 2.8 + (row % 2) * 0.4;
    if (y < 3.5 && Math.abs(z + 10.5) < 2) continue;
    if ((Math.abs(z + 16.5) < 0.8 || Math.abs(z + 5.5) < 0.8) && (Math.abs(y - 3) < 1 || Math.abs(y - 6.5) < 1)) continue;
    stones.box(-11.06, y, z, 0.07, 0.23, 0.92, hash(i,row) > 0.5 ? 0x9ca396 : 0xb4b6a7);
  }
  const banner = (x, y, z, width, height) => {
    const count = 5;
    for (let i = 0; i < count; i++) {
      const top = y - i * height / count, bottom = y - (i + 1) * height / count;
      flags.quad([x, top, z - width / 2], [x, bottom, z - width / 2], [x, bottom, z + width / 2], [x, top, z + width / 2], C.blue);
    }
    flags.tri([x, y - height, z - width / 2], [x, y - height - width * 0.45, z], [x, y - height, z + width / 2], C.blue);
    flags.quad([x + 0.09, y - height * 0.18, z], [x + 0.09, y - height * 0.45, z - width * 0.25],
      [x + 0.09, y - height * 0.73, z], [x + 0.09, y - height * 0.45, z + width * 0.25], C.paleBlue);
    props.box(x, y + 0.08, z, 0.12, 0.16, width + 0.3, C.gold);
  };
  banner(7.35, 7.15, 3, 1.35, 2.8); banner(7.35, 7.15, 13, 1.35, 2.8);
  banner(-10.98, 8.45, -8, 1.3, 3.2); banner(-10.98, 8.45, -13, 1.3, 3.2);
  for (const z of [-15.5, -8.5]) {
    props.box(-17, 14.0, z, 0.13, 3.8, 0.13, C.wood);
    banner(-17, 15.5, z + 0.8, 1.6, 1.05);
  }

  // Courtyard well, supply crates, a timber farmhouse and its wheat patch.
  stones.drum(-15, 0, 5, 1.25, 0.9, C.stone, 10);
  stones.drum(-15, 0.88, 5, 1.35, 0.17, C.edge, 10);
  props.drum(-15, 1.055, 5, 0.93, 0.01, 0x4c787d, 10);
  for (const x of [-16.05, -13.95]) props.box(x, 1.5, 5, 0.18, 1.7, 0.18, C.wood);
  roof(props, -15, 2.35, 5, 3.2, 0.75, 2.6, C.roof);
  for (const [x, z, y] of [[-22, 9, 0.6], [-21.4, 11, 0.6], [-22, 9, 1.8]]) {
    props.box(x, y, z, 1.35, 1.2, 1.35, 0xa07c48);
    props.box(x + 0.69, y, z, 0.05, 0.13, 1.4, C.wood);
    props.box(x, y, z + 0.69, 1.4, 0.13, 0.05, C.wood);
  }
  props.box(30.5, 1.6, -9, 6.4, 3.2, 5.4, 0xd1c6a2);
  roof(props, 30.5, 3.2, -9, 7.3, 2.5, 6.3, C.roof);
  for (const x of [27.3, 30.5, 33.7]) props.box(x, 1.6, -6.27, 0.16, 3.2, 0.14, C.wood);
  props.box(30.5, 2.9, -6.23, 6.5, 0.17, 0.16, C.wood);
  props.box(30.5, 1.1, -6.19, 1.1, 2.2, 0.16, C.wood);
  for (const x of [28.5, 32.5]) props.box(x, 1.9, -6.16, 0.85, 0.9, 0.08, 0x416375);
  props.box(32.2, 4.7, -10.5, 0.9, 2.2, 0.9, C.darkStone);
  const fence = (x, z, length, rotation = 0) => {
    const co = Math.cos(rotation), si = Math.sin(rotation);
    for (let d = -length / 2; d <= length / 2 + 0.1; d += 1.5) props.box(x + d * co, 0.58, z - d * si, 0.14, 1.16, 0.14, C.wood);
    for (const y of [0.42, 0.85]) props.box(x, y, z, length, 0.12, 0.12, 0xa48350, rotation);
  };
  fence(21, -8.5, 8, Math.PI / 2); fence(24, -12, 6); fence(21.8, -4.8, 1.8); fence(26.7, -4.8, 1.4);
  for (let x = 21.8; x < 27; x += 0.55) for (let z = -11.2; z < -5.2; z += 0.62) {
    const h = 0.6 + hash(x, z) * 0.35;
    props.box(x, h / 2, z, 0.055, h, 0.055, 0xb29b42);
    props.rock(x, h, z, 0.12, 0.24, 0.10, hash(x, z) > 0.5 ? 0xdbbd63 : 0xcbb05b);
  }
  for (const [x, z, scale] of TREES) {
    props.drum(x, 0, z, 0.36 * scale, 3.2 * scale, C.bark, 6, 0.22 * scale);
    props.box(x + 0.4 * scale, 2.8 * scale, z, 1.0 * scale, 0.22 * scale, 0.22 * scale, C.bark, 0.35);
    const parts = [[0, 3.6, 0, 1.6], [-0.9, 4.1, 0.35, 1.15], [0.85, 4.4, -0.45, 1.2], [0, 5.2, 0, 1.0]];
    for (let i = 0; i < parts.length; i++) {
      const [dx, y, dz, r] = parts[i]; leaves.rock(x + dx * scale, y * scale, z + dz * scale, r * scale, r * 0.83 * scale, r * scale, [0x48663b, 0x587944, 0x648650, 0x6d8d57][i]);
    }
  }
  for (const [x, z, r] of [[6.5, 23.5, 1.1], [22, 18, 1], [-3.7, -4.3, 0.85]]) {
    props.rock(x, 0.45, z, r, 0.8, r * 0.8, 0x9a9e85); props.rock(x + 0.65, 0.2, z - 0.3, 0.45, 0.4, 0.5, 0x8b917a);
  }
  for (let i = 0; i < 65; i++) {
    const x = 21 + hash(i, 8) * 13, z = 16 + hash(i, 4) * 20;
    if (Math.hypot(x - 30, z - 25) < 1.5) continue;
    props.box(x, 0.1, z, 0.035, 0.2, 0.035, 0x65834a);
    props.rock(x, 0.23, z, 0.12, 0.09, 0.12, i % 4 ? 0xeee1a3 : 0xe0b274);
  }
  // Low-poly scenery outside navigation bounds closes the horizon gently.
  for (const [x, z, r, h] of [[-36, -38, 15, 5], [-15, -43, 20, 5], [10, -44, 15, 4], [34, -38, 13, 6], [-44, 16, 12, 4], [40, 42, 13, 4]])
    leaves.rock(x, 0, z, r, h, r * 0.7, 0x849968);
  const staticMesh = stones.mesh(solid); staticMesh.name = 'Castle and bridge'; staticMesh.castShadow = true; group.add(staticMesh);
  const propMesh = props.mesh(solid); propMesh.name = 'Village details'; group.add(propMesh);
  const canopy = leaves.mesh(foliageMaterial); canopy.name = 'Swaying woodland'; canopy.castShadow = true; group.add(canopy);
  const cloth = flags.mesh(flagMaterial); cloth.name = 'Lumbridge blue banners'; group.add(cloth);

  // Articulated chunky figures: six local mesh groups, one shared lit material.
  const shadowMaterial = new THREE.ShaderMaterial({ transparent: true, depthWrite: false,
    uniforms: {}, vertexShader: 'varying vec2 vUv; void main(){vUv=uv;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0);}',
    fragmentShader: 'varying vec2 vUv; void main(){float a=(1.0-smoothstep(0.1,0.5,length(vUv-0.5)))*0.23;gl_FragColor=vec4(0.16,0.20,0.11,a);}' });
  const shadowGeometry = new THREE.PlaneGeometry(2.2, 2.2); shadowGeometry.rotateX(-Math.PI / 2);
  const contactPositions = [], contactUvs = [];
  const contacts = [...TREES.map(([x, z, s]) => [x + 0.5, z + 0.3, s * 2.7, s * 2.1]),
    [5.25, 3.2, 3.4, 3.1], [5.25, 13.2, 3.4, 3.1], [-17.1, -10.7, 7.7, 8.8], [-15, 5, 1.8, 1.6]];
  for (const [x, z, rx, rz] of contacts) {
    contactPositions.push(x-rx,.028,z-rz, x-rx,.028,z+rz, x+rx,.028,z+rz, x-rx,.028,z-rz, x+rx,.028,z+rz, x+rx,.028,z-rz);
    contactUvs.push(0,0, 0,1, 1,1, 0,0, 1,1, 1,0);
  }
  const contactGeometry = new THREE.BufferGeometry();
  contactGeometry.setAttribute('position', new THREE.Float32BufferAttribute(contactPositions,3));
  contactGeometry.setAttribute('uv', new THREE.Float32BufferAttribute(contactUvs,2));
  const contactsMesh = new THREE.Mesh(contactGeometry,shadowMaterial); contactsMesh.name='Soft ground contact'; group.add(contactsMesh);
  function makeActor(kind, tunic = 0x668957, scale = 1) {
    const actor = new THREE.Group(), rig = new THREE.Group(); actor.add(rig);
    actor.name = kind; const limbs = [], head = new THREE.Group(); rig.add(head);
    const shadow = new THREE.Mesh(shadowGeometry, shadowMaterial); shadow.position.y = 0.042; actor.add(shadow);
    const part = (parent, draw) => { const b = new Batch(); draw(b); const m = b.mesh(solid); m.receiveShadow = false; parent.add(m); return m; };
    const pivot = (x, y, z, draw, phase = 1) => { const g = new THREE.Group(); g.position.set(x, y, z); rig.add(g); part(g, draw); limbs.push({ g, phase }); return g; };
    let body;
    if (kind === 'chicken') {
      body = part(rig, b => { b.rock(0, 0.48, 0, 0.47, 0.38, 0.56, 0xf0e9d4); b.box(0, 0.65, -0.43, 0.35, 0.42, 0.16, 0xe0dcc9); });
      head.position.set(0, 0.62, 0.32); part(head, b => {
        b.box(0, 0.22, 0, 0.31, 0.39, 0.3, 0xf3ecd7); b.box(0, 0.46, -0.02, 0.12, 0.14, 0.26, 0xc55340);
        b.box(0, 0.15, 0.23, 0.15, 0.12, 0.2, 0xe3ab4d); b.box(0, 0.0, 0.18, 0.09, 0.17, 0.1, 0xc55340);
        for (const x of [-0.16, 0.16]) b.box(x, 0.26, 0.08, 0.035, 0.085, 0.075, C.eye);
      });
      for (const s of [-1, 1]) pivot(s * 0.17, 0.28, 0, b => { b.box(0, -0.13, 0, 0.055, 0.28, 0.055, 0xdca03e); b.box(0, -0.25, 0.09, 0.12, 0.04, 0.22, 0xdca03e); }, s);
      shadow.scale.setScalar(0.55);
    } else {
      const goblin = kind === 'goblin', dh = kind === 'dharok';
      const skin = goblin ? 0x90a969 : dh ? 0xa4a791 : C.skin;
      const armor = dh ? 0x6b6d5c : tunic;
      body = part(rig, b => {
        b.box(0, 1.02, 0, dh ? 0.8 : 0.65, 0.67, 0.43, armor);
        b.box(0, 0.73, 0, 0.68, 0.12, 0.45, C.wood); b.box(0, 0.74, 0.235, 0.16, 0.11, 0.045, C.gold);
        if (dh) { b.box(-0.47, 1.3, 0, 0.34, 0.28, 0.52, 0x7a7c65); b.box(0.47, 1.3, 0, 0.34, 0.28, 0.52, 0x7a7c65); b.box(0, 1.03, 0.235, 0.46, 0.4, 0.05, 0x535747); }
        else if (!goblin) { b.box(0, 1.04, -0.26, 0.6, 0.75, 0.09, 0x355e7e); b.box(0, 1.3, 0.23, 0.47, 0.07, 0.04, 0xe1ce9b); }
      });
      head.position.y = goblin ? 1.32 : 1.43;
      part(head, b => {
        b.box(0, 0.14, 0.02, goblin ? 0.62 : 0.48, 0.45, 0.42, skin);
        for (const s of [-1, 1]) {
          if (dh) b.box(s * 0.13, 0.21, 0.24, 0.115, 0.055, 0.035, C.eye);
          else { b.box(s * 0.13, 0.21, 0.24, 0.1, 0.1, 0.035, C.white); b.box(s * 0.13, 0.21, 0.263, 0.048, 0.062, 0.025, C.eye); }
        }
        b.box(0, 0.035, 0.24, 0.12, 0.035, 0.03, 0x7f6349);
        if (goblin) { b.box(0, 0.1, 0.32, 0.18, 0.19, 0.22, 0x839b5c); for (const s of [-1, 1]) { b.box(s * 0.4, 0.22, -0.01, 0.3, 0.18, 0.27, skin); b.box(s * 0.58, 0.27, -0.04, 0.15, 0.11, 0.19, skin); } }
        else if (dh) { b.box(0, 0.38, 0, 0.58, 0.27, 0.51, 0x575d51); b.box(0, 0.21, 0.25, 0.55, 0.12, 0.07, 0x575d51); for (const s of [-1, 1]) { b.box(s * 0.38, 0.31, 0, 0.23, 0.2, 0.25, 0x9e9b7b); b.box(s * 0.48, 0.49, 0, 0.16, 0.29, 0.17, 0xa9a387); } }
        else { b.box(0, 0.39, -0.03, 0.51, 0.15, 0.45, 0x6e4c33); b.box(0, 0.19, -0.22, 0.5, 0.35, 0.08, 0x6e4c33); }
      });
      for (const s of [-1, 1]) pivot(s * 0.19, 0.69, 0, b => {
        b.box(0, -0.29, 0, 0.26, 0.59, 0.28, dh ? 0x5c6355 : goblin ? 0x839b5c : 0x776344);
        b.box(0, -0.62, 0.065, 0.3, 0.17, 0.39, goblin ? 0x839b5c : C.boot);
      }, s);
      for (const s of [-1, 1]) pivot(s * 0.43, 1.28, 0, b => {
        b.box(0, -0.2, 0, 0.21, 0.42, 0.26, armor); b.box(0, -0.47, 0.03, 0.2, 0.18, 0.22, skin);
        if (dh && s === 1) greataxe(b);
      }, -s);
      if (goblin) rig.scale.set(1.03, 0.83, 1.03);
    }
    actor.scale.setScalar(scale);
    const result = { group: actor, baseScale: scale, animate(time, moving = false) {
      const reaction = actor.userData.reaction, stride = moving ? Math.sin(time * 9) : 0;
      rig.position.y = moving ? Math.abs(stride) * 0.045 : Math.sin(time * 2 + scale) * 0.012;
      if (reaction === 'celebrate') rig.position.y += Math.max(0, Math.sin(time * 8)) * 0.23;
      for (const { g, phase } of limbs) g.rotation.x = stride * 0.52 * phase;
      head.rotation.y = moving ? 0 : Math.sin(time * 0.85 + scale * 2) * 0.16;
      head.rotation.x = kind === 'chicken' && !moving ? Math.max(0, Math.sin(time * 1.1)) ** 12 * 0.7 : reaction === 'anticipating' ? -0.16 : 0;
      if (reaction === 'noticed') head.rotation.z = 0.12; else head.rotation.z = 0;
      body.scale.y = 1 + (moving ? 0 : Math.sin(time * 2) * 0.008);
    } };
    return result;
  }
  const actors = { player: makeActor('player', 0x6a8f65, 1.18), chicken: makeActor('chicken', 0, 1.25),
    goblin: makeActor('goblin', 0x9d624c, 1.05), dharok: makeActor('dharok', 0, 0.80) };
  actors.player.group.position.set(WORLD.spawn.x, 0, WORLD.spawn.z);
  const pickables = [];
  for (const c of CREATURES) {
    actors[c.id].group.position.set(c.x, 0, c.z); actors[c.id].group.rotation.y = c.id === 'dharok' ? 1.0 : 2.2;
    actors[c.id].group.traverse(mesh => { if (mesh.isMesh && mesh.material !== shadowMaterial) { mesh.userData.creatureId = c.id; pickables.push(mesh); } });
  }
  for (const actor of Object.values(actors)) group.add(actor.group);
  const villagers = [{ actor: makeActor('villager', 0xb28857, 1.07), x: -7, z: 6, radius: 2.0, phase: 0 },
    { actor: makeActor('villager', 0x71899e, 1.05), x: 27.5, z: 18, radius: 1.6, phase: 2.2 }];
  for (const npc of villagers) group.add(npc.actor.group);
  const hens = [{ actor: makeActor('chicken', 0, 0.85), x: 25.5, z: -2.5, phase: 0.7 },
    { actor: makeActor('chicken', 0, 0.75), x: 22.4, z: -2.2, phase: 3.1 }];
  for (const hen of hens) group.add(hen.actor.group);
  let lowQuality = false;
  function animate(time, state = {}) {
    for (const m of [foliageMaterial, flagMaterial, waterMaterial]) m.userData.clock.value = time;
    for (const [id, actor] of Object.entries(actors)) actor.animate(time, Boolean(actor.group.userData.moving ?? state.actors?.[id]?.moving));
    for (const npc of villagers) {
      const t = time * 0.22 + npc.phase;
      npc.actor.group.position.set(npc.x + Math.sin(t) * npc.radius, 0, npc.z + Math.cos(t) * npc.radius * 0.55);
      npc.actor.group.rotation.y = Math.atan2(Math.cos(t), -Math.sin(t) * 0.55); npc.actor.animate(time, true);
    }
    for (const hen of hens) {
      const t = time * 0.28 + hen.phase;
      hen.actor.group.position.set(hen.x + Math.sin(t) * 0.45, 0, hen.z + Math.cos(t) * 0.6);
      hen.actor.group.rotation.y = t + Math.PI / 2; hen.actor.animate(time, Math.sin(t * 2) > 0.4);
    }
  }
  animate(0);
  return { group, ground, actors, pickables, animate,
    setQuality(low) { lowQuality = Boolean(low); for (const h of hens) h.actor.group.visible = !lowQuality; },
    dispose() {
      const geometries = new Set(), materials = new Set();
      group.traverse(o => { if (o.geometry) geometries.add(o.geometry); if (o.material) materials.add(o.material); });
      for (const g of geometries) g.dispose(); for (const m of materials) m.dispose();
    },
  };
}
