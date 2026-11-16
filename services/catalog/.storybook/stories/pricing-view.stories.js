// Element 16 — PricingView. Low-fi, thin: the story owns the data, the renderer owns no state.
//
// The screen renders 16's read model and nothing else: one PricingReads body per currency
//   {offerId, currency, current[PricedItem], scheduled[PricedItem], history[PricedItem],
//    discounts{active[], scheduled[], expired[]}, effectivePreview{at, amountMinor, currency}|absent}
// with PriceItem/DiscountItem exactly as shop.catalog.reads spells them (a fixed discount carries basisPoints 0, a
// percentage carries amountMinor 0 and currency null). `source` is the same facts before the clock split them — what 10
// reads — and is used only to stand in for ActivePriceCalculation when the reader picks a preview date.
//
// Note the gap the gallery must not hide: GET /offers/{offerId}/pricing today answers 12's Pricing (a single `current`
// object, no preview), while 16's screen body is PricingReads. The split is computed by pricingOf() from one source, so a
// story can never disagree with itself.
const STORIES = {
  currentOnly: 'catalog-pricingview--current-only',
  changePrice: 'catalog-pricingview--change-price',
  afterChange: 'catalog-pricingview--after-change',
  schedulePrice: 'catalog-pricingview--schedule-price',
  scheduledHistory: 'catalog-pricingview--scheduled-history',
  addDiscount: 'catalog-pricingview--add-discount',
  addDiscountFixed: 'catalog-pricingview--add-discount-fixed',
  multiCurrency: 'catalog-pricingview--multi-currency',
  noPreview: 'catalog-pricingview--no-preview',
};

function go(storyId) {
  try {
    window.parent.location.href = '?path=/story/' + storyId;
  } catch (e) {
    window.location.href = '?path=/story/' + storyId;
  }
}

// The reference instant every story is rendered against; nothing here reads the wall clock.
const NOW = '2026-06-25T12:00:00Z';

const css = `
  :root { --ink:#1b1f24; --muted:#5c6672; --line:#d8dee6; --bg:#f6f7f9; --ok:#1f7a45; --bad:#b3261e; --warn:#8a5a00 }
  * { box-sizing:border-box }
  body { margin:0; font:14px/1.45 -apple-system,Segoe UI,Roboto,sans-serif; color:var(--ink); background:var(--bg) }
  header { display:flex; gap:12px; align-items:center; padding:10px 18px; background:#fff; border-bottom:1px solid var(--line) }
  header .role { margin-left:auto; font-size:12px; color:var(--muted) }
  main { display:grid; grid-template-columns:1fr 380px; gap:18px; padding:18px; max-width:1320px; align-items:start }
  .stack { display:grid; gap:14px }
  section.card, aside.card { background:#fff; border:1px solid var(--line); border-radius:8px; padding:14px }
  h1 { font-size:18px; margin:0 0 4px }
  h2 { font-size:13px; text-transform:uppercase; letter-spacing:.04em; color:var(--muted); margin:14px 0 8px }
  h2:first-child { margin-top:0 }
  .note { font-size:12px; color:var(--muted); margin:4px 0 10px }
  .banner { border-radius:6px; padding:8px 10px; margin:10px 0; font-size:13px; background:#fbfcfd; border:1px solid var(--line) }
  .banner.bad { color:var(--bad); border-color:#e6c3bf; background:#fdf6f5 }
  .banner.ok { color:var(--ok); border-color:#c3e0cf; background:#f5fbf7 }
  table { width:100%; border-collapse:collapse; font-size:13px }
  th, td { text-align:left; padding:7px 8px; border-bottom:1px solid var(--line); vertical-align:top }
  th { font-size:11px; text-transform:uppercase; letter-spacing:.04em; color:var(--muted); font-weight:600 }
  .num { font-variant-numeric:tabular-nums; white-space:nowrap }
  .price { font-size:22px; font-weight:600; font-variant-numeric:tabular-nums }
  .empty { color:var(--muted); font-style:italic }
  .row { display:grid; grid-template-columns:180px 1fr; gap:10px; padding:5px 0; border-bottom:1px dashed var(--line) }
  .row:last-child { border-bottom:0 }
  .row .k { color:var(--muted) }
  .columns { display:grid; grid-template-columns:repeat(3,1fr); gap:12px }
  .columns .tag { font-size:11px; text-transform:uppercase; letter-spacing:.04em; color:var(--muted); margin-bottom:4px }
  .columns ul { padding-left:16px; margin:0 }
  .columns li { margin:4px 0; font-size:13px }
  .actions { display:flex; gap:10px; margin-top:12px; flex-wrap:wrap }
  button { font:inherit; padding:9px 14px; border-radius:6px; border:1px solid var(--line); background:#fff; cursor:pointer }
  button.primary { background:var(--ink); color:#fff; border-color:var(--ink) }
  button.on { border-color:var(--ink); font-weight:600; opacity:1; color:var(--ink) }
  button[disabled] { opacity:.5; cursor:not-allowed }
  .why { font-size:12px; color:var(--muted); margin-top:6px }
  .why.bad { color:var(--bad) }
  .field { margin:10px 0 }
  .field label { display:block; font-size:12px; color:var(--muted); margin-bottom:4px }
  .field input, .field select { font:inherit; padding:7px 9px; border:1px solid var(--line); border-radius:6px; width:220px; background:#fff }
  .field input[readonly], .field select[disabled] { background:#f1f3f6; color:var(--muted) }
  .field .err { color:var(--bad); font-size:12px; margin-top:3px }
  .calc { font-family:ui-monospace,SFMono-Regular,Menlo,monospace; font-size:12px; background:#f1f3f6; border-radius:6px; padding:8px 10px; margin-top:8px }
  code { background:#f1f3f6; border-radius:4px; padding:1px 4px; font-size:12px }
`;

