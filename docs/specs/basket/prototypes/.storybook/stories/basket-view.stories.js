// Element 01 — Basket view. One export per meaningful state; the eight ids are fixed by
// `prototypes/.storybook/flows.json` and the spec board, so each story carries an explicit `__id`
// (Storybook's default id would be derived from the shared meta title).
//
// Every number, label and string comes from `docs/raw/materials/basket.json` (the screenshot transcription)
// unless the note says `(assumption)` — an unanswered register question answered with a default.
// No fetching, no state, no business logic: the renderer owns nothing, each story owns its data.
import { STORIES, esc, pln, assume, mount, button, link, checkbox, stepper, page } from './_ui.js';

const TRASH = `<svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.4"><path d="M2.5 4.5h11M6 4.5V2.6h4v1.9M4 4.5l.8 9h6.4l.8-9"/></svg>`;

const CHARGER = {
  id: 'toggle-item-1',
  title: 'UGREEN Nexode Szybka Ładowarka Sieciowa USB 2x USB Typ C Zasilacz QC PD 65W',
  attributes: [
    ['Liczba urządzeń ładowanych jednocześnie', '3'],
    ['Przewód w zestawie', 'nie'],
    ['Prąd wyjściowy', '0 mA'],
  ],
  quantity: 1,
  price: 99.89,
};

const HUB = {
  id: 'toggle-item-2',
  title: 'UGREEN Hub Adapter Stacja Revodok USB-C Do 3xUSB USB-C HDMI 4K 30Hz PD 100W',
  attributes: [['Liczba portów', '5']],
  quantity: 1,
  price: 64.99,
};

const item = (it) => `
  <div class="item${it.unavailable ? ' unavailable' : ''}">
    ${checkbox({ id: it.id, checked: it.selected, disabled: it.unavailable, go: it.go })}
    <div class="thumb">zdjęcie<br>produktu</div>
    <div>
      <h3>${esc(it.title)}</h3>
      <ul class="attrs">${it.attributes.map(([k, v]) => `<li>${esc(k)}: ${esc(v)}</li>`).join('')}</ul>
      ${
        it.flags
          ? `<div class="flags">${it.flags
              .map((f) => `<span class="flag${f.tone ? ' ' + f.tone : ''}">${esc(f.label)}${f.assumption ? ' ' + assume : ''}</span>`)
              .join('')}</div>`
          : ''
      }
      <div class="foot">
        ${stepper({ value: it.quantity, decreaseDisabled: it.quantity <= 1 })}
        <span class="price">${pln(it.price)}</span>
        <button class="trash" aria-label="usuń produkt" title="usuń produkt">${TRASH}</button>
      </div>
    </div>
  </div>`;

const shipment = (d) => `
  <section class="ship">
    <header>
      ${checkbox({ id: 'toggle-shipment', label: d.shipmentLabel, checked: d.all === true, indeterminate: d.all === 'indeterminate' })}
      <span class="total">${esc(d.itemsTotalLabel)} <b>${pln(d.itemsTotal)}</b></span>
    </header>
    ${d.estimate ? `<div class="estimate">${esc(d.estimate)} ${assume}</div>` : ''}
    ${d.items.map(item).join('')}
  </section>`;

const line = (l) =>
  `<div class="line${l.tone ? ' ' + l.tone : ''}"><span>${esc(l.label)}${l.assumption ? ' ' + assume : ''}</span><b>${l.negative ? '−' : ''}${pln(Math.abs(l.amount))}</b></div>`;

const basket = (d) => `
  <section class="card">
    <h1>${esc(d.title)}</h1>
    <div class="toolbar">
      ${checkbox({ id: 'toggle-all', label: d.wholeBasketLabel, checked: d.all === true, indeterminate: d.all === 'indeterminate', go: d.allGo })}
      ${button({ id: 'remove-selected', label: d.removeLabel, go: d.removeGo })}
    </div>
    ${shipment(d)}
    <div class="hint"><span>${esc(d.hint)}</span>${button({ label: d.crossSell })}</div>
  </section>

  <aside class="summary">
    <section class="card">
      ${d.lines.map(line).join('')}
      <div class="line total"><span>${esc(d.total.label)}</span><b>${pln(d.total.amount)}</b></div>
      <div class="cta">
        ${button({ id: 'checkout', label: d.primary.label, primary: true, disabled: d.primary.disabled, reason: d.primary.reason, reasonAssumption: d.primary.reasonAssumption })}
        ${link(d.secondary, STORIES.twoItems, 'continue-shopping')}
      </div>
    </section>
    <section class="card tiles">
      ${d.tiles
        .map(
          (t) => `<div class="tile"><strong>${esc(t.title)}</strong><span class="body">${esc(t.body)}</span>${
            t.action ? button({ label: t.action }) : ''
          }</div>`,
        )
        .join('')}
    </section>
  </aside>`;

