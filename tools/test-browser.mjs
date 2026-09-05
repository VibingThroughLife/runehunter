import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { access, mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Uses installed Playwright and Chrome; no package installation or remote server.
// Optional: PLAYWRIGHT_PACKAGE_PATH, PLAYWRIGHT_CHROMIUM_EXECUTABLE, RUNEHUNTER_QA_DIR.
const require = createRequire(import.meta.url);
let chromium;
for (const name of [process.env.PLAYWRIGHT_PACKAGE_PATH, 'playwright', 'playwright-core'].filter(Boolean)) {
  try { ({ chromium } = require(name)); if (chromium) break; } catch (error) { if (error.code !== 'MODULE_NOT_FOUND') throw error; }
}
assert.ok(chromium, 'Playwright is unavailable. Set NODE_PATH or PLAYWRIGHT_PACKAGE_PATH to an existing installation.');
const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const docs = path.join(repo, 'docs');
const output = path.resolve(process.env.RUNEHUNTER_QA_DIR || path.join(tmpdir(), 'runehunter-adventure-qa'));
assert.ok(!output.startsWith(`${repo}${path.sep}`), 'Keep browser screenshots outside the source checkout.');
await mkdir(output, { recursive: true });
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8', '.css': 'text/css', '.jpg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.json': 'application/json' };
const server = createServer(async (request, response) => {
  try {
    const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    const filename = path.resolve(docs, `.${pathname === '/' ? '/index.html' : pathname}`);
    if (!filename.startsWith(`${docs}${path.sep}`) || !(await stat(filename)).isFile()) throw new Error('Not found');
    response.writeHead(200, { 'Content-Type': mime[path.extname(filename)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
    response.end(await readFile(filename));
  } catch { response.writeHead(404); response.end('Not found'); }
});
await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
const origin = `http://127.0.0.1:${server.address().port}`;
let executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;
if (!executablePath && process.platform === 'darwin') {
  const chrome = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
  try { await access(chrome); executablePath = chrome; } catch { /* Fall back to Playwright's Chromium. */ }
}
const results = [];
let browser;

async function openPage(options, { webglUnavailable = false, debug = true } = {}) {
  const context = await browser.newContext(options);
  if (webglUnavailable) await context.addInitScript(() => {
    const nativeContext = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (kind, ...args) {
      return /^(?:webgl2?|experimental-webgl)$/.test(kind) ? null : nativeContext.call(this, kind, ...args);
    };
  });
  const page = await context.newPage();
  page.setDefaultTimeout(12_000);
  const errors = [], requests = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => { if (message.type() === 'error') errors.push(message.text()); });
  page.on('response', (response) => { if (response.status() >= 400) errors.push(`${response.status()} ${response.url()}`); });
  page.on('requestfailed', (request) => errors.push(`${request.url()}: ${request.failure()?.errorText}`));
  page.on('websocket', (socket) => { if (new URL(socket.url()).host !== new URL(origin).host) requests.push(socket.url()); });
  await context.route('**/*', (route) => {
    const url = new URL(route.request().url());
    if (url.origin !== origin && url.protocol !== 'data:') { requests.push(url.href); return route.abort(); }
    return route.continue();
  });
  await page.goto(`${origin}/${debug ? '?debug' : ''}`, { waitUntil: 'load' });
  await page.evaluate(() => document.fonts.ready);
  await page.locator('.hero h1').waitFor({ state: 'visible' });
  await page.waitForFunction(() => [...document.images].every((image) => image.complete && image.naturalWidth > 0));
  if (options.javaScriptEnabled !== false && debug) {
    await page.waitForFunction(() => typeof window.__runehunterWorld?.getState === 'function');
    await page.waitForFunction(() => {
      const renderer = window.__runehunterWorld.getState().renderer;
      return renderer.mode === 'fallback' || (renderer.frames > 0 && Number(getComputedStyle(document.getElementById('world-stage')).opacity) >= 0.99);
    });
  }
  return { context, page, errors, requests };
}
function assertClean(fixture, label) {
  assert.deepEqual(fixture.errors, [], `${label}: browser errors`);
  assert.deepEqual(fixture.requests, [], `${label}: external runtime requests`);
}
async function state(page) { return page.evaluate(() => window.__runehunterWorld.getState()); }
function frameIntervalSummary(intervals) {
  const sorted = [...intervals].sort((a, b) => a - b);
  const percentile = (p) => sorted.length ? Number(sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * p))].toFixed(2)) : null;
  return { samples: sorted.length, medianMs: percentile(.5), p95Ms: percentile(.95), maxMs: sorted.length ? Number(sorted.at(-1).toFixed(2)) : null };
}
async function waitState(page, predicate, arg, timeout = 20_000) {
  await page.waitForFunction(({ predicate, arg }) => new Function('state', 'arg', `return (${predicate})(state, arg)`)(window.__runehunterWorld.getState(), arg), { predicate: predicate.toString(), arg }, { timeout });
  return state(page);
}
async function assertLayout(page, label) {
  const metrics = await page.evaluate(() => ({
    viewport: innerWidth,
    width: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth),
    clipped: [...document.querySelectorAll('.hero h1,.hero .lede,.hero .cta a,.hero .cta button,dialog[open] h2,dialog[open] button')].flatMap((element) => {
      const style = getComputedStyle(element), rect = element.getBoundingClientRect();
      if (style.display === 'none' || style.visibility === 'hidden' || !rect.width) return [];
      return rect.left < -1 || rect.right > innerWidth + 1 ? [{ text: element.textContent.trim(), left: rect.left, right: rect.right }] : [];
    }),
  }));
  assert.ok(metrics.width <= metrics.viewport + 1, `${label}: horizontal overflow ${JSON.stringify(metrics)}`);
  assert.deepEqual(metrics.clipped, [], `${label}: controls are horizontally clipped`);
}
async function assertLinks(page) {
  const broken = await page.locator('a[href^="#"]').evaluateAll((links) => links.filter((link) => link.hash && !document.getElementById(decodeURIComponent(link.hash.slice(1)))).map((link) => link.getAttribute('href')));
  assert.deepEqual(broken, [], 'All fragment links need targets');
  const discord = await page.locator('a[href*="discord"]').evaluateAll((links) => links.map((link) => link.href));
  assert.ok(discord.length >= 2 && discord.every((url) => url === 'https://runehunter.gg/discord'), 'Community links must use the canonical redirect');
  assert.equal(await page.locator('#explore-world').getAttribute('href'), '#tour', 'Explore must retain its native no-JS destination');
}
async function assertDetails(page, selector, expected) {
  const details = page.locator(selector), summary = details.locator('summary');
  await summary.scrollIntoViewIfNeeded();
  await summary.focus();
  await page.keyboard.press('Enter');
  assert.equal(await details.getAttribute('open'), '', `${selector}: keyboard should open disclosure`);
  assert.match(await details.innerText(), expected, `${selector}: useful content is missing`);
  await page.keyboard.press('Enter');
  assert.equal(await details.getAttribute('open'), null, `${selector}: keyboard should close disclosure`);
}
async function enterWorld(page) {
  await page.locator('#explore-world').click();
  await page.locator('#adventure-dialog').waitFor({ state: 'visible' });
  await waitState(page, (s) => s.active);
  assert.equal(await page.locator('#adventure-dialog').evaluate((dialog) => dialog.open), true, 'Adventure must open as a native dialog');
  assert.ok(await page.locator('#adventure-dialog').evaluate((dialog) => dialog.contains(document.activeElement)), 'Focus must enter the adventure');
  assert.equal(await page.locator('#adventure-viewport #world-canvas').count(), 1, 'Opening must move the single scene canvas into the adventure viewport');
  assert.equal(await page.locator('#world-canvas').count(), 1, 'The page should reuse one canvas');
  assert.ok(await page.locator('#world-notice').getAttribute('aria-live') || ['status', 'alert'].includes(await page.locator('#world-notice').getAttribute('role')), 'Adventure feedback needs a live region');
  for (const id of ['exit-world', 'world-pause', 'world-replay', 'world-zoom-in', 'world-zoom-out', 'world-rotate-left', 'world-rotate-right', 'world-catch']) {
    const control = page.locator(`#${id}`);
    assert.ok((await control.getAttribute('aria-label')) || (await control.innerText()).trim(), `${id}: control needs an accessible name`);
  }
}
async function assertPaused(page) {
  const pause = page.locator('#world-pause');
  const beforeClick = await state(page);
  if (await pause.getAttribute('aria-pressed') !== 'true') await pause.click();
  assert.equal(await pause.getAttribute('aria-pressed'), 'true', 'Pause state must be exposed');
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  const before = await state(page);
  if (beforeClick.phase === 'throwing') {
    assert.equal(before.phase, 'throwing', 'Pausing an orb throw must not complete the catch');
    assert.deepEqual(before.caught, beforeClick.caught, 'Pause must not add a journal entry');
  }
  if (beforeClick.phase === 'approaching' && beforeClick.path.length > 4) {
    assert.equal(before.phase, 'approaching', 'Pausing an approach must not teleport the player to the creature');
    assert.ok(Math.hypot(before.player.x - beforeClick.player.x, before.player.z - beforeClick.player.z) < 1.5, 'Pause must preserve the in-progress route position');
  }
  await page.waitForTimeout(350);
  const after = await state(page);
  assert.equal(after.time, before.time, 'Pause must freeze simulation time');
  assert.deepEqual(after.player, before.player, 'Pause must freeze player movement');
  assert.deepEqual(after.followerPosition, before.followerPosition, 'Pause must freeze companion movement');
  assert.equal(after.throwProgress, before.throwProgress, 'Pause must freeze the orb throw');
  assert.equal(after.renderer.frames, before.renderer.frames, 'An unchanged paused scene should stop rendering');
  await pause.click();
  assert.equal(await pause.getAttribute('aria-pressed'), 'false', 'Resume state must be exposed');
}
async function assertCameraControls(page) {
  const project = () => page.evaluate(() => window.__runehunterWorld.projectPoint(10, 0, 8));
  const beforeZoom = await project();
  await page.locator('#world-zoom-in').click();
  await page.waitForTimeout(250);
  const zoomed = await project();
  assert.ok(Math.hypot(zoomed.x - beforeZoom.x, zoomed.y - beforeZoom.y) > 0.5, 'Zoom must visibly change the view');
  await page.locator('#world-zoom-out').click();
  const beforeRotation = await project();
  await page.locator('#world-rotate-left').click();
  await page.waitForTimeout(250);
  const rotated = await project();
  assert.ok(Math.hypot(rotated.x - beforeRotation.x, rotated.y - beforeRotation.y) > 0.5, 'Rotate must visibly change the view');
  await page.locator('#world-rotate-right').click();
}
async function assertKeyboardMovement(page) {
  const before = await state(page);
  await page.locator('#world-canvas').focus();
  await page.keyboard.press('ArrowLeft');
  await waitState(page, (s, p) => Math.hypot(s.player.x - p.x, s.player.z - p.z) > 0.35, before.player, 5_000);
  await waitState(page, (s) => !s.player.moving);
}
async function assertGroundMovement(page) {
  const before = await state(page);
  const point = await page.evaluate(() => window.__runehunterWorld.projectPoint(26, 0, 7));
  assert.ok(point?.visible && Number.isFinite(point.x) && Number.isFinite(point.y), 'A nearby walkable tile must project onto the canvas');
  await page.mouse.click(point.x, point.y);
  await waitState(page, (s, p) => Math.hypot(s.player.x - p.x, s.player.z - p.z) > 0.35, before.player);
  const after = await waitState(page, (s) => !s.player.moving);
  assert.ok(Math.hypot(after.player.x - 26, after.player.z - 7) < 1.5, 'Ground selection must move the avatar toward the chosen tile');
}
async function catchCreature(page, id, count, { keyboard = false, verifyPause = false, captureName = null } = {}) {
  const selection = page.locator(`[data-creature="${id}"]`);
  if (keyboard) { await selection.focus(); await page.keyboard.press('Enter'); }
  else await selection.click();
  if (verifyPause) {
    await waitState(page, (s, id) => s.selected === id && s.phase === 'approaching', id);
    await assertPaused(page);
  }
  await waitState(page, (s, id) => s.selected === id && s.phase === 'ready', id, 30_000);
  const capture = page.locator('#world-catch');
  assert.equal(await capture.isEnabled(), true, `Catch ${id} must become available after approaching`);
  if (captureName) {
    await page.waitForTimeout(750); // Let the encounter camera settle for visual review.
    await page.screenshot({ path: path.join(output, `${captureName}-${id}-ready.png`) });
  }
  if (keyboard) { await capture.focus(); await page.keyboard.press('Space'); }
  else await capture.click();
  if (verifyPause) {
    await waitState(page, (s) => s.phase === 'throwing');
    await assertPaused(page);
  }
  const caught = await waitState(page, (s, id) => s.caught.includes(id), id, 8_000);
  assert.equal(caught.caught.length, count, 'A catch must add exactly one journal entry');
  assert.equal(new Set(caught.caught).size, count, 'The journal cannot contain duplicate catches');
  assert.equal(caught.follower, id, 'The newly caught creature should become the companion');
  assert.match(await page.locator('#world-progress').innerText(), new RegExp(`${count}\\s*\\/\\s*3`), 'Visible collection count must match simulation');
  assert.ok((await page.locator('#world-notice').innerText()).trim(), 'Catch result should be explained');
  if (captureName) {
    await page.waitForTimeout(250);
    await page.screenshot({ path: path.join(output, `${captureName}-${id}-caught.png`) });
  }
}
async function assertReplay(page) {
  await page.locator('#world-replay').click();
  const reset = await waitState(page, (s) => s.caught.length === 0 && s.phase === 'exploring');
  assert.equal(reset.follower, null, 'Replay must reset the companion');
  assert.equal(reset.selected, null, 'Replay must reset the selection');
  assert.equal(reset.player.moving, false, 'Replay must reset movement');
  assert.match(await page.locator('#world-progress').innerText(), /0\s*\/\s*3/, 'Replay must reset the journal');
}
async function exitWorld(page, escape = false) {
  if (escape) await page.keyboard.press('Escape'); else await page.locator('#exit-world').click();
  await page.locator('#adventure-dialog').waitFor({ state: 'hidden' });
  const inactive = await waitState(page, (s) => !s.active);
  assert.equal(await page.locator('#explore-world').evaluate((element) => document.activeElement === element), true, 'Closing should return focus to Explore');
  assert.equal(await page.locator('#world-stage #world-canvas').count(), 1, 'Closing must return the scene to its stage');
  await page.waitForTimeout(200);
  assert.equal((await state(page)).time, inactive.time, 'The closed adventure must not keep simulating');
}

