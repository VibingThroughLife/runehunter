import * as THREE from '../vendor/three.module.js';
import { WORLD, CREATURES, TREES } from './world-data.js';

// A small original geometry kit, adapted from RuneHunter's earlier triangle models.
// Static surfaces share vertex-colour batches. Lighting is supplied by the renderer.
const C = { stone: 0x999e99, edge: 0xb8bbb1, darkStone: 0x757d77, slit: 0x343d3e,
  blue: 0x3665ab, paleBlue: 0xd8d8b6, gold: 0xe3bd65, wood: 0x805c38,
  bark: 0x765336, leaf: 0x698b49, roof: 0xa66143, skin: 0xd7ae82,
  eye: 0x292a29, white: 0xf0e6c7, boot: 0x554533 };
const hash = (x, z) => { const n = Math.sin(x * 127.1 + z * 311.7) * 43758.5453; return n - Math.floor(n); };

class Batch {
  constructor() { this.p = []; this.c = []; }
  add(g, color, x = 0, y = 0, z = 0, sx = 1, sy = 1, sz = 1, ry = 0) {
    let flat = g.index ? g.toNonIndexed() : g;
    const p = flat.attributes.position, colors = flat.attributes.color, c = new THREE.Color(color), co = Math.cos(ry), si = Math.sin(ry);
    for (let i = 0; i < p.count; i++) {
      const px = p.getX(i) * sx, py = p.getY(i) * sy, pz = p.getZ(i) * sz;
      this.p.push(x + px * co + pz * si, y + py, z - px * si + pz * co);
      this.c.push(c.r * (colors ? colors.getX(i) : 1), c.g * (colors ? colors.getY(i) : 1), c.b * (colors ? colors.getZ(i) : 1));
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
// A tapered rectangular prism gives limbs the angular OSRS silhouette without
// subdivided cubes or a toy-like square head.
function taper(b,x,y,z,top,bottom,h,depth,color) {
  const p=[[-bottom/2,-h/2,-depth/2],[bottom/2,-h/2,-depth/2],[bottom/2,-h/2,depth/2],[-bottom/2,-h/2,depth/2],
    [-top/2,h/2,-depth/2],[top/2,h/2,-depth/2],[top/2,h/2,depth/2],[-top/2,h/2,depth/2]].map(a=>[x+a[0],y+a[1],z+a[2]]);
  for(const a of [[0,1,5,4],[1,2,6,5],[2,3,7,6],[3,0,4,7],[4,5,6,7],[3,2,1,0]])b.quad(...a.map(i=>p[i]).reverse(),color);
}
function greataxe(b) {
  // Dharok's actual asymmetrical, broad chipped iron blade and long dark haft.
  // It is deliberately distinct from the former generic double crescent axe.
  const shape = new THREE.Shape();
  [[-.10,.33],[.18,.40],[.55,.26],[.81,.42],[.68,.68],[.71,1.02],[.99,1.46],[.55,1.40],[.18,1.12],[-.09,.61]].forEach(([x,y],i)=>i?shape.lineTo(x,y):shape.moveTo(x,y));shape.closePath();
  b.add(new THREE.ExtrudeGeometry(shape,{depth:.12,bevelEnabled:false}),0x777d73,0,0,-.04);
  b.tri([.20,1.12,.085],[.55,1.4,.085],[.99,1.46,.085],0xa0a393);
  b.tri([.20,1.12,.085],[.99,1.46,.085],[.71,1.02,.085],0x8c9487);
  b.box(.07,.13,.02,.105,1.95,.10,0x484b30);
  b.box(.07,-.87,.02,.16,.16,.14,0x3b412b);
  for(let i=0;i<4;i++)b.tri([.015,-.2+i*.22,.081],[-.18,-.16+i*.22,.081],[.015,.02+i*.22,.081],0xcbc08a);
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
  for (let x = -90; x < 90; x += 3) for (let z = -87; z < 90; z += 3) {
    const river = x >= 12 && x < 21;
    const courtyard = x > -24 && x < 4 && z > -22 && z < 16;
    const road = (x >= -36 && x <= -30 && z >= -8 && z <= 24) || (z >= 18 && z <= 24 && x >= -36 && x <= 10) || (x >= -33 && x <= -24 && z >= -8 && z <= -2) || (z >= 6 && z <= 10 && x > -12 && x < 34) || (x >= -4 && x <= -2 && z > -14 && z < 10)
      || (x > -12 && x < -2 && z > -14 && z < -10) || (x >= 6 && x <= 8 && z > 10 && z < 25)
      || (x >= 24 && x <= 26 && z > -6 && z < 12);
    let color = river ? 0x617f70 : road ? path[Math.floor(hash(x, z) * path.length)] : grass[Math.floor(hash(x, z) * grass.length)];
    if (courtyard && !road) color = hash(x, z) > 0.6 ? 0x77846b : 0x708065;
    const y = river ? -0.42 : 0;
    groundBatch.quad([x, y, z], [x, y, z + 3], [x + 3, y, z + 3], [x + 3, y, z], color);
    if (x === 18) groundBatch.quad([19, 0, z], [19, 0, z + 3], [21, 0, z + 3], [21, 0, z], grass[Math.floor(hash(x, z) * grass.length)]);
  }
  const ground = groundBatch.mesh(solid); ground.name = 'Walkable terrain'; group.add(ground);

  // Water stays below the walkable bridge; short pale facets imply flowing ripples.
  for (let z = -87; z < 90; z += 3) for (let x = 12; x < 19; x += 1) {
    water.quad([x, -0.21, z], [x, -0.21, z + 3], [x + 1, -0.21, z + 3], [x + 1, -0.21, z],
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

  // OSRS reference: a long terraced palace, separate courtyard gate and twin
  // fountains. The west kitchen door remains readable throughout the rear shot.
  const wall = (x, z, w, d, h = 3.4) => {
    stones.box(x, h / 2, z, w, h, d, C.stone);
    stones.box(x, h, z, w + .16, .2, d + .16, C.darkStone);
    crenellations(stones, x, h + .45, z, w, d);
    stones.box(x, .22, z, w + .18, .44, d + .18, C.darkStone);
  };
  wall(-10, -22, 30, 1.3); wall(-10, 16, 30, 1.3);
  wall(5, -10.5, 1.3, 23); wall(5, 15.5, 1.3, 1);
  const arrowSlit = (x,y,z,rotation = 0) => {
    const b = new Batch();
    b.box(0,0,0,.035,1.16,.14,C.slit); b.box(0,.2,0,.045,.15,.53,C.slit);
    b.tri([.025,.75,0],[.025,.34,-.2],[.025,.34,.2],C.slit);
    stones.add(b.geometry(),0xffffff,x,y,z,1,1,1,rotation);
  };
  function tower(x,z,height = 6.4) {
    stones.drum(x,0,z,2.0,height,C.stone,12,1.96);
    stones.drum(x,0,z,2.14,.42,C.darkStone,12);
    stones.drum(x,height-.12,z,2.22,.3,C.edge,12);
    for(let i=0;i<12;i++) { const a=i*Math.PI/6;
      stones.box(x+Math.cos(a)*2.05,height+.43,z+Math.sin(a)*2.05,.64,.9,.62,C.edge,-a);
    }
    for(const y of [2.2,4.5]) { arrowSlit(x+1.965,y,z); arrowSlit(x,y,z+1.965, -Math.PI/2); }
    for(let row=1;row<5.5;row+=1.15) for(let i=0;i<8;i++) {
      const a=i*Math.PI/4+row*.2;
      stones.box(x+Math.cos(a)*1.99,row,z+Math.sin(a)*1.99,.43,.15,.05,hash(i,row)>.5?C.darkStone:C.edge,-a+Math.PI/2);
    }
  }
  tower(5,3); tower(5,13);
  // A crenellated lintel across the open gateway, supported by the two towers.
  stones.box(5,4.5,8,1.4,1.1,6.5,C.stone); crenellations(stones,5,5.48,8,1.4,6.5);
  for(const z of [5.25,10.75]) props.box(4.2,1.45,z,.15,2.9,.16,C.wood);

  const terrace = (x,z,w,d,y,h) => {
    stones.box(x,y+h/2,z,w,h,d,C.stone);
    stones.box(x,y+h+.05,z,w+.18,.24,d+.18,C.darkStone);
    crenellations(stones,x,y+h+.6,z,w,d);
  };
  // The long north–south silhouette and ascending roof terraces replace the
  // previous generic square keep. All four faces are intentionally modeled.
  terrace(-18,-3,14,38,0,4.0);
  terrace(-19,-3,11.8,31,4.05,3.9);
  terrace(-20,-5,8.8,16,8.05,3.0);
  // Walkable-looking roof surfaces make the bank roof/cannons read from above.
  stones.box(-18,4.18,-3,13.4,.08,37.4,0x8b8d84);
  stones.box(-19,8.18,-3,11.2,.08,30.4,0x8b8d84);
  stones.box(-20,11.22,-5,8.2,.08,15.4,0x8b8d84);
  // Broad staggered stone faces add OSRS masonry rhythm without a texture
  // download. They sit behind arrow slits and door frames, and share one batch.
  const masonry=(x,z0,z1,y0,y1,facing)=>{
    const shades=[0x949a93,0xa2a79e,0x8c948c,0x9ca298];
    for(let row=0,y=y0+.08;y<y1-.2;row++,y+=.52)for(let col=0,z=z0+.06-(row%2)*.62;z<z1-.07;col++,z+=1.28){
      const a=Math.max(z,z0+.055),b=Math.min(z+1.23,z1-.055),top=Math.min(y+.46,y1-.08);
      if(b-a<.08)continue;
      const points=[[x,y,a],[x,top,a],[x,top,b],[x,y,b]];
      if(facing<0)points.reverse();stones.quad(...points,shades[Math.floor(hash(row+facing,col)*shades.length)]);
    }
  };
  masonry(-10.975,-22,16,0,4,1);masonry(-25.025,-22,16,0,4,-1);
  masonry(-13.075,-18.5,12.5,4.2,8,1);masonry(-24.925,-18.5,12.5,4.2,8,-1);
  masonry(-15.575,-13,3,8.2,11,1);masonry(-24.425,-13,3,8.2,11,-1);
  const door = (x,z,y,width,height,facing = 1) => {
    const fx=x+facing*.045;
    stones.box(x,y+height/2,z,.14,height+.5,width+.55,C.edge);
    props.box(fx+facing*.09,y+height/2,z,.08,height,width,C.wood);
    for(let q=-width/2+.12;q<width/2;q+=.24) props.box(fx+facing*.14,y+height/2,z+q,.04,height-.08,.04,0x5d482e);
    props.box(fx+facing*.15,y+height*.42,z,.04,.09,width,0x55584d);
    props.box(fx+facing*.18,y+height*.5,z+width*.2,.05,.13,.14,C.gold);
  };
  door(-10.97,7.8,.6,2.6,2.8); door(-25.06,-5,0,2.1,2.6,-1);
  stones.box(-25.47,.075,-5,.88,.15,2.65,C.edge);
  stones.box(-25.93,.035,-5,.30,.07,2.85,C.darkStone);
  // A kitchen broom is a small rear-door landmark, kept against the wall.
  props.add(new THREE.CylinderGeometry(.025,.036,1.65,5).rotateZ(-.10),C.wood,-25.40,.98,-6.67);
  taper(props,-25.48,.18,-6.67,.24,.54,.26,.18,0xb2a16b);
  for(let q=0;q<4;q++)props.box(-25.70+q*.14,.14,-6.567,.027,.22,.014,0x81734b);
  for(let n=0;n<3;n++) stones.box(-10.1+n*.7,.1*(3-n),7.8,.8,.2*(3-n),3.9,C.edge);
  // Cross-shaped slits and sparse raised stone courses echo the original model.
  for(const z of [-18,-12,-5,2,12]) for(const y of [2.3,6.2]) {
    if(y<4 && Math.abs(z-7.8)<3)continue;
    arrowSlit(y<4?-10.96:-13.04,y,z);
    arrowSlit(y<4?-25.07:-24.95,y,z,Math.PI);
  }
  for(const x of [-22,-16]) for(const y of [2.3,6.2]) {
    arrowSlit(x,y,16.06,-Math.PI/2); arrowSlit(x,y,-22.06,Math.PI/2);
  }
  for(const z of [-10,-3]) { arrowSlit(-15.55,9.8,z); arrowSlit(-24.45,9.8,z,Math.PI); }
  for(let row=0;row<7;row++) for(let q=0;q<13;q++) {
    const z=-20.9+q*2.85+(row%2)*.8,y=.65+row*1.06;
    const x=y<4?-25.105:-24.94;
    if(Math.abs(z+5)<1.6 && y<3)continue;
    stones.box(x,y,z,.04,.18,.85,hash(q,row)>.5?0x88918a:0xa4aaa1);
    if(y<3.5) stones.box(-10.94,y,z,.04,.15,.85,hash(row,q)>.5?0x88918a:0xa4aaa1);
  }
  const banner = (x,y,z,width,height) => {
    for(let i=0;i<7;i++) {
      const top=y-i*height/7,bottom=y-(i+1)*height/7;
      flags.quad([x,top,z-width/2],[x,bottom,z-width/2],[x,bottom,z+width/2],[x,top,z+width/2],C.blue);
    }
    // Broad white angular chevron is the familiar Lumbridge heraldry.
    flags.tri([x+.015,y-height*.32,z],[x+.015,y-height*.85,z-width*.44],[x+.015,y-height*.73,z],C.paleBlue);
    flags.tri([x+.015,y-height*.32,z],[x+.015,y-height*.73,z],[x+.015,y-height*.85,z+width*.44],C.paleBlue);
    props.box(x,y+.08,z,.09,.12,width+.25,C.wood);
  };
  banner(7.03,5.9,3,1.25,2.25); banner(7.03,5.9,13,1.25,2.25);
  for(const z of [-13,4,12]) { props.box(-12.1,6.0,z,.11,4.2,.11,C.wood); banner(-12.1,8,z+.8,1.6,2.1); }
  for(const z of [-17,8]) { props.box(-24,9.7,z,.11,3.8,.11,C.wood); banner(-24,11.45,z+.85,1.6,2.2); }
  const cannon=(x,y,z,rot=0)=>{
    const kit=new Batch();
    kit.box(0,.16,0,.9,.22,1.25,C.wood);
    for(const side of [-1,1]) kit.add(new THREE.CylinderGeometry(.31,.31,.14,8).rotateZ(Math.PI/2),0x665334,side*.56,.32,.2);
    kit.add(new THREE.CylinderGeometry(.22,.29,1.4,8).rotateZ(Math.PI/2),0x464d49,.32,.59,0);
    kit.add(new THREE.CylinderGeometry(.16,.16,.015,8).rotateZ(Math.PI/2),0x252d2a,1.025,.59,0);
    props.add(kit.geometry(),0xffffff,x,y,z,1,1,1,rot);
  };
  for(const z of [-17,-8,1,12]) cannon(-11.4,4.28,z);
  for(const z of [-15,7]) cannon(-14,8.28,z);
  // Rooftop bank booths: timber supports and the little barred counter roof.
  for(const z of [-17,-14,-11]) {
    props.box(-19.6,8.62,z,2.1,.8,1.9,0x745839);
    for(const x of [-20.5,-18.8]) props.box(x,9.45,z-.84,.12,2.0,.12,C.wood);
    props.box(-19.65,10.35,z-.84,2.2,.18,.25,C.wood);
    for(let q=0;q<5;q++)props.box(-20.4+q*.37,9.53,z-.83,.07,1.4,.06,0xc2ae78);
  }
  const fountain=(x,z)=>{
    stones.drum(x,0,z,1.75,.3,C.darkStone,12); stones.drum(x,.3,z,1.58,.52,C.edge,12);
    water.drum(x,.82,z,1.36,.035,0x71a2aa,12);
    stones.drum(x,.8,z,.34,1.5,C.stone,8,.21);
    stones.drum(x,1.8,z,.83,.32,C.edge,10,1.02);
    water.drum(x,2.12,z,.81,.025,0x90b5bc,10);
    stones.drum(x,2.14,z,.19,.62,C.stone,8,.10);
    water.drum(x,2.65,z,.10,.22,0xc2d5ce,6,.045);
    for(let i=0;i<6;i++){const a=i*Math.PI/3;water.box(x+Math.cos(a)*.82,1.47,z+Math.sin(a)*.82,.045,1.05,.045,0xa5c8c8);}
  };
  fountain(-4,-10.5); fountain(-4,3);
  const statue=(x,z,king=false)=>{
    stones.box(x,.26,z,1.3,.5,1.3,C.darkStone); stones.box(x,.65,z,.85,.3,.85,C.edge);
    stones.drum(x,.8,z,.36,.85,C.stone,5,.21); stones.rock(x,1.85,z,.31,.48,.2,C.stone);
    stones.rock(x,2.5,z,.22,.3,.2,C.edge);
    stones.box(x+.36,1.95,z,.18,.75,.17,C.stone,-.35);
    stones.box(x+.47,1.76,z+.22,.08,1.85,.08,C.edge);
    if(king)for(let q=-1;q<=1;q++)stones.add(new THREE.ConeGeometry(.08,.25,4),C.edge,x+q*.13,2.86,z);
    else stones.box(x-.35,1.9,z,.18,.7,.18,C.stone,.3);
  };
  statue(-8,4.6,true);statue(-8,11.0);
  // Rear tree farming patch frames Dharok without crossing the camera corridor.
  props.box(-32,-.015,-8.3,5.8,.07,4.9,0x6c6147);
  for(const z of [-10.9,-5.7])props.box(-32,.065,z,6.1,.16,.18,0xb0ad90);
  for(const x of [-35.1,-28.9])props.box(x,.065,-8.3,.18,.16,5.4,0xb0ad90);
  for(const z of [-9.7,-8.8,-7.9,-7])props.box(-32,.04,z,5.4,.035,.12,0x4b4935);
  props.box(-27.6,.32,-7.7,.9,.65,.9,0x88653f);
  props.box(-27.6,.68,-7.7,1.02,.1,1.02,0xaa8a53);
  for(const x of [-27.96,-27.24])props.box(x,.33,-7.237,.10,.57,.055,0x664e32);
  for(const y of [.09,.57])props.box(-27.6,y,-7.231,.85,.075,.05,0xa28452);
  for(const z of [-7.98,-7.70,-7.42])props.box(-27.6,.738,z,.96,.018,.025,0x7c5b38);

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
  for (const [x, z, r] of [[4.5,27.5,1.1],[22,18,1],[-28,-10.6,.65]]) {
    props.rock(x, 0.45, z, r, 0.8, r * 0.8, 0x9a9e85); props.rock(x + 0.65, 0.2, z - 0.3, 0.45, 0.4, 0.5, 0x8b917a);
  }
  for (let i = 0; i < 65; i++) {
    const x = 21 + hash(i, 8) * 13, z = 16 + hash(i, 4) * 20;
    if (Math.hypot(x - 30, z - 25) < 1.5) continue;
    props.box(x, 0.1, z, 0.035, 0.2, 0.035, 0x65834a);
    props.rock(x, 0.23, z, 0.12, 0.09, 0.12, i % 4 ? 0xeee1a3 : 0xe0b274);
  }
  // Low-poly scenery outside navigation bounds closes the horizon gently.
  for (const [x, z, r, h] of [[-72,-66,24,5],[-30,-78,28,6],[16,-72,21,5],[64,-48,22,6],[-76,46,24,4],[59,71,23,5]])
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
    [5.25,3.2,2.9,2.9],[5.25,13.2,2.9,2.9],[-18,-3,8.4,20],[-4,-10.5,2.2,2.2],[-4,3,2.2,2.2]];
  for (const [x, z, rx, rz] of contacts) {
    contactPositions.push(x-rx,.028,z-rz, x-rx,.028,z+rz, x+rx,.028,z+rz, x-rx,.028,z-rz, x+rx,.028,z+rz, x+rx,.028,z-rz);
    contactUvs.push(0,0, 0,1, 1,1, 0,0, 1,1, 1,0);
  }
  const contactGeometry = new THREE.BufferGeometry();
  contactGeometry.setAttribute('position', new THREE.Float32BufferAttribute(contactPositions,3));
  contactGeometry.setAttribute('uv', new THREE.Float32BufferAttribute(contactUvs,2));
  const contactsMesh = new THREE.Mesh(contactGeometry,shadowMaterial); contactsMesh.name='Soft ground contact'; group.add(contactsMesh);
  function makeActor(kind, tunic = 0x668957, scale = 1) {
    const actor=new THREE.Group(),rig=new THREE.Group(),head=new THREE.Group();actor.add(rig);rig.add(head);actor.name=kind;
    const shadow=new THREE.Mesh(shadowGeometry,shadowMaterial);shadow.position.y=.042;actor.add(shadow);
    const part=(parent,draw)=>{const b=new Batch();draw(b);const m=b.mesh(solid);m.receiveShadow=false;parent.add(m);return m;};
    const limbs=[], arms=[];let rightHand=new THREE.Object3D();
    const pivot=(x,y,z,draw,phase=1)=>{const g=new THREE.Group();g.position.set(x,y,z);rig.add(g);part(g,draw);limbs.push({g,phase});return g;};
    if(kind==='chicken') {
      part(rig,b=>{b.rock(0,.48,0,.47,.38,.56,0xf0e9d4);b.box(0,.65,-.43,.35,.42,.16,0xe0dcc9);});
      head.position.set(0,.62,.32);part(head,b=>{
        taper(b,0,.22,0,.27,.23,.39,.28,0xf3ecd7);b.box(0,.46,-.02,.12,.14,.26,0xc55340);
        b.box(0,.15,.23,.15,.12,.2,0xe3ab4d);b.box(0,0,.18,.09,.17,.1,0xc55340);
        for(const x of [-.14,.14])b.box(x,.26,.08,.035,.085,.075,C.eye);
      });
      for(const side of [-1,1])pivot(side*.17,.28,0,b=>{b.box(0,-.13,0,.055,.28,.055,0xdca03e);b.box(0,-.25,.09,.12,.04,.22,0xdca03e);},side);
      shadow.scale.setScalar(.55);rig.add(rightHand);
    } else {
      const goblin=kind==='goblin',dh=kind==='dharok',skin=goblin?0x90a969:C.skin,armor=dh?0x505b43:tunic;
      part(rig,b=>{
        taper(b,0,1.30,0,dh?.85:.70,dh?.61:.50,.66,.38,armor);
        taper(b,0,.95,0,.55,.61,.18,.36,dh?0x464f3a:0x74613e);
        b.box(0,.99,.21,.16,.11,.055,C.gold);
        if(dh){
          b.rock(0,1.28,.2,.37,.35,.10,0x616b50);
          b.tri([-.13,1.34,.304],[.12,1.34,.304],[.06,1.60,.26],0xc9bd7f);
          b.tri([-.10,1.13,.25],[.08,1.13,.25],[-.10,.95,.25],0xc9bd7f);
        }else if(!goblin){
          // Muted blue cape with a sloped hem, recognizable starter adventurer kit.
          taper(b,0,1.13,-.24,.53,.68,.95,.08,0x385d7c);
          b.box(0,1.55,.20,.43,.055,.045,0xcfc195);
        }
      });
      head.position.y=1.74;
      part(head,b=>{
        if(dh){
          // OSRS Dharok wears a close dark hood-like helm with an open face.
          // No Viking horns: the old broad horned helmet was the wrong equipment.
          taper(b,0,.14,-.055,.46,.49,.51,.42,0x46503a);
          taper(b,0,.12,.174,.32,.24,.34,.06,skin);
          b.tri([-.25,.41,.17],[.25,.41,.17],[0,.26,.22],0x46503a);
          for(const side of [-1,1])b.tri([side*.24,-.12,.2],[side*.24,.27,.2],[side*.15,.23,.24],0x46503a);
          b.tri([-.025,.16,.225],[.04,.16,.225],[.025,-.05,.235],0x46503a);
          for(const side of [-1,1])b.box(side*.087,.15,.218,.09,.042,.012,C.eye);
        }else{
          taper(b,0,.12,.01,.41,.29,.41,.34,skin);
          b.rock(0,.07,.197,.055,.07,.05,skin);
          for(const side of [-1,1])b.box(side*.10,.18,.19,.055,.044,.018,C.eye);
          b.box(0,-.002,.188,.1,.028,.012,0x8a6245);
          if(goblin){for(const side of [-1,1])b.tri([side*.19,.20,.02],[side*.48,.30,.01],[side*.22,.06,.04],skin);}
          else{taper(b,0,.31,-.02,.38,.45,.16,.38,0x6d4d32);b.box(0,.13,-.178,.40,.36,.05,0x6d4d32);}
        }
      });
      for(const side of [-1,1])pivot(side*.18,.94,0,b=>{
        taper(b,0,-.23,0,.28,.21,.48,.29,dh?0x505941:goblin?skin:0x746d50);
        taper(b,0,-.59,0,.22,.16,.30,.22,dh?0x39432e:goblin?skin:0x746d50);
        taper(b,0,-.84,.065,.20,.25,.22,.37,goblin?skin:C.boot);
        if(dh)b.rock(0,-.47,.16,.18,.17,.12,0xc8bf8b);
      },side);
      for(const side of [-1,1]){
        const arm=pivot(side*(dh?.49:.40),1.59,0,b=>{
          taper(b,0,-.18,0,dh?.30:.25,.18,.39,.25,dh?0x626e52:armor);
          if(dh)b.rock(0,-.03,0,.30,.20,.29,0x626e52);
        },-side);
        const forearm=new THREE.Group();forearm.position.y=-.37;arm.add(forearm);
        part(forearm,b=>{
          taper(b,0,-.13,0,.19,.145,.29,.20,dh?0x505b43:skin);
          b.rock(0,-.32,.03,.13,.14,.12,skin);
          if(dh){for(let q=0;q<2;q++)b.tri([side*.09,-.03-q*.14,.09],[side*.31,.02-q*.14,.09],[side*.09,.09-q*.14,.09],0xccc08a);}
        });
        if(side===1){rightHand.position.set(0,-.32,.05);forearm.add(rightHand);if(dh)part(rightHand,greataxe);}
        arms.push({arm,forearm,side});
      }
      if(goblin)rig.scale.set(1.03,.78,1.03);
    }
    actor.scale.setScalar(scale);
    let authored=false;
    const result={group:actor,baseScale:scale,
      pose({time=0,walkPhase=0,walkWeight=0,throwProgress=null,peek=0,noticed=0,caught=0}={}){
        authored=true;const stride=Math.sin(walkPhase)*walkWeight;
        const idle=(1-Math.min(1,walkWeight))*(throwProgress>0?0:1),shift=Math.sin(time*.63),breath=Math.sin(time*1.28);
        rig.position.set(idle*shift*.014,Math.abs(stride)*.055,0);
        rig.rotation.set(idle*breath*.005,0,idle*shift*.010);
        head.rotation.set(0,idle*Math.sin(time*.43)*.035,0);
        for(const {g,phase} of limbs)g.rotation.set(stride*.58*phase,0,0);
        for(const {arm,forearm,side} of arms){arm.rotation.z=side*(.05+idle*breath*.010);forearm.rotation.x=-.10+idle*breath*.013;}
        if(kind==='dharok'){
          // He first leans from cover, then puts his weight evenly on both feet
          // as the player arrives. Quiet shifts keep the heavy axe believable.
          const emergence=1-Math.min(1,Math.max(0,peek));
          rig.rotation.z=-.16*emergence+idle*shift*.014;
          rig.position.x-=emergence*.035;
          head.rotation.y+=emergence*.20;
          head.rotation.z=.075*emergence+.055*noticed+idle*Math.sin(time*.53)*.010;
          const a=arms.find(a=>a.side===1);if(a){
            a.arm.rotation.x=-.24-.13*noticed+idle*breath*.012;
            a.arm.rotation.z=.12+idle*shift*.018;
            a.forearm.rotation.x=-.22-idle*breath*.015;
          }
          rig.position.y+=caught*.12;
          if(throwProgress>0){head.rotation.x=-Math.sin(throwProgress*Math.PI)*.28;rig.position.z=-Math.sin(throwProgress*Math.PI)*.15;}
        }else if(throwProgress>0){
          // Wind up to 12%, release at 18%, then follow through to 42%.
          const p=Math.min(1,Math.max(0,throwProgress)), wind=Math.min(1,p/.12),release=Math.min(1,Math.max(0,(p-.12)/.06)),settle=Math.min(1,Math.max(0,(p-.23)/.19));
          const a=arms.find(a=>a.side===1);if(a){a.arm.rotation.x=(-2.7*wind+1.3*release)*(1-settle);a.arm.rotation.z=-.12*wind*(1-settle);a.forearm.rotation.x=(-.65*wind+.55*release)*(1-settle);}
          rig.rotation.y=-.16*wind*(1-release);rig.rotation.x=.08*release*(1-settle);
        }
      },
      handWorldPosition(target=new THREE.Vector3()){actor.updateWorldMatrix(true,true);return rightHand.getWorldPosition(target);},
      animate(time,moving=false){
        if(authored)return;
        result.pose({walkPhase:time*8,walkWeight:moving?1:0});authored=false;
        rig.position.y+=moving?0:Math.sin(time*1.7+scale)*.012;
        head.rotation.y=moving?0:Math.sin(time*.6+scale)*.12;
        if(kind==='chicken')head.rotation.x=moving?0:Math.max(0,Math.sin(time*.9))**12*.6;
      }
    };
    return result;
  }
  const actors = { player: makeActor('player',0x64885c,1.05), chicken: makeActor('chicken', 0, 1.25),
    goblin: makeActor('goblin', 0x9d624c, 1.05), dharok: makeActor('dharok',0,.68) };
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
