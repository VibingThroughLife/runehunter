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
const output = path.resolve(process.env.RUNEHUNTER_QA_DIR || path.join(tmpdir(), 'runehunter-scroll-qa'));
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

async function openPage(options, { webglUnavailable = false, debug = true, hash = '' } = {}) {
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
  await page.goto(`${origin}/${debug ? '?debug' : ''}${hash}`, { waitUntil: 'load' });
  await page.evaluate(() => document.fonts.ready);
  await page.locator('#intro h1').waitFor({ state: 'attached' });
  await page.waitForFunction(() => [...document.images].every((image) => image.complete && image.naturalWidth > 0));
  if (options.javaScriptEnabled !== false && debug) {
    await page.waitForFunction(() => typeof window.__runehunterIntro?.getState === 'function');
    if (!hash) await waitState(page, (s) => s.renderer.mode === 'fallback' || s.renderer.frames > 0);
  }
  return { context, page, errors, requests };
}
function assertClean(fixture, label) {
  assert.deepEqual(fixture.errors, [], `${label}: browser errors`);
  assert.deepEqual(fixture.requests, [], `${label}: external runtime requests`);
}
async function state(page) { return page.evaluate(() => window.__runehunterIntro.getState()); }
function frameIntervalSummary(intervals = []) {
  const sorted = [...intervals].sort((a, b) => a - b);
  const percentile = (p) => sorted.length ? Number(sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * p))].toFixed(2)) : null;
  return { samples: sorted.length, medianMs: percentile(.5), p95Ms: percentile(.95), maxMs: sorted.length ? Number(sorted.at(-1).toFixed(2)) : null };
}
async function waitState(page, predicate, arg, timeout = 12_000) {
  await page.waitForFunction(({ predicate, arg }) => new Function('state', 'arg', `return (${predicate})(state, arg)`)(window.__runehunterIntro.getState(), arg), { predicate: predicate.toString(), arg }, { timeout });
  return state(page);
}
async function scrollToProgress(page, progress) {
  await page.evaluate((p) => {
    const intro = document.getElementById('intro');
    const top = intro.getBoundingClientRect().top + scrollY;
    window.scrollTo({ top: top + (intro.offsetHeight - innerHeight) * p, behavior: 'instant' });
  }, progress);
  if (progress >= 0 && progress <= 1) await waitState(page, (s, p) => Math.abs(s.progress - p) < .015, progress);
  await page.waitForTimeout(100);
}
async function assertLayout(page, label) {
  const metrics = await page.evaluate(() => ({
    viewport: innerWidth,
    width: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth),
    clipped: [...document.querySelectorAll('#intro h1,#intro h2,#intro button,#intro a,nav button,nav a')].flatMap((element) => {
      const style = getComputedStyle(element), rect = element.getBoundingClientRect();
      if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0 || !rect.width || rect.bottom < 0 || rect.top > innerHeight) return [];
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
  assert.equal(await page.locator('#intro-begin').getAttribute('href'), '#encounter', 'Begin must retain a native anchor');
  assert.equal(await page.locator('#intro-skip').getAttribute('href'), '#how', 'Skip must retain a native anchor');
}
async function assertDetails(page) {
  const details = page.locator('#faq details').nth(1), summary = details.locator('summary');
  await summary.scrollIntoViewIfNeeded();
  await summary.focus();
  await page.keyboard.press('Enter');
  assert.equal(await details.getAttribute('open'), '', 'FAQ should open using the keyboard');
  assert.ok((await details.innerText()).length > (await summary.innerText()).length + 10, 'FAQ must contain a useful answer');
  await page.keyboard.press('Enter');
  assert.equal(await details.getAttribute('open'), null, 'FAQ should close using the keyboard');
}
async function screenshot(page, name) {
  await assertLayout(page, name);
  const current = await state(page);
  assert.ok(current.renderer.triangles <= 50_000, `${name}: triangle budget exceeded`);
  assert.ok(current.renderer.drawCalls <= 75, `${name}: draw-call budget exceeded`);
  await page.screenshot({ path: path.join(output, `${name}.png`) });
  return current;
}
async function assertCameraSweepBudget(page, label) {
  const samples = [];
  for (const progress of [0, .1, .2, .3, .42, .5, .58, .65, .76, 1]) {
    await scrollToProgress(page, progress);
    const current = await state(page);
    assert.equal(current.renderer.mode, 'webgl', `${label}: camera sweep must measure the live scene`);
    const sample = { progress, triangles: current.renderer.triangles, drawCalls: current.renderer.drawCalls };
    assert.ok(sample.triangles > 0 && sample.triangles <= 50_000, `${label}: triangle budget at progress ${progress}: ${sample.triangles}`);
    assert.ok(sample.drawCalls > 0 && sample.drawCalls <= 75, `${label}: draw-call budget at progress ${progress}: ${sample.drawCalls}`);
    samples.push(sample);
  }
  await scrollToProgress(page, 0);
  return {
    maxTriangles: Math.max(...samples.map((sample) => sample.triangles)),
    maxDrawCalls: Math.max(...samples.map((sample) => sample.drawCalls)),
    samples,
  };
}
async function beginCatch(page, keyboard = false) {
  const button = page.locator('#intro-catch');
  assert.equal(await button.isEnabled(), true, 'The visible encounter must offer a catch');
  if (keyboard) { await button.focus(); await page.keyboard.press('Space'); }
  else await button.click();
}
async function finishCatch(page, label) {
  const caught = await waitState(page, (s) => s.phase === 'caught');
  assert.equal(caught.catchCount, 1, `${label}: the catch must complete exactly once`);
  assert.match(await page.locator('#intro-encounter').innerText(), /Mini Dharok caught/i, `${label}: catch confirmation missing`);
  assert.ok(await page.locator('#intro-replay').isVisible(), 'A completed catch needs Replay');
  return caught;
}
async function replay(page) {
  await page.locator('#intro-replay').click();
  await waitState(page, (s) => s.phase !== 'caught' && s.phase !== 'throwing' && s.catchCount === 0);
  assert.ok(await page.locator('#intro-catch').isEnabled(), 'Replay must offer a new catch');
}
async function assertPause(page) {
  const pause = page.locator('#motion-toggle');
  await pause.click();
  await waitState(page, (s) => s.paused);
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  const before = await state(page);
  assert.equal(before.phase, 'throwing', 'Pause must preserve an in-flight throw');
  await page.waitForTimeout(450);
  const after = await state(page);
  assert.equal(after.elapsed, before.elapsed, 'Pause must freeze encounter elapsed time');
  assert.equal(after.catchCount, before.catchCount, 'Pause must not complete the catch');
  assert.deepEqual(after.player, before.player, 'Pause must freeze the player pose');
  assert.deepEqual(after.camera, before.camera, 'Pause must freeze the camera');
  assert.equal(after.renderer.frames, before.renderer.frames, 'An unchanged paused frame must stop rendering');
  await pause.click();
  await waitState(page, (s) => !s.paused);
}
async function testViewport(name, width, height) {
  const fixture = await openPage({ viewport: { width, height }, deviceScaleFactor: 1, isMobile: width < 600, hasTouch: width < 600 });
  const { page } = fixture;
  try {
    await assertLinks(page);
    assert.equal(await page.locator('dialog').count(), 0, 'Intro must not use a modal');
    assert.equal(await page.locator('#world-canvas').count(), 1, 'One canvas must carry the full journey');
    await screenshot(page, `${name}-arrival`);
    const budgetSweep = await assertCameraSweepBudget(page, name);
    if (name === 'desktop') {
      await page.mouse.wheel(0, 300);
      await waitState(page, (s) => s.progress > .03);
      await page.locator('body').click({ position: { x: width - 5, y: height - 5 } });
      const beforeKey = await page.evaluate(() => scrollY);
      await page.keyboard.press('PageDown');
      await page.waitForFunction((before) => scrollY > before + 50, beforeKey);
      await page.waitForTimeout(600); // Native keyboard scrolling may finish after its first scroll event.
    } else {
      const client = await fixture.context.newCDPSession(page);
      await client.send('Input.synthesizeScrollGesture', { x: Math.round(width / 2), y: Math.round(height * .75), yDistance: -220, gestureSourceType: 'touch' });
      await waitState(page, (s) => s.progress > .01);
      await client.detach();
    }
    await scrollToProgress(page, .42);
    const corner = await screenshot(page, `${name}-castle-corner`);
    await scrollToProgress(page, .76);
    const encounter = await screenshot(page, `${name}-encounter`);
    assert.notDeepEqual(corner.camera, encounter.camera, 'Scrolling must move the camera from the castle corner to the encounter');
    assert.ok(await page.locator('#intro-catch').isVisible(), 'Dharok encounter must offer an accessible action');
    assert.ok((await page.locator('#intro-status').getAttribute('role')) === 'status' || await page.locator('#intro-status').getAttribute('aria-live'), 'Catch status needs live feedback');
    await beginCatch(page, name === 'desktop');
    await waitState(page, (s) => s.phase === 'throwing');
    // Dispatch duplicate native activation while the first catch is in flight.
    await page.locator('#intro-catch').evaluate((button) => { button.click(); button.click(); });
    if (name === 'desktop') await assertPause(page);
    await page.waitForTimeout(950);
    await screenshot(page, `${name}-orb-release`);
    const completed = await finishCatch(page, name);
    await screenshot(page, `${name}-caught`);
    await scrollToProgress(page, .1);
    assert.equal((await state(page)).catchCount, 1, 'Reverse scrolling must preserve a completed catch');
    await scrollToProgress(page, .76);
    assert.equal((await state(page)).phase, 'caught', 'Returning must retain completion');
    await replay(page);
    await beginCatch(page);
    await waitState(page, (s) => s.phase === 'throwing');
    await scrollToProgress(page, .2);
    assert.equal((await state(page)).phase, 'caught', 'Leaving backward during a throw must settle completion');
    assert.equal((await state(page)).catchCount, 1, 'Leaving during a throw must settle once');
    await scrollToProgress(page, .76);
    await replay(page);
    await beginCatch(page);
    await page.locator('#intro-skip').click();
    await page.waitForFunction(() => location.hash === '#how');
    await waitState(page, (s) => s.phase === 'caught' && !s.visible);
    await page.waitForTimeout(150);
    const offscreenBefore = await state(page);
    await page.waitForTimeout(350);
    assert.equal((await state(page)).renderer.frames, offscreenBefore.renderer.frames, 'Offscreen intro must stop rendering');
    await scrollToProgress(page, .8);
    await scrollToProgress(page, 0);
    await scrollToProgress(page, .9);
    assert.equal((await state(page)).catchCount, 1, 'Rapid scroll jumps must not duplicate a catch');
    assert.equal(await page.locator('#dex .slot:not(.sec)').count(), 87, 'Collection log must retain all public creatures');
    await assertDetails(page);
    assertClean(fixture, name);
    results.push({ name, width, height, passed: true, budgetSweep, frameIntervals: frameIntervalSummary(completed.renderer.frameTimes), renderer: completed.renderer });
    console.log(`Passed ${name}: native scroll, camera journey, catch, pause, replay, skip, offscreen suspension and local runtime; 10-point sweep peaks ${budgetSweep.maxTriangles} triangles / ${budgetSweep.maxDrawCalls} draw calls.`);
  } catch (error) { console.error(name, await state(page).then(({ progress, chapter, phase, elapsed, catchCount, paused, visible, renderer }) => ({ progress, chapter, phase, elapsed, catchCount, paused, visible, renderer: { ...renderer, frameTimes: frameIntervalSummary(renderer.frameTimes) } })).catch(() => null)); await page.screenshot({ path: path.join(output, `${name}-failure.png`) }).catch(() => {}); throw error; }
  finally { await fixture.context.close(); }
}

try {
  browser = await chromium.launch({ headless: true, ...(executablePath ? { executablePath } : {}) });
  for (const [name, width, height] of [['desktop', 1440, 900], ['mobile', 390, 844], ['small-mobile', 320, 740]]) await testViewport(name, width, height);

  const restored = await openPage({ viewport: { width: 1200, height: 850 } });
  try {
    const { page } = restored;
    await scrollToProgress(page, .78);
    await page.reload({ waitUntil: 'load' });
    await waitState(page, (s) => s.progress > .65 && s.catchCount === 0);
    assert.ok(await page.locator('#intro-catch').isVisible(), 'Reload at a restored scroll position must expose the correct chapter');
    await page.setViewportSize({ width: 390, height: 844 });
    await assertLayout(page, 'resize');
    await scrollToProgress(page, .78);
    await screenshot(page, 'restored-scroll-resized');
    assertClean(restored, 'restored scroll and resize');
    results.push({ name: 'restored-scroll-resize', passed: true });
  } finally { await restored.context.close(); }

  const deepLink = await openPage({ viewport: { width: 390, height: 844 } }, { hash: '#faq' });
  try {
    await deepLink.page.waitForFunction(() => Math.abs(document.getElementById('faq').getBoundingClientRect().top) < 180);
    await assertDetails(deepLink.page);
    assertClean(deepLink, 'FAQ deep link');
    results.push({ name: 'direct-faq-link', passed: true });
  } finally { await deepLink.context.close(); }

  const recovery = await openPage({ viewport: { width: 1200, height: 850 } });
  try {
    const { page } = recovery;
    await scrollToProgress(page, .78);
    const loss = await page.evaluateHandle(() => document.getElementById('world-canvas').getContext('webgl2').getExtension('WEBGL_lose_context'));
    assert.equal(await loss.evaluate((extension) => Boolean(extension)), true, 'Browser must support controlled context loss');
    await loss.evaluate((extension) => extension.loseContext());
    await waitState(page, (s) => s.renderer.contextLost || s.renderer.mode === 'fallback');
    assert.ok(await page.locator('img[src*="lumbridge-rear"]').first().isVisible(), 'Context loss must show the scene fallback');
    await beginCatch(page, true);
    await finishCatch(page, 'context fallback');
    await screenshot(page, 'context-lost-caught');
    await loss.evaluate((extension) => extension.restoreContext());
    await waitState(page, (s) => !s.renderer.contextLost && s.renderer.mode === 'webgl');
    assert.equal((await state(page)).catchCount, 1, 'Restored graphics must preserve the completed catch');
    await screenshot(page, 'context-restored-caught');
    await loss.dispose();
    assertClean(recovery, 'WebGL context recovery');
    results.push({ name: 'webgl-context-recovery', passed: true });
  } finally { await recovery.context.close(); }

  const reduced = await openPage({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' });
  try {
    await scrollToProgress(reduced.page, .78);
    const before = await state(reduced.page);
    assert.equal(before.reducedMotion, true, 'Reduced-motion preference must be honored');
    await reduced.page.waitForTimeout(350);
    assert.equal((await state(reduced.page)).renderer.frames, before.renderer.frames, 'Reduced motion must not keep animating');
    await beginCatch(reduced.page, true);
    const completed = await finishCatch(reduced.page, 'reduced motion');
    assert.equal(completed.phase, 'caught', 'Reduced motion catch must complete discretely');
    await screenshot(reduced.page, 'reduced-motion-caught');
    await reduced.page.locator('#motion-toggle').click();
    await waitState(reduced.page, (s) => !s.paused && !s.reducedMotion);
    await replay(reduced.page);
    await beginCatch(reduced.page, true);
    await waitState(reduced.page, (s) => s.phase === 'throwing');
    const optedIn = await state(reduced.page);
    await reduced.page.waitForTimeout(200);
    const animated = await state(reduced.page);
    assert.ok(animated.elapsed > optedIn.elapsed, 'Explicit Resume must allow animated catches after a reduced-motion default');
    assert.ok(animated.renderer.frames > optedIn.renderer.frames, 'Explicit Resume must restart rendering');
    await finishCatch(reduced.page, 'explicit motion opt-in');
    assertClean(reduced, 'reduced motion');
    results.push({ name: 'reduced-motion', passed: true });
  } finally { await reduced.context.close(); }

  const simple = await openPage({ viewport: { width: 390, height: 844 } }, { webglUnavailable: true });
  try {
    assert.equal((await state(simple.page)).renderer.mode, 'fallback', 'Unavailable WebGL must use scene stills');
    await scrollToProgress(simple.page, .78);
    await beginCatch(simple.page, true);
    await finishCatch(simple.page, 'WebGL unavailable');
    await screenshot(simple.page, 'webgl-fallback');
    await replay(simple.page);
    assertClean(simple, 'WebGL unavailable');
    results.push({ name: 'webgl-unavailable', passed: true });
  } finally { await simple.context.close(); }

  const noJS = await openPage({ viewport: { width: 390, height: 844 }, javaScriptEnabled: false }, { debug: false });
  try {
    const { page } = noJS;
    assert.ok(await page.locator('img[src*="lumbridge-hero"]').first().isVisible(), 'No-JS intro needs a real-scene still');
    await assertLayout(page, 'no JS');
    await assertLinks(page);
    await page.locator('#intro-begin').click();
    await page.waitForURL('**/#encounter');
    assert.match(await page.locator('#intro').innerText(), /Dharok/i, 'No-JS narrative must describe the encounter');
    await page.screenshot({ path: path.join(output, 'no-js-encounter.png') });
    await page.locator('#intro-skip').click();
    await page.waitForURL('**/#how');
    await assertDetails(page);
    assertClean(noJS, 'no JS');
    results.push({ name: 'no-js', passed: true });
  } finally { await noJS.context.close(); }

  const publicPage = await openPage({ viewport: { width: 1200, height: 850 } }, { debug: false });
  try {
    assert.equal(await publicPage.page.evaluate(() => typeof window.__runehunterIntro), 'undefined', 'Debug bridge must not be exposed on ordinary visits');
    assertClean(publicPage, 'public page');
    results.push({ name: 'debug-isolation', passed: true });
  } finally { await publicPage.context.close(); }
  await writeFile(path.join(output, 'results.json'), JSON.stringify({ checkedAt: new Date().toISOString(), browser: browser.version(), performanceNote: 'Local headless Chrome animation-frame intervals, including automated interactions and screenshots. These are not GPU render times or physical-device measurements. Mobile and touch coverage uses browser emulation.', results }, null, 2) + '\n');
  console.log(`RuneHunter scroll introduction acceptance passed. Screenshots and traces: ${output}`);
} finally {
  if (browser) await browser.close();
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
}
