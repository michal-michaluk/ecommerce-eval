// Shared low-fi markup for the basket prototype (spec element 01 — Basket view).
// No business logic: every story owns its data, this module only renders and navigates.

const CSS = `
  :root { --ink:#1b1f24; --muted:#5c6672; --line:#d8dee6; --bg:#f6f7f9; --ok:#1f7a45; --bad:#b3261e; --warn:#8a5a00 }
  * { box-sizing:border-box }
  body { margin:0; font:14px/1.45 -apple-system,Segoe UI,Roboto,sans-serif; color:var(--ink); background:var(--bg) }
  header.app { display:flex; gap:12px; align-items:baseline; padding:10px 18px; background:#fff; border-bottom:1px solid var(--line) }
  .proto { display:flex; gap:14px; align-items:center; flex-wrap:wrap; padding:6px 18px; background:#fffbe8; border-bottom:1px dashed #d9c88a; font-size:12px }
  .proto b { color:var(--warn); text-transform:uppercase; letter-spacing:.04em }
  .proto a { color:var(--ink); text-decoration:none; border-bottom:1px dotted var(--muted) }
  .proto a[aria-current] { font-weight:600; border-bottom-style:solid }
  main { padding:18px; max-width:1180px; display:grid; gap:18px }
  main.two { grid-template-columns:1fr 340px; align-items:start }
  .card { background:#fff; border:1px solid var(--line); border-radius:8px; padding:14px }
  h1 { font-size:20px; margin:0 0 10px }
  h2 { font-size:12px; text-transform:uppercase; letter-spacing:.05em; color:var(--muted); margin:0 0 8px }
  .toolbar { display:flex; gap:12px; align-items:center; padding-bottom:10px; border-bottom:1px solid var(--line); margin-bottom:12px; font-size:13px }
  .toolbar > :last-child { margin-left:auto }
  .check { display:inline-flex; gap:8px; align-items:center; font-size:13px; cursor:pointer }
  input[type=checkbox] { width:16px; height:16px; margin:0; accent-color:var(--ink) }
  .ship { border:1px solid var(--line); border-radius:8px; margin-bottom:12px }
  .ship > header { display:flex; gap:12px; align-items:center; padding:9px 12px; background:#fbfcfd; border-bottom:1px solid var(--line) }
  .ship .total { margin-left:auto; font-size:11px; color:var(--muted); text-transform:uppercase; letter-spacing:.04em }
  .ship .total b { font-size:15px; color:var(--ink); margin-left:6px; letter-spacing:0; text-transform:none }
  .estimate { padding:7px 12px; border-bottom:1px dashed var(--line); font-size:12px; color:var(--muted) }
  .item { display:grid; grid-template-columns:18px 64px 1fr; gap:12px; padding:12px; border-bottom:1px solid var(--line) }
  .item:last-child { border-bottom:0 }
  .item.unavailable { background:#fafbfc; color:var(--muted) }
  .item.unavailable h3, .item.unavailable .price, .item.unavailable .thumb { opacity:.55 }
  .thumb { width:64px; height:64px; border:1px dashed var(--line); border-radius:6px; display:grid; place-items:center; font-size:10px; line-height:1.2; color:var(--muted); text-align:center }
  .item h3 { font-size:13px; font-weight:500; margin:0 0 6px }
  .attrs { list-style:none; margin:0 0 8px; padding:0; font-size:12px; color:var(--muted) }
  .flags { display:flex; gap:8px; flex-wrap:wrap; margin-bottom:8px }
  .flag { font-size:11px; border-radius:10px; padding:1px 8px; border:1px solid var(--line); background:#f2f4f7; white-space:nowrap }
  .flag.bad { color:var(--bad); border-color:#f0c2c0; background:#fdf5f5 }
  .flag.warn { color:var(--warn); border-color:#e6d6a8; background:#fdf8ec }
  .foot { display:flex; align-items:center; gap:14px }
  .qty { display:inline-flex; align-items:center; border:1px solid var(--line); border-radius:6px; overflow:hidden }
  .qty button { border:0; border-radius:0; padding:2px 8px; background:#fff }
  .qty span { padding:2px 12px; border-left:1px solid var(--line); border-right:1px solid var(--line); font-size:13px }
  .price { margin-left:auto; font-weight:600; white-space:nowrap }
  .trash { border:0; background:none; padding:4px; color:var(--muted); cursor:pointer; display:inline-flex }
  .hint { display:flex; gap:12px; align-items:center; font-size:13px; color:var(--muted) }
  .hint > :last-child { margin-left:auto }
  .summary { display:grid; gap:18px; align-content:start }
  .act { display:grid; gap:3px }
  .summary .line { display:flex; justify-content:space-between; gap:10px; padding:6px 0; font-size:13px }
  .summary .line.discount b { color:var(--ok) }
  .summary .line.total { border-top:1px solid var(--line); margin-top:6px; padding-top:10px; font-size:15px; font-weight:600 }
  .cta { display:grid; gap:8px; margin-top:14px; justify-items:stretch }
  .cta button.primary { padding:11px 12px; text-transform:uppercase; letter-spacing:.03em; font-size:13px }
  .cta a { text-align:center; font-size:12px }
  .tiles { display:grid; gap:18px }
  .tile { display:grid; gap:6px; font-size:13px }
  .tile .body { font-size:12px; color:var(--muted) }
  .tile button { justify-self:start }
  button { font:inherit; padding:7px 12px; border-radius:6px; border:1px solid var(--line); background:#fff; cursor:pointer }
  button.primary { background:var(--ink); color:#fff; border-color:var(--ink) }
  button[disabled] { opacity:.45; cursor:not-allowed }
  a { color:var(--ink) }
  .why { font-size:11px; color:var(--muted); max-width:320px }
  .assume { font-size:11px; color:var(--warn); border:1px dashed #e6d6a8; border-radius:4px; padding:0 4px; white-space:nowrap }
  .empty { padding:32px 12px; display:grid; gap:14px; justify-items:center; text-align:center }
  .empty p { margin:0; font-size:15px }
  .empty a { font-size:13px }
  .note { grid-column:1/-1; font-size:12px; color:var(--muted); border-top:1px dashed var(--line); padding-top:10px; margin:0 }
`;

