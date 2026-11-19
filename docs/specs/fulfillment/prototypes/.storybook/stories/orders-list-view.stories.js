// Element 15 — OrdersListView. Low-fi, thin: the story owns the rows, the renderer owns no state.
// Columns, actions and states follow 15-orders-list-view.md; every column is a marked default (D-UI2).
// No money column anywhere (spec.md D5). A blocked action always carries its reason.
import { STORIES, esc, mount, table, pill, button, page } from './_ui.js';

const ME = 'employee-42';

const COLUMNS = ['State', 'Processing reference', 'Recipient', 'Positions', 'Assignee', 'Placed at', 'Action'];

// 15 layout minimum: "one action per row … a row held by another employee shows the assignee instead of a button,
// never a disabled claim without a reason" — so a foreign assignment renders the holder, not a dead button.
const action = (a) => {
  if (a.kind === 'claim') return button({ id: a.id, label: 'claim', primary: true, go: a.target });
  if (a.kind === 'open') return button({ id: a.id, label: 'open', primary: true, go: a.target });
  return `<span class="act">${esc(a.holder)}<span class="why">${esc(a.reason)}</span></span>`;
};

const rows = (list) =>
  list.map((r) => ({
    tone: r.assignee === ME ? 'mine' : undefined,
    cells: [
      { html: pill(r.state, { hold: r.hold, word: r.statusWord }) },
      r.processingId,
      r.buyerName,
      `${r.packed}/${r.total} packed`,
      r.assignee ?? { html: '<span class="muted">unclaimed</span>' },
      r.placedAt,
      r.act ? { html: action(r.act) } : '',
    ],
  }));

const view = (d) => `
  ${d.banner ? `<div class="banner${d.bannerBad ? ' bad' : ''}">${esc(d.banner)}</div>` : ''}
  <section class="card">
    <h1>Current orders</h1>
    ${table(COLUMNS, rows(d.orders), d.placeholder)}
    <p class="note">${esc(d.note)}</p>
  </section>`;

function render(args) {
  return mount(
    document.createElement('div'),
    page({
      crumbs: '/ fulfillment / orders',
      actor: args.actor,
      access: args.access,
      currentStory: args.currentStory,
      wide: true,
      body: view(args),
    }),
  );
}

export default {
  title: 'Fulfillment/Orders List',
  render,
  argTypes: { actor: { control: 'text' }, banner: { control: 'text' } },
};

const base = {
  actor: ME,
  access: 'no role is defined — any authenticated warehouse user may claim (F3, Q-15-2)',
  note:
    'No filter, no search, no sort, no pagination, no counters — no criterion exists in any source (15 Q-15-1). ' +
    'A claim is granted or refused atomically, never queued (07). OP-2026-0001 and OP-2026-0002 are element 02\'s ' +
    'example ids, shown here at other moments of the same processing.',
};

// 02's own example instance: one position of quantity 3, all packed, assignee employee-17, on hold.
const HELD_BY_OTHER = {
  processingId: 'OP-2026-0002',
  state: 'onHold',
  hold: true,
  buyerName: 'Firma Testowa Sp. z o.o.',
  packed: 1,
  total: 1,
  assignee: 'employee-17',
  placedAt: '2026-11-19 10:02',
  act: { holder: 'employee-17', reason: 'held by employee-17 — a second claim is refused, never queued (07 RULE-1)' },
};

const ASSIGNED_TO_OTHER = {
  processingId: 'OP-2026-0002',
  state: 'assigned',
  buyerName: 'Firma Testowa Sp. z o.o.',
  packed: 0,
  total: 1,
  assignee: 'employee-17',
  placedAt: '2026-11-19 10:02',
  act: { holder: 'employee-17', reason: 'held by employee-17 — a second claim is refused, never queued (07 RULE-1)' },
};

export const Pending = {
  args: {
    ...base,
    currentStory: STORIES.ordersPending,
    banner: 'One order is unclaimed. Claiming it is the only write action on this screen.',
    orders: [
      {
        processingId: 'OP-2026-0001',
        state: 'pending',
        statusWord: 'pending',
        buyerName: 'Jan Kowalski',
        packed: 0,
        total: 1,
        assignee: null,
        placedAt: '2026-11-19 09:31',
        act: { kind: 'claim', id: 'claim', target: STORIES.ordersPicking },
      },
      ASSIGNED_TO_OTHER,
      {
        processingId: 'OP-2026-0003',
        state: 'pending',
        statusWord: 'pending',
        buyerName: 'Anna Nowak',
        packed: 0,
        total: 2,
        assignee: null,
        placedAt: '2026-11-19 09:58',
        act: { kind: 'claim', target: STORIES.ordersPicking },
      },
    ],
  },
};

export const Picking = {
  args: {
    ...base,
    currentStory: STORIES.ordersPicking,
    banner: 'After the claim the row is mine: it offers open, never claim. Nobody else can take it.',
    orders: [
      {
        processingId: 'OP-2026-0001',
        state: 'assigned',
        buyerName: 'Jan Kowalski',
        packed: 0,
        total: 1,
        assignee: ME,
        placedAt: '2026-11-19 09:31',
        act: { kind: 'open', id: 'open', target: STORIES.pickScanIdle },
      },
      {
        processingId: 'OP-2026-0005',
        state: 'picking',
        statusWord: 'processed',
        buyerName: 'Piotr Wiśniewski',
        packed: 2,
        total: 4,
        assignee: ME,
        placedAt: '2026-11-19 08:12',
        act: { kind: 'open', id: 'open-mixed', target: STORIES.pickMixed },
      },
      ASSIGNED_TO_OTHER,
    ],
  },
};

export const OnHold = {
  args: {
    ...base,
    currentStory: STORIES.ordersOnHold,
    banner: 'A hold marks the chip, never a separate column. A hold is only reachable from picking or packed, so a held order always has an assignee (02).',
    bannerBad: true,
    orders: [
      {
        processingId: 'OP-2026-0001',
        state: 'onHold',
        hold: true,
        buyerName: 'Jan Kowalski',
        packed: 1,
        total: 1,
        assignee: ME,
        placedAt: '2026-11-19 09:31',
        act: { kind: 'open', id: 'open', target: STORIES.pickBlockedOnHold },
      },
      HELD_BY_OTHER,
    ],
  },
};

export const Empty = {
  args: {
    ...base,
    currentStory: STORIES.ordersEmpty,
    banner: 'Wording is a default — no source describes the empty list (15 states).',
    orders: [],
  },
};

export const Loading = {
  args: {
    ...base,
    currentStory: STORIES.ordersLoading,
    banner: 'Read model not yet computed. No cadence, latency or refresh interval exists in any source, so none is shown.',
    orders: [],
    placeholder: 'Loading the current orders…',
    note:
      'Row re-rendering is event-driven only: ordered, employee assigned to order fulfilment, position packed, ' +
      'ready for delivery, issues, order is on hold, delivery state notification (15 actions).',
  },
};

export const Error = {
  args: {
    ...base,
    currentStory: STORIES.ordersError,
    banner: 'Read model unavailable — claiming is refused, never queued: a claim must stay atomic (07).',
    bannerBad: true,
    orders: [],
    placeholder: 'No orders shown: the read model is unavailable. Nothing is invented here.',
    note: 'An error never renders a claimable row — a queued or provisional claim would break the exclusive guard (07 RULE-1).',
  },
};
