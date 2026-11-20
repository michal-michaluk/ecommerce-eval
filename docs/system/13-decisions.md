This chapter indexes architecture decisions, and the repository keeps none in the directory an index
reads: `docs/adr` does not exist, so the appendix below states, correctly, that its table has no rows.
The decisions this system did take are written elsewhere, and they are not missing — the
specification carries its own register, `D1` to `D16`, and `docs/decisions/` holds the operator
answers that settled what no source could, plus the register of what this document genre claims.
<!-- evidence: docs/specs/fulfillment/spec.md:70-90, docs/decisions/operator-input.md:6-17, docs/decisions/genre-provenance.md:1-4, docs/decisions/overview.md:1-3 -->

A reader looking for a decision behind a rule in this document should therefore read the register
entry the chapter names — of which the code's own notes are the second half, recorded per phase in the
service's `docs/phase-1-decisions.md`.
<!-- evidence: docs/specs/fulfillment/spec.md:83, services/fulfillment/docs/phase-1-decisions.md:246 -->