const SCALE = { PLN: 2, EUR: 2, JPY: 0, USD: 2 };

function money(amountMinor, currency) {
  const scale = SCALE[currency] ?? 2;
  const digits = String(Math.abs(amountMinor)).padStart(scale + 1, '0');
  const whole = digits.slice(0, digits.length - scale);
  const fraction = scale ? ',' + digits.slice(-scale) : '';
  return (amountMinor < 0 ? '−' : '') + whole + fraction + ' ' + currency;
}

const instant = (iso) => (iso ? iso.replace('T', ' ').slice(0, 16) + ' UTC' : 'otwarty');

const inRange = (e, at) => Date.parse(e.validFrom) <= at && (e.validUntil === null || at < Date.parse(e.validUntil));
const startsLater = (e, at) => Date.parse(e.validFrom) > at;
const endedBy = (e, at) => e.validUntil !== null && Date.parse(e.validUntil) <= at;

// PricingReads.ofNullable's split, so the fixture is the body the screen renders — never a `prices[]` series.
function pricingOf(source, offerId, currency, now) {
  const at = Date.parse(now);
  const prices = source.prices.filter((p) => p.currency === currency);
  const discounts = source.discounts.filter((d) => d.kind === 'percentage' || d.currency === currency);
  return {
    offerId,
    currency,
    current: prices.filter((p) => inRange(p, at)),
    scheduled: prices.filter((p) => startsLater(p, at)).sort((a, b) => Date.parse(a.validFrom) - Date.parse(b.validFrom)),
    history: prices.filter((p) => endedBy(p, at)).sort((a, b) => Date.parse(b.validFrom) - Date.parse(a.validFrom)),
    discounts: {
      active: discounts.filter((d) => inRange(d, at)),
      scheduled: discounts.filter((d) => startsLater(d, at)),
      expired: discounts.filter((d) => endedBy(d, at)),
    },
  };
}

// 10's ActivePriceCalculation, D3 order: fixed amounts first, percentages after, floor at 0, one half-up rounding at the end.
function effectivePrice(source, currency, atIso) {
  const at = Date.parse(atIso);
  const price = source.prices.filter((p) => p.currency === currency).find((p) => inRange(p, at));
  if (!price) return null;
  const active = source.discounts.filter((d) => inRange(d, at) && (d.kind === 'percentage' || d.currency === currency));
  const fixed = active.filter((d) => d.kind === 'fixed').reduce((sum, d) => sum + d.amountMinor, 0);
  const pct = Math.min(10000, active.filter((d) => d.kind === 'percentage').reduce((sum, d) => sum + d.basisPoints, 0));
  const net = Math.max(0, price.amountMinor - fixed);
  return {
    priceId: price.priceId,
    base: price.amountMinor,
    fixed,
    pct,
    net,
    amountMinor: Math.round((net * (10000 - pct)) / 10000),
    currency,
    fixedIds: active.filter((d) => d.kind === 'fixed').map((d) => d.discountId),
    pctIds: active.filter((d) => d.kind === 'percentage').map((d) => d.discountId),
  };
}

