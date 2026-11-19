# Fulfillment prototypes — low-fidelity gallery

Specification artifact for views **15–18** of [`../spec.md`](../spec.md). It carries **no business logic, no mock
service and no backend**: every story owns its data and the renderer owns no state.

```sh
cd docs/specs/fulfillment/prototypes/.storybook
npm run storybook                        # http://localhost:6011 (127.0.0.1 only)
npm run build-storybook                  # -> .storybook/storybook-static (gitignored), gate: 18 index entries
npm run verify                           # CDP quality gate + click-through flows, exit 0 only if all pass
npm run shots                            # one .png per story into ../shots/
```

`node_modules` is a **symlink** to `services/catalog/.storybook/node_modules` (no second install, no `package-lock.json`
here). To make it self-contained instead, run `npm install` inside this directory — versions are pinned exactly.
The gate of record is `verify-storybook.frames.cjs` (`require('playwright')` resolves through that symlink).

## Stories — 18, one per meaningful state

| view | story ids |
| --- | --- |
| 15 `fulfillment-orders-list` | `--pending`, `--picking`, `--on-hold`, `--empty`, `--loading`, `--error` |
| 16 `fulfillment-pick-workstation` | `--scan-idle`, `--position-picked`, `--mixed`, `--all-packed`, `--blocked-on-hold` |
| 17 `fulfillment-issue-dashboard` | `--open-issues`, `--empty`, `--error` |
| 18 `fulfillment-customer-state` | `--ready-for-delivery`, `--in-transit`, `--held`, `--error` |

Click-through chains live in [`flows.json`](../.storybook/flows.json) and are executed by `npm run verify`.
Screenshots of every story are in [`../shots/`](shots/) — durable, readable at 1280 px.

## Truth rules this gallery obeys

- **No money anywhere** (spec.md D5): no price, amount, currency or VAT column.
- **One order, one timeline**: `OP-2026-0001`, `OP-2026-0002`, `OP-2026-0005` keep their processing state, assignee and
  position states consistent across views (02 / 03 examples are honoured; other stories are other moments of the same ids).
- **Every blocked action carries its reason**; an action with no target state renders disabled, never dead.
- **No invented vocabulary** where the elements are explicit: no `damaged` issue kind, no resolve/close action on 17,
  no unassign/release/transfer on 15, no ETA, waybill, stock, location or issue kind on 18.
- **17's rows name the product only** — a row cannot say "1 of 2" today (element 04 Q-04-5).
- Cross-actor navigation is **prototype chrome only**: the yellow strip switches surface (employee ↔ resolver ↔
  customer); no view offers an action of another view's actor.

## Two documented deviations from the generic prototype rules

1. `verify-storybook.frames.cjs` enumerates stories from `/index.json`, not from the sidebar links of the upstream
   `verify-storybook.cdp.js`. Storybook 8 renders only the expanded group's links (3 of 18) — the sidebar list is
   incomplete, so it cannot be the gate.
2. The issue dashboard has **no disabled `resolve` button**, only the explanatory line: element 17 says "one line of
   explanatory text instead of a disabled button" (blocking action, reason carried by the sentence).

## Housekeeping

- `npm run storybook` is the normal way to start it. A session may also leave the server running as a **user launchctl
  job** — no npm script creates one; it is submitted manually and survives the terminal:
  `launchctl remove com.bottega.fulfillment-storybook` stops it (verify with `launchctl list | grep bottega`).
