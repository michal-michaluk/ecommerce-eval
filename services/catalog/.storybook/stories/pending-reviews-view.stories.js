// Element 15 — PendingReviewsView. Low-fi, thin: the story owns the data, the renderer owns no state.
//
// Two served bodies and nothing else drive the screen:
//   GET /reviews?page=&size=                → {items[{reviewId, offerId, title, requestedBy, requestedAt, waitingFor,
//                                              findingsCount}], page{number,size,totalElements,totalPages}}
//   GET /offers/{offerId}/review            → {reviewId, offerId, state, requestedBy, requestedAt, reviewedBy,
//                                              reviewedAt, reason, publishedOfferVersion,
//                                              candidate{description, media, availabilityStart},
//                                              findings[{rule, field, severity, message}],
//                                              override: {actor, recordedAt, findings[]} | null}
// `state`, `findings` and `override` are read from that one review object, never duplicated beside it.
const STORIES = {
  pendingList: 'catalog-pendingreviewsview--pending-list',
  ownRequestFiltered: 'catalog-pendingreviewsview--own-request-filtered',
  listEmpty: 'catalog-pendingreviewsview--list-empty',
  listAfterDecision: 'catalog-pendingreviewsview--list-after-decision',
  reviewBlocked: 'catalog-pendingreviewsview--review-blocked',
  reviewOverridden: 'catalog-pendingreviewsview--review-with-author-override',
  reviewReady: 'catalog-pendingreviewsview--review-ready',
  reviewNoDiff: 'catalog-pendingreviewsview--review-no-diff',
  rejectNeedsReason: 'catalog-pendingreviewsview--reject-needs-reason',
  reviewAccepted: 'catalog-pendingreviewsview--review-accepted',
  reviewRejected: 'catalog-pendingreviewsview--review-rejected',
  reviewDecidedElsewhere: 'catalog-pendingreviewsview--review-decided-elsewhere',
  requesterWithdraw: 'catalog-pendingreviewsview--requester-withdraw',
  requesterWithdrawn: 'catalog-pendingreviewsview--requester-withdrawn',
};

function go(storyId) {
  try {
    window.parent.location.href = '?path=/story/' + storyId;
  } catch (e) {
    window.location.href = '?path=/story/' + storyId;
  }
}

// The reference instant every story is rendered against; nothing here reads the wall clock.
const NOW = '2026-06-15T09:10:00Z';

const css = `
  :root { --ink:#1b1f24; --muted:#5c6672; --line:#d8dee6; --bg:#f6f7f9; --ok:#1f7a45; --bad:#b3261e; --warn:#8a5a00 }
  * { box-sizing:border-box }
  body { margin:0; font:14px/1.45 -apple-system,Segoe UI,Roboto,sans-serif; color:var(--ink); background:var(--bg) }
  header { display:flex; gap:12px; align-items:center; padding:10px 18px; background:#fff; border-bottom:1px solid var(--line) }
  header .role { margin-left:auto; font-size:12px; color:var(--muted) }
  main { display:grid; grid-template-columns:1fr 380px; gap:18px; padding:18px; max-width:1320px; align-items:start }
  main.wide { grid-template-columns:1fr }
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
  .empty { color:var(--muted); font-style:italic }
  .row { display:grid; grid-template-columns:180px 1fr; gap:10px; padding:5px 0; border-bottom:1px dashed var(--line) }
  .row:last-child { border-bottom:0 }
  .row .k { color:var(--muted) }
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
  .why.bad { color:var(--bad) }
  textarea { font:inherit; width:100%; min-height:64px; border:1px solid var(--line); border-radius:6px; padding:8px }
  .field { margin:8px 0 }
  .field .err { color:var(--bad); font-size:12px }
  code { background:#f1f3f6; border-radius:4px; padding:1px 4px; font-size:12px }
`;

