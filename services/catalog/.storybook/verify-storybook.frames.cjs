// Storybook quality gate for Storybook 8, adapted from specification/references/verify-storybook.cdp.js.
// The upstream script reads `#storybook-root` from the manager page, but Storybook 8 renders the preview in an
// <iframe>, so every story reports "empty". This version resolves the preview frame first and keeps the same
// checks: every story renders content and produces no console error / page error.
//
// The story list comes from the static `index.json`, not from the sidebar: SB8 renders the tree lazily and a
// collapsed group hides its children, so a sidebar-only sweep silently checked 4 of 23 stories and still passed.
// Sidebar links are still collected, as a warning that the tree is not what this gate enumerates.
//
// Usage: node verify-storybook.frames.cjs [url] [--flow flows.json]
//
// The gate is self-contained: when nothing answers at the target url it builds `storybook-static` (if the build is
// missing) and serves it itself, so `node verify-storybook.frames.cjs` is one command and does not depend on a server
// someone else left running. `--flow` defaults to flows.json beside this script.
const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const http = require('http');
const path = require('path');
const fs = require('fs');

const STATIC_DIR = path.join(__dirname, 'storybook-static');
const BASE = process.argv[2] && !process.argv[2].startsWith('--') ? process.argv[2] : 'http://localhost:6010';
const flowIdx = process.argv.indexOf('--flow');
const FLOW = flowIdx !== -1 && process.argv[flowIdx + 1]
  ? path.resolve(process.argv[flowIdx + 1])
  : fs.existsSync(path.join(__dirname, 'flows.json')) ? path.join(__dirname, 'flows.json') : null;

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.mjs': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
};

const isUp = async (base) => {
  try {
    const response = await fetch(base.replace(/\/$/, '') + '/index.json');
    return response.ok;
  } catch (error) {
    return false;
  }
};

/** Serves the static build over http, the way the gate needs it: any path, query ignored, index.html at the root. */
function serveStatic(root, port) {
  const server = http.createServer((request, response) => {
    const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    const file = path.resolve(root, pathname === '/' ? 'index.html' : pathname.replace(/^\/+/, ''));
    if (!file.startsWith(path.resolve(root))) {
      response.writeHead(403).end();
      return;
    }
    fs.readFile(file, (error, data) => {
      if (error) {
        response.writeHead(404).end();
        return;
      }
      response.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' }).end(data);
    });
  });
  return new Promise((done, fail) => {
    server.once('error', fail);
    server.listen(port, () => done(server));
  });
}

/** Builds and serves the static build when the url answers nothing, so the cold-start check is the same command. */
async function selfServe(base) {
  const port = Number(new URL(base).port || 80);
  if (!fs.existsSync(path.join(STATIC_DIR, 'index.json'))) {
    console.log('no storybook-static build found, running `npm run build-storybook` first');
    execFileSync('npm', ['run', 'build-storybook'], { cwd: __dirname, stdio: 'inherit' });
  }
  const server = await serveStatic(STATIC_DIR, port);
  console.log(`nothing answered on ${base} — serving storybook-static on port ${port}`);
  return server;
}

const previewFrame = (page) =>
  page.frames().find((f) => f.url().includes('iframe.html')) || null;

async function waitForPreview(page) {
  for (let i = 0; i < 40; i++) {
    const f = previewFrame(page);
    if (f) {
      const root = await f.$('#storybook-root');
      if (root) return f;
    }
    await page.waitForTimeout(250);
  }
  return null;
}

(async () => {
  const ownServer = (await isUp(BASE)) ? null : await selfServe(BASE);
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const errors = [];
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(`[console] ${m.text()}`);
  });
  page.on('pageerror', (e) => errors.push(`[pageerror] ${e.message}`));

  await page.goto(BASE, { waitUntil: 'networkidle' });
  await page.waitForSelector('#storybook-explorer-tree', { timeout: 30000 });

  const links = await page.$$eval('#storybook-explorer-tree a', (as) =>
    as.map((a) => ({ href: a.href, text: a.textContent.trim() })).filter((l) => l.href.includes('/story/') && !l.href.includes('#') && !/^skip/i.test(l.text)),
  );

  const fromIndex = async () => {
    const response = await page.request.get(BASE.replace(/\/$/, '') + '/index.json');
    if (!response.ok()) return [];
    const index = await response.json();
    return Object.values(index.entries)
      .filter((entry) => entry.type === 'story')
      .map((entry) => ({ name: `${entry.title} › ${entry.name}`, href: `${BASE.replace(/\/$/, '')}/?path=/story/${entry.id}` }));
  };

  const stories = await fromIndex();
  console.log(`sidebar exposes ${links.length} links (collapsed groups hide the rest); index.json lists ${stories.length} stories`);
  if (!stories.length) {
    console.log('no stories found in index.json');
    await browser.close();
    process.exit(1);
  }
  if (!links.length) console.log('warning: the sidebar rendered no story link, checking index.json only');

  console.log(`checking ${stories.length} stories on ${BASE}`);
  const failed = [];

  for (const link of stories) {
    const before = errors.length;
    await page.goto(link.href, { waitUntil: 'domcontentloaded', timeout: 20000 });
    const frame = await waitForPreview(page);
    const len = frame ? (await frame.$eval('#storybook-root', (el) => el.innerHTML.trim().length)) : 0;
    const newErrors = errors.slice(before);
    const ok = len > 0 && newErrors.length === 0;
    const label = link.name ?? link.text;
    console.log(`  ${ok ? 'PASS' : 'FAIL'} ${label} (root ${len} chars${newErrors.length ? ', ' + newErrors.length + ' errors' : ''})`);
    if (!ok) failed.push({ name: label, href: link.href, len, errors: newErrors });
  }

  let flowFailed = [];
  if (FLOW) {
    const { flows } = JSON.parse(fs.readFileSync(FLOW, 'utf8'));
    for (const flow of flows) {
      console.log(`flow ${flow.name || flow.start}`);
      await page.goto(`${BASE}/?path=/story/${flow.start}`, { waitUntil: 'domcontentloaded' });
      await waitForPreview(page);
      for (const [i, step] of flow.steps.entries()) {
        const frame = await waitForPreview(page);
        const el = frame ? await frame.$(step.click) : null;
        if (!el) {
          flowFailed.push(`${flow.start} step ${i + 1}: selector ${step.click} not found`);
          break;
        }
        await el.click();
        await page.waitForTimeout(400);
        if (step.expect) {
          const url = page.url();
          const ok = url.includes(step.expect);
          console.log(`  ${ok ? 'PASS' : 'FAIL'} ${step.click} -> ${url}`);
          if (!ok) flowFailed.push(`${flow.start} step ${i + 1}: expected ${step.expect}, got ${url}`);
        }
      }
    }
  }

  await browser.close();
  if (ownServer) ownServer.close();
  const bad = failed.length + flowFailed.length;
  console.log(bad ? `\nQUALITY GATE FAILED (${bad})` : '\nQUALITY GATE PASSED');
  flowFailed.forEach((f) => console.log('  ' + f));
  process.exit(bad ? 1 : 0);
})();
