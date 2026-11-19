// Element 17 — IssueDashboard. Read-only by design: no resolve / close / dismiss / resume exists in any source.
// Rows grouped by order (a hold holds the whole order, H1). No counters, charts, SLA badges or colour thresholds.
// The row carries product name + number only: 17 says it cannot say "1 of 2" today (Q-17-1).
import { STORIES, esc, mount, pill, table, link, page } from './_ui.js';

const COLUMNS = ['Issue kind', 'Product', 'Reporter', 'Reported'];

const group = (g) => `
  <div class="group">
    <h3>
      <span class="loc">${esc(g.processingId)}</span>
      ${pill(g.state, { hold: true })}
      <span class="muted">assignee ${esc(g.assignee)}</span>
      ${g.stateTarget ? `<span class="muted">·</span> ${link('open processing state (read-only)', g.stateTarget, g.stateAnchor)}` : ''}
    </h3>
    ${table(COLUMNS, g.issues.map((i) => ({
      cells: [
        { html: `<b>${esc(i.kind)}</b>` },
        i.product,
        i.reporter,
        { html: `${esc(i.age)} ago <span class="muted">· ${esc(i.reportedAt)}</span>` },
      ],
    })))}
  </div>`;

const view = (d) => `
  <section class="card">
    <h1>Current issues</h1>
    ${d.banner ? `<div class="banner${d.bannerBad ? ' bad' : ''}">${esc(d.banner)}</div>` : ''}
    ${d.groups.length ? d.groups.map(group).join('') : `<p class="empty">${esc(d.placeholder || 'No open issues.')}</p>`}
    <p class="note">${esc(d.note)}</p>
  </section>`;

const render = (args) =>
  mount(
    document.createElement('div'),
    page({
      crumbs: '/ fulfillment / issues',
      actor: args.actor,
      access: args.access,
      currentStory: args.currentStory,
      wide: true,
      body: view(args),
    }),
  );

export default { title: 'Fulfillment/Issue Dashboard', render };

const base = {
  actor: 'employee-17',
  access: 'read-only, any warehouse user — no resolver role exists (17 permissions, Q-17-2)',
  note:
    'Actions: none. There is no resolve, close, dismiss, resume or report button on this screen — no command, event or ' +
    'issue lifecycle exists (17 actions, Q-17-1), resolution is physical and the fallback runs outside the system ' +
    '[seg 149-151] / [seg 104]. No SLA, deadline or escalation number exists, so none is shown. Quantity and position ' +
    'reference are not stated in any source, so a row names the product only — it cannot say "1 of 2" today (Q-17-1).',
};

export const OpenIssues = {
  args: {
    ...base,
    currentStory: STORIES.issuesOpen,
    banner: 'Oldest first — ordering is a default, no source names one. Rows never leave: an issue has no exit inside the system (R-17-1).',
    groups: [
      {
        processingId: 'OP-2026-0002',
        state: 'onHold',
        assignee: 'employee-17',
        issues: [
          { kind: 'LOST', product: "LEGO Star Wars SMART Play X-Wing 75423", reporter: 'employee-17', age: '1 d 4 h', reportedAt: '2026-11-18 08:12' },
          { kind: 'WRONG_LOCATION', product: 'LEGO Star Wars SMART Play X-Wing 75423', reporter: 'employee-17', age: '4 h 05 min', reportedAt: '2026-11-19 08:19' },
        ],
      },
      {
        processingId: 'OP-2026-0001',
        state: 'onHold',
        assignee: 'employee-42',
        stateTarget: STORIES.pickBlockedOnHold,
        stateAnchor: 'open-state',
        issues: [{ kind: 'LOST', product: 'LEGO Star Wars SMART Play X-Wing 75423', reporter: 'employee-42', age: '22 min', reportedAt: '2026-11-19 12:01' }],
      },
    ],
  },
};

export const Empty = {
  args: {
    ...base,
    currentStory: STORIES.issuesEmpty,
    banner: 'Empty wording is a default — no source describes it.',
    groups: [],
  },
};

export const Error = {
  args: {
    ...base,
    currentStory: STORIES.issuesError,
    banner: 'Projection failed at 12:40 — showing the last known issues, 47 min old. A dashboard that hides issues behind a failure tells the resolver the warehouse is fine (17 states).',
    bannerBad: true,
    groups: [
      {
        processingId: 'OP-2026-0002',
        state: 'onHold',
        assignee: 'employee-17',
        issues: [
          { kind: 'LOST', product: 'LEGO Star Wars SMART Play X-Wing 75423', reporter: 'employee-17', age: '1 d 5 h', reportedAt: '2026-11-18 08:12' },
          { kind: 'WRONG_LOCATION', product: 'LEGO Star Wars SMART Play X-Wing 75423', reporter: 'employee-17', age: '4 h 52 min', reportedAt: '2026-11-19 08:19' },
        ],
      },
      {
        processingId: 'OP-2026-0001',
        state: 'onHold',
        assignee: 'employee-42',
        stateTarget: STORIES.pickBlockedOnHold,
        stateAnchor: 'open-state',
        issues: [{ kind: 'LOST', product: 'LEGO Star Wars SMART Play X-Wing 75423', reporter: 'employee-42', age: '1 h 09 min', reportedAt: '2026-11-19 12:01' }],
      },
    ],
  },
};
