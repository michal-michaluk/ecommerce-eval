// Captures one .png per story into ../shots/ (durable spec artifacts, versioned next to the spec).
// Usage: node shoot.cjs [url] [outDir]   — defaults: http://localhost:6011 and ../shots
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const URL = process.argv[2] || 'http://localhost:6011';
const OUT = path.resolve(process.argv[3] || path.join(__dirname, '..', 'shots'));

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const index = await (await fetch(`${URL}/index.json`)).json();
  const entries = Object.keys(index.entries);

  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
  for (const id of entries) {
    await page.goto(`${URL}/iframe.html?id=${id}&viewMode=story`, { waitUntil: 'networkidle' });
    await page.waitForSelector('#storybook-root > *', { timeout: 15000 });
    const file = path.join(OUT, `${id}.png`);
    await page.screenshot({ path: file, fullPage: true });
    console.log(`shot ${path.basename(file)}`);
  }
  await browser.close();
  console.log(`\n${entries.length} screenshots -> ${OUT}`);
})();
