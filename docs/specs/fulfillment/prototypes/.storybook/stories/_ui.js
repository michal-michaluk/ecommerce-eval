// Shared low-fi markup for the fulfillment prototype (spec views 15-18).
// No business logic: every story owns its data, this module only renders and navigates.

const CSS = `
  :root { --ink:#1b1f24; --muted:#5c6672; --line:#d8dee6; --bg:#f6f7f9; --ok:#1f7a45; --bad:#b3261e; --warn:#8a5a00 }
  * { box-sizing:border-box }
  body { margin:0; font:14px/1.45 -apple-system,Segoe UI,Roboto,sans-serif; color:var(--ink); background:var(--bg) }
  header.app { display:flex; gap:12px; align-items:baseline; padding:10px 18px; background:#fff; border-bottom:1px solid var(--line) }
  header.app .crumbs { color:var(--muted) }
  header.app .actor { margin-left:auto; font-size:12px; color:var(--muted) }
  .proto { display:flex; gap:14px; align-items:center; padding:6px 18px; background:#fffbe8; border-bottom:1px dashed #d9c88a; font-size:12px }
  .proto b { color:var(--warn); text-transform:uppercase; letter-spacing:.04em }
  .proto a { color:var(--ink); text-decoration:none; border-bottom:1px dotted var(--muted) }
  .proto a[aria-current] { font-weight:600; border-bottom-style:solid }
  main { padding:18px; max-width:1280px; display:grid; gap:18px }
  main.two { grid-template-columns:1fr 340px; align-items:start }
  .card { background:#fff; border:1px solid var(--line); border-radius:8px; padding:14px }
  h1 { font-size:18px; margin:0 0 10px }
  h2 { font-size:12px; text-transform:uppercase; letter-spacing:.05em; color:var(--muted); margin:0 0 8px }
  table { width:100%; border-collapse:collapse; font-size:13px }
  th { text-align:left; font-weight:600; color:var(--muted); border-bottom:1px solid var(--line); padding:6px 8px; white-space:nowrap }
  td { border-bottom:1px solid var(--line); padding:7px 8px; vertical-align:top }
  tr:last-child td { border-bottom:0 }
  tr.mine td { background:#f7fbf8 }
  tr.confirmed td { color:var(--muted) }
  tr.confirmed td { color:var(--muted) }
  .muted { color:var(--muted) }
  .empty { color:var(--muted); font-style:italic; padding:14px 8px }
  .waiting { padding:14px 8px; color:var(--muted) }
  .pill { display:inline-block; padding:1px 8px; border-radius:10px; border:1px solid var(--line); background:#f2f4f7; font-size:12px; white-space:nowrap }
  .pill.assigned, .pill.picking { background:#eef4ff; border-color:#c4d6f5 }
  .pill.packed, .pill.readyForDelivery { background:#eef8f1; border-color:#bfe0cc }
  .pill.onHold { background:#fdeeee; border-color:#f0c2c0; color:var(--bad) }
  .pill.hold::after { content:" ⏸ hold" }
  .pill .word { color:var(--muted) }
  .actions { display:flex; gap:10px; flex-wrap:wrap; margin-top:14px; align-items:flex-start }
  .act { display:inline-flex; flex-direction:column; gap:3px; align-items:flex-start }
  .why { font-size:11px; color:var(--muted); max-width:230px }
  button { font:inherit; padding:7px 12px; border-radius:6px; border:1px solid var(--line); background:#fff; cursor:pointer }
  button.primary { background:var(--ink); color:#fff; border-color:var(--ink) }
  button[disabled] { opacity:.45; cursor:not-allowed }
  input[type=text] { font:inherit; padding:8px 10px; border:1px solid var(--ink); border-radius:6px; width:280px }
  input[type=text][disabled] { border-color:var(--line); background:#f2f4f7; color:var(--muted); cursor:not-allowed }
  .row { display:grid; grid-template-columns:150px 1fr; gap:10px; padding:4px 0; border-bottom:1px dashed var(--line) }
  .row:last-child { border-bottom:0 }
  .row .k { color:var(--muted) }
  ol.timeline { list-style:none; margin:0 0 14px; padding:0 }
  ol.timeline li { padding:6px 0 6px 26px; position:relative; border-left:2px solid var(--line); margin-left:8px }
  ol.timeline li::before { content:""; position:absolute; left:-7px; top:12px; width:10px; height:10px; border-radius:50%; background:#fff; border:2px solid var(--line) }
  ol.timeline li.done { border-color:var(--ok) }
  ol.timeline li.done::before { background:var(--ok); border-color:var(--ok) }
  ol.timeline li.current { font-weight:600 }
  ol.timeline li.current::before { background:var(--warn); border-color:var(--warn) }
  ol.timeline li.future { color:var(--muted) }
  ol.timeline li .at { color:var(--muted); font-weight:400; font-size:12px; margin-left:8px }
  .note { font-size:12px; color:var(--muted); margin-top:10px }
  .row .note { margin-top:2px; font-style:italic }
  .banner { padding:8px 12px; border:1px dashed var(--line); border-radius:6px; background:#fbfcfd; font-size:13px; margin-bottom:12px }
  .banner.bad { border-color:#f0c2c0; background:#fdf5f5; color:var(--bad) }
  .group { margin-bottom:16px }
  .group h3 { font-size:13px; margin:0 0 6px; display:flex; gap:10px; align-items:baseline }
  .loc { font-family:ui-monospace,SFMono-Regular,Menlo,monospace; font-size:12px }
`;

