# 01 — ProductDescription

Element 01 · type: business concept · role: **Value Object** — immutable, published in the **shared kernel** · accepted.

The content of a product listing: title, identifiers, brand, condition, category path, parameters, description blocks. It has **no identity and no lifecycle of its own** —
two instances with equal components are the same value. Media lives in 02 Offer, not here.

**Shared kernel.** `ProductDescription` and its nested records (`Identifiers`, `Parameter`, `DescriptionBlock` + sealed alternatives) are
exported for other contexts in the service and published inside 13's snapshot events, so `review`, `quality`, `reads` and the shop all
read one shape. A consumer lists them in its `sharedKernelUsed`; the offer context lists them in `sharedKernelExposed`
(`context-boundaries.md`). Because the type is public, it may not carry a context-internal type in a signature.

## Instance A — the sample page

```yaml
# instance A — a value, addressed by content
title: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423"
identifiers: { productNumber: "75423", eanGtin: "5702017817705", setName: "SMART Play X-Wing Czerwona Piątka Luke'a" }
brand: { name: "LEGO", verified: true }
condition: new
categoryPath: [Dziecko, Zabawki, Klocki, LEGO, Zestawy, Star Wars]
parameters:
  - { code: childAge,      label: "Wiek dziecka",   value: "6+",   valueType: rangeFrom, from: 6, unit: lat }
  - { code: pieceCount,    label: "Liczba elementów", value: 581,   valueType: integer }
  - { code: gender,        label: "Płeć",           value: [boys, girls, unisex], valueType: enumSet }
  - { code: certificates,  label: "Certyfikaty, opinie, atesty", value: ["CE", "EN 71"], valueType: list }
  - { code: safetyInfo,    label: "Informacje dot. bezpieczeństwa i zgodności produktu", value: "Nie nadaje się dla dzieci w wieku poniżej 36 miesięcy", valueType: text }
description:
  blocks:
    - { type: heading, level: 3, text: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423" }
    - { type: paragraph, text: "Odkryj nową galaktykę LEGO® Star Wars™ dzięki interaktywnej zabawce konstrukcyjnej …" }
    - { type: bulletList, items: ["Interaktywna zabawka konstrukcyjna ożywia X-winga…", "SMART klocek umożliwia…"] }
```

## Instance B — same shape, different content (from the page's own carousel)

```yaml
# instance B — a value, addressed by content
title: "LEGO Star Wars Sokół Millennium 75375 + LEGO Brelok na dzień Ojca"
identifiers: { productNumber: "75375", eanGtin: "5702017584065", setName: "Sokół Millennium" }
brand: { name: "LEGO", verified: true }
condition: new
categoryPath: [Dziecko, Zabawki, Klocki, LEGO, Zestawy, Star Wars]
parameters:
  - { code: childAge,   label: "Wiek dziecka",    value: "18+", valueType: rangeFrom, from: 18, unit: lat }
  - { code: pieceCount, label: "Liczba elementów", value: 921,   valueType: integer }
  - { code: gender,     label: "Płeć",            value: [unisex], valueType: enumSet }
description:
  blocks:
    - { type: paragraph, text: "Zbuduj kultowy Sokół Millennium…" }
```

## Behaviour

Created from a title + identifiers → blank draft; edited by ContentAuthor any number of times; read by ContentReviewer; frozen into a version when 02 Offer publishes; never mutated after freezing.

## Domain rules

- **RULE-1** title required, 1–200 chars, editable until the **next** publication — every publication freezes a new version, so a
  published title is never mutated, it is superseded (segment 6; the version model of 02 RULE-4 makes "until publication" a
  statement about the frozen value, not a lock on the draft).
- **RULE-2** `productNumber` and `eanGtin` unique per product; `eanGtin` = 13 digits with valid check digit.
- **RULE-3** a value is immutable: a change produces a **new value**, never a mutation. Versioning is not this object's concern — the offer version (02) is what carries a change into the world.
- **RULE-4** draft content never leaves the context — no event, no query outside it (segments 9–11).
- **RULE-5** `code` unique within a description; `valueType ∈ {text, integer, decimal, rangeFrom, boolean, date, enumSet, list}`; `label` is display text, not an identifier.
- **RULE-6** at least one `paragraph` block; `image` blocks reference media by id owned by 02 Offer.
- **RULE-7** equality is by value: every component participates in `equals`/`hashCode`, so two offers carrying equal content carry the
  same value and the published payload can be deduplicated by `descriptionHash` (13). Nothing identifies a description beyond its content.
- **RULE-8** uniqueness of `productNumber`/`eanGtin` is **not** enforceable here (a value object cannot police a population) — 02 enforces it.
- **RULE-9** `brand {name, verified}`, `condition ∈ {new, used, refurbished}` and `categoryPath` are components of the value, not of the
  offer: 13 publishes them inside `OfferPublished`, so they freeze with the version exactly like the rest of the content (D30).
  `categoryPath` is matched by 08's category rules; `brand.verified` is display metadata and carries no rule.

## Instance diff — draft v1 → published v2

```diff
  title: "LEGO Star Wars SMART Play X-Wing… 75423"
+ identifiers: { productNumber: "75423", eanGtin: "5702017817705" }
+ parameters: [childAge, pieceCount, gender, certificates, safetyInfo]
- description.blocks: [ { type: paragraph, text: "TODO" } ]
+ description.blocks: [ heading, paragraph, paragraph, paragraph, bulletList ]
+ frozenAt: "2025-06-15T10:00:00+02:00"
```

## Domain events

None of its own — a value object has no lifecycle. The events that move a description belong to 02 Offer
(`OfferDraftUpdated` while drafting, `OfferVersionFrozen` which carries the value) and 13 for the public side.

## Assumptions recorded at acceptance

- **A1** identifiers limited to `productNumber` + `eanGtin` + `setName` — Q3.
- **A2** parameters are an attribute list (`code/label/value/valueType`) rather than fixed columns — Q6.
- **A3** description is structured blocks, not HTML — Q7.

## Implementation note (accepted with the element)

The entire description is implemented in a **single file** — `ProductDescription.java` — with its details as
**inner records** (`Identifiers`, `Parameter`, `DescriptionBlock` and its sealed alternatives), not one file per
part. This follows the existing convention `docs/arch/microservice-java-spring/domain-model-value-object.md:10`
("declare a value object used only inside its parent as a nested record of that parent") and
`docs/arch/microservice-java-spring/code-structure.md:39-40` (one `<Concept>.java` holding the concept snapshot
and its nested event records). Open-ended alternative sets stay a `sealed interface` with one nested record per
alternative, so exhaustive `switch` replaces `instanceof`. Neither the nesting policy nor the sealed-alternative policy is an
encoded `sqt` rule — the ids `vo.nested` / `vo.sealed-alternatives` are review vocabulary, not checks (verified against
`src/main/java/.sqt.yaml`); the reviewer enforces them, not the gate.