// The wire carries an RFC 3339 duration (PendingReviewsReads.waitingFor) and Java's Duration.toString() never emits days,
// so the view is what turns PT24H into "1 d 0 h" — the reading 15's sample documents.
function waited(duration) {
  const parts = /^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$/.exec(duration);
  const total = Number(parts?.[1] ?? 0) * 86400 + Number(parts?.[2] ?? 0) * 3600 + Number(parts?.[3] ?? 0) * 60 + Number(parts?.[4] ?? 0);
  const days = Math.floor(total / 86400);
  const hours = Math.floor((total % 86400) / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  if (days > 0) return days + ' d ' + hours + ' h';
  if (hours > 0) return hours + ' h ' + minutes + ' min';
  return minutes + ' min';
}

// Duration.toString() of the reference instant minus requestedAt, so the fixtures say exactly what the projection would send.
function wireWait(requestedAt, now) {
  const seconds = Math.max(0, Math.floor((Date.parse(now) - Date.parse(requestedAt)) / 1000));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const rest = seconds % 60;
  return 'PT' + (hours ? hours + 'H' : '') + (minutes ? minutes + 'M' : '') + (rest || (!hours && !minutes) ? rest + 'S' : '');
}

const header = (d) => `
  <header>
    <strong>catalog</strong><span style="color:var(--muted)">/ przeglądy</span>
    <span class="role">Zalogowany: <b>${d.actor}</b> · zakres: <code>${d.scope}</code> · teraz: <code>${d.now}</code></span>
  </header>`;

const ownFilterNote = (d) => `
  <p class="note">Zapytanie listy: <code>findAllByStateAndRequestedByNotOrderByRequestedAtAsc</code> — własne zgłoszenia są odfiltrowane w zapytaniu, nie wyłączone w wierszu (15 RULE-4, 04 RULE-2). Odjęte: ${d.filteredOut.length}
    ${d.filteredOut.length && d.showFiltered ? `(<code>${d.filteredOut.map((r) => r.reviewId).join(', ')}</code>)` : ''}
    ${d.filteredOut.length && !d.showFiltered ? '— <button id="show-own" data-story="ownRequestFiltered">Pokaż, co odfiltrowano</button>' : ''}</p>
  ${d.showFiltered
    ? `<div class="block"><span class="tag">odfiltrowane zgłoszenia tej przeglądającej</span>
         ${d.filteredOut
           .map(
             (r) => `<div style="margin-top:6px">${r.reviewId} · ${r.title} · zgłosił ${r.requestedBy} · ustalenia: ${r.findingsCount}
               <div class="note">Nie są częścią <code>items</code> ani <code>page.totalElements</code>: nie da się ich otworzyć ani rozstrzygnąć, bo recenzent nie decyduje o własnym zgłoszeniu.</div></div>`,
           )
           .join('')}
       </div>`
    : ''}`;

function listView(d) {
  const rows = d.items
    .map(
      (r) => `
      <tr>
        <td class="num">${r.reviewId}</td>
        <td>${r.title}<div class="note" style="margin:2px 0 0">${r.offerId}</div></td>
        <td>${r.requestedBy}</td>
        <td class="num">${r.requestedAt}</td>
        <td class="num">${waited(r.waitingFor)}<div class="note" style="margin:2px 0 0">${r.waitingFor}</div></td>
        <td class="num ${r.findingsCount ? '' : 'okline'}">${r.findingsCount}</td>
        <td>${d.routes[r.reviewId] ? `<button id="open-${r.reviewId}" data-story="${d.routes[r.reviewId]}">Otwórz przegląd</button>` : '<span class="empty">—</span>'}</td>
      </tr>`,
    )
    .join('');

  return `
    ${header(d)}
    <main class="wide">
      <section class="card">
        <h1>Oferty oczekujące na przegląd</h1>
        <p class="note">Kolejność: najstarsze zgłoszenie pierwsze (15 RULE-1). <code>waitingFor</code> przychodzi jako czas trwania RFC 3339 policzony przy renderze z <code>requestedAt</code>; <code>findingsCount</code> to liczba ustaleń zamrożonego kandydata przeglądu, którego wiersz otwiera.</p>
        ${d.banner ? `<p class="banner ${d.bannerTone || ''}">${d.banner}</p>` : ''}
        ${d.items.length
          ? `<table>
               <tr><th>Przegląd</th><th>Oferta</th><th>Zgłosił</th><th>Zgłoszono</th><th>Czeka</th><th>Ustalenia</th><th></th></tr>
               ${rows}
             </table>`
          : '<p class="empty">Brak ofert oczekujących na przegląd</p>'}
        <p class="note">Strona ${d.page.number} · ${d.page.totalElements} zgłoszeń · rozmiar ${d.page.size} · ${d.page.totalPages} stron. Lista odświeża się po <code>ReviewRequested</code> i usuwa wiersz po <code>ReviewAccepted</code>, <code>ReviewRejected</code>, <code>ReviewWithdrawn</code> (RULE-6).</p>
        ${ownFilterNote(d)}
        ${d.stale ? `<p class="note">Karta otwarta wcześniej w innej zakładce: <button id="${d.stale.id}" data-story="${d.stale.story}">${d.stale.label}</button></p>` : ''}
        <div class="actions">
          ${d.refreshStory ? `<button class="primary" id="refresh" data-story="${d.refreshStory}">Odśwież listę</button>` : ''}
          ${d.backStory ? `<button id="back" data-story="${d.backStory}">Wróć do listy</button>` : ''}
        </div>
      </section>
    </main>`;
}

const blocks = (list) =>
  list
    .map((b) =>
      b.type === 'bulletList'
        ? `<div class="block"><span class="tag">bulletList</span> ${b.items.length} punkty</div>`
        : `<div class="block"><span class="tag">${b.type}</span> ${b.text}</div>`,
    )
    .join('');

const media = (items) =>
  items.length
    ? items
        .map((m) => `<div class="thumb${m.primary ? ' primary' : ''}">${m.mediaId}<br>${m.kind === 'VIDEO' ? 'wideo' : m.primary ? 'główne' : 'zdjęcie'}<br>poz. ${m.position}</div>`)
        .join('')
    : '<div class="thumb">brak zdjęć</div>';

// ProductDescription.Parameter.Value is a sealed carrier, so the wire value is a one-key object: {text}, {value}, {from, unit}, {values}.
function parameterValue(value) {
  if (value.text !== undefined) return value.text;
  if (value.value !== undefined) return String(value.value);
  if (value.from !== undefined) return 'od ' + value.from + ' ' + (value.unit ?? '');
  if (value.values !== undefined) return value.values.join(', ');
  if (value.date !== undefined) return value.date;
  return '—';
}

const parameters = (list) =>
  list.length
    ? list.map((p) => `<div class="row"><span class="k">${p.label}</span><span>${parameterValue(p.value)}</span></div>`).join('')
    : '<p class="empty">— brak parametrów</p>';

const findingsGroup = (list, severity) => {
  const of = list.filter((f) => f.severity === severity);
  if (!of.length) return '<p class="empty">— brak</p>';
  return `<ul class="findings">${of
    .map((f) => `<li><span class="sev-${severity.toLowerCase()}">${f.rule}</span> <code>${f.field}</code> — ${f.message}</li>`)
    .join('')}</ul>`;
};

function decisionBlock(review) {
  if (review.state === 'requested') return '';
  return `
    <h2>Decyzja</h2>
    <div class="row"><span class="k">Stan</span><span>${review.state}</span></div>
    <div class="row"><span class="k">Zdecydował</span><span>${review.reviewedBy ?? '—'}</span></div>
    <div class="row"><span class="k">Kiedy</span><span>${review.reviewedAt ?? '—'}</span></div>
    <div class="row"><span class="k">Opublikowana wersja</span><span>${review.publishedOfferVersion ?? '— (nie opublikowano)'}</span></div>
    ${review.state === 'rejected' ? `<div class="row"><span class="k">Powód</span><span>${review.reason}</span></div>` : ''}`;
}

function overrideBlock(d, review) {
  if (!review.override) {
    return `<p class="note">Override autora: brak (<code>override: null</code>). Push autora poza bramkę jakości jest możliwy tylko przy <code>catalog.publish.override.enabled = true</code>${d.config.overrideEnabled ? '' : ' — tutaj wyłączonym, więc przegląd z ustaleniami blokującymi nie mógłby nawet powstać (04 RULE-8, 15 S3)'}.</p>`;
  }
  if (!d.config.overrideEnabled) {
    return `<p class="note">Override autora (${review.override.actor}, ${review.override.recordedAt}: ${review.override.findings.join(', ')}) — blok ukryty, <code>catalog.publish.override.enabled = false</code>.</p>`;
  }
  return `
    <div class="block">
      <span class="tag">Override autora (04 RULE-8)</span>
      <div>${review.override.actor} · ${review.override.recordedAt}</div>
      <div>Przejęte ustalenia: ${review.override.findings.map((r) => `<code>${r}</code>`).join(' ')}</div>
      <p class="why">Override tylko <b>otworzył</b> przegląd. Nie jest upoważnieniem do publikacji: przeglądający nie ma własnego override, a 09 R2 odmawia akceptacji dopóki kandydat niesie ustalenie blokujące (15 RULE-3, D27, F15).</p>
    </div>`;
}

function reviewView(d) {
  const review = d.review;
  const blocking = review.findings.filter((f) => f.severity === 'BLOCKING');
  const warnings = review.findings.filter((f) => f.severity === 'WARNING');
  const open = review.state === 'requested';
  const reviewer = d.role === 'reviewer';
  const requester = d.actor === review.requestedBy;
  const canAccept = open && reviewer && blocking.length === 0;
  const canReject = open && reviewer;
  const canWithdraw = open && requester;

  const acceptWhy = canAccept
    ? 'Akceptacja dozwolona: brak ustaleń blokujących (09 R1).'
    : !open
      ? `„Akceptuj” nieaktywne: przegląd nie jest otwarty — stan ${review.state}, decyzja ${review.reviewedBy} ${review.reviewedAt ?? ''} (12 ${d.refusal ?? 'REVIEW_NOT_OPEN'}).`
      : !reviewer
        ? 'Decyzje przeglądającego: nie renderowane — zakres tej sesji to catalog-author (12, auth matrix).'
        : `„Akceptuj” nieaktywne: ${blocking.length} ustalenia blokujące (${blocking.map((f) => f.rule).join(', ')}). Przeglądający nie ma override (15 RULE-3, 09 R2 — API odpowiada 409 QUALITY_FINDINGS)${review.override ? '; override autora pozwolił tylko otworzyć przegląd' : ''}.`;

  const rejectWhy = canReject ? 'Odrzucenie wymaga powodu (15 RULE-5, 04 RULE-5).' : `„Odrzuć” nieaktywne: ${!open ? 'przegląd nie jest otwarty' : 'zakres tej sesji to catalog-author'} (12 ${d.refusal ?? 'REVIEW_NOT_OPEN'}).`;

  const withdrawWhy = caller(review, d, canWithdraw);

  return `
    ${header(d)}
    <main>
      <section class="card">
        <h1>${review.candidate.description.title}</h1>
        <p class="note">${review.reviewId} · ${review.offerId} · stan <b>${review.state}</b> · zgłosił ${review.requestedBy} ${review.requestedAt} · czeka ${waited(wireWait(review.requestedAt, d.now))} (${wireWait(review.requestedAt, d.now)})</p>
        ${d.banner ? `<p class="banner ${d.bannerTone || ''}">${d.banner}</p>` : ''}
        <h2>Kandydat zamrożony przy zgłoszeniu — tylko do odczytu (RULE-1, 04 RULE-3)</h2>
        <div class="row"><span class="k">Numer produktu</span><span>${review.candidate.description.identifiers.productNumber}</span></div>
        <div class="row"><span class="k">EAN (GTIN)</span><span>${review.candidate.description.identifiers.eanGtin}</span></div>
        <div class="row"><span class="k">Marka</span><span>${review.candidate.description.brand.name}${review.candidate.description.brand.verified ? ' · zweryfikowana' : ''}</span></div>
        <div class="row"><span class="k">Stan produktu</span><span>${review.candidate.description.condition}</span></div>
        <div class="row"><span class="k">Kategoria</span><span>${review.candidate.description.categoryPath.join(' / ')}</span></div>
        <div class="row"><span class="k">Dostępność od</span><span>${review.candidate.availabilityStart ?? '— brak'}</span></div>
        <h2>Parametry</h2>
        ${parameters(review.candidate.description.parameters)}
        <h2>Opis</h2>
        ${blocks(review.candidate.description.blocks)}
        <h2>Zdjęcia</h2>
        <div class="media">${media(review.candidate.media)}</div>
        ${d.config.diff
          ? `<h2>Diff wobec wersji opublikowanej</h2>
             <div class="row"><span class="k">dostępność od</span><span>v1: 2026-06-01 → kandydat: ${review.candidate.availabilityStart ?? 'brak'}</span></div>
             <div class="row"><span class="k">zdjęcia</span><span>v1: 1 zdjęcie (główne M-00) → kandydat: ${review.candidate.media.length} zdjęcia, brak głównego</span></div>`
          : '<p class="note">Panel diffu ukryty: <code>catalog.review.diff = false</code> (wariant no-diff).</p>'}
      </section>

      <aside class="card">
        <h2>Ustalenia jakości — zamrożone, nie przeliczane (RULE-2)</h2>
        <div class="tag" style="font-size:11px;color:var(--bad);text-transform:uppercase">blokujące (${blocking.length})</div>
        ${findingsGroup(review.findings, 'BLOCKING')}
        <div class="tag" style="font-size:11px;color:var(--warn);text-transform:uppercase">ostrzeżenia (${warnings.length})</div>
        ${findingsGroup(review.findings, 'WARNING')}
        ${blocking.length === 0 ? '<p class="okline" style="font-size:12px">Brak ustaleń blokujących — kandydat gotowy do akceptacji (09 R1).</p>' : ''}
        ${overrideBlock(d, review)}
        ${decisionBlock(review)}
        <div class="actions">
          ${reviewer ? `<button class="primary" id="accept" ${canAccept ? `data-story="${d.acceptStory ?? 'reviewAccepted'}"` : 'disabled'}>Akceptuj i opublikuj</button>` : ''}
          ${reviewer ? `<button id="open-reject" ${canReject ? `data-story="${d.rejectStory ?? 'rejectNeedsReason'}"` : 'disabled'}>Odrzuć</button>` : ''}
          ${canWithdraw ? `<button id="withdraw" data-story="${d.withdrawStory ?? 'requesterWithdrawn'}">Wycofaj zgłoszenie</button>` : ''}
        </div>
        ${reviewer ? `<p class="why ${canAccept ? '' : 'bad'}">${acceptWhy}</p><p class="why ${canReject ? '' : 'bad'}">${rejectWhy}</p>` : ''}
        <p class="why ${canWithdraw ? '' : 'bad'}">${withdrawWhy}</p>
        ${d.panel === 'reject' ? rejectPanel(d) : ''}
        <div class="actions">${d.backStory ? `<button id="back" data-story="${d.backStory}">${d.backLabel ?? 'Wróć do listy'}</button>` : ''}</div>
      </aside>
    </main>`;
}

function caller(review, d, canWithdraw) {
  if (canWithdraw) return 'Wycofanie dozwolone: sesja należy do zgłaszającego i przegląd jest otwarty (04 RULE-6, 12 PATCH decision=withdrawn).';
  if (review.requestedBy === d.actor) return `„Wycofaj zgłoszenie” nieaktywne: przegląd nie jest otwarty (stan ${review.state}).`;
  return `Wycofanie nie jest renderowane: widzi je tylko zgłaszający (${review.requestedBy}), a przeglądający nigdy nie widzi własnego zgłoszenia (15 RULE-4, 04 RULE-6, D50).`;
}

const rejectPanel = (d) => `
  <h2>Powód odrzucenia</h2>
  <div class="field">
    <textarea id="reason" placeholder="Powód odrzucenia (wymagany)">${d.reasonDraft}</textarea>
    ${d.reasonError ? `<div class="err">${d.reasonError}</div>` : ''}
  </div>
  <div class="actions">
    <button id="fill-reason" data-set="reasonDraft=Opis bez akapitu — QP-6 nie spełnione. Zdjęcie główne dodane po zgłoszeniu, kandydat go nie ma.|reasonError=">Wpisz przykładowy powód</button>
    <button class="primary" id="reject" ${d.reasonDraft.trim() ? `data-story="${d.rejectDoneStory ?? 'reviewRejected'}"` : 'disabled'}>Odrzuć przegląd</button>
  </div>
  <p class="why ${d.reasonDraft.trim() ? '' : 'bad'}">${d.reasonDraft.trim() ? 'Powód podany — odrzucenie dozwolone; oferta wraca do draftu, a wiersz znika z listy (RULE-5, RULE-6).' : '„Odrzuć przegląd” nieaktywne: powód jest pusty (15 RULE-5, 04 RULE-5).'}</p>`;

function render(d) {
  return `<style>${css}</style>${d.mode === 'list' ? listView(d) : reviewView(d)}`;
}

function setPath(target, path, value) {
  const keys = path.split('.');
  const leaf = keys.pop();
  const owner = keys.reduce((node, key) => node[key], target);
  owner[leaf] = value;
}

function mount(canvasElement, args) {
  const state = JSON.parse(JSON.stringify(args));
  const paint = () => {
    canvasElement.innerHTML = render(state);
  };
  canvasElement.addEventListener('click', (event) => {
    const target = event.target.closest('[data-story],[data-set]');
    if (!target || target.disabled) return;
    if (target.dataset.story) return go(STORIES[target.dataset.story]);
    (target.dataset.set || '').split('|').filter(Boolean).forEach((pair) => {
      const [path, value] = pair.split('=');
      setPath(state, path, value);
    });
    paint();
  });
  paint();
  return canvasElement;
}

const FINDINGS_BLOCKING = [
  { rule: 'QP-5', field: 'media.primary', severity: 'BLOCKING', message: 'brak zdjęcia głównego' },
  { rule: 'QP-6', field: 'description.blocks', severity: 'BLOCKING', message: 'opis bez akapitu' },
  { rule: 'QP-12', field: 'description.parameters', severity: 'WARNING', message: 'parametr „setName” nie jest wymagany, ale pomaga w wyszukiwaniu' },
];

const candidateOf = (title, productNumber, eanGtin) => ({
  description: {
    title,
    identifiers: { productNumber, eanGtin, setName: 'Star Wars' },
    brand: { name: 'LEGO', verified: true },
    condition: 'NEW',
    categoryPath: ['Dziecko', 'Zabawki', 'Klocki', 'LEGO', 'Zestawy', 'Star Wars'],
    parameters: [
      { code: 'condition', label: 'Stan', value: { text: 'Nowy' } },
      { code: 'pieceCount', label: 'Liczba elementów', value: { value: 581 } },
      { code: 'childAge', label: 'Wiek dziecka', value: { from: 6, unit: 'lat' } },
      { code: 'certificates', label: 'Certyfikaty, opinie, atesty', value: { values: ['CE', 'EN 71'] } },
    ],
    blocks: [
      { type: 'paragraph', text: 'Odkryj nową galaktykę LEGO® Star Wars™ dzięki interaktywnej zabawce konstrukcyjnej — zestaw posiada ruchome elementy i akcesoria do zabawy…' },
      { type: 'paragraph', text: 'Aktywuj dźwięki strzałów laserowych i monitoruj komunikację z centrum dowodzenia. Model reaguje na ruchy i zabawę dzieci…' },
      { type: 'bulletList', items: ['Interaktywna zabawka konstrukcyjna ożywia model', 'Klocek SMART umożliwia korzystanie z systemu SMART Play', 'Model emituje dźwięki i światła w reakcji na ruchy'] },
    ],
  },
  media: [
    { mediaId: 'M-01', kind: 'IMAGE', position: 1, primary: false, format: 'jpg', url: 'https://cdn.example/M-01.jpg' },
    { mediaId: 'M-02', kind: 'IMAGE', position: 2, primary: false, format: 'jpg', url: 'https://cdn.example/M-02.jpg' },
  ],
  availabilityStart: '2026-07-01',
});

const review = (over) => ({
  reviewId: 'RV-12',
  offerId: 'OFR-75423ABC',
  state: 'requested',
  requestedBy: 'author.nowak',
  requestedAt: '2026-06-14T09:10:00Z',
  reviewedBy: null,
  reviewedAt: null,
  reason: null,
  publishedOfferVersion: null,
  candidate: candidateOf("LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423", '75423', '5702017817705'),
  findings: FINDINGS_BLOCKING,
  override: null,
  ...over,
});

const RAVEN = review({
  reviewId: 'RV-13',
  offerId: 'OFR-75375XYZ',
  requestedBy: 'author.zielinski',
  requestedAt: '2026-06-14T16:40:00Z',
  candidate: candidateOf('LEGO Star Wars Sokół Millennium 75375 + LEGO Brelok na dzień Ojca', '75375', '5702017584171'),
  override: { actor: 'author.zielinski', recordedAt: '2026-06-14T16:40:00Z', findings: ['QP-5', 'QP-6'] },
});

const READY = review({
  reviewId: 'RV-17',
  offerId: 'OFR-75801QWE',
  requestedBy: 'author.kowalski',
  requestedAt: '2026-06-15T08:05:00Z',
  candidate: candidateOf('LEGO Technic Ciężarówka Volvo FMX 42175', '42175', '5702017602008'),
  findings: [],
  override: null,
});

const WITHDRAWN = review({
  reviewId: 'RV-18',
  offerId: 'OFR-75411OWN',
  state: 'withdrawn',
  requestedBy: 'author.nowak',
  requestedAt: '2026-06-15T07:15:00Z',
  reviewedBy: 'author.nowak',
  reviewedAt: '2026-06-15T09:00:00Z',
  candidate: candidateOf('LEGO Icons Bukiet róż 10328', '10328', '5702017582985'),
  findings: [],
  override: null,
});

const row = (r) => ({
  reviewId: r.reviewId,
  offerId: r.offerId,
  title: r.candidate.description.title,
  requestedBy: r.requestedBy,
  requestedAt: r.requestedAt,
  waitingFor: wireWait(r.requestedAt, NOW),
  findingsCount: r.findings.length,
});

const ITEMS = [row(review()), row(RAVEN), row(READY)];

const FILTERED_OUT = [
  {
    reviewId: 'RV-15',
    offerId: 'OFR-75399QWE',
    title: 'LEGO Archeologia Muzeum Historii Naturalnej 10326',
    requestedBy: 'reviewer.kowalska',
    requestedAt: '2026-06-14T18:05:00Z',
    waitingFor: wireWait('2026-06-14T18:05:00Z', NOW),
    findingsCount: 1,
  },
];

const ROUTES = {
  'RV-12': 'reviewBlocked',
  'RV-13': 'reviewOverridden',
  'RV-17': 'reviewReady',
};

const REQUESTED_BY_ACTOR = review({
  reviewId: 'RV-18',
  offerId: 'OFR-75411OWN',
  requestedBy: 'author.nowak',
  requestedAt: '2026-06-15T07:15:00Z',
  candidate: candidateOf('LEGO Icons Bukiet róż 10328', '10328', '5702017582985'),
  findings: [],
});

const base = {
  mode: 'list',
  actor: 'reviewer.kowalska',
  scope: 'catalog-reviewer',
  role: 'reviewer',
  now: NOW,
  items: ITEMS,
  routes: ROUTES,
  page: { number: 0, size: 20, totalElements: 3, totalPages: 1 },
  filteredOut: FILTERED_OUT,
  showFiltered: false,
  config: { overrideEnabled: true, diff: true },
  review: review(),
  reasonDraft: '',
  reasonError: null,
  refusal: null,
  banner: '',
  bannerTone: '',
  backStory: null,
  backLabel: null,
};

export default {
  title: 'Catalog/PendingReviewsView',
  render: (args) => mount(document.createElement('div'), args),
  argTypes: { now: { control: 'text' }, actor: { control: 'text' }, role: { control: 'text' } },
  parameters: {
    referenceInstant: NOW,
    wallClock: 'nieużywany — waitingFor jest liczone z requestedAt i args.now, więc żadna data w historiach nie wygasa',
    servedBodies: 'GET /reviews?page=&size= (list) i GET /offers/{offerId}/review (review); state/findings/override czytane wyłącznie z review',
  },
};

export const PendingList = {
  args: { ...base },
};

export const OwnRequestFiltered = {
  args: {
    ...base,
    showFiltered: true,
    backStory: 'pendingList',
    banner: 'Zapytanie listy nie zwraca zgłoszenia samej przeglądającej — RV-15 nie ma go ani w items, ani w page.totalElements. Widać tylko fakt odfiltrowania (15 RULE-4, 04 RULE-2).',
  },
};

export const ListEmpty = {
  args: {
    ...base,
    items: [],
    page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
    banner: 'Brak ofert oczekujących na przegląd',
    bannerTone: 'ok',
  },
};

export const ListAfterDecision = {
  args: {
    ...base,
    items: [row(review()), row(READY)],
    page: { number: 0, size: 20, totalElements: 2, totalPages: 1 },
    banner: 'RV-13 zniknęło z listy po odrzuceniu (RULE-6). Karta RV-13 z godziny 10:02 jest nadal otwarta w innej zakładce — jej decyzja zostanie odrzucona.',
    stale: { id: 'open-RV-13', story: 'reviewDecidedElsewhere', label: 'Otwórz kartę RV-13 z 10:02' },
    refreshStory: 'listEmpty',
  },
};

export const ReviewBlocked = {
  args: {
    ...base,
    mode: 'review',
    review: review(),
    config: { overrideEnabled: false, diff: true },
    backStory: 'listAfterDecision',
    banner: 'Ustalenia zamrożone przy zgłoszeniu: 2 blokujące, 1 ostrzeżenie. Override wyłączony konfiguracją, więc bloku override nie ma, a kandydat z ustaleniami blokującymi nie mógłby nawet zostać zgłoszony (wariant no-override, 15 S3).',
    bannerTone: 'bad',
  },
};

export const ReviewWithAuthorOverride = {
  args: {
    ...base,
    mode: 'review',
    review: RAVEN,
    backStory: 'listAfterDecision',
    banner: 'Autor zgłosił przegląd mimo ustaleń, przejmując QP-5 i QP-6 (04 RULE-8). Override jest zarejestrowany i widoczny, ale nie pozwala zaakceptować: 09 R2 nadal odmawia (15 RULE-3, F15, 15 S4).',
  },
};

export const ReviewReady = {
  args: {
    ...base,
    mode: 'review',
    review: READY,
    backStory: 'listAfterDecision',
    banner: 'Kandydat bez ustaleń blokujących — akceptacja dozwolona i opublikuje wersję (09 R1, D15).',
    bannerTone: 'ok',
  },
};

export const ReviewNoDiff = {
  args: {
    ...base,
    mode: 'review',
    review: READY,
    config: { overrideEnabled: true, diff: false },
    backStory: 'listAfterDecision',
    banner: 'Wariant konfiguracji: catalog.review.diff = false — panel porównania wobec wersji opublikowanej nie jest renderowany (15 wariant no-diff).',
  },
};

export const RejectNeedsReason = {
  args: {
    ...base,
    mode: 'review',
    review: review(),
    config: { overrideEnabled: false, diff: true },
    panel: 'reject',
    reasonDraft: '',
    reasonError: 'Odrzucenie bez powodu jest odrzucane: powód jest wymagany (15 RULE-5, 04 RULE-5).',
    backStory: 'reviewBlocked',
    backLabel: 'Wróć do przeglądu',
    banner: 'Przeglądający użył „Odrzuć” z pustym powodem — akcja odmówiona, stan bez zmian (15 S6).',
    bannerTone: 'bad',
  },
};

export const ReviewAccepted = {
  args: {
    ...base,
    mode: 'review',
    review: review({
      reviewId: 'RV-17',
      offerId: 'OFR-75801QWE',
      state: 'accepted',
      requestedBy: 'author.kowalski',
      requestedAt: '2026-06-15T08:05:00Z',
      reviewedBy: 'reviewer.kowalska',
      reviewedAt: NOW,
      publishedOfferVersion: 1,
      candidate: candidateOf('LEGO Technic Ciężarówka Volvo FMX 42175', '42175', '5702017602008'),
      findings: [],
    }),
    backStory: 'listAfterDecision',
    backLabel: 'Wróć do listy (wiersz usunięty)',
    banner: 'Zaakceptowano i opublikowano wersję 1 (D15). Wiersz RV-17 usunięty z listy (RULE-6).',
    bannerTone: 'ok',
  },
};

export const ReviewRejected = {
  args: {
    ...base,
    mode: 'review',
    review: review({
      reviewId: 'RV-13',
      offerId: 'OFR-75375XYZ',
      state: 'rejected',
      requestedBy: 'author.zielinski',
      requestedAt: '2026-06-14T16:40:00Z',
      reviewedBy: 'reviewer.kowalska',
      reviewedAt: NOW,
      reason: 'Opis bez akapitu — QP-6 nie spełnione. Zdjęcie główne dodane po zgłoszeniu, kandydat go nie ma.',
      candidate: candidateOf('LEGO Star Wars Sokół Millennium 75375 + LEGO Brelok na dzień Ojca', '75375', '5702017584171'),
      override: { actor: 'author.zielinski', recordedAt: '2026-06-14T16:40:00Z', findings: ['QP-5', 'QP-6'] },
    }),
    config: { overrideEnabled: false, diff: true },
    backStory: 'listAfterDecision',
    backLabel: 'Wróć do listy (wiersz usunięty)',
    banner: 'Odrzucono z powodem: oferta wraca do draftu, przegląd jest terminalny — żadna decyzja nie jest już możliwa (RULE-5, RULE-6, REVIEW_NOT_OPEN).',
  },
};

export const ReviewDecidedElsewhere = {
  args: {
    ...base,
    mode: 'review',
    review: review({
      reviewId: 'RV-13',
      offerId: 'OFR-75375XYZ',
      state: 'accepted',
      requestedBy: 'author.zielinski',
      requestedAt: '2026-06-14T16:40:00Z',
      reviewedBy: 'reviewer.nowak',
      reviewedAt: '2026-06-15T09:02:00Z',
      publishedOfferVersion: 1,
      candidate: candidateOf('LEGO Star Wars Sokół Millennium 75375 + LEGO Brelok na dzień Ojca', '75375', '5702017584171'),
      findings: [],
    }),
    refusal: 'REVIEW_NOT_OPEN',
    backStory: 'listAfterDecision',
    banner: 'Akcja odmówiona: REVIEW_NOT_OPEN — przegląd RV-13 rozstrzygnął reviewer.nowak o 09:02 w innej zakładce. Wiersz zniknął z listy po odświeżeniu (15 S7, RULE-6).',
    bannerTone: 'bad',
  },
};

export const RequesterWithdraw = {
  args: {
    ...base,
    mode: 'review',
    actor: 'author.nowak',
    scope: 'catalog-author',
    role: 'author',
    review: REQUESTED_BY_ACTOR,
    backStory: 'pendingList',
    backLabel: 'Wróć do listy przeglądów',
    banner: 'Sesja zgłaszającego: widzi „Wycofaj zgłoszenie” (04 RULE-6). Akcje przeglądającego nie są tu renderowane — decyzje należą do zakresu catalog-reviewer (12, auth matrix).',
  },
};

export const RequesterWithdrawn = {
  args: {
    ...base,
    mode: 'review',
    actor: 'author.nowak',
    scope: 'catalog-author',
    role: 'author',
    review: WITHDRAWN,
    backStory: 'pendingList',
    backLabel: 'Wróć do listy przeglądów',
    banner: 'Zgłoszenie wycofane: przegląd zamknięty, wiersz usunięty z listy przeglądających (RULE-6). Wycofanie widzi tylko zgłaszający (04 RULE-6, D50).',
    bannerTone: 'ok',
  },
};
