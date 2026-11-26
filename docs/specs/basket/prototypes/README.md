# Basket prototypes — low-fidelity gallery

Specification artifact for element **01 — Basket view** of [`../index.md`](../index.md). It carries **no business
logic, no mock service and no backend**: every story owns its data and the renderer owns no state.

```sh
cd docs/specs/basket/prototypes/.storybook
npm run storybook                        # http://127.0.0.1:6012 (127.0.0.1 only)
npm run build-storybook                  # -> .storybook/storybook-static (gitignored), gate: 8 index entries
npm run verify                           # CDP quality gate + click-through flows, exit 0 only if all pass
npm run shots                            # one .png per story into ../shots/
```

`node_modules` is a **symlink** to `services/catalog/.storybook/node_modules` (no second install, no `package-lock.json`
here). To make it self-contained instead, run `npm install` inside this directory — versions are pinned exactly.
The gate of record is `verify-storybook.frames.cjs` (`require('playwright')` resolves through that symlink).

## Stories — 8, one per meaningful state

| state | story id | register |
| --- | --- | --- |
| the screenshot verbatim | `basket-two-items--default` | `basket.json`, D8 |
| no items | `basket-empty--default` | Q10, D18 — copy is an assumption |
| item 2 unchecked | `basket-partial-selection--default` | Q12, D8 |
| item 2 out of stock, blocking | `basket-out-of-stock--default` | D33 (overrides R7, `[seg 70-71]`) |
| item 2's offer removed (`Niedostępny`) | `basket-unavailable--default` | Q11, D18 |
| shipment estimate (+ discount shown inside the price) | `basket-delivery-estimate--default` | D36, D22 — the date is illustrative; the discount is never subtracted twice |
| blocking problem on the row that causes it | `basket-blocked--default` | Q11, D18 — problem kind is an assumption |
| flow target only | `basket-two-items-removed` | Q10, D18 |

Click-through chains live in [`flows.json`](.storybook/flows.json) and are executed by `npm run verify`.
Screenshots of every story are in [`shots/`](shots/) — durable, readable at 1280 px.

## Truth rules this gallery obeys

- **Every number, label and string comes from `docs/raw/materials/basket.json`** unless it carries `(assumption)` in the
  view itself — not only in the footer note. An assumption is a register question answered with a default (Q10–Q12 → D18),
  never a discovered fact.
- **No invented identity**: the capture has no site header and the transcription records no login state, so the low-fi
  chrome carries no customer name.
- **A shown item price is the line total** for its quantity; delivery is priced per seller shipment (D8), so
  `164,88 + 9,99 = 174,87`.
- **Only counted items reach `Wartość produktów`**: an unavailable item is excluded and blocks the CTA (Q11/D18);
  an out-of-stock item stays counted but blocks the CTA (D33).
- **Every disabled `DOSTAWA I PŁATNOŚĆ` carries its reason**, and the row that causes the block names the problem. An
  enabled CTA carries no caption — annotations live in the footer note, never inside the view.
- The eight states are **one view at eight moments**; the yellow strip is prototype chrome (state switch), not an app action.

### Where `(assumption)` appears in the view

| state | marked in the view |
| --- | --- |
| empty / removed | copy + inline `Q10 default (assumption)` line |
| out of stock | `Chwilowo niedostępny` badge, the `blokuje DOSTAWĘ I PŁATNOŚĆ (D33)` badge and the CTA reason wording |
| unavailable | `nie wliczony w RAZEM ani w sumę` badge and the CTA reason wording |
| delivery estimate | `Dostawa: czw. 27 lis` and the `Rabat` row label |
| blocked | problem kind badge, `wliczony w sumę` badge and the CTA reason wording |

## Three documented deviations

1. Story ids cannot all be produced by Storybook's `title + export name` rule (state 8 has no `--<state>` suffix), so every
   story pins `parameters.__id` as a **string literal** — the static indexer reads `h.value.value`, so an identifier
   (`STORIES.twoItems`) silently falls back to `toId(title, exportName)`.
2. The whole-basket checkbox in `basket-two-items--default` is inert: unchecking it means "nothing selected", a state this
   gallery does not have (see Q12). The reverse direction (`basket-partial-selection--default` → `#toggle-all`) does navigate.
3. The out-of-stock row's checkbox in `basket-out-of-stock--default` is inert while the CTA reason tells the customer to deselect
   it (VIEW-2, D33): the deselected-but-still-marked state is a ninth state this gallery does not have, so no story is wired as
   its target and `flows.json` carries no such flow. The instruction is proven by `basket-partial-selection--default` instead.
## Housekeeping

- `npm run storybook` is the normal way to start it. A session may also leave the server running as a **user launchctl
  job** — no npm script creates one; `launchctl submit` is issued manually and survives the terminal:
  `launchctl remove com.bottega.basket-storybook` stops it (verify with `launchctl list | grep bottega`).
