## 10 Ownership and escalation

**GAP-4 — No owner is recorded.** Nothing in this repository names a team, a rotation, an escalation path or
a support window for this service. The image's main class, the manifests and the specification carry no
contact, and no `CODEOWNERS`, catalogue entry or on-call document exists.

That absence has one consequence an operator must plan around before the first incident: there is nobody
the repository says to wake. It is not a documentation gap that this chapter can close by naming a team — it
is a fact about the repository, and inventing a name would be the manual tier making an operational
commitment nobody made.

<!-- evidence: services/shopping/build.gradle:1-12, services/shopping/AGENTS.md:1-11, docs/specs/basket/index.md:1-3 -->

### 10.1 What the repository does say about who changes what

| Area | What the repository states |
|---|---|
| the service's code and manifests | no owner; the branch and the specification are the only record of who built it |
| the offer seed | the contract's offers are the browsing slice's data; this increment states them as configuration |
| the checkout slice | its own specification, which does not exist yet |
| the shared cluster | a workspace property, not this service's: the cluster and its context are created by the workspace's own tooling |

<!-- evidence: services/shopping/docs/offer-fixture.md:11-16, services/shopping/k8s/overlays/k3d/kustomization.yaml:1-13, services/shopping/src/main/java/shop/shopping/checkout/package-info.java:1-13 -->

### 10.2 Escalation, as far as it can be stated

A failure of this service is either a failure of the database, of the identity provider or of the offer
read. The first two are the platform's, and this repository names no escalation for them; the third is a
counterpart that does not exist yet. What remains is the service itself, and its failure modes are chapter 6.

<!-- evidence: services/shopping/k8s/overlays/k3d/kustomization.yaml:8-12, services/shopping/src/main/resources/application.yml:14-19 -->
