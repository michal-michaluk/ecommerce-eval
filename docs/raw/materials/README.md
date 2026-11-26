# Spec materials — machine-readable transcriptions of `docs/raw/`

Every raw source is transcribed into JSON here, once, so the three context specs read a stable,
structured representation instead of re-parsing images and subtitles. Sources stay in `docs/raw/`
(untouched, including the 27 MB screenshot); this folder holds only the derived JSON.

| JSON | Source | What it is | Used by |
| --- | --- | --- | --- |
| `eventstorming.json` | `docs/raw/ecommerce-eventstorming.jpg` | 5 lanes with actors, commands, events, read models, policies, hot spots, boundary crossings, external systems, verbatim typos | all three |
| `transcript.json` | `docs/raw/ecommerce-eventstorming.improved.srt` | 153 segments with timings and detected gaps | all three |
| `offer-lego.json` | `docs/raw/offer-lego.png` | the published product page, field by field: identity, identifiers, variant, media, pricing, availability, delivery, seller, ratings, parameters, description blocks, other sellers; plus page furniture | catalog, shopping |
| `basket.json` | `docs/raw/basket.png` | the basket page, field by field: whole-basket selection and bulk removal, seller shipment with its two items (attributes, quantity stepper, price, remove), basket delivery hint, cross-sell action, summary panel (product value, delivery from, total with delivery, both actions), donation and buyer-protection tiles; plus arithmetic checks, reading ambiguities and what the screen does not show | basket |
| `consignment-note.json` | `docs/raw/consignment-note.png` | DPD parcel label: carrier, sender, recipient, parcel i/n, weight, branch/routing code | fulfillment |
| `goods-issue-note.json` | `docs/raw/goods-issue-note.png` | WZ external goods issue note: three parties with NIP, lines with net/VAT/gross, related documents, summary, amount in words, signatures | fulfillment |

Known gaps recorded in the JSONs themselves: `transcript.json` `gaps` (71.2 s of content missing
between segments 71 and 72 — basket delivery-time/price estimation), `eventstorming.json`
`verbatimTypos` and `lanes[].hotSpots` (open design questions the board itself raised).

Spec folders (`docs/specs/<context>/`) hold `spec.md`, numbered element files and `prototypes/`;
they link back here instead of duplicating these JSONs.
