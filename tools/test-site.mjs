import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sourcePath = path.join(repo, 'runehunter-index.html');
const pagesPath = path.join(repo, 'docs', 'index.html');
const ogPath = path.join(repo, 'docs', 'og.jpg');

const [source, pages, og] = await Promise.all([
  readFile(sourcePath, 'utf8'),
  readFile(pagesPath, 'utf8'),
  readFile(ogPath),
]);

assert.equal(pages, source, 'docs/index.html must exactly match runehunter-index.html');

const scriptMatch = source.match(/<script>([\s\S]*?)<\/script>/);
assert.ok(scriptMatch, 'inline script not found');
new Function(scriptMatch[1]);

for (const phrase of [
  'Nothing leaves your computer',
  'RuneHunter ships a public integration API',
  'it only carries the creature being traded',
  'Alpha · on the Plugin Hub',
]) {
  assert.ok(!source.includes(phrase), `outdated claim remains: ${phrase}`);
}

assert.ok(source.includes('Not on the Plugin Hub yet.'), 'release status must remain explicit');
assert.ok(source.includes('No RuneHunter server or telemetry'), 'privacy summary is missing');
assert.ok(source.includes('No broadcaster, state files or copy-paste consumer stub ship yet.'), 'API draft status is missing');
assert.equal((source.match(/[—–]/g) || []).length, 0, 'Unicode dashes are not allowed');
assert.equal((source.match(/Matthew/gi) || []).length, 0, 'real name must not appear');
assert.equal((source.match(/[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/g) || []).length, 0, 'email address must not appear');

const discordHrefs = [...source.matchAll(/href="([^"]*discord[^"]*)"/gi)].map((m) => m[1]);
assert.equal(discordHrefs.length, 4, 'expected four Discord links');
assert.ok(discordHrefs.every((href) => href === 'https://runehunter.gg/discord'), 'Discord links must use the canonical redirect');

assert.ok(!/<script\s+[^>]*src=/i.test(source), 'external scripts are not allowed');
assert.ok(!/<link\s+[^>]*rel="stylesheet"/i.test(source), 'external stylesheets are not allowed');
assert.ok(!/<img\b/i.test(source), 'runtime images should remain embedded or canvas-rendered');
assert.ok(!/\b(?:fetch|XMLHttpRequest|WebSocket|sendBeacon)\s*\(/.test(scriptMatch[1]), 'network calls are not allowed');
assert.equal((source.match(/@font-face/g) || []).length, 5, 'expected five embedded game fonts');
assert.ok(source.includes('var LPATCH='), 'Lumbridge terrain patch is missing');

const ids = [...source.matchAll(/\sid="([^"]+)"/g)].map((m) => m[1]);
assert.equal(new Set(ids).size, ids.length, 'duplicate HTML id found');
const idSet = new Set(ids);
for (const match of source.matchAll(/href="#([^"]+)"/g)) {
  assert.ok(idSet.has(match[1]), `broken fragment link: #${match[1]}`);
}

const rosterMatch = source.match(/var ROSTER=(\[[\s\S]*?\]);\nvar TC=/);
assert.ok(rosterMatch, 'site roster not found');
const roster = new Function(`return ${rosterMatch[1]}`)();
assert.equal(roster.length, 87, 'site roster must contain all 87 public creatures');
assert.equal(new Set(roster.map(([name]) => name.toLowerCase())).size, 87, 'site roster names must be unique');
const tierCounts = roster.reduce((counts, [, tier]) => {
  counts[tier] = (counts[tier] || 0) + 1;
  return counts;
}, {});
assert.deepEqual(tierCounts, { 1: 12, 2: 15, 3: 18, 4: 18, 5: 24 }, 'site tier counts must match CreatureRoster');

assert.ok(source.includes('<main class="page" id="top">'), 'main landmark is missing');
assert.ok(source.includes('.sr:focus{'), 'visible skip-link focus style is missing');
assert.equal((source.match(/aria-pressed="false"/g) || []).length, 3, 'prayer controls need pressed state');
assert.ok(source.includes('@media(forced-colors:active)'), 'forced-colors fallback is missing');
assert.ok(source.includes('var t=REDUCED?0:'), 'reduced-motion renderer freeze is missing');

assert.equal(og.readUInt16BE(0), 0xffd8, 'social card must be a JPEG');
function jpegDimensions(buffer) {
  const sof = new Set([0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf]);
  let offset = 2;
  while (offset < buffer.length) {
    if (buffer[offset] !== 0xff) { offset += 1; continue; }
    while (buffer[offset] === 0xff) offset += 1;
    const marker = buffer[offset++];
    if (marker === 0xd9 || marker === 0xda) break;
    const length = buffer.readUInt16BE(offset);
    if (sof.has(marker)) {
      return { height: buffer.readUInt16BE(offset + 3), width: buffer.readUInt16BE(offset + 5) };
    }
    offset += length;
  }
  throw new Error('JPEG dimensions not found');
}
assert.deepEqual(jpegDimensions(og), { width: 1200, height: 630 }, 'social card must be 1200x630');
assert.ok(source.includes('https://runehunter.gg/og.jpg'), 'social metadata must reference og.jpg');

const externalHosts = new Set(
  [...source.matchAll(/https:\/\/[^"'\s<)]+/g)]
    .map((m) => new URL(m[0]).hostname)
);
assert.deepEqual([...externalHosts].sort(), ['github.com', 'runehunter.gg'], 'unexpected external host');

console.log('RuneHunter site checks passed: truthful copy, 87-creature roster, embedded runtime, accessibility hooks, and 1200x630 social card.');