const discountValue = (d) =>
  d.kind === 'percentage' ? `${(d.basisPoints / 100).toLocaleString('pl-PL')} % (${d.basisPoints} bp)` : money(d.amountMinor, d.currency);

const header = (d) => `
  <header>
    <strong>catalog</strong><span style="color:var(--muted)">/ oferty / ${d.offerId} / ceny</span>
    <span class="role">Zalogowany: <b>${d.actor}</b> · zakres: <code>${d.scope}</code> · teraz: <code>${d.now}</code></span>
  </header>`;

const switcher = (d) =>
  d.currencies.length > 1
    ? `<p class="note">Waluta:
         ${d.currencies.map((c) => `<button id="switch-${c}" data-currency="${c}" class="${c === d.currency ? 'on' : ''}">${c}</button>`).join(' ')}
         — jedno ciało odpowiedzi na walutę, panel nigdy nie sumuje walut (16 multi-currency, D2).</p>`
    : `<p class="note">Przełącznik walut nie jest renderowany: oferta ma cenę tylko w ${d.currency} (16 multi-currency, D2).</p>`;

function currentSection(d, pricing) {
  const price = pricing.current[0];
  if (!price) {
    return `<h2>Cena bieżąca</h2><p class="empty">Brak otwartego wpisu w ${pricing.currency} — 10 zwraca ABSENT, nie 0.</p>`;
  }
  const started = Date.parse(price.validFrom) <= Date.parse(d.now);
  return `
    <h2>Cena bieżąca</h2>
    <div class="row"><span class="k">Cena</span><span class="price">${money(price.amountMinor, price.currency)}</span></div>
    <div class="row"><span class="k">Wpis</span><span>${price.priceId} · od ${instant(price.validFrom)} · do ${instant(price.validUntil)}</span></div>
    <div class="actions">
      <button id="change-price" data-story="changePrice">Zmień cenę</button>
      ${d.form === 'schedule' ? '' : '<button id="schedule" data-story="schedulePrice">Zaplanuj cenę</button>'}
    </div>
    <p class="why">Cena nie jest edytowana w miejscu: zmiana zamyka bieżący wpis i dołącza nowy (16 RULE-1, 03 RULE-2).</p>
    <p class="why">Akcja „usuń” na wpisie bieżącym nie jest renderowana — wpis już się rozpoczął (${instant(price.validFrom)}${started ? ' ≤ teraz' : ''}), więc zamyka go nowy wpis, a nie usunięcie (16 RULE-2, 03 RULE-8; 16 S5).</p>`;
}

function scheduledSection(d, pricing) {
  const rows = pricing.scheduled
    .map(
      (p) => `<tr>
        <td class="num">${p.priceId}</td>
        <td class="num">${money(p.amountMinor, p.currency)}</td>
        <td class="num">${instant(p.validFrom)}</td>
        <td class="num">${instant(p.validUntil)}</td>
        <td>${Date.parse(p.validFrom) > Date.parse(d.now)
          ? `<button id="remove-${p.priceId}" data-remove="${p.priceId}">Usuń zaplanowany wpis</button>`
          : '<span class="empty">wpis już się rozpoczął — usunięcie niedozwolone (RULE-2)</span>'}</td>
      </tr>`,
    )
    .join('');
  return `
    <h2>Zaplanowane wpisy — najbliższy pierwszy</h2>
    ${pricing.scheduled.length
      ? `<table><tr><th>Wpis</th><th>Cena</th><th>Od</th><th>Do</th><th></th></tr>${rows}</table>`
      : '<p class="empty">Brak zaplanowanych wpisów</p>'}
    <p class="why">Usunąć można tylko wpis, który się jeszcze nie zaczął (16 RULE-2, 03 RULE-8); usunięcie nie tworzy wpisu w historii. Zaplanowanie nie emituje zdarzenia do <code>validFrom</code> (03 RULE-4).</p>`;
}

