# Offer fixture — the catalog the basket reads until the browsing slice lands

The service reads offer data through element 08's `OfferCatalog` port. In this increment the adapter behind it
(`basket/FixtureOfferCatalog`) answers from a **seeded fixture**: element 08 §Increments and workspace decision D38 defer the
provider's shop-facing offer read, and the basket slice has to be exercisable over HTTP before it exists. The fixture is a
stand-in, **not a catalogue** — it has no search, no browsing, no reservation, and no way to add one without widening the port
an ArchUnit rule forbids (R6). The browsing slice's adapter replaces it and this wiring does not change.

## Where the seed lives

The seed is deployment configuration, never compiled in and never in `src/main/resources`:

| Piece | Where |
| --- | --- |
| the document | `k8s/base/configmap.yaml`, key `offer-fixture.yaml` |
| its arrival in the pod | `k8s/base/deployment.yaml`: the key is mounted at `/etc/shopping/offer-fixture.yaml` and `SPRING_CONFIG_ADDITIONAL_LOCATION` points at it |
| the binding | `basket.offer-fixture.offers` → `BasketConfiguration.OfferFixture` → `FixtureOfferCatalog` |

`application.yml` deliberately carries no key of this block: the integration-test profile boots the same `application.yml`,
and a seed there would make every test see offers it never stated. A deployment that states no seed gets an **empty** catalog
— the shape every test keeps.

A malformed seed (a missing title, picture, price or currency, or a stock answer that is neither `notRead` nor a count) stops
the pod while the bean is created, like a bad `basket.currency`: an offer that answers `200` and describes nothing would
otherwise surface as `OFFER_READ_UNAVAILABLE` on the first add.

## The seeded offers

Every offer exists because a step of `docs/specs/basket/prototypes/basket-api.prove.md` names it. Prices are in fine units —
one digit finer than the currency's minor unit, so `99890` is `99,89 zł` (element 03). This table mirrors the ConfigMap;
`OfferSeedDocumentTest` binds the ConfigMap itself and is the authority when the two disagree.

| Offer | Seller | Current price | List price | Currency | State | Answers (per read of this offer) | Why it is seeded |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `OF-1001` | `UGREEN_POLSKA` | 99 890 | 109 890 | PLN | `onSale` | 9 units, always | steps 3–8, 11–22 — the row that prices, is deselected, and is handed over |
| `OF-1002` | `UGREEN_POLSKA` | 64 990 | 69 900 | PLN | `onSale` | 9 units for the first 9 reads, **0 units from the 10th** | steps 4–22 — the row that goes `outOfStock` at step 15, having priced the basket until then |
| `OF-2007` | `ANKER_STORE` | 129 000 | 129 000 | PLN | `removed` | no stock answer (`notRead`) — the offer answers, only its stock does not | steps 20–22 — a **described** removal: a `200` row marked `unavailable` that keeps its price and blocks the handover, instead of the `404` an undescribed removal gives |
| `OF-9001` | `UGREEN_POLSKA` | 4 990 | 5 990 | EUR | `onSale` | 9 units, always | step 9 — the foreign-currency probe: adding it to a PLN basket must answer `422 CURRENCY_MISMATCH` |
| `OF-0000` | — | — | — | — | — | — | step 10 — never seeded: an id the fixture does not know is answered as `removed` without a description, which is the `404 OFFER_NOT_FOUND` the step expects |

`title` and `picture` are placeholders (`Fixture offer OF-…`, `https://fixture.invalid/…`): every row renders both, so the
fixture must state them, and the contract ignores them (`$.items[*].title`, `$.items[*].picture`). The list price is stated
because element 08's record carries it; no row may hold it (D53), so it is the one field the contract cannot observe.

## The answer queue

`answers` is per offer and ordered: each read of that offer takes the next entry, and the last entry repeats — an offer that
runs out stays out. A single entry is therefore a constant answer, and `reads: n` holds one answer for `n` consecutive reads
instead of repeating the line.

```yaml
- id: OF-1002
  # ...
  answers:
    - stock: 9
      reads: 9      # on sale through the ninth read of this offer
    - stock: 0      # out of stock from the tenth, and every read after it
```

The queue is per offer, not global, because the contract's own sequence turns one row out of stock while its neighbours stay
on sale (D66, D67). Each route that answers basket content pulls, and an add reads the added offer once more before it does —
so the tenth read of `OF-1002` is step 15's selection command, which is why the golden moves that row to `outOfStock` there.
The read that lands it is worth naming, because one of them comes from a command that never wrote anything:

| Read of `OF-1002` | Sent by | What the read is |
| --- | --- | --- |
| 1 | step 4 | the add's own read of the offer it is about to add |
| 2 | step 4 | the pull that composes the answer |
| 3–5 | steps 5, 6, 7 | the pull behind each quantity command |
| 6–8 | steps 11, 12, 13 | the read route and the two selection commands |
| 9 | step 14 | the pull behind a checkout that is then **refused** (`NOTHING_SELECTED`) — `BasketService.handOver` observes before it refuses |
| 10 | step 15 | the pull behind the selection that selects everything — the answer turns to 0 units |

That is the coupling to keep in mind: the ordinal is calibrated to the current pull policy. A route that starts or stops
pulling, or one that pulls twice, moves the transition by a step and shows up as a golden mismatch at 15 — not as a fixture
bug. Re-derive the table before changing `reads:`.

**A read of no offers rewinds every queue.** That is the scenario's own boundary — step 2 clears the basket, and the pull
that answers it asks the catalog for nothing — so the 24 steps are replayable against one running pod without a restart.

Two properties follow, and both are the price of that replayability:

- **The counters are one process's.** `replicas: 1` is not incidental: two pods would each answer from their own queue, so a
  scaled-out deployment splits the sequence and the transition survives or vanishes by load balancer. A rolling restart in
  the middle of a run starts the sequence over for the new pod.
- **Any render of an empty basket rewinds**, not only step 2's clear — a second customer whose basket is empty, a probe of a
  freshly opened basket, anything that pulls with nothing in it. Harmless for one contract run against one customer, which is
  what this fixture is for, and the reason it is a fixture and not a catalogue.

`stock` is the warehouse's answer, never repaired (D56): a count below zero travels through and the decision reads it as none
left, and `notRead` states that the offer answered while only its stock did not (A5).

## Changing it

The seed is gated: `OfferSeedDocumentTest` parses `k8s/base/configmap.yaml`, binds it with the machinery the pod uses and
asserts the four offers and the queue `OF-1002` answers, and `build.gradle` declares the ConfigMap an input of `test` so an
edited seed re-runs it. A seed that cannot describe a row fails `./gradlew build`, not step 3.

1. Edit the `offer-fixture.yaml` value in `k8s/base/configmap.yaml`.
2. `.agents/skills/run-apps/scripts/run-apps deploy shopping` — it applies the manifests and restarts the deployment because
   the ConfigMap changed (`env` and mounted files are read at container start).
3. `bash docs/specs/basket/prototypes/prove-local.sh` — 24/24, or the step that moved.

The seed follows the scenario: an offer is added here only when a step of the contract names it, never "for completeness".