const STORIES = {
  ordersPending: 'fulfillment-orders-list--pending',
  ordersPicking: 'fulfillment-orders-list--picking',
  ordersOnHold: 'fulfillment-orders-list--on-hold',
  ordersEmpty: 'fulfillment-orders-list--empty',
  ordersLoading: 'fulfillment-orders-list--loading',
  ordersError: 'fulfillment-orders-list--error',
  pickScanIdle: 'fulfillment-pick-workstation--scan-idle',
  pickPositionPicked: 'fulfillment-pick-workstation--position-picked',
  pickMixed: 'fulfillment-pick-workstation--mixed',
  pickAllPacked: 'fulfillment-pick-workstation--all-packed',
  pickBlockedOnHold: 'fulfillment-pick-workstation--blocked-on-hold',
  issuesOpen: 'fulfillment-issue-dashboard--open-issues',
  issuesEmpty: 'fulfillment-issue-dashboard--empty',
  issuesError: 'fulfillment-issue-dashboard--error',
  customerReady: 'fulfillment-customer-state--ready-for-delivery',
  customerInTransit: 'fulfillment-customer-state--in-transit',
  customerHeld: 'fulfillment-customer-state--held',
  customerError: 'fulfillment-customer-state--error',
};

const esc = (s) =>
  String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

// Storybook 8 manager URL form: ?path=/story/<id>. Absolute, because a relative URL assigned from inside the
// preview iframe resolves against iframe.html and would drop the manager chrome (sidebar) entirely.
function go(storyId) {
  const url = window.location.origin + '/?path=/story/' + storyId;
  try {
    window.parent.location.href = url;
  } catch (e) {
    window.location.href = url;
  }
}

// One delegated listener per canvas: a disabled button never fires and never navigates.
function mount(canvas, html) {
  canvas.innerHTML = `<style>${CSS}</style>` + html;
  canvas.addEventListener('click', (e) => {
    const el = e.target.closest('[data-go]');
    if (!el) return;
    e.preventDefault();
    if (el.disabled || el.getAttribute('aria-disabled') === 'true') return;
    go(el.dataset.go);
  });
  return canvas;
}

const cell = (v) => (v && v.html !== undefined ? v.html : esc(v));

function table(columns, rows, placeholder) {
  const head = `<tr>${columns.map((c) => `<th>${esc(c)}</th>`).join('')}</tr>`;
  const body = rows.length
    ? rows.map((r) => `<tr${r.tone ? ` class="${r.tone}"` : ''}>${r.cells.map((c) => `<td>${cell(c)}</td>`).join('')}</tr>`).join('')
    : `<tr><td colspan="${columns.length}" class="empty">${esc(placeholder || 'No rows.')}</td></tr>`;
  return `<table><thead>${head}</thead><tbody>${body}</tbody></table>`;
}

function pill(state, opts = {}) {
  const word = opts.word ? ` <span class="word">— ${esc(opts.word)}</span>` : '';
  const hold = opts.hold ? ' hold' : '';
  const inner = `${esc(state)}${word}`;
  return opts.go
    ? `<a class="pill ${esc(state)}${hold}" href="#" data-go="${opts.go}">${inner}</a>`
    : `<span class="pill ${esc(state)}${hold}">${inner}</span>`;
}

function button({ id = '', label, go: target, primary, disabled, reason }) {
  const attrs = [
    id ? `id="${id}"` : '',
    primary && !disabled ? 'class="primary"' : '',
    disabled ? 'disabled' : '',
    target && !disabled ? `data-go="${target}"` : '',
  ]
    .filter(Boolean)
    .join(' ');
  const b = `<button ${attrs}>${esc(label)}</button>`;
  return disabled && reason ? `<span class="act">${b}<span class="why">${esc(reason)}</span></span>` : `<span class="act">${b}</span>`;
}

const link = (label, target, id = '') =>
  `<a href="#" ${id ? `id="${id}"` : ''} data-go="${target}">${esc(label)}</a>`;

// Prototype chrome only (never app chrome): the four views belong to three different actors, so switching
// surface is not an action of any view — it is a prototype affordance and is labelled as one.
const PROTO_VIEWS = [
  ['Orders list (15)', STORIES.ordersPending, 'proto-orders'],
  ['Pick workstation (16)', STORIES.pickScanIdle, 'proto-pick'],
  ['Issues (17)', STORIES.issuesOpen, 'proto-issues'],
  ['Customer (18)', STORIES.customerReady, 'proto-customer'],
];

const protoNav = (currentStory) => `
  <div class="proto">
    <b>low-fi prototype</b><span>no business logic · no backend · spec artifact</span>
    ${PROTO_VIEWS.map(
      ([label, id, anchor]) =>
        `<a href="#" id="${anchor}" data-go="${id}"${currentStory.startsWith(id.slice(0, id.lastIndexOf('--'))) ? ' aria-current="page"' : ''}>${label}</a>`,
    ).join('')}
  </div>`;

const page = ({ crumbs, actor, access, body, currentStory, wide }) => `
  <header class="app">
    <strong>fulfillment</strong><span class="crumbs">${esc(crumbs)}</span>
    <span class="actor">signed in: <b>${esc(actor)}</b> · ${esc(access)}</span>
  </header>
  ${protoNav(currentStory)}
  <main${wide ? '' : ' class="two"'}>${body}</main>`;

export { STORIES, esc, go, mount, table, pill, button, link, page, protoNav, cell };