const emptyState = (d) => `
  <section class="card empty">
    <h1>${esc(d.title)}</h1>
    <p>${esc(d.emptyMessage)}</p>
    ${link(d.secondary, STORIES.twoItems, 'continue-shopping')}
    <span class="why">Q10 default ${assume} — no material carries this copy</span>
  </section>`;

const render = (args) =>
  mount(
    document.createElement('div'),
    page({
      body: args.empty ? emptyState(args) : basket(args),
      currentStory: args.currentStory,
      note: args.note,
      wide: args.empty,
    }),
  );

export default { title: 'Basket', render };

const base = {
  title: 'Koszyk',
  wholeBasketLabel: 'cały koszyk',
  removeLabel: 'USUŃ ▾',
  shipmentLabel: 'Przesyłka od UGREEN_POLSKA',
  itemsTotalLabel: 'RAZEM',
  hint: 'dostawa od 9,99 zł lub za 0,00 zł ze SMART',
  crossSell: 'DOBIERZ PRODUKTY ›',
  secondary: 'KONTYNUUJ ZAKUPY',
  deliveryLabel: 'Dostawa od',
  totalLabel: 'Razem z dostawą',
  primary: { label: 'DOSTAWA I PŁATNOŚĆ' },
  tiles: [
    {
      title: 'Przekaż 10 zł dla Szlachetnej Paczki',
      body: 'Wesprzyj Szlachetną Paczkę, aby mogła pomagać na większą skalę.',
      action: 'WESPRZYJ',
    },
    {
      title: 'Allegro Ochrona Kupujących',
      body: 'Wygodne zwroty, reklamacje online oraz 2 lata ochrony zakupu.',
    },
  ],
};

const NOTE_TRUTH =
  'register: docs/raw/materials/basket.json (screenshot transkrybowany pole po polu) · D8 (a shown price is the line total for its quantity; delivery is priced per shipment)';

export const TwoItems = {
  name: 'Two items — default',
  parameters: { __id: 'basket-two-items--default' },
  args: {
    ...base,
    currentStory: STORIES.twoItems,
    all: true,
    itemsTotal: 164.88,
    items: [{ ...CHARGER, selected: true }, { ...HUB, selected: true, go: STORIES.partial }],
    lines: [
      { label: 'Wartość produktów', amount: 164.88 },
      { label: base.deliveryLabel, amount: 9.99 },
    ],
    total: { label: base.totalLabel, amount: 174.87 },
    removeGo: STORIES.twoItemsRemoved,
    note: `${NOTE_TRUTH} · 164,88 + 9,99 = 174,87 · DOSTAWA I PŁATNOŚĆ has no target in this gallery (checkout is a separate slice, D4)`,
  },
};

export const Empty = {
  name: 'Empty — default',
  parameters: { __id: 'basket-empty--default' },
  args: {
    ...base,
    currentStory: STORIES.empty,
    empty: true,
    emptyMessage: 'Twój koszyk jest pusty',
    note: 'register: Q10 + D18 — the empty-basket copy is an (assumption): no material contains it',
  },
};

export const PartialSelection = {
  name: 'Partial selection — default',
  parameters: { __id: 'basket-partial-selection--default' },
  args: {
    ...base,
    currentStory: STORIES.partial,
    all: 'indeterminate',
    allGo: STORIES.twoItems,
    itemsTotal: 99.89,
    items: [{ ...CHARGER, selected: true }, { ...HUB, selected: false, go: STORIES.twoItems }],
    lines: [
      { label: 'Wartość produktów', amount: 99.89 },
      { label: base.deliveryLabel, amount: 9.99 },
    ],
    total: { label: base.totalLabel, amount: 109.88 },
    removeGo: STORIES.twoItemsRemoved,
    note: 'register: basket.json selection controls · Q12 + D18 (USUŃ removes the selected items) · D8 (only selected items count in RAZEM and in the summary) · DOSTAWA I PŁATNOŚĆ has no target in this gallery (D4)',
  },
};

