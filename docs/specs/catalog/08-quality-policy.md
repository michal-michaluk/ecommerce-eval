# 08 — QualityPolicy

Element 08 · type: calculation · accepted.

`(ProductDescription.Draft, media, categoryPath) → findings[]`. Runs continuously on the draft, is frozen into the
review candidate at request (04 RULE-3), and is re-enforced by `Draft.toDefinition()` (02 RULE-10).

## Logic definition

```text
Findings(draft, media, categoryPath) =
      universal(draft, media)                     # QP-1..QP-9, ordered
    + categoryRules(draft, categoryPath)          # QP-10..QP-12, only for a configured category

prose(draft) = sum(length(block.text) for paragraph blocks)
             + sum(length(item)     for bulletList items)
```

| Rule | Check | Field | Severity |
|---|---|---|---|
| QP-1 | title present, trimmed, 1–200 chars | `description.title` | blocking |
| QP-2 | `eanGtin` present, 13 digits, valid check digit | `description.ids.eanGtin` | blocking |
| QP-3 | `productNumber` present, non-empty | `description.ids.productNumber` | blocking |
| QP-4 | at least one image in media | `media` | blocking |
| QP-5 | exactly one `primary: true` | `media.primary` | blocking |
| QP-6 | `prose(draft) >= minProseChars` and at least one `paragraph` block | `description.blocks` | blocking |
| QP-7 | every `image` block references an existing media id | `description.blocks` | blocking |
| QP-8 | media `position` contiguous from 1, at most one video | `media` | warning |
| QP-9 | grammar / correctness validation | `description` | **out of scope** (Q1a: needs a new external technology; reserved as a `grammar-enabled` variant, unimplemented) |
| QP-10 | every required parameter of the matched category is present | `parameters.<code>` | blocking |
| QP-11 | a configured parameter's `valueType` matches the draft's | `parameters.<code>` | blocking |
| QP-12 | a parameter is present that the category config does not name | `parameters.<code>` | warning |

### QP-6 threshold — derived from the LEGO sample (Q1b)

Measured on `docs/raw/materials/offer-lego.json` description blocks:

| Measure | Value |
|---|---|
| paragraph blocks | 3 |
| paragraph chars | 1 025 (449 / 382 / 194) |
| bullet items | 8 |
| bullet chars | 601 |
| heading chars | 61 |
| **prose = paragraphs + bullets** | **1 626** |

`minProseChars = 1000` = the sample rounded down to a round number at ~61 % of its measured prose. Rationale for that
level: 1000 > the longest single paragraph (449), so the rule cannot be satisfied by one long block — it forces a
multi-block description, which is what the sample actually looks like. Heading text is excluded (it repeats the title).

### Category rules (Q1c)

```yaml
catalog:
  quality:
    min-prose-chars: 1000
    severity:                      # per-rule severity override; absent = 08's table
      QP-8: warning                # | blocking  (the `strict-media` variant)
    grammar:
      enabled: false               # reserved, unimplemented (QP-9)
    required-parameters:           # a list, not a map key: the prefix is a real list value
      - prefix: [Dziecko, Zabawki, Klocki, LEGO]
        parameters:                # declaration order is the report order for QP-10..QP-12
          condition:    { value-type: text,      required: true }
          childAge:     { value-type: rangeFrom, required: true }
          pieceCount:   { value-type: integer,   required: true }
          gender:       { value-type: enumSet,   required: true }
          certificates: { value-type: list,      required: true }
          safetyInfo:   { value-type: text,      required: true }
```

This is the binding shape (`@ConfigurationProperties`, key `catalog.quality`): a list of `{prefix, parameters}`, because a category
path is a list and a YAML map key cannot carry one (D33).

- `required` has no default: an omitted key binds to `false`, so the category allows that parameter to be absent and QP-10 stays
  silent for it. Every parameter of a blocking set therefore states `required: true` explicitly.
- `severity` names a rule id and a severity word, both of which must exist: an unknown key (`QP-9`, a typo) or an unknown word fails
  binding at startup and is never ignored, because a severity nobody reads is a silently misconfigured policy.
- Ordering and matching: findings are ordered by rule number, then by the configuration's **declaration order** within one rule. The draft's `categoryPath` is matched against configured prefixes; the **longest matching prefix wins**;
  the first match is final (deterministic).
- No match → no QP-10…QP-12 findings; universal rules still apply. One category (`LEGO`) is configured today.
- A deeper prefix **inherits nothing**: the matched prefix's parameter set is the whole set. `[Dziecko, Zabawki, Klocki, LEGO, Star Wars]`
  matches the LEGO entry only because that entry is its longest configured prefix, not because `Star Wars` adds rules. If a future category
  needs the LEGO set plus its own, the configuration repeats the inherited parameters (binding composes, the policy does not).
- Adding a category is a config change only — no code change, no migration.

## Parameter and value-object origins

| Value object | Values | Origin | Source |
|---|---|---|---|
| DescriptionDraft | title, ids, parameters, blocks | Business concept | 01 ProductDescription (`Draft`) |
| MediaSet | items, primary, position | Business concept | 02 Offer |
| CategoryPath | path | Business concept | 01 ProductDescription (component of the value) |
| QualityConfig | minProseChars, categories (per-category required parameters), severityOverrides | Configuration | config keys `catalog.quality.*` |