function historySection(pricing) {
  return `
    <h2>Historia — tylko do odczytu</h2>
    ${pricing.history.length
      ? `<table><tr><th>Wpis</th><th>Cena</th><th>Od</th><th>Do</th></tr>${pricing.history
          .map(
            (p) => `<tr><td class="num">${p.priceId}</td><td class="num">${money(p.amountMinor, p.currency)}</td>
              <td class="num">${instant(p.validFrom)}</td><td class="num">${instant(p.validUntil)}</td></tr>`,
          )
          .join('')}</table>`
      : '<p class="empty">Brak wpisów historycznych</p>'}
    <p class="why">Historia jest skutkiem zamknięcia wpisu, nie osobną tabelą: żadna akcja nie jest tu renderowana (16 RULE-5).</p>`;
}

function discountsSection(d, pricing) {
  const column = (label, list, tone) => `
    <div>
      <div class="tag" style="${tone}">${label} (${list.length})</div>
      ${list.length
        ? `<ul>${list.map((x) => `<li><code>${x.discountId}</code> ${discountValue(x)}<div class="note" style="margin:2px 0 0">${instant(x.validFrom)} → ${instant(x.validUntil)}</div></li>`).join('')}</ul>`
        : '<p class="empty">— brak</p>'}
    </div>`;
  return `
    <h2>Rabaty w ${pricing.currency}</h2>
    <div class="columns">
      ${column('aktywne', pricing.discounts.active, 'color:var(--ok)')}
      ${column('zaplanowane', pricing.discounts.scheduled, 'color:var(--warn)')}
      ${column('wygasłe', pricing.discounts.expired, '')}
    </div>
    <p class="why">Podział wynika z zegara, nie z zapisu: ten sam rabat przechodzi z „zaplanowane” do „aktywne” bez edycji (03 RULE-6, 16 RULE-6).
      Rabat procentowy dotyczy każdej waluty, rabat stały tylko swojej (03 RULE-5).</p>
    ${d.form === 'discount' ? '' : '<div class="actions"><button id="add-discount" data-story="addDiscount">Dodaj rabat</button></div>'}`;
}

function previewSection(d, source) {
  if (d.previewEnabled === false) {
    return `<h2>Podgląd ceny na wybrany dzień (10)</h2>
      <p class="empty">Podgląd wyłączony konfiguracją</p>
      <p class="why"><code>catalog.pricing.preview = false</code> — komponent <code>effectivePreview</code> nie jest renderowany (16 wariant no-preview).</p>`;
  }
  const result = effectivePrice(source, d.currency, d.previewAt);
  return `
    <h2>Podgląd ceny na wybrany dzień (10)</h2>
    <div class="actions" style="margin-top:0">
      ${d.previewDates.map((x) => `<button id="${x.id}" data-set="previewAt=${x.at}" class="${x.at === d.previewAt ? 'on' : ''}">${x.label}</button>`).join('')}
    </div>
    ${result
      ? `<p class="price" style="margin:10px 0 0">${money(result.amountMinor, result.currency)}</p>
         <div class="note">na ${instant(d.previewAt)}</div>
         <div class="calc">${result.base} − ${result.fixed} (rabaty stałe: ${result.fixedIds.join(' + ') || 'brak'}) = ${result.net}
× (10000 − ${result.pct}${result.pctIds.length ? ' za ' + result.pctIds.join(' + ') : ''}) / 10000 = ${result.amountMinor} — cena ${result.priceId}</div>
         <p class="why">Kolejność stosowania: kwoty stałe, potem procenty, podłoga 0, jedno zaokrąglenie half-up (10 D3). Podgląd pokazuje efekt przed zapisem (16 RULE-4).</p>`
      : `<p class="empty" style="margin:10px 0 0">brak ceny — ABSENT, nie 0</p>
         <p class="why">Dla ${instant(d.previewAt)} nie ma otwartego wpisu w ${d.currency}; 10 zwraca „brak ceny”, a nie zero (10 „undefined”).</p>`}`;
}

function changeForm(d) {
  return `
    <h2>Zmiana ceny</h2>
    <div class="field"><label>Nowa cena (minor, ${d.currency})</label><input value="${d.draft.amountMinor}" readonly></div>
    <p class="banner">Bieżący wpis ${d.draft.closesId} zostanie zamknięty w tej chwili (${instant(d.now)}), a nowy wpis ${d.draft.newId} dołączony od tego instantu — historia zostaje (16 RULE-1, 03 RULE-2).</p>
    <p class="why">Kwota jest w jednostkach najmniejszych z walutą (D1: ${d.currency} skala ${SCALE[d.currency] ?? 2}).</p>
    <div class="actions">
      <button class="primary" id="save-change" data-story="afterChange">Zapisz zmianę</button>
      <button id="cancel" data-story="currentOnly">Anuluj</button>
    </div>`;
}