const STORIES = {
  twoItems: 'basket-two-items--default',
  empty: 'basket-empty--default',
  partial: 'basket-partial-selection--default',
  outOfStock: 'basket-out-of-stock--default',
  unavailable: 'basket-unavailable--default',
  deliveryEstimate: 'basket-delivery-estimate--default',
  blocked: 'basket-blocked--default',
  twoItemsRemoved: 'basket-two-items-removed',
};

const esc = (s) =>
  String(s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);

// PLN, 2 decimals, pl-PL format (spec index.md §Properties, D14 at the presentation boundary).
const pln = (amount) => new Intl.NumberFormat('pl-PL', { style: 'currency', currency: 'PLN' }).format(amount);

const assume = '<span class="assume">(assumption)</span>';

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

// One delegated listener per canvas: a disabled control never fires and never navigates.
function mount(canvas, html) {
  canvas.innerHTML = `<style>${CSS}</style>` + html;
  canvas.querySelectorAll('[data-indeterminate]').forEach((el) => (el.indeterminate = true));
  canvas.addEventListener('click', (e) => {
    const el = e.target.closest('[data-go]');
    if (!el) return;
    e.preventDefault();
    if (el.disabled || el.getAttribute('aria-disabled') === 'true') return;
    go(el.dataset.go);
  });
  return canvas;
}

function button({ id = '', label, go: target, primary, disabled, reason, reasonAssumption }) {
  const attrs = [
    id ? `id="${id}"` : '',
    primary && !disabled ? 'class="primary"' : '',
    disabled ? 'disabled' : '',
    target && !disabled ? `data-go="${target}"` : '',
  ]
    .filter(Boolean)
    .join(' ');
  const why = disabled && reason ? `<span class="why">${esc(reason)}${reasonAssumption ? ' ' + assume : ''}</span>` : '';
  return why ? `<span class="act">${`<button ${attrs}>${esc(label)}</button>`}${why}</span>` : `<button ${attrs}>${esc(label)}</button>`;
}

const link = (label, target, id = '') =>
  `<a href="#" ${id ? `id="${id}"` : ''} data-go="${target}">${esc(label)}</a>`;

function checkbox({ id = '', label, checked, indeterminate, disabled, go: target }) {
  const attrs = [
    'type="checkbox"',
    id ? `id="${id}"` : '',
    checked ? 'checked' : '',
    indeterminate ? 'data-indeterminate' : '',
    disabled ? 'disabled' : '',
    target && !disabled ? `data-go="${target}"` : '',
  ]
    .filter(Boolean)
    .join(' ');
  const box = `<input ${attrs}>`;
  return label ? `<label class="check">${box}<span>${esc(label)}</span></label>` : box;
}

const stepper = ({ value, decreaseDisabled }) => `
  <span class="qty">
    <button ${decreaseDisabled ? 'disabled' : ''} aria-label="zmniejsz ilość">−</button><span>${esc(value)}</span><button aria-label="zwiększ ilość">+</button>
  </span>`;

// Prototype chrome only (never app chrome): the eight states are the same view at eight moments, and switching
// between them is not an action of the view — it is a prototype affordance and is labelled as one.
const PROTO_STATES = [
  ['two items', STORIES.twoItems, 'proto-two-items'],
  ['empty', STORIES.empty, 'proto-empty'],
  ['partial selection', STORIES.partial, 'proto-partial'],
  ['out of stock', STORIES.outOfStock, 'proto-out-of-stock'],
  ['unavailable', STORIES.unavailable, 'proto-unavailable'],
  ['delivery estimate', STORIES.deliveryEstimate, 'proto-delivery-estimate'],
  ['blocked', STORIES.blocked, 'proto-blocked'],
  ['two items removed', STORIES.twoItemsRemoved, 'proto-removed'],
];

const protoNav = (currentStory) => `
  <div class="proto">
    <b>low-fi prototype</b><span>no business logic · no backend · spec artifact</span>
    ${PROTO_STATES.map(
      ([label, id, anchor]) =>
        `<a href="#" id="${anchor}" data-go="${id}"${currentStory === id ? ' aria-current="page"' : ''}>${label}</a>`,
    ).join('')}
  </div>`;

const page = ({ body, currentStory, note, wide }) => `
  <header class="app">
    <strong>Koszyk</strong><span class="muted">basket view — spec element 01</span>
  </header>
  ${protoNav(currentStory)}
  <main${wide ? '' : ' class="two"'}>${body}<p class="note">${esc(note)}</p></main>`;

export { STORIES, esc, pln, assume, go, mount, button, link, checkbox, stepper, page, protoNav };
