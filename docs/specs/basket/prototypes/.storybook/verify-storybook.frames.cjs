// Storybook quality gate for Storybook 8 — adapted from specification/references/verify-storybook.cdp.js.
//
// Two deltas vs. the upstream script, both proven necessary on Storybook 8.6:
//  1. the preview renders inside an <iframe>, so `#storybook-root` must be read from the preview frame
//     (upstream reads it from the manager page and reports every story as "empty");
//  2. the sidebar only renders the links of the expanded group, so story links are enumerated from the
//     built `/index.json` instead — that is the complete, deterministic list.
//
// Usage: node verify-storybook.frames.cjs [url] [--flow flows.json]
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const URL = process.argv[2] && !process.argv[2].startsWith('--') ? process.argv[2] : 'http://localhost:6012';
const flowIdx = process.argv.indexOf('--flow');
const FLOW = flowIdx !== -1 && process.argv[flowIdx + 1] ? path.resolve(process.argv[flowIdx + 1]) : null;

const previewFrame = (page) => page.frames().find((f) => f.url().includes('iframe.html')) || null;

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
  const index = await (await fetch(`${URL}/index.json`)).json();
  const stories = Object.entries(index.entries).map(([id, e]) => ({ id, name: `${e.title} / ${e.name}` }));

  const browser = await chromium.launch();
  const page = await browser.newPage();
  const errors = [];
  page.on('console', (m) => {
    if (m.type() === 'error') errors.push(`[console] ${m.text()}`);
  });
  page.on('pageerror', (e) => errors.push(`[pageerror] ${e.message}`));

  console.log(`checking ${stories.length} stories on ${URL}`);
  const failed = [];

  for (const story of stories) {
    const before = errors.length;
    await page.goto(`${URL}/?path=/story/${story.id}`, { waitUntil: 'networkidle', timeout: 20000 });
    const frame = await waitForPreview(page);
    const len = frame ? await frame.$eval('#storybook-root', (el) => el.innerHTML.trim().length) : 0;
    const newErrors = errors.slice(before);
    const ok = len > 0 && newErrors.length === 0;
    console.log(`  ${ok ? 'PASS' : 'FAIL'} ${story.id} (root ${len} chars${newErrors.length ? ', ' + newErrors.length + ' errors' : ''})`);
    if (!ok) failed.push({ id: story.id, len, errors: newErrors });
  }

  const flowFailed = [];
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
        const url = page.url();
        const stateOk = !step.expect || url.includes(step.expect);
        // the click must move the manager (sidebar keeps working), never escape into a standalone iframe.html
        const stayed = url.startsWith(`${URL}/?path=/story/`);
        console.log(`  ${stateOk && stayed ? 'PASS' : 'FAIL'} ${step.click} -> ${url}`);
        if (!stateOk) flowFailed.push(`${flow.start} step ${i + 1}: expected ${step.expect}, got ${url}`);
        if (!stayed) flowFailed.push(`${flow.start} step ${i + 1}: escaped the manager to ${url}`);
      }
    }
  }

  await browser.close();
  const bad = failed.length + flowFailed.length;
  console.log(bad ? `\nQUALITY GATE FAILED (${bad})` : '\nQUALITY GATE PASSED');
  failed.forEach((f) => console.log(`  ${f.id} root ${f.len} chars, ${f.errors.length} errors: ${f.errors[0] || ''}`));
  flowFailed.forEach((f) => console.log('  ' + f));
  process.exit(bad ? 1 : 0);
})();