function scheduleForm(d) {
  return `
    <h2>Zaplanuj cenę</h2>
    <div class="field"><label>Nowa cena (minor, ${d.currency})</label><input value="${d.draft.amountMinor}" readonly></div>
    <div class="field"><label>Obowiązuje od</label><input value="${d.draft.validFrom.slice(0, 10)}" readonly>
      <div class="note">validFrom ${instant(d.draft.validFrom)} jest późniejsze niż teraz (${instant(d.now)}) — warunek akcji „zaplanuj cenę”.</div>
    </div>
    <p class="why">Do ${instant(d.draft.validFrom)} wpis jest cichy poza kontekstem: żadnego <code>PriceActivated</code> (03 RULE-4). Wpis trafi pod „zaplanowane”, nie pod „bieżąca”.</p>
    <p class="why">Wariant odrzucany: validFrom 2026-06-20 (&lt; teraz) — „zaplanuj cenę” wymaga daty w przyszłości, a wpis, który już się zaczął, zamyka się nowym wpisem (16 RULE-1/RULE-2).</p>
    <div class="actions">
      <button class="primary" id="save-schedule" data-story="scheduledHistory">Zaplanuj</button>
      <button id="cancel" data-story="afterChange">Anuluj</button>
    </div>`;
}

function discountErrors(draft) {
  const errors = [];
  if (draft.kind === 'percentage' && !(draft.basisPoints >= 1 && draft.basisPoints <= 10000)) {
    errors.push(`basisPoints = ${draft.basisPoints} poza zakresem 1…10000 — API odpowiada 400 z errors[0].field = basisPoints (16 RULE-6, 03 RULE-5).`);
  }
  if (draft.kind === 'fixed' && !(draft.amountMinor > 0)) {
    errors.push('amountMinor musi być > 0 (16 RULE-6).');
  }
  if (!(Date.parse(draft.validFrom) < Date.parse(draft.validUntil))) {
    errors.push('validFrom musi być wcześniej niż validUntil (16 RULE-6).');
  }
  return errors;
}

function discountForm(d) {
  const draft = d.draft;
  const errors = discountErrors(draft);
  const percentage = draft.kind === 'percentage';
  return `
    <h2>Dodaj rabat — ${percentage ? 'procentowy' : 'stały'}</h2>
    <p class="note">Rodzaj:
      ${percentage
        ? '<button id="kind-percentage" class="on" disabled>procentowy</button><button id="kind-fixed" data-story="addDiscountFixed">stały</button>'
        : '<button id="kind-percentage" data-story="addDiscount">procentowy</button><button id="kind-fixed" class="on" disabled>stały</button>'}
    </p>
    ${percentage
      ? `<div class="field"><label>Rabat w punktach bazowych (1…10000)</label><input value="${draft.basisPoints}" readonly>
           <div class="err">${errors.length ? errors[0] : `${draft.basisPoints} bp = ${(draft.basisPoints / 100).toLocaleString('pl-PL')} % — w zakresie (16 RULE-6).`}</div>
         </div>`
      : `<div class="field"><label>Kwota rabatu (minor)</label><input value="${draft.amountMinor}" readonly></div>
         <div class="field"><label>Waluta rabatu stałego</label>
           <select id="discount-currency" disabled>
             <option>${d.currency}</option>
           </select>
           <div class="note">Jedyne dostępne wejście to waluta ceny (${d.currency}) — pola nie da się przestawić na EUR, bo rabat stały w walucie, której oferta nie ma wyceny, jest odrzucany przez API jako <code>DISCOUNT_CURRENCY_NOT_PRICED</code> (16 RULE-3, 03 RULE-5).</div>
         </div>`}
    <div class="field"><label>Okres</label><input value="${draft.validFrom.slice(0, 10)} → ${draft.validUntil.slice(0, 10)}" readonly>
      <div class="note">Zakres połówkowo otwarty: rabat działa od validFrom, przestaje działać w validUntil (03 RULE-5).</div>
    </div>
    <div class="actions">
      <button class="primary" id="save-discount" ${errors.length ? 'disabled' : 'data-story="scheduledHistory"'}>Zapisz rabat</button>
      <button id="cancel" data-story="scheduledHistory">Anuluj</button>
    </div>
    <p class="why ${errors.length ? 'bad' : ''}">${errors.length ? '„Zapisz rabat” nieaktywne: ' + errors[0] : 'Wartości w zakresie — zapis dozwolony (PATCH /offers/{offerId}/pricing, components discounts).'}</p>`;
}

