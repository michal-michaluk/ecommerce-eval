# Genre provenance — the shopping docset

Authored, not generated. No `<!-- evidence: -->` marker points into this file. One row per genre claim
applied to the shopping docset; the knowledge base is
`~/.agents/knowledge/enterprise-software-documentation`, and the instantiation of the pipeline is
`docs/scripts/config.shopping.json`.

## Scope decision — four documents, and why this one differs from fulfillment's three

The shopping docset is the second configuration of the pipeline that renders the fulfillment docset. It
keeps that docset's three documents and adds the fourth the pipeline's default asks for, because this
service has facts an operator needs and fulfillment's does not: three probes with thresholds, resource
requests and limits, a telemetry exporter that only one overlay declares, a seeded fixture that must be
changed by redeploy and not by rebuild, and a set of recorded failure modes with wire signals. The
judgement is recorded as operator answer 11 in `docs/decisions/operator-input.md`, which also states what
the manual must **not** invent: no alert, no dashboard, no objective, no rotation.

| Default home | Here | Why |
| --- | --- | --- |
| operations manual → deployment view | operations manual, chapter 2 | the 03:00 reader needs probes, limits and env sources in one place |
| operations manual → telemetry surface | operations manual, chapter 5 | the collector and the agent are operational facts, and the chapter names the four capabilities that do not exist |
| operations manual → revision history | operations manual, chapter 11 | the manual is the document an operator re-reads after a change |
| operations manual → failure modes | operations manual, chapter 6 | the service has seven distinguishable failures with wire signals, so a bounded sequence per failure is honest here |
| business guide → business impact | business guide, chapter 5, with `GAP-B1` | no criticality number exists in any material |
| system documentation → deployment | shortened to a pointer | the deployment view is generated once, into the document whose reader needs it |

## Genre claims

| Document / chapter | Knowledge base kind | Claim applied |
| --- | --- | --- |
| system documentation (whole) | `architecture/arc42-architecture-document` §7.3 | fixed sections: context, building blocks, crosscutting rules, decisions, limits |
| system documentation (whole) | `systems-services/system-overview-and-ownership` §7.3 | the system boundary and its deliberate absences stated in the opening chapter |
| system documentation — context and slices | `architecture/c4-model-diagram-set` §7.3 | one abstraction level per figure: context at system level, the slices at component level |
| system documentation — decisions | `architecture/architecture-decision-record` §7.3 | the index is empty because the directory is absent; the records that exist live in the specification's register and are indexed, not reconstructed |
| integrator guide (whole) | `product-user-docs/api-reference-and-developer-portal` §7.3 | one chapter per operation, authentication and the error model stated once |
| integrator guide (response bodies) | `architecture/integration-catalogue-and-interface-specs` §7.3 | the payload of each route stated field by field, with the wire form of an amount |
| integrator guide (contract) | `systems-services/api-and-integration-documentation` §7.3 | how to run the executable contract, and what it does not cover |
| operations manual (whole) | `systems-services/operations-and-administration-guide` §7.3 | deployment view, configuration, routine procedures, telemetry, failure modes |
| operations manual — failure modes | `operations-support/runbook` §7.3 | one bounded sequence per failure, each naming its signal; no alert is claimed because none exists |
| operations manual — findings | `risk-resilience/business-continuity-plan` §7.3 | absent capabilities as findings, never as advice |
| operations manual — revision history | `product-user-docs/release-notes-and-changelog` §7.3 | history quoted from version control, with the classification's own uncertainty printed |
| business guide (whole) | `business-operations/business-domain-overview` §7.3 | actors, capabilities and where responsibility ends, before any rule |
| business guide — glossary | `requirements/glossary-ubiquitous-language` §7.3 | one definition per term, synonyms rejected explicitly, each term citing its code |
| business guide — rules | `business-operations/business-rules-and-decision-tables` §7.3 | four labelled parts per rule: Inputs, Output, Configuration, Internal data fetched |
| business guide — process maps | `business-operations/end-to-end-process-maps` §7.3 | steps name real route ids; the maps say they are not BPMN |
| business guide — impact | `risk-resilience/business-impact-analysis` §7.3 | MTPD/RTO/RPO as a gap, never an estimate |
| all documents — gaps appendix | `operations-support/runbook` §7.6 | an absent capability is a numbered gap, never advice |

## What this docset deliberately does not claim

- **Threat model** (`architecture/threat-model`) — none exists in the repository; the access matrix is
  generated from the filter chain and the wider-than-needed management surface is recorded as a gap.
- **SLO and error budget** (`operations-support/slo-and-error-budget-policy`) — no target exists in any
  source, and the specification says so in its own words.
- **Incident postmortem** (`operations-support/incident-postmortem`) — no incident is recorded anywhere.

## Pipeline instantiation notes

Three facts about how this docset was produced that a later reader will otherwise have to rediscover.

1. **Templates are per docset.** `docs/templates-shopping/` is a copy of `docs/templates/` with the sentences
   that assert fulfillment's facts removed or made data-derived: the environment-variable count, the probe
   defaults, the one infrastructure manifest, the collector's exporters and the revision-history share. A
   template is manual-tier prose once it states a fact, so two docsets cannot share one set of templates
   without one of them lying.
2. **The extractor set was written for a hurl scenario.** This service's executable contract is a `prove`
   document, so the per-operation scenario chapters (`op-*`), the transcript chapters (`fx-*`) and the
   operational-endpoints chapter (`api-operational`) render with empty states and no document includes them.
   The `caller` argument of every handler is a `@AuthenticationPrincipal`, and the extractor reports it as a
   query parameter — another reason those chapters are not composed into a document.
3. **The offer seed is the only catalogue.** `request_response_blocks` counts the three healthcheck requests
   and nothing else; that count is pinned in `expected_counts` because it is the true one, not because it is
   the useful one.