export const OutOfStock = {
  name: 'Out of stock — default',
  parameters: { __id: 'basket-out-of-stock--default' },
  args: {
    ...base,
    currentStory: STORIES.outOfStock,
    all: true,
    itemsTotal: 164.88,
    items: [
      { ...CHARGER, selected: true },
      {
        ...HUB,
        selected: true,
        flags: [
          { label: 'Chwilowo niedostępny', tone: 'warn', assumption: true },
          { label: 'blokuje DOSTAWĘ I PŁATNOŚĆ (D33)', tone: 'bad', assumption: true },
        ],
      },
    ],
    lines: [
      { label: 'Wartość produktów', amount: 164.88 },
      { label: base.deliveryLabel, amount: 9.99 },
    ],
    total: { label: base.totalLabel, amount: 174.87 },
    primary: { label: base.primary.label, disabled: true, reason: 'Odznacz produkty niedostępne, aby przejść dalej', reasonAssumption: true },
    removeGo: STORIES.twoItemsRemoved,
    note: `register: D33 — an out-of-stock item blocks handover and deselecting the row enables checkout for the remaining subset, which overrides R7 and the materials' non-blocking reading ([seg 70-71]) · no length is added to the estimate: an out-of-stock item is never in a checkable basket (element 05) · the Chwilowo niedostępny badge and the row's block label are (assumption): no material names an out-of-stock label, and D33 fixes the behaviour, not the copy · the block-reason wording is (assumption): VIEW-2b (transcript 74) requires a reason on a disabled CTA, not this one · the row still counts in RAZEM and in the summary, because D33 does not touch the price · ${NOTE_TRUTH}`,
  },
};

export const Unavailable = {
  name: 'Unavailable — default',
  parameters: { __id: 'basket-unavailable--default' },
  args: {
    ...base,
    currentStory: STORIES.unavailable,
    all: true,
    itemsTotal: 99.89,
    items: [
      { ...CHARGER, selected: true },
      { ...HUB, selected: true, unavailable: true, flags: [{ label: 'Niedostępny', tone: 'bad' }, { label: 'nie wliczony w RAZEM ani w sumę', tone: 'warn', assumption: true }] },
    ],
    lines: [
      { label: 'Wartość produktów', amount: 99.89 },
      { label: base.deliveryLabel, amount: 9.99 },
    ],
    total: { label: base.totalLabel, amount: 109.88 },
    primary: { label: base.primary.label, disabled: true, reason: 'Usuń niedostępne produkty, aby przejść dalej', reasonAssumption: true },
    removeGo: STORIES.twoItemsRemoved,
    note: 'register: Q11 + D18 — the exclusion rule and the block reason wording are (assumption): the operator never answered Q11 (log.md D18), and D18 only requires that a disabled CTA carries a reason, not this one · 99,89 + 9,99 = 109,88',
  },
};

export const DeliveryEstimate = {
  name: 'Delivery estimate — default',
  parameters: { __id: 'basket-delivery-estimate--default' },
  args: {
    ...base,
    currentStory: STORIES.deliveryEstimate,
    all: true,
    itemsTotal: 164.88,
    estimate: 'Dostawa: czw. 27 lis',
    items: [{ ...CHARGER, selected: true }, { ...HUB, selected: true }],
    lines: [
      { label: 'Wartość produktów', amount: 164.88, note: 'w tym rabat 10,00 zł (D22)' },
      { label: base.deliveryLabel, amount: 9.99 },
    ],
    total: { label: base.totalLabel, amount: 174.87 },
    removeGo: STORIES.twoItemsRemoved,
    note: 'register: D36 (estimate = today + 2 … + 4 days; the date shown is an illustration), D22 (catalog flattens price and discount, so the discount is inside Wartość produktów and is never subtracted twice) · DOSTAWA I PŁATNOŚĆ has no target in this gallery (D4) · 164,88 + 9,99 = 174,87',
  },
};

export const Blocked = {
  name: 'Blocked — default',
  parameters: { __id: 'basket-blocked--default' },
  args: {
    ...base,
    currentStory: STORIES.blocked,
    all: true,
    itemsTotal: 164.88,
    items: [
      { ...CHARGER, selected: true },
      {
        ...HUB,
        selected: true,
        flags: [
          { label: 'Sprzedaż wstrzymana przez sprzedawcę', tone: 'bad', assumption: true },
          { label: 'wliczony w sumę', tone: 'warn', assumption: true },
        ],
      },
    ],
    lines: [
      { label: 'Wartość produktów', amount: 164.88 },
      { label: base.deliveryLabel, amount: 9.99 },
    ],
    total: { label: base.totalLabel, amount: 174.87 },
    primary: { label: base.primary.label, disabled: true, reason: 'Usuń produkty, których sprzedaż wstrzymano, aby przejść dalej', reasonAssumption: true },
    removeGo: STORIES.twoItemsRemoved,
    note: 'register: Q11 + D18 (the blocking machinery: a disabled CTA always carries its reason and the causing row names the problem) · the problem kind, the block-reason wording and the fact that the blocked row still counts are (assumption): no material names a blocking cause other than an unavailable offer, and only that offer has an exclusion rule (Q11)',
  },
};

export const TwoItemsRemoved = {
  name: 'Two items removed',
  parameters: { __id: 'basket-two-items-removed' },
  args: {
    ...base,
    currentStory: STORIES.twoItemsRemoved,
    empty: true,
    emptyMessage: 'Twój koszyk jest pusty',
    note: 'flow target only, not a second design (the prototype has no undo: "remove selected" lands here) · same render as basket-empty--default · register: Q10 + D18',
  },
};
