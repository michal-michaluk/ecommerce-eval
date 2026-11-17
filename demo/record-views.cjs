// Views of 14, 15 and 16 recorded from the real storybook gallery, one frame at a time, with a real browser.
//
//   node demo/record-views.cjs demo/views.webm
//
// The gallery is the static build of services/catalog/.storybook (`npm run build-storybook`); this script serves it on
// 6011 and drives the three screens of the catalogue in one pass. Playwright records the browser window itself, so the
// video is a capture of rendered HTML, never a still image.
const http = require('http');
const fs = require('fs');
const path = require('path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const ROOT = path.resolve(__dirname, '../services/catalog/.storybook/storybook-static');
const PORT = 6011;
const OUT = path.resolve(process.argv[2] || path.join(__dirname, 'views.webm'));
const STOPS = [
  { id: 'catalog-draftdescriptionview--ready', hold: 3200 },
  { id: 'catalog-draftdescriptionview--in-progress', hold: 2600 },
  { id: 'catalog-pendingreviewsview--pending-list', hold: 3000 },
  { id: 'catalog-pendingreviewsview--review-with-author-override', hold: 2600 },
  { id: 'catalog-pricingview--scheduled-history', hold: 2800 },
  { id: 'catalog-pricingview--add-discount-fixed', hold: 3400 },
];

const MIME = { '.html': 'text/html', '.js': 'application/javascript', '.json': 'application/json', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.woff2': 'font/woff2', '.woff': 'font/woff', '.map': 'application/json' };

const server = http.createServer((request, response) => {
  const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
  const file = path.resolve(ROOT, pathname === '/' ? 'index.html' : pathname.replace(/^\/+/, ''));
  if (!file.startsWith(ROOT) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    response.writeHead(404).end();
    return;
  }
  response.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(response);
});

(async () => {
  await new Promise((resolve) => server.listen(PORT, resolve));
  const browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    recordVideo: { dir: path.dirname(OUT), size: { width: 1280, height: 800 } },
  });
  const page = await context.newPage();

  for (const stop of STOPS) {
    await page.goto(`http://localhost:${PORT}/iframe.html?id=${stop.id}&viewMode=story`, { waitUntil: 'networkidle' });
    await page.waitForTimeout(stop.hold);
  }

  const video = page.video();
  await context.close();
  await browser.close();
  await new Promise((resolve) => server.close(resolve));
  fs.renameSync(await video.path(), OUT);
  console.log(`views recorded to ${OUT}`);
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
