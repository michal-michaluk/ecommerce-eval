// Element 14 — DraftDescriptionView. Low-fi, thin: the story owns the data, the renderer owns no state.
const STORIES = {
  empty: 'catalog-draftdescriptionview--empty',
  inProgress: 'catalog-draftdescriptionview--in-progress',
  ready: 'catalog-draftdescriptionview--ready',
  reviewRequested: 'catalog-draftdescriptionview--review-requested',
};

function go(storyId) {
  try {
    window.parent.location.href = '?path=/story/' + storyId;
  } catch (e) {
    window.location.href = '?path=/story/' + storyId;
  }
}

const PROSE_THRESHOLD = 1000; // 08 QP-6, derived from the LEGO sample (1 626 chars)

const css = `
  :root { --ink:#1b1f24; --muted:#5c6672; --line:#d8dee6; --bg:#f6f7f9; --ok:#1f7a45; --bad:#b3261e; --warn:#8a5a00 }
  * { box-sizing:border-box }
  body { margin:0; font:14px/1.45 -apple-system,Segoe UI,Roboto,sans-serif; color:var(--ink); background:var(--bg) }
  header { display:flex; gap:12px; align-items:center; padding:10px 18px; background:#fff; border-bottom:1px solid var(--line) }
  header .role { margin-left:auto; font-size:12px; color:var(--muted) }
  main { display:grid; grid-template-columns:1fr 380px; gap:18px; padding:18px; max-width:1320px }
  section.card, aside.card { background:#fff; border:1px solid var(--line); border-radius:8px; padding:14px }
  h1 { font-size:18px; margin:0 0 4px }
  h2 { font-size:13px; text-transform:uppercase; letter-spacing:.04em; color:var(--muted); margin:14px 0 8px }
  .row { display:grid; grid-template-columns:180px 1fr; gap:10px; padding:5px 0; border-bottom:1px dashed var(--line) }
  .row:last-child { border-bottom:0 }
  .row .k { color:var(--muted) }
  .empty { color:var(--muted); font-style:italic }
  .block { border:1px solid var(--line); border-radius:6px; padding:8px 10px; margin:8px 0 }
  .block .tag { font-size:11px; color:var(--muted); text-transform:uppercase }
  .media { display:flex; gap:10px; flex-wrap:wrap }
  .thumb { width:92px; height:69px; border:1px solid var(--line); border-radius:6px; display:grid; place-items:center; font-size:11px; color:var(--muted); background:#fbfcfd; text-align:center }
  .thumb.primary { outline:2px solid var(--ok) }
  .findings { padding-left:18px; margin:0 }
  .findings li { margin:6px 0 }
  .sev-blocking { color:var(--bad); font-weight:600 }
  .sev-warning { color:var(--warn); font-weight:600 }
  .okline { color:var(--ok); font-weight:600 }
  .actions { display:flex; gap:10px; margin-top:14px; flex-wrap:wrap }
  button { font:inherit; padding:9px 14px; border-radius:6px; border:1px solid var(--line); background:#fff; cursor:pointer }
  button.primary { background:var(--ink); color:#fff; border-color:var(--ink) }
  button[disabled] { opacity:.5; cursor:not-allowed }
  .why { font-size:12px; color:var(--muted); margin-top:6px }
`;

const finding = (f) =>
  `<li><span class="sev-${f.severity}">${f.rule}</span> ${f.message}</li>`;

const media = (items) =>
  items.length
    ? items
        .map(
          (m) =>
            `<div class="thumb${m.primary ? ' primary' : ''}">${m.id}<br>${m.kind === 'video' ? 'wideo ' + m.durationSec + ' s' : m.primary ? 'główne' : ''}</div>`,
        )
        .join('')
    : '<div class="thumb">brak zdjęć</div>';

const blocks = (list) =>
  list.length
    ? list
        .map((b) =>
          b.type === 'bulletList'
            ? `<div class="block"><span class="tag">bulletList</span> ${b.items.length} punkty</div>`
            : `<div class="block"><span class="tag">${b.type}</span> ${b.text}</div>`,
        )
        .join('')
    : '<div class="block"><span class="tag">paragraph</span> <span class="empty">— brak treści</span></div>';