// 16's default variant is tabs (current / scheduled / history); the gallery opens all three at once and the tabs narrow
// the view, so a reader sees the whole model and the variant's control at the same time.
const TABS = [
  ['all', 'wszystkie'],
  ['current', 'bieżąca'],
  ['scheduled', 'zaplanowane'],
  ['history', 'historia'],
];

const tabBar = (d) => `
  <div class="actions" style="margin:0 0 6px">
    ${TABS.map(([id, label]) => `<button id="tab-${id}" data-tab="${id}" class="${d.tab === id ? 'on' : ''}">${label}</button>`).join('')}
  </div>`;

function render(d) {
  const pricing = d.pricings[d.currency];
  const removed = d.removed ?? [];
  const shown = removed.length
    ? { ...pricing, scheduled: pricing.scheduled.filter((p) => !removed.includes(p.priceId)) }
    : pricing;
  const source = { ...d.sources[d.currency], prices: d.sources[d.currency].prices.filter((p) => !removed.includes(p.priceId)) };
  const tab = (id) => d.tab === 'all' || d.tab === id;
  return `
  <style>${css}</style>
  ${header(d)}
  <main>
    <div class="stack">
      <section class="card">
        <h1>Ceny oferty ${d.offerId}</h1>
        <p class="note">Ciało ekranu to <code>PricingReads</code> 16 dla jednej waluty: <code>current[]</code>, <code>scheduled[]</code>, <code>history[]</code>, <code>discounts{{active,scheduled,expired}}</code>. Uwaga: <code>GET /offers/{offerId}/pricing</code> z 12 zwraca dziś <code>current</code> jako pojedynczy obiekt i bez podglądu — brak tej trasy dla ekranu 16 jest otwartym tematem.</p>
        ${switcher(d)}
        ${d.banner ? `<p class="banner ${d.bannerTone || ''}">${d.banner}</p>` : ''}
        ${removed.length ? `<p class="banner">Usunięto zaplanowany wpis ${removed.join(', ')} (<code>PATCH … removePriceIds</code>): wiersz zniknął z listy, historii nie przybyło (16 S4).</p>` : ''}
        ${tabBar(d)}
        ${tab('current') ? currentSection(d, shown) : ''}
        ${tab('scheduled') ? scheduledSection(d, shown) : ''}
        ${tab('history') ? historySection(shown) : ''}
      </section>
      <section class="card">${discountsSection(d, shown)}</section>
      ${d.form === 'discount' ? `<section class="card">${discountForm(d)}</section>` : ''}
      ${d.form === 'change' ? `<section class="card">${changeForm(d)}</section>` : ''}
      ${d.form === 'schedule' ? `<section class="card">${scheduleForm(d)}</section>` : ''}
    </div>
    <aside class="card">
      ${previewSection(d, source)}
      <div class="actions">${d.backStory ? `<button id="back" data-story="${d.backStory}">Wróć do cen</button>` : ''}</div>
      <p class="why">Widok czyta jedno ciało na walutę (16 D2). Historia, zaplanowane wpisy i rabaty są rozdzielone przy renderze, bo „bieżąca” jest zdaniem o zegarze.</p>
    </aside>
  </main>`;
}

function mount(canvasElement, args) {
  const state = JSON.parse(JSON.stringify(args));
  const paint = () => {
    canvasElement.innerHTML = render(state);
  };
  canvasElement.addEventListener('click', (event) => {
    const target = event.target.closest('[data-story],[data-set],[data-currency],[data-remove],[data-tab]');
    if (!target || target.disabled) return;
    if (target.dataset.story) return go(STORIES[target.dataset.story]);
    if (target.dataset.currency) state.currency = target.dataset.currency;
    if (target.dataset.remove) state.removed = [...(state.removed ?? []), target.dataset.remove];
    if (target.dataset.tab) state.tab = target.dataset.tab;
    (target.dataset.set || '').split('|').filter(Boolean).forEach((pair) => {
      const [path, value] = pair.split('=');
      state[path] = value;
    });
    paint();
  });
  paint();
  return canvasElement;
}

