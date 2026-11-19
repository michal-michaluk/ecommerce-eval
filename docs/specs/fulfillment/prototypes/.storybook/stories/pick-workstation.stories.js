// Element 16 — PickWorkstation. Two regions on one screen (W1): positions + processing state.
// Defaults marked in 16-pick-workstation.md: keyboard-emulating scan field, next-position hint, confirmed rows stay
// visible and greyed, both fallback and happy-path actions always visible. No money, no line editing, no invented status.
import { STORIES, esc, mount, table, pill, button, page } from './_ui.js';

const ME = 'employee-42';
const COLUMNS = ['#', 'Product', 'Qty to take', 'Location(s)', 'Position state'];

// PS1 (before a scan) has no name in any source (R-16-1) — "not scanned" is invented vocabulary, kept internal.
const positionState = (p) => {
  if (p.state === 'picked' || p.state === 'packed') return `<span class="pill ${p.state}">${p.state}</span>`;
  return p.next ? '<span class="pill picking">next to scan</span> not scanned' : '<span class="muted">not scanned</span>';
};

const positions = (list) =>
  list.map((p) => ({
    tone: p.state === 'picked' || p.state === 'packed' ? 'confirmed' : undefined,
    cells: [
      p.index,
      { html: `${esc(p.name)} <span class="muted">· ${esc(p.productNumber)}</span>` },
      String(p.quantity),
      { html: p.locations.map((l) => `<span class="loc">${esc(l)}</span>`).join(' · ') },
      { html: positionState(p) },
    ],
  }));

const view = (d) => `
  <section class="card">
    <h1>Pick list — ${esc(d.order.processingId)}</h1>
    ${d.banner ? `<div class="banner${d.bannerBad ? ' bad' : ''}">${esc(d.banner)}</div>` : ''}
    <div class="row"><span class="k">Scan position</span><span>
      <input type="text" id="scan-input" placeholder="${d.scanEnabled ? 'scan a position barcode, press Enter' : 'scanning refused'}" ${d.scanEnabled ? 'autofocus' : 'disabled'} />
      <span class="why">keyboard-emulating scanner — no driver, no handheld described in any source (W4)</span>
    </span></div>
    <div style="margin-top:12px">${table(COLUMNS, positions(d.positions))}</div>
    <p class="note">${esc(d.note)}</p>
  </section>

  <aside class="card">
    <h2>Order processing state</h2>
    <div class="row"><span class="k">State</span><span>${pill(d.order.state, { hold: d.order.hold })}</span></div>
    <div class="row"><span class="k">Positions packed</span><span>${d.order.packed}/${d.order.total} packed</span></div>
    <div class="row"><span class="k">Assignee</span><span>${esc(d.order.assignee)}</span></div>
    ${d.issue ? `<div class="row"><span class="k">Open issue</span><span>${esc(d.issue.kind)} · reported by ${esc(d.issue.reportedBy)}</span></div>` : ''}
    <h2 style="margin-top:16px">Actions</h2>
    <div class="actions">
      ${button(d.actions.scan)}
      ${button(d.actions.pack)}
      ${button(d.actions.ready)}
      ${button(d.actions.report)}
    </div>
    <p class="note">${esc(d.actionsNote)}</p>
  </aside>`;

function render(args) {
  const canvas = mount(
    document.createElement('div'),
    page({
      crumbs: `/ fulfillment / orders / ${args.order.processingId} / pick workstation`,
      actor: args.actor,
      access: args.access,
      currentStory: args.currentStory,
      body: view(args),
    }),
  );
  const input = canvas.querySelector('#scan-input');
  if (input && !input.disabled) {
    input.addEventListener('keydown', (e) => {
      if (e.key !== 'Enter') return;
      e.preventDefault();
      canvas.querySelector('#scan').click();
    });
  }
  return canvas;
}

export default {
  title: 'Fulfillment/Pick Workstation',
  render,
};

const base = {
  actor: ME,
  access: 'assignee only — no picker role exists (F3, 16 permissions)',
  issue: null,
  actionsNote:
    'Both the happy path and the fallback stay on screen at every moment (16, [seg 139]). ' +
    'One scan confirms one position, the bulk action packs picked positions — the Q-16-1 default (R-A).',
};

// Element 02's example instance for OP-2026-0001: one position of product 75423, quantity 1, assignee employee-42.
const POSITION = {
  index: 1,
  productNumber: '75423',
  name: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a",
  quantity: 1,
  locations: ['A-01-03'],
};

export const ScanIdle = {
  args: {
    ...base,
    currentStory: STORIES.pickScanIdle,
    banner: 'Order is assigned, nothing scanned yet. The first confirmation is what moves the order to picking (02 Q-02-1).',
    order: { processingId: 'OP-2026-0001', state: 'assigned', assignee: ME, packed: 0, total: 1 },
    positions: [{ ...POSITION, state: 'not scanned', next: true }],
    scanEnabled: true,
    note: 'One scan confirms one position; the walk order is advisory, not enforced — an out-of-order scan is accepted (KB pick-list.md:32-42).',
    actions: {
      scan: { id: 'scan', label: 'scan packed product', primary: true, go: STORIES.pickPositionPicked },
      pack: { id: 'pack', label: 'mark picked positions packed', disabled: true, reason: 'no positions picked yet' },
      ready: { id: 'ready', label: 'mark done / ready for delivery', disabled: true, reason: '0 of 1 positions packed (08 guard)' },
      report: { id: 'report', label: 'report a problem', disabled: true, reason: 'picking has not started — the machine has no hold edge from assigned (02)' },
    },
  },
};

