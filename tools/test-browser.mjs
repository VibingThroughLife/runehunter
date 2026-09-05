import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { access, mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// Use an existing Playwright installation; this project has no package-install step.
// NODE_PATH=/path/to/node_modules node tools/test-browser.mjs
// PLAYWRIGHT_PACKAGE_PATH=/path/to/playwright also accepts a package entry file.
// PLAYWRIGHT_CHROMIUM_EXECUTABLE overrides the browser, RUNEHUNTER_QA_DIR the output directory.
const require = createRequire(import.meta.url);
let chromium;
for (const name of [process.env.PLAYWRIGHT_PACKAGE_PATH, 'playwright', 'playwright-core'].filter(Boolean)) {
  try { ({ chromium } = require(name)); if (chromium) break; } catch (error) {
    if (error.code !== 'MODULE_NOT_FOUND') throw error;
  }
}
assert.ok(chromium, 'Playwright is unavailable. Set NODE_PATH or PLAYWRIGHT_PACKAGE_PATH to an existing installation.');
const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const docs = path.join(repo, 'docs');
const output = path.resolve(process.env.RUNEHUNTER_QA_DIR || path.join(tmpdir(), 'runehunter-hero-v1-qa'));
assert.ok(!output.startsWith(`${repo}${path.sep}`), 'Keep browser screenshots outside the source checkout.');
await mkdir(output, { recursive: true });

const mime = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.jpg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp', '.svg': 'image/svg+xml', '.woff2': 'font/woff2' };
const server = createServer(async (request, response) => {
  try {
    const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    const filename = path.resolve(docs, `.${pathname === '/' ? '/index.html' : pathname}`);
    if (!filename.startsWith(`${docs}${path.sep}`) || !(await stat(filename)).isFile()) throw new Error('Not found');
    response.writeHead(200, { 'Content-Type': mime[path.extname(filename)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
    response.end(await readFile(filename));
  } catch {
    response.writeHead(404); response.end('Not found');
  }
});
await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
const origin = `http://127.0.0.1:${server.address().port}`;
let executablePath = process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;
if (!executablePath && process.platform === 'darwin') {
  const chrome = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
  try { await access(chrome); executablePath = chrome; } catch { /* Try Playwright's bundled Chromium. */ }
}
const results = [];
let browser;

async function openPage(options) {
  const context = await browser.newContext(options);
  const page = await context.newPage();
  page.setDefaultTimeout(10_000);
  const errors = [];
  const requests = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => { if (message.type() === 'error') errors.push(message.text()); });
  page.on('response', (response) => { if (response.status() >= 400) errors.push(`${response.status()} ${response.url()}`); });
  page.on('requestfailed', (request) => errors.push(`${request.url()}: ${request.failure()?.errorText}`));
  await context.route('**/*', (route) => {
    const url = new URL(route.request().url());
    if (url.origin !== origin && url.protocol !== 'data:') {
      requests.push(url.href);
      return route.abort();
    }
    return route.continue();
  });
  await page.goto(origin, { waitUntil: 'load' });
  await page.evaluate(() => document.fonts.ready);
  await page.locator('.hero h1').waitFor({ state: 'visible' });
  await page.waitForFunction(() => [...document.images].every((image) => image.complete && image.naturalWidth > 0));
  assert.ok(await page.locator('img[src*="lumbridge-hero"]').isVisible(), 'Hero needs its locally shipped scene artwork');
  return { context, page, errors, requests };
}

async function assertLayout(page, label) {
  const metrics = await page.evaluate(() => ({
    viewport: innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
    clipped: [...document.querySelectorAll('.hero h1, .hero .kicker, .hero .lede, .hero .cta a, .hero button')].flatMap((element) => {
      const style = getComputedStyle(element), rect = element.getBoundingClientRect();
      if (style.display === 'none' || style.visibility === 'hidden' || !rect.width) return [];
      return rect.left < -1 || rect.right > innerWidth + 1 ? [{ text: element.textContent.trim(), left: rect.left, right: rect.right }] : [];
    }),
  }));
  assert.ok(metrics.document <= metrics.viewport + 1 && metrics.body <= metrics.viewport + 1, `${label}: horizontal overflow ${JSON.stringify(metrics)}`);
  assert.deepEqual(metrics.clipped, [], `${label}: hero content is clipped`);
}

async function waitForScrollSettled(page) {
  // Native smooth scrolling also runs without page JavaScript. Let it finish
  // before the next keyboard action or Playwright's element-stability check.
  let previous = null, stable = 0;
  for (let sample = 0; sample < 40; sample += 1) {
    const position = await page.evaluate(() => scrollY);
    stable = position === previous ? stable + 1 : 0;
    if (stable >= 3) return;
    previous = position;
    await page.waitForTimeout(50);
  }
  throw new Error('Native scrolling did not settle within two seconds');
}

async function assertLinks(page) {
  const broken = await page.locator('a[href^="#"]').evaluateAll((links) => links
    .filter((link) => link.hash && !document.getElementById(decodeURIComponent(link.hash.slice(1))))
    .map((link) => link.getAttribute('href')));
  assert.deepEqual(broken, [], 'All fragment links need targets');
  const discord = await page.locator('a[href*="discord"]').evaluateAll((links) => links.map((link) => link.href));
  assert.equal(discord.length, 4, 'Expected four Discord links');
  assert.ok(discord.every((url) => url === 'https://runehunter.gg/discord'), 'Discord links must use the canonical redirect');
  const primary = page.locator('.hero .cta a').first();
  assert.equal(await primary.getAttribute('href'), '#tour', 'Hero primary action should begin the world tour');
  await primary.click();
  await page.waitForURL('**/#tour');
  await waitForScrollSettled(page);
  assert.ok(await page.locator('#tour').isVisible(), 'Primary action needs a visible destination');
  assert.match(await page.locator('#tour').innerText(), /Lumbridge|Dharok/i, 'World tour should retain its scene explanation');
}

async function assertFaq(page) {
  const faq = page.locator('#faq details').filter({ has: page.locator('summary', { hasText: 'Does it send my data anywhere?' }) });
  assert.equal(await faq.count(), 1, 'Privacy FAQ is missing');
  const summary = faq.locator('summary');
  await summary.scrollIntoViewIfNeeded();
  await summary.focus();
  await page.keyboard.press('Enter');
  assert.equal(await faq.getAttribute('open'), '', 'FAQ should open with the keyboard');
  assert.match(await faq.locator('.ans').innerText(), /RuneLite.*Party/s, 'Privacy answer must explain the Party relay');
  await page.keyboard.press('Enter');
  assert.equal(await faq.getAttribute('open'), null, 'FAQ should close with the keyboard');
}

async function assertDiscovery(page) {
  const discovery = page.locator('.hero-discovery');
  const summary = discovery.locator('summary');
  await summary.scrollIntoViewIfNeeded();
  await summary.focus();
  await page.keyboard.press('Enter');
  assert.equal(await discovery.getAttribute('open'), '', 'Examine Dharok should open with the keyboard');
  assert.match(await discovery.locator('.discovery-answer').innerText(), /Dharok.*Barrows/s, 'Examine should reveal the creature description');
  assert.equal(await discovery.locator('.discovery-answer a').getAttribute('href'), '#roster', 'Examine should lead into the collection log');
  await page.keyboard.press('Enter');
  assert.equal(await discovery.getAttribute('open'), null, 'Examine should close with the keyboard');
  await waitForScrollSettled(page);
}

async function assertPrayers(page) {
  const buttons = page.locator('#prays button');
  assert.equal(await buttons.count(), 3, 'Battle demo needs three prayer controls');
  for (let index = 0; index < 3; index += 1) {
    const button = buttons.nth(index);
    assert.ok(await button.getAttribute('aria-label'), 'Prayer controls need accessible names');
    await button.scrollIntoViewIfNeeded();
    await button.focus();
    await page.keyboard.press('Space');
    const states = await buttons.evaluateAll((items) => items.map((item) => item.getAttribute('aria-pressed')));
    assert.deepEqual(states, [0, 1, 2].map((i) => i === index ? 'true' : 'false'), 'Keyboard prayer selection must update exclusive pressed states');
  }
  assert.equal(await page.locator('#tel').getAttribute('role'), 'status', 'Battle telegraph must remain accessible');
}

async function canvasFrame(page) {
  return page.locator('#world').evaluate((canvas) => canvas.toDataURL());
}

async function assertFrozenCanvas(page, label) {
  // Let a frame that was already queued finish, then compare two later samples.
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  const before = await canvasFrame(page);
  await page.waitForTimeout(250);
  assert.ok(await canvasFrame(page) === before, `${label}: the world canvas keeps animating`);
}

async function assertMotion(page, reduced = false) {
  const toggle = page.locator('#motion-toggle');
  assert.ok(await toggle.isVisible(), 'Motion pause button must be visible');
  assert.equal(await toggle.getAttribute('aria-pressed'), reduced ? 'true' : 'false', 'Motion toggle should reflect the motion preference');
  await page.locator('#tour').scrollIntoViewIfNeeded();
  if (!reduced) { await toggle.click(); }
  assert.equal(await toggle.getAttribute('aria-pressed'), 'true', 'Pause should expose its pressed state');
  const animation = await page.locator('.hero-art img').evaluate((image) => ({ name: getComputedStyle(image).animationName, state: getComputedStyle(image).animationPlayState }));
  assert.ok(animation.name === 'none' || animation.state === 'paused', 'Pause must also stop the hero artwork animation');
  await assertFrozenCanvas(page, reduced ? 'Reduced motion' : 'Paused motion');
  if (!reduced) {
    await toggle.click();
    assert.equal(await toggle.getAttribute('aria-pressed'), 'false', 'Resume should clear its pressed state');
  }
  await page.evaluate(() => scrollTo({ top: 0, behavior: 'instant' }));
}

try {
  browser = await chromium.launch({ headless: true, ...(executablePath ? { executablePath } : {}) });
  for (const [name, width, height] of [['desktop', 1440, 900], ['square', 1200, 850], ['mobile', 390, 844], ['small-mobile', 320, 740]]) {
    const fixture = await openPage({ viewport: { width, height }, deviceScaleFactor: 1, isMobile: width < 600, hasTouch: width < 600 });
    const { page } = fixture;
    try {
      await assertLayout(page, name);
      await assertMotion(page);
      await page.screenshot({ path: path.join(output, `${name}-hero.png`) });
      await assertDiscovery(page);
      await assertLinks(page);
      assert.equal(await page.locator('#dex .slot:not(.sec)').count(), 87, 'Rendered roster must include all 87 creatures');
      for (const section of ['#how', '#roster', '#battle', '#community', '#faq', '#install', '#dev']) {
        await page.locator(section).scrollIntoViewIfNeeded();
        await assertLayout(page, `${name} ${section}`);
      }
      await assertFaq(page);
      await assertPrayers(page);
      if (name === 'desktop') {
        await page.locator('#motion-toggle').click();
        const pausedTick = await page.locator('#tick').innerText();
        await page.waitForTimeout(850);
        assert.equal(await page.locator('#tick').innerText(), pausedTick, 'Pause must stop the live battle demonstration');
        await page.screenshot({ path: path.join(output, 'desktop-full-page.png'), fullPage: true });
      }
      assert.deepEqual(fixture.errors, [], `${name}: browser errors`);
      assert.deepEqual(fixture.requests, [], `${name}: unexpected external runtime requests`);
      results.push({ name, width, height, passed: true });
      console.log(`Passed ${name} ${width}x${height}: artwork, layout, navigation, roster, FAQ, prayer controls, motion, and no external requests.`);
    } catch (error) {
      await page.screenshot({ path: path.join(output, `${name}-failure.png`) }).catch(() => {});
      throw error;
    } finally { await fixture.context.close(); }
  }
  const reduced = await openPage({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' });
  try {
    await assertMotion(reduced.page, true);
    await assertLayout(reduced.page, 'reduced-motion');
    await reduced.page.screenshot({ path: path.join(output, 'reduced-motion-hero.png') });
    assert.deepEqual(reduced.errors, [], 'Reduced-motion browser errors');
    assert.deepEqual(reduced.requests, [], 'Reduced-motion external requests');
    results.push({ name: 'reduced-motion', passed: true });
  } finally { await reduced.context.close(); }

  const fallback = await openPage({ viewport: { width: 390, height: 844 }, javaScriptEnabled: false });
  try {
    const { page } = fallback;
    assert.match(await page.locator('.hero').innerText(), /RuneLite/i, 'No-JS hero must explain the product');
    assert.match(await page.locator('.hero').innerText(), /Dharok|OSRS|Gielinor/i, 'No-JS hero must retain its game context');
    assert.ok(await page.locator('img[src*="lumbridge-hero"]').isVisible(), 'No-JS hero needs the local scene artwork');
    assert.equal(await page.locator('#motion-toggle').isVisible(), false, 'No-JS fallback should hide the inactive motion control');
    await assertLayout(page, 'no-JS');
    await page.screenshot({ path: path.join(output, 'no-js-hero.png') });
    await assertDiscovery(page);
    await assertLinks(page);
    await assertFaq(page);
    assert.deepEqual(fallback.errors, [], 'No-JS browser errors');
    assert.deepEqual(fallback.requests, [], 'No-JS external requests');
    results.push({ name: 'no-js', passed: true });
  } finally { await fallback.context.close(); }

  await writeFile(path.join(output, 'results.json'), JSON.stringify({ checkedAt: new Date().toISOString(), results }, null, 2) + '\n');
  console.log(`RuneHunter browser acceptance passed. Screenshots and results: ${output}`);
} finally {
  if (browser) await browser.close();
  await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
}
