// Storybook quality gate for Storybook 8, adapted from specification/references/verify-storybook.cdp.js.
// The upstream script reads `#storybook-root` from the manager page, but Storybook 8 renders the preview in an
// <iframe>, so every story reports "empty". This version resolves the preview frame first and keeps the same
// checks: every sidebar story renders content and produces no console error / page error.
//
// Usage: node verify-storybook.frames.cjs [url] [--flow flows.json]
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const URL = process.argv[2] && !process.argv[2].startsWith('--') ? process.argv[2] : 'http://localhost:6010';
const flowIdx = process.argv.indexOf('--flow');
const FLOW = flowIdx !== -1 && process.argv[flowIdx + 1] ? path.resolve(process.argv[flowIdx + 1]) : null;

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
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const errors = [];
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(`[console] ${m.text()}`);
  });
  page.on('pageerror', (e) => errors.push(`[pageerror] ${e.message}`));

  await page.goto(URL, { waitUntil: 'networkidle' });
  await page.waitForSelector('#storybook-explorer-tree', { timeout: 30000 });

  const links = await page.$$eval('#storybook-explorer-tree a', (as) =>
    as.map((a) => ({ href: a.href, text: a.textContent.trim() })).filter((l) => l.href.includes('/story/') && !l.href.includes('#') && !/^skip/i.test(l.text)),
  );

  if (!links.length) {
    console.log('no story links found in the sidebar');
    await browser.close();
    process.exit(1);
  }

  console.log(`checking ${links.length} stories on ${URL}`);
  const failed = [];

  for (const link of links) {
    const before = errors.length;
    await page.goto(link.href, { waitUntil: 'networkidle', timeout: 20000 });
    const frame = await waitForPreview(page);
    const len = frame ? (await frame.$eval('#storybook-root', (el) => el.innerHTML.trim().length)) : 0;
    const newErrors = errors.slice(before);
    const ok = len > 0 && newErrors.length === 0;
    console.log(`  ${ok ? 'PASS' : 'FAIL'} ${link.text} (root ${len} chars${newErrors.length ? ', ' + newErrors.length + ' errors' : ''})`);
    if (!ok) failed.push({ name: link.text, href: link.href, len, errors: newErrors });
  }

  let flowFailed = [];
  if (FLOW) {
    const { flows } = JSON.parse(fs.readFileSync(FLOW, 'utf8'));
    for (const flow of flows) {
      console.log(`flow ${flow.name || flow.start}`);
      await page.goto(`${URL}/?path=/story/${flow.start}`, { waitUntil: 'networkidle' });
      await waitForPreview(page);
      for (const [i, step] of flow.steps.entries()) {
        const frame = await waitForPreview(page);
        const el = frame ? await frame.$(step.click) : null;
        if (!el) {
          flowFailed.push(`${flow.start} step ${i + 1}: selector ${step.click} not found`);
          break;
        }
        await el.click();
        await page.waitForTimeout(700);
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
  const bad = failed.length + flowFailed.length;
  console.log(bad ? `\nQUALITY GATE FAILED (${bad})` : '\nQUALITY GATE PASSED');
  flowFailed.forEach((f) => console.log('  ' + f));
  process.exit(bad ? 1 : 0);
})();