export const PositionPicked = {
  args: {
    ...base,
    currentStory: STORIES.pickPositionPicked,
    banner: 'One position confirmed → the order moved to picking. The row stays visible and greyed, never removed (W2).',
    order: { processingId: 'OP-2026-0001', state: 'picking', assignee: ME, packed: 0, total: 1 },
    positions: [{ ...POSITION, state: 'picked' }],
    scanEnabled: false,
    note: 'A duplicate scan of a confirmed position changes nothing — idempotency is mandatory (02 RULE-6) and no undo exists.',
    actions: {
      scan: { id: 'scan', label: 'scan packed product', disabled: true, reason: 'the only position is already confirmed — a duplicate scan is a no-op' },
      pack: { id: 'pack', label: 'mark picked positions packed', primary: true, go: STORIES.pickAllPacked },
      ready: { id: 'ready', label: 'mark done / ready for delivery', disabled: true, reason: '0 of 1 positions packed (08 guard)' },
      report: { id: 'report', label: 'report a problem', go: STORIES.pickBlockedOnHold },
    },
  },
};

// The only story where W2 is visible at once: packed, picked and not-yet-scanned positions on screen together.
export const Mixed = {
  args: {
    ...base,
    currentStory: STORIES.pickMixed,
    banner: 'Four positions, three states at once: two packed, one picked, one not scanned. The confirmed rows stay visible and greyed (W2).',
    order: { processingId: 'OP-2026-0005', state: 'picking', assignee: ME, packed: 2, total: 4 },
    positions: [
      { index: 1, productNumber: '75423', name: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a", quantity: 1, locations: ['A-01-03'], state: 'packed' },
      { index: 2, productNumber: '75375', name: 'LEGO Star Wars Millennium Falcon', quantity: 2, locations: ['B-04-11'], state: 'packed' },
      { index: 3, productNumber: '75389', name: 'LEGO Star Wars Dark Falcon', quantity: 1, locations: ['A-02-07', 'B-04-12'], state: 'picked' },
      { index: 4, productNumber: '75401', name: 'LEGO Star Wars Ahsoka Tano’s Jedi Interceptor', quantity: 1, locations: ['C-09-02'], state: 'not scanned', next: true },
    ],
    scanEnabled: false,
    note:
      'Rows are in the calculation\'s walk order (P5) and two positions carry more than one location (P3). Product numbers ' +
      '75375 / 75389 / 75401 are placeholders — only 75423 exists in the materials (01 example). Confirmed rows are greyed, not removed (W2).',
    actions: {
      scan: { id: 'scan', label: 'scan packed product', disabled: true, reason: 'no further state in this gallery (prototype scope)' },
      pack: { id: 'pack', label: 'mark picked positions packed', disabled: true, reason: 'no further state in this gallery (prototype scope)' },
      ready: { id: 'ready', label: 'mark done / ready for delivery', disabled: true, reason: '2 of 4 positions not packed (08 guard)' },
      report: { id: 'report', label: 'report a problem', disabled: true, reason: 'no further state in this gallery (prototype scope)' },
    },
  },
};

export const AllPacked = {
  args: {
    ...base,
    currentStory: STORIES.pickAllPacked,
    banner: 'Every position packed → the order is packed. Completion is the employee declaration, not a computed consequence of scans (C4).',
    order: { processingId: 'OP-2026-0001', state: 'packed', assignee: ME, packed: 1, total: 1 },
    positions: [{ ...POSITION, state: 'packed' }],
    scanEnabled: false,
    note: 'mark done requests collection on the parcel as its second effect (08, [seg 122]) — the carrier has not confirmed anything yet (03).',
    actions: {
      scan: { id: 'scan', label: 'scan packed product', disabled: true, reason: 'every position is packed' },
      pack: { id: 'pack', label: 'mark picked positions packed', disabled: true, reason: 'no picked positions left' },
      ready: { id: 'ready', label: 'mark done / ready for delivery', primary: true, go: STORIES.customerReady },
      report: { id: 'report', label: 'report a problem', go: STORIES.pickBlockedOnHold },
    },
  },
};

export const BlockedOnHold = {
  args: {
    ...base,
    currentStory: STORIES.pickBlockedOnHold,
    banner: 'A hold refuses every confirmation and mark done (02 RULE-7). Resume is not on this screen and does not exist anywhere yet (17 Q-17-1).',
    bannerBad: true,
    order: { processingId: 'OP-2026-0001', state: 'onHold', hold: true, assignee: ME, packed: 1, total: 1 },
    positions: [{ ...POSITION, state: 'packed' }],
    issue: { kind: 'LOST', reportedBy: ME },
    scanEnabled: false,
    note: 'Issue kinds are an open taxonomy (lost / destroyed / stolen / wrong location, [seg 141] / [seg 150-151]); reporting is never a closed list.',
    actions: {
      scan: { id: 'scan', label: 'scan packed product', disabled: true, reason: 'hold active' },
      pack: { id: 'pack', label: 'mark picked positions packed', disabled: true, reason: 'hold active' },
      ready: { id: 'ready', label: 'mark done / ready for delivery', disabled: true, reason: 'issues remain — a hold blocks the 08 guard' },
      report: { id: 'report', label: 'report a problem', disabled: true, reason: 'no second-issue state in this gallery — whether a held order takes another issue is undecided (Q-17-1)' },
    },
  },
};