function render(d) {
  const blocking = d.findings.filter((f) => f.severity === 'blocking').length;
  const requestEnabled = d.offerState === 'draft' && blocking === 0 && !!d.reviewTarget;

  return `
  <style>${css}</style>
  <header>
    <strong>catalog</strong><span style="color:var(--muted)">/ oferty / ${d.offerId}</span>
    <span class="role">Zalogowany: <b>${d.actor}</b> · zakres: <code>${d.scope}</code></span>
  </header>
  <main>
    <section class="card">
      <h1>${d.title}</h1>
      ${d.banner ? `<p class="${d.bannerTone}">${d.banner}</p>` : ''}
      <h2>Opis produktu</h2>
      <div class="row"><span class="k">Numer produktu</span><span>${d.identifiers.productNumber}</span></div>
      <div class="row"><span class="k">EAN (GTIN)</span><span class="${d.identifiers.eanGtin ? '' : 'empty'}">${d.identifiers.eanGtin ?? '— brak'}</span></div>
      <div class="row"><span class="k">Kategoria</span><span>${d.categoryPath.join(' / ')}</span></div>
      <div class="row"><span class="k">Parametry</span><span class="${d.parameters.length ? '' : 'empty'}">${d.parameters.length ? d.parameters.map((p) => p.code).join(' · ') : '— brak (LEGO wymaga: condition, childAge, pieceCount, gender, certificates, safetyInfo)'}</span></div>
      <h2>Opis</h2>
      ${blocks(d.blocks)}
      ${d.prose ? `<p class="empty">Suma treści: ${d.prose} znaków (próg ${PROSE_THRESHOLD}).</p>` : ''}
      <h2>Zdjęcia</h2>
      <div class="media">${media(d.media)}</div>
      <h2>Dostępność</h2>
      <div class="row"><span class="k">Data dostępności</span><span class="${d.availabilityStart ? '' : 'empty'}">${d.availabilityStart ?? '— brak'}</span></div>
    </section>

    <aside class="card">
      <h2>Kontrola jakości (08 QualityPolicy)</h2>
      ${d.findings.length ? `<ul class="findings">${d.findings.map(finding).join('')}</ul>` : '<p class="okline">Brak uwag — 0 reguł blokujących, 0 ostrzeżeń.</p>'}
      <div class="actions">
        ${d.nextLabel ? `<button class="primary" id="next">${d.nextLabel}</button>` : ''}
        <button ${requestEnabled ? 'class="primary"' : 'disabled'} id="request">Poproś o przegląd</button>
      </div>
      <p class="why">${d.why}</p>
    </aside>
  </main>`;
}

function mount(canvasElement, d) {
  canvasElement.innerHTML = render(d);
  const next = canvasElement.querySelector('#next');
  if (next && d.nextStory) next.addEventListener('click', () => go(STORIES[d.nextStory]));
  const request = canvasElement.querySelector('#request');
  if (request && !request.disabled) request.addEventListener('click', () => go(STORIES.reviewRequested));
  return canvasElement;
}

const base = {
  offerId: 'PRD-75423',
  actor: 'ContentAuthor',
  scope: 'catalog-author',
  title: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423",
  categoryPath: ['Dziecko', 'Zabawki', 'Klocki', 'LEGO', 'Zestawy', 'Star Wars'],
  offerState: 'draft',
  availabilityStart: null,
  parameters: [],
  blocks: [],
  media: [],
  prose: 0,
  findings: [],
  why: '',
};

export default {
  title: 'Catalog/DraftDescriptionView',
  render: (args) => mount(document.createElement('div'), args),
  argTypes: { nextLabel: { control: 'text' }, why: { control: 'text' } },
};

export const Empty = {
  args: {
    ...base,
    identifiers: { productNumber: '75423', eanGtin: null },
    findings: [
      { rule: 'QP-2', severity: 'blocking', message: 'EAN (GTIN) brak' },
      { rule: 'QP-4', severity: 'blocking', message: 'brak zdjęcia' },
      { rule: 'QP-5', severity: 'blocking', message: 'brak zdjęcia głównego' },
      { rule: 'QP-6', severity: 'blocking', message: 'opis 0 / 1000 znaków, brak akapitu' },
      { rule: 'QP-10', severity: 'blocking', message: 'brakuje: condition, childAge, pieceCount, gender, certificates, safetyInfo' },
    ],
    why: '„Poproś o przegląd” nieaktywne: 5 reguł blokujących (QP-2, QP-4, QP-5, QP-6, QP-10).',
    nextLabel: 'Uzupełnij szkic',
    nextStory: 'inProgress',
    banner: 'Szkic utworzony przed chwilą — puste pola nie blokują edycji, blokują publikację.',
    bannerTone: 'empty',
  },
};