const price = (priceId, amountMinor, currency, validFrom, validUntil) => ({ priceId, amountMinor, currency, validFrom, validUntil });
const percent = (discountId, basisPoints, validFrom, validUntil) => ({ discountId, kind: 'percentage', basisPoints, amountMinor: 0, currency: null, validFrom, validUntil });
const fixed = (discountId, amountMinor, currency, validFrom, validUntil) => ({ discountId, kind: 'fixed', basisPoints: 0, amountMinor, currency, validFrom, validUntil });

const SOURCE_SINGLE = { prices: [price('P-1', 30989, 'PLN', '2026-05-01T00:00:00Z', null)], discounts: [] };

const SOURCE_AFTER_CHANGE = {
  prices: [price('P-1', 30989, 'PLN', '2026-05-01T00:00:00Z', NOW), price('P-4', 28900, 'PLN', NOW, null)],
  discounts: [],
};

const SOURCE_SERIES = {
  prices: [
    price('P-1', 30989, 'PLN', '2026-05-01T00:00:00Z', NOW),
    price('P-4', 28900, 'PLN', NOW, '2026-07-01T00:00:00Z'),
    price('P-5', 27900, 'PLN', '2026-07-01T00:00:00Z', '2026-09-01T00:00:00Z'),
    price('P-6', 26900, 'PLN', '2026-09-01T00:00:00Z', null),
  ],
  discounts: [
    percent('D-0', 500, '2026-05-01T00:00:00Z', '2026-06-01T00:00:00Z'),
    percent('D-1', 1000, '2026-06-20T00:00:00Z', '2026-07-01T00:00:00Z'),
    fixed('D-2', 2000, 'PLN', '2026-07-01T00:00:00Z', '2026-07-08T00:00:00Z'),
  ],
};

const SOURCE_PLN = {
  prices: [price('P-1', 28900, 'PLN', '2026-05-01T00:00:00Z', null)],
  discounts: [percent('D-1', 1000, '2026-06-20T00:00:00Z', null)],
};

const SOURCE_EUR = {
  prices: [price('P-E1', 6699, 'EUR', '2026-05-01T00:00:00Z', null)],
  discounts: [fixed('D-E1', 500, 'EUR', '2026-06-20T00:00:00Z', null)],
};

const OFFER = 'OFR-75423ABC';

const DATES_TWO = [
  { id: 'at-now', label: 'teraz (2026-06-25 12:00 UTC)', at: NOW },
  { id: 'at-2026-07-02', label: '2026-07-02 (po wejściu P-5 i D-2)', at: '2026-07-02T00:00:00Z' },
];

const base = {
  offerId: OFFER,
  actor: 'sales.nowak',
  scope: 'catalog-sales',
  now: NOW,
  currency: 'PLN',
  sources: { PLN: SOURCE_SINGLE },
  pricings: { PLN: pricingOf(SOURCE_SINGLE, OFFER, 'PLN', NOW) },
  currencies: ['PLN'],
  previewDates: [{ id: 'at-now', label: 'teraz (2026-06-25 12:00 UTC)', at: NOW }],
  previewAt: NOW,
  previewEnabled: true,
  removed: [],
  tab: 'all',
  form: null,
  draft: {},
  banner: '',
  bannerTone: '',
  backStory: null,
};

export default {
  title: 'Catalog/PricingView',
  render: (args) => mount(document.createElement('div'), args),
  argTypes: { now: { control: 'text' }, currency: { control: 'text' } },
  parameters: {
    referenceInstant: NOW,
    wallClock: 'nieużywany — current/scheduled/history i rabaty są już podzielone w ciele odpowiedzi zbudowanym dla args.now, a podgląd liczy się dla args.previewAt',
    servedBody: 'PricingReads (16): current[], scheduled[], history[], discounts{active,scheduled,expired}, effectivePreview; GET /pricing z 12 zwraca current jako obiekt i bez podglądu',
  },
};

export const CurrentOnly = {
  args: { ...base },
};

export const ChangePrice = {
  args: {
    ...base,
    form: 'change',
    draft: { amountMinor: 28900, closesId: 'P-1', newId: 'P-4' },
    banner: 'Zmiana ceny: bieżący wpis zostanie zamknięty, nowy dołączony — historia nie jest nadpisywana (16 RULE-1, 16 S2).',
    backStory: 'currentOnly',
  },
};

