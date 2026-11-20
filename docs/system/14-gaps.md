## 14 Gaps

<!-- evidence: operator-input#8 -->

Numbered gaps for this document. The ids are local to this document — another document's `GAP-2` is a
different gap, so a reference that crosses documents names the document with the id. Each entry names what is
absent and what a reader would need; none is advice.

| Gap | Absent | Consequence for a reader |
| --- | --- | --- |
| `GAP-1` | the customer scope is not excluded from the actuator surface | a `fulfillment-customer` token can read `env` and `prometheus`, although its contract is "only their own processing" |
| `GAP-2` | the `customer` slice declares no per-slice visibility rule in `src/main/java/shop/fulfillment/customer/.sqt.yaml` | the package-private boundary of that slice is enforced by the compiler only, not by the style gate |
| `GAP-3` | `services/fulfillment/AGENTS.md` still describes four slices and omits `issues` | a reader trusting the service's own README sees a structure that no longer matches the tree |
| `GAP-4` | no volume, latency or availability figure anywhere in the repository | every capacity question is answered "not declared in the repository"; no source states what the service must sustain |
| `GAP-5` | the slice figure shows one event reaching the processing slice; the code has that listener plus four projection listeners on the same fact | the figure is a reading aid, not a complete wiring diagram |
| `GAP-6` | no alert rule, dashboard or paging configuration | nothing states when a human is woken; the metrics exist, the policy does not |
| `GAP-7` | no SLO, error budget or latency target | delivery-state latency *is* the polling interval, and neither number is declared |
| `GAP-8` | no outbox, no broker, no cross-process event transport | another service cannot subscribe; the only cross-context call is the synchronous handover |
| `GAP-9` | no projection rebuild command | a projection that drifts can only be corrected by a manual SQL replay |
| `GAP-10` | `StockChanged` has no subscriber | the pick list is computed on read, so a stock change is visible only when someone opens the list |
| `GAP-11` | no scaling rule and no cursor lock | more than one replica would double-publish or skip stock windows; correctness rests on `replicas: 1` |
| `GAP-12` | carrier credentials are a precondition for picking, not only for shipping | a warehouse without carrier credentials cannot scan a single position |
| `GAP-13` | no retention or archival policy for `order_processing`, `orders_list` or the issue rows | an auditor must ask outside the system |
| `GAP-14` | no restore or backup procedure | `k8s/` declares the database dependency but no backup job |