## Variants and configurations

| Variant | Selected by | Behaviour | Configuration source |
|---|---|---|---|
| default | no match | QP-1…QP-8 + category rules | — |
| grammar-enabled | config `catalog.quality.grammar.enabled = true` | QP-9 added — **not implemented**, needs a `poc` and an external service | config key `catalog.quality.grammar.enabled` |
| strict-media | config `catalog.quality.severity.QP-8 = blocking` | QP-8 promoted to blocking (the name states the stricter behaviour) | config key `catalog.quality.severity.*` |

Every variant above except `grammar-enabled` is implemented as a bound config property and covered by a scenario in this file's
list (D29); `grammar-enabled` stays a documented key with no behaviour.

## Executable scenarios

Draft with category `[Dziecko, Zabawki, Klocki, LEGO]` unless stated.

| Scenario | draft / media | expected findings |
|---|---|---|
| S1 | complete, media 3 images 1 primary, prose 1 626 | none |
| S2 | `eanGtin: null` | QP-2 blocking |
| S3 | prose 1 025 as 12 bullet items, no paragraph block | QP-6 blocking (no paragraph) |
| S4 | single paragraph 1 025 chars | none — rule is a total, not a block count |
| S5 | prose 800 | QP-6 blocking |
| S6 | no paragraph, heading + bullets only | QP-6 blocking |
| S7 | image block → missing media id | QP-7 blocking |
| S8 | media `[]` | QP-4 blocking, QP-5 blocking |
| S9 | two `primary: true` | QP-5 blocking |
| S10 | positions `[1,3]` | QP-8 warning |
| S11 | LEGO draft missing `pieceCount` | QP-10 blocking (pieceCount) |
| S12 | LEGO draft `pieceCount: "581"` (text) | QP-11 blocking |
| S13 | LEGO draft with extra param `batteryType` | QP-12 warning |
| S14 | category `[Dziecko, Moda dziecięca]`, missing every parameter | no QP-10…QP-12 (unconfigured category) |
| S15 | LEGO draft missing both `pieceCount` and `safetyInfo` | QP-10 twice, in config declaration order |
| S16 | title `""` (empty) | QP-1 blocking |
| S17 | `productNumber: null` | QP-3 blocking |
| S18 | LEGO draft, findings include QP-8 warning only | `publishable` stays true (warnings never block — 09 takes blocking findings only) |

## Domain events

None — pure function of draft state and configuration. Findings are read by 14, frozen by 04, consumed by 09.

## Worked example — input, configuration, result

One call of `Findings(draft, media, categoryPath)`.

### Input parameter

```yaml
draft:
  title: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423"
  ids: { productNumber: "75423", eanGtin: "5702017817705" }
  parameters:
    - { code: childAge,   value: "6+",  valueType: rangeFrom, from: 6, unit: lat }
    - { code: gender,     value: [boys, girls, unisex], valueType: enumSet }
  blocks:
    - { type: heading, text: "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423" }
    - { type: paragraph, text: "Odkryj nową galaktykę LEGO® Star Wars™ dzięki interaktywnej zabawce konstrukcyjnej …" }   # 620 chars
media:
  - { id: M-01, kind: image, position: 1, primary: true,  format: jpg }
  - { id: M-02, kind: image, position: 2, primary: false, format: jpg }
  - { id: M-03, kind: video, position: 3, primary: false, format: mp4 }
categoryPath: [Dziecko, Zabawki, Klocki, LEGO, Zestawy, Star Wars]
```

### Configuration

```yaml
catalog:
  quality:
    min-prose-chars: 1000
    required-parameters:
      - prefix: [Dziecko, Zabawki, Klocki, LEGO]
        parameters:
          condition:    { value-type: text,      required: true }
          childAge:     { value-type: rangeFrom, required: true }
          pieceCount:   { value-type: integer,   required: true }
          gender:       { value-type: enumSet,   required: true }
          certificates: { value-type: list,      required: true }
          safetyInfo:   { value-type: text,      required: true }
```

Matched prefix: `[Dziecko, Zabawki, Klocki, LEGO]` (longest of the configured prefixes; `…, Star Wars` is not configured).

### Result

```yaml
prose: 620                     # paragraphs (620) + bullets (0); heading excluded
matchedCategory: "[Dziecko, Zabawki, Klocki, LEGO]"
findings:                      # QP-1…QP-8 first, then QP-10…QP-12 in config declaration order
  - { rule: QP-6,  field: description.blocks,       severity: blocking, message: "prose 620 < 1000 (minProseChars)" }
  - { rule: QP-10, field: parameters.condition,     severity: blocking, message: "required parameter missing for category LEGO" }
  - { rule: QP-10, field: parameters.pieceCount,    severity: blocking, message: "required parameter missing for category LEGO" }
  - { rule: QP-10, field: parameters.certificates,  severity: blocking, message: "required parameter missing for category LEGO" }
  - { rule: QP-10, field: parameters.safetyInfo,    severity: blocking, message: "required parameter missing for category LEGO" }
publishable: false             # 09 turns this into BLOCK (findings non-empty, no override)
```

Media passes QP-4, QP-5 and QP-8 (three items, exactly one primary, positions 1–3 contiguous, one video);
QP-1, QP-2, QP-3 and QP-7 pass as well — they are simply absent from `findings`.