export const InProgress = {
  args: {
    ...base,
    identifiers: { productNumber: '75423', eanGtin: '5702017817705' },
    availabilityStart: '2026-07-01',
    parameters: [{ code: 'condition' }, { code: 'childAge' }, { code: 'pieceCount' }, { code: 'gender' }, { code: 'certificates' }, { code: 'safetyInfo' }],
    blocks: [
      { type: 'paragraph', text: "Odkryj nową galaktykę LEGO® Star Wars™ dzięki interaktywnej zabawce konstrukcyjnej X-Wing™ Czerwona Piątka Luke'a (75423)…" },
      { type: 'paragraph', text: 'Posłuchaj ryku silnika podczas lotu myśliwca X-wing. Aktywuj dźwięki strzałów laserowych…' },
      { type: 'bulletList', items: ['a', 'b', 'c'] },
    ],
    media: [
      { id: 'M-01', kind: 'image', primary: true },
      { id: 'M-02', kind: 'image', primary: false },
      { id: 'M-03', kind: 'video', primary: false, durationSec: 18 },
    ],
    prose: 620,
    findings: [{ rule: 'QP-6', severity: 'blocking', message: 'opis 620 / 1000 znaków' }],
    why: '„Poproś o przegląd” nieaktywne: 1 reguła blokująca (QP-6).',
    nextLabel: 'Dopisz brakujące 380 znaków',
    nextStory: 'ready',
    banner: 'Autozapis 12:04. Zdjęcia dodaje fotograf — ten ekran nie pozwala ich zmieniać.',
    bannerTone: 'empty',
  },
};

export const Ready = {
  args: {
    ...base,
    identifiers: { productNumber: '75423', eanGtin: '5702017817705' },
    availabilityStart: '2026-07-01 — oferta ukryta do tej daty',
    parameters: [{ code: 'condition' }, { code: 'childAge' }, { code: 'pieceCount' }, { code: 'gender' }, { code: 'certificates' }, { code: 'safetyInfo' }],
    blocks: [
      { type: 'paragraph', text: 'Odkryj nową galaktykę LEGO® Star Wars™…' },
      { type: 'paragraph', text: 'Posłuchaj ryku silnika podczas lotu myśliwca X-wing…' },
      { type: 'paragraph', text: 'Dzięki aplikacji LEGO SMART Assist możesz pomóc dzieciom rozpocząć zabawę…' },
      { type: 'bulletList', items: new Array(8).fill('x') },
    ],
    media: [
      { id: 'M-01', kind: 'image', primary: true },
      { id: 'M-02', kind: 'image', primary: false },
      { id: 'M-03', kind: 'video', primary: false, durationSec: 18 },
    ],
    prose: 1626,
    findings: [],
    why: 'Zgłoszenie zamraża kandydata (04 RULE-3) — dalsze edycje nie zmienią tego, co zobaczy recenzent.',
    reviewTarget: 'reviewRequested',
    banner: 'Szkic spełnia politykę jakości — można zgłosić do przeglądu.',
    bannerTone: 'okline',
  },
};

export const ReviewRequested = {
  args: {
    ...base,
    offerState: 'review-requested',
    identifiers: { productNumber: '75423', eanGtin: '5702017817705' },
    availabilityStart: '2026-07-01',
    parameters: [{ code: 'condition' }, { code: 'pieceCount' }],
    blocks: [{ type: 'paragraph', text: 'Odkryj nową galaktykę LEGO® Star Wars™…' }],
    media: [{ id: 'M-01', kind: 'image', primary: true }],
    prose: 1626,
    findings: [],
    why: '„Edytuj szkic” nieaktywne: przegląd otwarty (04 RULE-1). Recenzent: reviewer.kowalska.',
    nextLabel: 'Wycofaj zgłoszenie',
    nextStory: 'inProgress',
    banner: 'Przegląd zgłoszony — RV-12. Szkic zablokowany do decyzji recenzenta.',
    bannerTone: 'empty',
  },
};
