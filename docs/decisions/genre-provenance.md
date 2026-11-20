# Genre provenance — the fulfillment docset

Authored, not generated. No `<!-- evidence: -->` marker ever points into this file. One row per genre
claim applied to the docset; the knowledge base is `~/.agents/knowledge/enterprise-software-documentation`.

## Scope decision — three documents, not four

The request was *system documentation, integrator guide, business guide*. The default docset has a
fourth — an operations manual — which this repository can honestly support (k8s manifests, an OTEL
collector, Spring Boot actuator). It is **omitted deliberately**, and the two chapters that would have
lived there are relocated rather than dropped:

| Default home | Here | Why |
| --- | --- | --- |
| operations manual → deployment view | system documentation, Part IV | the same reader who asks "what is this made of" asks "where does it run"; no on-call rotation exists for this eval repo, so a 03:00 runbook would be fiction |
| operations manual → telemetry surface | system documentation, Part IV | the telemetry facts are extractable; the alerting/on-call policy behind them is not |
| operations manual → failure-mode runbook | not written | no alert list, no paging, no SLO exists in the repository; inventing one would be the manual tier making up operational commitments |
| operations manual → revision history | integrator guide appendix | the document that changes most often is the contract |
| business guide → business impact (MTPD/RTO/RPO) | business guide, as `GAP` entries | the repository holds no criticality numbers; a gap is the honest form |

## Genre claims

| Document / chapter | Knowledge base kind | Claim applied |
| --- | --- | --- |
| system documentation (whole) | `architecture/arc42-architecture-document` §7.3 | fixed sections, quality goals stated before structure |
| system documentation (whole) | `systems-services/system-overview-and-ownership` §7.3 | ownership and system boundary stated in the opening chapter |
| system documentation — context and building blocks | `architecture/c4-model-diagram-set` §7.3 | one abstraction level per diagram; no element at two levels at once |
| integrator guide (whole) | `product-user-docs/api-reference-and-developer-portal` §7.3 | one chapter per operation, one field table per response media type, one example minimum |
| integrator guide (cross-cutting) | `systems-services/api-and-integration-documentation` §7.3 | authentication, error model and idempotency stated once, not per operation |
| integrator guide (revision history) | `operations-support/runbook` §7.6 | history of the contract, generated from git |
| business guide (whole) | `business-operations/business-domain-overview` §7.3 | actors, capabilities and value stream before any rule |
| business guide — glossary | `requirements/glossary-ubiquitous-language` §7.3 | one definition per term, rejected names recorded; the chapter is not titled "ubiquitous language" |
| business guide — rules | `business-operations/business-rules-and-decision-tables` §7.3 | four labelled parts per rule: Inputs, Output, Configuration, Internal data fetched |
| business guide — process maps | `business-operations/end-to-end-process-maps` §7.3 | steps name real operation ids or domain events; the maps are not BPMN-conformant and say so |
| business guide — impact | `risk-resilience/business-impact-analysis` §7.3 | MTPD/RTO/RPO as gaps, never as estimates |
| all documents — gaps appendix | `operations-support/runbook` §7.6 | an absent capability is a numbered gap, never advice |

## What this docset deliberately does not claim

- **Threat model** (`architecture/threat-model`) — no such analysis exists in the repository and writing
  one would be an author's opinion presented as a design property. The security decisions the code does
  make are tabulated in the integrator guide instead.
- **SLO and error budget** (`operations-support/slo-and-error-budget-policy`) — no latency, volume or
  availability target exists in any source; the spec itself records "volume/latency absent from every
  source". Recorded as gaps in the business guide's impact chapter.
