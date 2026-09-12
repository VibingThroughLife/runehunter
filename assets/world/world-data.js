/** The showcase's small, walkable Lumbridge. Distances are world metres. */
export const TREES = [
  [-33,-17,1.5],[-32,-8.3,1.12],[-46,-19,1.4],[-53,-6,1.55],[-52,28,1.6],[-65,9,1.4],
  [-19,28,1.25],[-9,31,1.5],[1,31,1.4],[8,-14,1.3],
  [8,-27,1.6],[24,-24,1.45],[32,-18,1.7],[32,0,1.4],
  [30,25,1.55],[22,33,1.2],[-34,37,1.3],[-16,-30,1.5],
  [-5,-28,1.2],[33,35,1.6],[25,-16,1.05],[-67,-29,1.6],
];

export const WORLD = {
  bounds: { minX: -58, maxX: 36, minZ: -32, maxZ: 38 },
  spawn: { x: 8, z: 12 },
  river: { minX: 12, maxX: 19 },
  bridge: { minX: 8, maxX: 23, minZ: 6, maxZ: 11 },
  obstacles: [
    // Curtain walls. The east wall has a real, traversable gate between towers.
    { type: 'rect', minX: -25, maxX: -11, minZ: -22, maxZ: 16 },
    { type: 'rect', minX: -25, maxX: 5, minZ: -23, maxZ: -21 },
    { type: 'rect', minX: -25, maxX: 5, minZ: 15, maxZ: 17 },
    { type: 'rect', minX: 4, maxX: 6, minZ: -22, maxZ: 1 },
    { type: 'rect', minX: 4, maxX: 6, minZ: 15, maxZ: 16 },
    { type: 'circle', x: 5, z: 3, r: 2.65 },
    { type: 'circle', x: 5, z: 13, r: 2.65 },
    // Keep, courtyard well, stacked supplies, bridge parapets and farmhouse.
    { type: 'circle', x: -4, z: -10.5, r: 1.75 },
    { type: 'circle', x: -4, z: 3, r: 1.75 },
    { type: 'rect', minX: 9, maxX: 22, minZ: 5.2, maxZ: 6.1 },
    { type: 'rect', minX: 9, maxX: 22, minZ: 10.9, maxZ: 11.8 },
    { type: 'rect', minX: 27, maxX: 34, minZ: -12, maxZ: -6 },
    // Fenced wheat patch: there is a gap on its south side.
    { type: 'rect', minX: 20.5, maxX: 21.5, minZ: -12.5, maxZ: -4.5 },
    { type: 'rect', minX: 20.5, maxX: 27.5, minZ: -12.5, maxZ: -11.5 },
    { type: 'rect', minX: 20.5, maxX: 23, minZ: -5.2, maxZ: -4.3 },
    { type: 'rect', minX: 26, maxX: 27.5, minZ: -5.2, maxZ: -4.3 },
    ...TREES.map(([x, z, scale]) => ({ type: 'circle', x, z, r: 0.55 * scale + 0.35 })),
    { type: 'circle', x: 4.5, z: 27.5, r: 1.25 },
    { type: 'circle', x: 22, z: 18, r: 1.2 },

  ],
};

export const CREATURES = [
  {
    id: 'chicken', name: 'Chicken', tier: 'Common', color: 0xf4c76a,
    description: 'Small feet. Big plans. This curious chicken is ready for its first adventure.',
    hint: 'A little rustle by the wheat field, on this side of the river.',
    x: 24, z: 1,
  },
  {
    id: 'goblin', name: 'Goblin', tier: 'Common', color: 0x8ecf91,
    description: 'A pocket-sized troublemaker who would much rather join the hunt than start a fight.',
    hint: 'Cross the bridge, then follow the riverbank south.',
    x: 7, z: 20,
  },
  {
    id: 'dharok', name: 'Miniature Dharok', tier: 'Epic', color: 0xc5a1f0,
    description: 'That enormous axe has become a very small axe. A Barrows legend, now your travelling companion.',
    hint: 'A very small Barrows legend is hiding behind the castle.',
    x: -32, z: -3.4,
  },
];

/** Uses integer tile centres and also accepts continuous world coordinates. */
export function isWalkable(x, z) {
  if (!Number.isFinite(x) || !Number.isFinite(z)) return false;
  const b = WORLD.bounds;
  if (x < b.minX || x > b.maxX || z < b.minZ || z > b.maxZ) return false;
  const bridge = WORLD.bridge;
  if (x >= WORLD.river.minX && x <= WORLD.river.maxX &&
      !(x >= bridge.minX && x <= bridge.maxX && z >= bridge.minZ && z <= bridge.maxZ)) return false;
  return !WORLD.obstacles.some(o => o.type === 'circle'
    ? (x - o.x) ** 2 + (z - o.z) ** 2 <= o.r ** 2
    : x >= o.minX && x <= o.maxX && z >= o.minZ && z <= o.maxZ);
}
