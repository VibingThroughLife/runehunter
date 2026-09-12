import assert from 'node:assert/strict';
import { readFile, readdir, stat } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { gzipSync } from 'node:zlib';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const docs = path.join(repo, 'docs');
const origin = 'https://runehunter.gg';
const [source, pages, og] = await Promise.all([
  readFile(path.join(repo, 'runehunter-index.html'), 'utf8'),
  readFile(path.join(docs, 'index.html'), 'utf8'),
  readFile(path.join(docs, 'og.jpg')),
]);
assert.equal(pages, source, 'docs/index.html must exactly match runehunter-index.html');

function attribute(tag, name) {
  return tag.match(new RegExp(`(?:\\s|^)${name}\\s*=["']([^"']*)["']`, 'i'))?.[1];
}
const checkedAssets = new Set();
const checkedModules = new Set();
const styles = [];
async function localAsset(value, base = `${origin}/`) {
  if (value.startsWith('data:') || value.startsWith('#')) return null;
  const url = new URL(value, base);
  assert.equal(url.origin, origin, `Runtime assets must be same-origin: ${value}`);
  const filename = path.resolve(docs, `.${decodeURIComponent(url.pathname)}`);
  assert.ok(filename.startsWith(`${docs}${path.sep}`), `Asset is outside docs: ${value}`);
  assert.ok((await stat(filename)).isFile(), `Runtime asset is missing: ${value}`);
  checkedAssets.add(filename);
  return { filename, url: url.href };
}
function syntaxCheck(code, label, module = false) {
  if (!module) { new Function(code); return; }
  const result = spawnSync(process.execPath, ['--check', '--input-type=module'], { input: code, encoding: 'utf8' });
  assert.equal(result.status, 0, `Invalid module ${label}: ${result.stderr || result.error || ''}`);
}
async function inspectModule(code, base, label) {
  syntaxCheck(code, label, true);
  const imports = [
    ...[...code.matchAll(/\b(?:import|export)\s+(?:[^;'"`]*?\s+from\s*)?['"]([^'"]+)['"]/g)].map((match) => match[1]),
    ...[...code.matchAll(/\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g)].map((match) => match[1]),
  ];
  for (const specifier of imports) {
    assert.ok(/^(?:\.\.?\/|\/)/.test(specifier), `Module imports must use local paths: ${label} -> ${specifier}`);
    const asset = await localAsset(specifier, base);
    assert.ok(asset, `Module cannot import a data URL: ${label}`);
    if (checkedModules.has(asset.filename)) continue;
    checkedModules.add(asset.filename);
    await inspectModule(await readFile(asset.filename, 'utf8'), asset.url, path.relative(docs, asset.filename));
  }
  for (const match of code.matchAll(/new\s+URL\(\s*['"]([^'"]+)['"]\s*,\s*import\.meta\.url\s*\)/g)) {
    await localAsset(match[1], base);
  }
}
async function inspectCss(css, base) {
  styles.push(css);
  for (const match of css.matchAll(/@import\s+(?:url\(\s*)?['"]([^'"]+)['"]\s*\)?[^;]*;/gi)) {
    const asset = await localAsset(match[1], base);
    assert.ok(asset, 'Stylesheets cannot import a data URL');
    if (!styles.includes(await readFile(asset.filename, 'utf8'))) await inspectCss(await readFile(asset.filename, 'utf8'), asset.url);
  }
  for (const match of css.matchAll(/url\(\s*(?:"([^"]*)"|'([^']*)'|([^"'()\s]+))\s*\)/gi)) {
    await localAsset(match[1] ?? match[2] ?? match[3], base);
  }
}

const scriptTags = [...source.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)];
assert.ok(scriptTags.length, 'Page scripts are missing');
for (const [tag, attributes, code] of scriptTags) {
  const type = attribute(attributes, 'type') || 'text/javascript';
  const src = attribute(attributes, 'src');
  if (type === 'application/ld+json') { JSON.parse(code); continue; }
  assert.ok(['module', 'text/javascript'].includes(type), `Unsupported script type: ${type}`);
  if (src) {
    const asset = await localAsset(src);
    assert.ok(asset, 'Script sources must be local files');
    const externalCode = await readFile(asset.filename, 'utf8');
    if (type === 'module') {
      checkedModules.add(asset.filename);
      await inspectModule(externalCode, asset.url, src);
    } else syntaxCheck(externalCode, src);
  } else if (type === 'module') await inspectModule(code, `${origin}/`, 'inline module');
  else syntaxCheck(code, 'inline script');
}
assert.ok(scriptTags.some(([, attributes]) => attribute(attributes, 'type') === 'module'), 'The scroll-introduction module must be loaded');
for (const match of source.matchAll(/<style\b[^>]*>([\s\S]*?)<\/style>/gi)) await inspectCss(match[1], `${origin}/`);
for (const [tag] of source.matchAll(/<(?:img|source|link|video|audio)\b[^>]*>/gi)) {
  if (/^<link\b/i.test(tag)) {
    const rel = attribute(tag, 'rel');
    if (!['stylesheet', 'modulepreload', 'preload', 'icon'].includes(rel)) continue;
    const href = attribute(tag, 'href');
    assert.ok(href, `${rel} link has no href`);
    const asset = await localAsset(href);
    if (rel === 'stylesheet' && asset) await inspectCss(await readFile(asset.filename, 'utf8'), asset.url);
  } else {
    for (const name of ['src', 'poster']) { const value = attribute(tag, name); if (value) await localAsset(value); }
    const srcset = attribute(tag, 'srcset');
    if (srcset && !srcset.startsWith('data:')) {
      for (const candidate of srcset.split(',')) await localAsset(candidate.trim().split(/\s+/)[0]);
    }
  }
}

async function listFiles(directory, prefix = '') {
  const result = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const relative = path.join(prefix, entry.name);
    if (entry.isDirectory()) result.push(...await listFiles(path.join(directory, entry.name), relative));
    else if (entry.isFile()) result.push(relative);
    else assert.fail(`Asset tree cannot contain symlinks or special files: ${relative}`);
  }
  return result.sort();
}
const [publishedAssets, previewAssets] = await Promise.all([listFiles(path.join(docs, 'assets')), listFiles(path.join(repo, 'assets'))]);
assert.deepEqual(previewAssets, publishedAssets, 'Root preview and docs assets must contain the same files');
for (const relative of publishedAssets) {
  const [published, preview] = await Promise.all([readFile(path.join(docs, 'assets', relative)), readFile(path.join(repo, 'assets', relative))]);
  assert.ok(published.equals(preview), `Asset mirrors differ: assets/${relative}`);
}
const threeLicense = await readFile(path.join(docs, 'assets/vendor/THREE-LICENSE.txt'), 'utf8');
assert.match(threeLicense, /Permission is hereby granted/i, 'Vendored Three.js must retain its MIT license');
assert.match(threeLicense, /three\.js|threejs|mrdoob/i, 'Vendored license must identify Three.js');
assert.ok([...checkedModules].some((filename) => filename.endsWith('/vendor/three.module.js')), 'The local Three.js entry point was not reached');
assert.ok([...checkedModules].some((filename) => filename.endsWith('/vendor/three.core.js')), 'The local Three.js core dependency was not reached');

const css = styles.join('\n');
assert.equal((css.match(/@font-face/g) || []).length, 5, 'Expected five embedded game fonts');
for (const [face] of css.matchAll(/@font-face\s*\{[^}]*\}/gi)) assert.match(face, /src\s*:\s*url\(\s*data:font\//i, 'Game fonts must remain embedded');
for (const phrase of ['Nothing leaves your computer', 'RuneHunter ships a public integration API', 'it only carries the creature being traded', 'Alpha · on the Plugin Hub']) {
  assert.ok(!source.includes(phrase), `Outdated claim remains: ${phrase}`);
}
assert.ok(source.includes('Not on the Plugin Hub yet.'), 'Plugin availability must remain explicit');
assert.ok(source.includes('No RuneHunter server or telemetry'), 'Privacy summary is missing');
assert.ok(source.includes('When you join a RuneLite Party') && source.includes('Those messages are relayed'), 'Party data-sharing disclosure is missing');
assert.ok(source.includes('No broadcaster, state files or copy-paste consumer stub ship yet.'), 'API draft status is missing');
assert.match(source, /(?:website|browser).{0,80}(?:demo|adventure|showcase)|(?:demo|adventure|showcase).{0,80}(?:website|browser)/i, 'Adventure must be identified as a website demonstration');
assert.equal((source.match(/[—–]/g) || []).length, 0, 'Unicode dashes are not allowed');
assert.equal((source.match(/Matthew/gi) || []).length, 0, 'Real name must not appear');
assert.equal((source.match(/[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}/g) || []).length, 0, 'Email address must not appear');
const discord = [...source.matchAll(/href=["']([^"']*discord[^"']*)["']/gi)].map((match) => match[1]);
assert.ok(discord.length >= 2, 'Community links are missing');
assert.ok(discord.every((href) => href === 'https://runehunter.gg/discord'), 'Discord links must use the canonical redirect');

const ids = [...source.matchAll(/\sid=["']([^"']+)["']/g)].map((match) => match[1]);
assert.equal(new Set(ids).size, ids.length, 'Duplicate HTML id found');
const idSet = new Set(ids);
for (const match of source.matchAll(/href=["']#([^"']+)["']/g)) assert.ok(idSet.has(match[1]), `Broken fragment link: #${match[1]}`);
for (const id of ['top', 'intro', 'intro-sticky', 'world-canvas', 'intro-arrival', 'intro-journey', 'intro-encounter', 'intro-catch', 'intro-replay', 'intro-status', 'intro-skip', 'intro-begin', 'encounter', 'motion-toggle', 'how', 'faq']) {
  assert.ok(idSet.has(id), `Scroll introduction control is missing: #${id}`);
}
for (const id of ['adventure-dialog', 'adventure-viewport', 'explore-world', 'world-pause', 'world-replay', 'world-catch', 'world-zoom-in', 'world-rotate-left', 'world-progress']) {
  assert.ok(!idSet.has(id), `Obsolete exploration control remains: #${id}`);
}
assert.ok(!/<dialog\b/i.test(source), 'The scroll introduction must not use a modal');
for (const [id, href] of [['intro-skip', '#how'], ['intro-begin', '#encounter']]) {
  const tag = source.match(new RegExp(`<a\\b[^>]*id=["']${id}["'][^>]*>`, 'i'))?.[0];
  assert.ok(tag && attribute(tag, 'href') === href, `${id} needs the native destination ${href}`);
}
assert.match(source, /id=["']intro-status["'][^>]*(?:role=["']status["']|aria-live=["']polite["'])|(?:role=["']status["']|aria-live=["']polite["'])[^>]*id=["']intro-status["']/i, 'Catch feedback needs a live status region');
assert.match(source, /<button\b[^>]*id=["']intro-catch["']/i, 'Throw an orb must be a native button');
assert.match(source, /<button\b[^>]*id=["']intro-replay["']/i, 'Replay catch must be a native button');
assert.match(css, /position\s*:\s*sticky/, 'The introduction must use native sticky positioning');
for (const fallback of ['lumbridge-hero.webp', 'lumbridge-hero-mobile.webp', 'lumbridge-rear.webp', 'lumbridge-rear-mobile.webp']) {
  assert.ok(publishedAssets.includes(fallback), `Finished scene fallback is missing: ${fallback}`);
}
assert.ok(![...checkedModules].some((filename) => filename.endsWith('/simulation.js')), 'The old free-movement simulation must not ship in the intro dependency tree');
assert.ok([...checkedModules].some((filename) => filename.endsWith('/world/intro.js')), 'The isolated intro-state module must be loaded');
let worldGzipBytes = 0;
for (const relative of publishedAssets.filter((name) => /^(?:world|vendor)\//.test(name))) {
  worldGzipBytes += gzipSync(await readFile(path.join(docs, 'assets', relative))).byteLength;
}
assert.ok(worldGzipBytes <= 2 * 1024 * 1024, `Compressed world exceeds 2 MB: ${worldGzipBytes} bytes`);
assert.match(source, /<main\b[^>]*id=["']top["']/i, 'Main landmark is missing');
assert.match(css, /\.sr:focus\s*\{/, 'Visible skip-link focus style is missing');
assert.match(css, /@media\s*\(\s*forced-colors\s*:\s*active\s*\)/, 'Forced-colors fallback is missing');
assert.match(css, /prefers-reduced-motion\s*:\s*reduce/, 'Reduced-motion preference is missing');

const rosterMatch = source.match(/(?:var|const|let)\s+ROSTER\s*=\s*(\[[\s\S]*?\]);\s*(?:var|const|let)\s+TC\s*=/);
assert.ok(rosterMatch, 'Site roster not found');
const roster = new Function(`return ${rosterMatch[1]}`)();
assert.equal(roster.length, 87, 'Site roster must contain all 87 public creatures');
assert.equal(new Set(roster.map(([name]) => name.toLowerCase())).size, 87, 'Site roster names must be unique');
const tierCounts = roster.reduce((counts, [, tier]) => ({ ...counts, [tier]: (counts[tier] || 0) + 1 }), {});
assert.deepEqual(tierCounts, { 1: 12, 2: 15, 3: 18, 4: 18, 5: 24 }, 'Site tier counts must match CreatureRoster');

function jpegDimensions(buffer) {
  const sof = new Set([0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6, 0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce, 0xcf]);
  let offset = 2;
  while (offset < buffer.length) {
    if (buffer[offset] !== 0xff) { offset += 1; continue; }
    while (buffer[offset] === 0xff) offset += 1;
    const marker = buffer[offset++];
    if (marker === 0xd9 || marker === 0xda) break;
    const length = buffer.readUInt16BE(offset);
    if (sof.has(marker)) return { height: buffer.readUInt16BE(offset + 3), width: buffer.readUInt16BE(offset + 5) };
    offset += length;
  }
  throw new Error('JPEG dimensions not found');
}
assert.equal(og.readUInt16BE(0), 0xffd8, 'Social card must be a JPEG');
assert.deepEqual(jpegDimensions(og), { width: 1200, height: 630 }, 'Social card must be 1200x630');
assert.ok(source.includes('https://runehunter.gg/og.jpg'), 'Social metadata must reference og.jpg');
console.log(`RuneHunter static acceptance passed: ${checkedModules.size} local modules, ${checkedAssets.size} runtime assets, ${publishedAssets.length} mirrored assets, Three.js license, 87-creature roster, truthful status and accessible scroll-introduction controls; ${worldGzipBytes} gzip world bytes.`);