export const AfterChange = {
  args: {
    ...base,
    sources: { PLN: SOURCE_AFTER_CHANGE },
    pricings: { PLN: pricingOf(SOURCE_AFTER_CHANGE, OFFER, 'PLN', NOW) },
    banner: 'Po zmianie: P-1 jest w historii (zamknięty w tej chwili), P-4 jest bieżący od tego instantu — zakres połówkowo otwarty, więc wpis działa już od validFrom. Historia bez akcji (16 RULE-5).',
    bannerTone: 'ok',
  },
};

export const SchedulePrice = {
  args: {
    ...base,
    sources: { PLN: SOURCE_AFTER_CHANGE },
    pricings: { PLN: pricingOf(SOURCE_AFTER_CHANGE, OFFER, 'PLN', NOW) },
    form: 'schedule',
    draft: { amountMinor: 27900, validFrom: '2026-07-01T00:00:00Z' },
    banner: 'Zaplanowanie wpisu: trafia pod „zaplanowane”, żadne zdarzenie nie wychodzi z kontekstu do daty wejścia (16 S3, 03 RULE-4).',
    backStory: 'afterChange',
  },
};

export const ScheduledHistory = {
  args: {
    ...base,
    sources: { PLN: SOURCE_SERIES },
    pricings: { PLN: pricingOf(SOURCE_SERIES, OFFER, 'PLN', NOW) },
    previewDates: DATES_TWO,
    banner: 'Jeden model, trzy listy cen i trzy listy rabatów; podział policzony dla args.now. Podgląd na 2026-07-02 pokazuje efekt nałożenia P-5 i D-2 (16 RULE-4, 10 D3) — a po usunięciu P-5 tę datę zostawia bez ceny.',
    backStory: 'currentOnly',
  },
};

export const AddDiscount = {
  args: {
    ...base,
    sources: { PLN: SOURCE_SERIES },
    pricings: { PLN: pricingOf(SOURCE_SERIES, OFFER, 'PLN', NOW) },
    previewDates: DATES_TWO,
    previewAt: '2026-07-02T00:00:00Z',
    form: 'discount',
    draft: { kind: 'percentage', basisPoints: 12000, amountMinor: 2000, validFrom: '2026-07-01T00:00:00Z', validUntil: '2026-07-08T00:00:00Z' },
    backStory: 'scheduledHistory',
    banner: 'Rabat procentowy: 12000 bp jest poza zakresem 1…10000 — zapis nieaktywny z powodem (16 RULE-6, 16 S6).',
    bannerTone: 'bad',
  },
};

export const AddDiscountFixed = {
  args: {
    ...base,
    sources: { PLN: SOURCE_SERIES },
    pricings: { PLN: pricingOf(SOURCE_SERIES, OFFER, 'PLN', NOW) },
    previewDates: DATES_TWO,
    previewAt: '2026-07-02T00:00:00Z',
    form: 'discount',
    draft: { kind: 'fixed', basisPoints: 1000, amountMinor: 2000, currency: 'PLN', validFrom: '2026-07-01T00:00:00Z', validUntil: '2026-07-08T00:00:00Z' },
    backStory: 'scheduledHistory',
    banner: 'Rabat stały: waluta jest zablokowana do waluty ceny — jedyna dostępna opcja to PLN, EUR jest nieosiągalne (16 RULE-3, 16 S7).',
  },
};

export const MultiCurrency = {
  args: {
    ...base,
    sources: { PLN: SOURCE_PLN, EUR: SOURCE_EUR },
    pricings: { PLN: pricingOf(SOURCE_PLN, OFFER, 'PLN', NOW), EUR: pricingOf(SOURCE_EUR, OFFER, 'EUR', NOW) },
    currencies: ['PLN', 'EUR'],
    banner: 'Dwie waluty wycenione: przełącznik widoczny, jedno ciało odpowiedzi na walutę. Podgląd liczy się tylko dla wybranej waluty — sumy nigdy nie są mieszane (16 S8, 16 D2).',
    bannerTone: 'ok',
    backStory: 'scheduledHistory',
  },
};

export const NoPreview = {
  args: {
    ...base,
    sources: { PLN: SOURCE_SERIES },
    pricings: { PLN: pricingOf(SOURCE_SERIES, OFFER, 'PLN', NOW) },
    previewEnabled: false,
    banner: 'Wariant konfiguracji: catalog.pricing.preview = false — komponent effectivePreview nie jest renderowany (16 wariant no-preview).',
    backStory: 'scheduledHistory',
  },
};
