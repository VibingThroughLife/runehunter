import assert from 'node:assert/strict';
import { readFile, stat } from 'node:fs/promises';
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
assert.ok(source.includes('When you join a RuneLite Party') && source.includes('Those messages are relayed'), 'Party data-sharing disclosure is missing');
assert.ok(source.includes('No broadcaster, state files or copy-paste consumer stub ship yet.'), 'API draft status is missing');
assert.equal((source.match(/[—–]/g) || []).length, 0, 'Unicode dashes are not allowed');
assert.equal((source.match(/Matthew/gi) || []).length, 0, 'real name must not appear');
assert.equal((source.match(/[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/g) || []).length, 0, 'email address must not appear');

const discordHrefs = [...source.matchAll(/href="([^"]*discord[^"]*)"/gi)].map((m) => m[1]);
assert.equal(discordHrefs.length, 4, 'expected four Discord links');
assert.ok(discordHrefs.every((href) => href === 'https://runehunter.gg/discord'), 'Discord links must use the canonical redirect');

assert.ok(!/<script\s+[^>]*src=/i.test(source), 'external scripts are not allowed');
const assetRoot = path.join(repo, 'docs');
const checkedAssets = new Set();
async function checkLocalAsset(value, base = 'https://runehunter.gg/') {
  if (value.startsWith('data:') || value.startsWith('#')) return;
  const url = new URL(value, base);
  assert.equal(url.origin, 'https://runehunter.gg', `runtime asset must be same-origin: ${value}`);
  const asset = path.resolve(assetRoot, `.${decodeURIComponent(url.pathname)}`);
  assert.ok(asset.startsWith(`${assetRoot}${path.sep}`), `asset is outside docs: ${value}`);
  assert.ok((await stat(asset)).isFile(), `runtime asset is missing: ${value}`);
  checkedAssets.add(asset);
}
function attribute(tag, name) {
  return tag.match(new RegExp(`(?:\\s|^)${name}\\s*=["']([^"']*)["']`, 'i'))?.[1];
}
const styleSources = [...source.matchAll(/<style[^>]*>([\s\S]*?)<\/style>/gi)]
  .map((match) => ({ css: match[1], base: 'https://runehunter.gg/' }));
for (const [tag] of source.matchAll(/<(?:img|source|link)\b[^>]*>/gi)) {
  if (/^<link\b/i.test(tag)) {
    if (attribute(tag, 'rel') !== 'stylesheet') continue;
    const href = attribute(tag, 'href');
    assert.ok(href, 'stylesheet has no href');
    await checkLocalAsset(href);
    const url = new URL(href, 'https://runehunter.gg/');
    styleSources.push({ css: await readFile(path.resolve(assetRoot, `.${decodeURIComponent(url.pathname)}`), 'utf8'), base: url.href });
  } else {
    const src = attribute(tag, 'src');
    if (src) await checkLocalAsset(src);
    const srcset = attribute(tag, 'srcset');
    if (srcset && !srcset.startsWith('data:')) {
      for (const candidate of srcset.split(',')) await checkLocalAsset(candidate.trim().split(/\s+/)[0]);
    }
  }
}
const styles = styleSources.map(({ css }) => css).join('\n');
assert.ok(!/@import\b/i.test(styles), 'CSS imports are not allowed');
for (const { css, base } of styleSources) {
  for (const match of css.matchAll(/url\(\s*(?:"([^"]*)"|'([^']*)'|([^"'()\s]+))\s*\)/gi)) {
    await checkLocalAsset(match[1] ?? match[2] ?? match[3], base);
  }
}
for (const asset of checkedAssets) {
  if (!/\.(?:webp|png|jpe?g|svg)$/i.test(asset)) continue;
  const relative = path.relative(assetRoot, asset);
  const [published, preview] = await Promise.all([readFile(asset), readFile(path.join(repo, relative))]);
  assert.ok(published.equals(preview), `Root preview image must match docs/${relative} byte-for-byte`);
}
assert.ok(!/\b(?:fetch|XMLHttpRequest|WebSocket|sendBeacon)\s*\(/.test(scriptMatch[1]), 'network calls are not allowed');
assert.equal((styles.match(/@font-face/g) || []).length, 5, 'expected five embedded game fonts');
for (const [face] of styles.matchAll(/@font-face\s*\{[^}]*\}/gi)) {
  assert.ok(/src\s*:\s*url\(\s*data:font\//i.test(face), 'game fonts must remain embedded');
}
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
assert.equal((source.match(/<button\b[^>]*class="pray"[^>]*aria-pressed="false"/g) || []).length, 3, 'prayer controls need pressed state');
assert.ok(source.includes('@media(forced-colors:active)'), 'forced-colors fallback is missing');
assert.ok(source.includes('prefers-reduced-motion: reduce'), 'reduced-motion preference is missing');
assert.ok(source.includes('id="motion-toggle"'), 'motion pause control is missing');

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

console.log(`RuneHunter site checks passed: truthful copy, 87-creature roster, ${checkedAssets.size} local assets, embedded fonts and scripts, accessibility hooks, and 1200x630 social card.`);