try {
  browser = await chromium.launch({ headless: true, ...(executablePath ? { executablePath } : {}) });
  for (const [name, width, height] of [['desktop', 1440, 900], ['square', 1200, 850], ['mobile', 390, 844], ['small-mobile', 320, 740]]) {
    const fixture = await openPage({ viewport: { width, height }, deviceScaleFactor: 1, isMobile: width < 600, hasTouch: width < 600 });
    const { page } = fixture;
    try {
      await assertLayout(page, name);
      await assertLinks(page);
      assert.equal((await state(page)).active, false, 'Adventure should wait for explicit entry');
      await page.screenshot({ path: path.join(output, `${name}-hero.png`) });
      await assertDetails(page, '.hero-discovery', /Dharok.*Barrows/s);
      await enterWorld(page);
      await assertLayout(page, `${name} adventure`);
      await waitState(page, (s) => s.renderer.mode === 'webgl' && s.renderer.frames > 0);
      await page.screenshot({ path: path.join(output, `${name}-adventure.png`) });
      if (name === 'desktop') { await assertCameraControls(page); await assertKeyboardMovement(page); await assertGroundMovement(page); }
      await assertPaused(page);
      const captureName = name === 'desktop' || name === 'mobile' ? name : null;
      await catchCreature(page, 'chicken', 1, { keyboard: name === 'desktop', verifyPause: name === 'desktop', captureName });
      if (name === 'desktop' || name === 'mobile') {
        await catchCreature(page, 'goblin', 2, { captureName });
        await catchCreature(page, 'dharok', 3, { captureName });
        await page.screenshot({ path: path.join(output, `${name}-caught.png`) });
      }
      const summary = await state(page);
      await assertReplay(page);
      await exitWorld(page, name === 'desktop');
      await enterWorld(page);
      assert.equal((await state(page)).caught.length, 0, 'Repeated entry must not restore stale reset state');
      await exitWorld(page);
      assert.equal(await page.locator('#dex .slot:not(.sec)').count(), 87, 'Collection log must retain all 87 public creatures');
      await assertDetails(page, '#faq details:has(summary:text-is("Does it send my data anywhere?"))', /RuneLite.*Party/s);
      if (name === 'desktop') await page.screenshot({ path: path.join(output, 'desktop-full-page.png'), fullPage: true });
      assertClean(fixture, name);
      results.push({ name, width, height, passed: true, frameIntervals: frameIntervalSummary(summary.renderer.frameTimes), renderer: summary.renderer });
      console.log(`Passed ${name}: WebGL, layout, real adventure actions, journal, pause, replay, focus recovery and local-only runtime.`);
    } catch (error) { await page.screenshot({ path: path.join(output, `${name}-failure.png`) }).catch(() => {}); throw error; }
    finally { await fixture.context.close(); }
  }

  const recovery = await openPage({ viewport: { width: 1200, height: 850 } });
  try {
    const { page } = recovery;
    await enterWorld(page);
    await page.locator('[data-creature="chicken"]').click();
    await waitState(page, (s) => s.phase === 'approaching' && s.player.moving);
    const loss = await page.evaluateHandle(() => document.getElementById('world-canvas').getContext('webgl2').getExtension('WEBGL_lose_context'));
    assert.equal(await loss.evaluate((extension) => Boolean(extension)), true, 'Browser must support controlled WebGL context loss');
    await loss.evaluate((extension) => extension.loseContext());
    await waitState(page, (s) => s.renderer.contextLost);
    assert.ok(await page.locator('#adventure-viewport').evaluate((element) => element.classList.contains('simple-view')), 'Context loss must show the usable simple view');
    assert.ok(await page.locator('#world-fallback-message').isVisible(), 'Context loss needs an explanation');
    assert.equal(await page.locator('#world-canvas').getAttribute('tabindex'), '-1', 'The unavailable canvas should leave the keyboard tab order');
    for (const id of ['world-zoom-in', 'world-zoom-out', 'world-rotate-left', 'world-rotate-right']) assert.equal(await page.locator(`#${id}`).isDisabled(), true, 'Unavailable camera controls should be disabled');
    await catchCreature(page, 'chicken', 1, { keyboard: true });
    await catchCreature(page, 'goblin', 2, { keyboard: true });
    await page.screenshot({ path: path.join(output, 'context-lost-simple-view.png') });
    await loss.evaluate((extension) => extension.restoreContext());
    await waitState(page, (s) => !s.renderer.contextLost && s.renderer.mode === 'webgl');
    assert.deepEqual((await state(page)).caught, ['chicken', 'goblin'], 'Restoring graphics must preserve the collection');
    assert.equal(await page.locator('#world-canvas').getAttribute('tabindex'), '0', 'Restoring graphics should restore keyboard scene controls');
    assert.equal(await page.locator('#world-zoom-in').isEnabled(), true, 'Camera controls should recover with WebGL');
    await catchCreature(page, 'dharok', 3);
    await page.screenshot({ path: path.join(output, 'context-restored-adventure.png') });
    await exitWorld(page);
    await loss.dispose();
    assertClean(recovery, 'WebGL context recovery');
    results.push({ name: 'webgl-context-recovery', passed: true });
  } finally { await recovery.context.close(); }

  const reduced = await openPage({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' });
  try {
    await enterWorld(reduced.page);
    await assertLayout(reduced.page, 'reduced motion');
    const before = await state(reduced.page);
    await reduced.page.waitForTimeout(350);
    const after = await state(reduced.page);
    assert.equal(after.time, before.time, 'Reduced motion must not run an ambient simulation');
    await catchCreature(reduced.page, 'chicken', 1, { keyboard: true });
    await reduced.page.screenshot({ path: path.join(output, 'reduced-motion-adventure.png') });
    await exitWorld(reduced.page);
    assertClean(reduced, 'reduced motion');
    results.push({ name: 'reduced-motion', passed: true });
  } finally { await reduced.context.close(); }

  const simple = await openPage({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' }, { webglUnavailable: true });
  try {
    await enterWorld(simple.page);
    assert.equal((await state(simple.page)).renderer.mode, 'fallback', 'Unavailable WebGL should select the simple view');
    await assertLayout(simple.page, 'WebGL fallback');
    for (const [index, id] of ['chicken', 'goblin', 'dharok'].entries()) await catchCreature(simple.page, id, index + 1, { keyboard: true });
    await simple.page.screenshot({ path: path.join(output, 'webgl-fallback.png') });
    await exitWorld(simple.page);
    assertClean(simple, 'WebGL fallback');
    results.push({ name: 'webgl-unavailable', passed: true });
  } finally { await simple.context.close(); }

  const noJS = await openPage({ viewport: { width: 390, height: 844 }, javaScriptEnabled: false }, { debug: false });
  try {
    const { page } = noJS;
    assert.match(await page.locator('.hero').innerText(), /RuneLite/i, 'No-JS hero must explain the product');
    assert.ok(await page.locator('img[src*="lumbridge-hero"]').first().isVisible(), 'No-JS fallback needs local scene artwork');
    await assertLayout(page, 'no JS');
    await assertLinks(page);
    await assertDetails(page, '.hero-discovery', /Dharok.*Barrows/s);
    await page.locator('#explore-world').click();
    await page.waitForURL('**/#tour');
    assert.ok(await page.locator('#tour').isVisible(), 'Explore fallback must reach readable content');
    assert.equal(await page.locator('#adventure-dialog').isVisible(), false, 'No-JS fallback must not strand the visitor in a dialog');
    await page.screenshot({ path: path.join(output, 'no-js.png') });
    assertClean(noJS, 'no JS');
    results.push({ name: 'no-js', passed: true });
  } finally { await noJS.context.close(); }

  const publicPage = await openPage({ viewport: { width: 1200, height: 850 } }, { debug: false });
  try {
    assert.equal(await publicPage.page.evaluate(() => typeof window.__runehunterWorld), 'undefined', 'Debug bridge must not be exposed on the ordinary page');
    assertClean(publicPage, 'public page');
    results.push({ name: 'debug-isolation', passed: true });
  } finally { await publicPage.context.close(); }
  await writeFile(path.join(output, 'results.json'), JSON.stringify({ checkedAt: new Date().toISOString(), browser: browser.version(), performanceNote: 'Local headless Chrome animation-frame intervals, including automated interactions and screenshot capture. Renderer samples exclude intervals of 250 ms or more. These are not GPU render times or physical mobile-device measurements.', results }, null, 2) + '\n');
  console.log(`RuneHunter browser acceptance passed. Screenshots and results: ${output}`);
} finally {
  if (browser) await browser.close();
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
}
