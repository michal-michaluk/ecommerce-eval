## 3 Configuration and secrets

Everything the service reads comes from three places: the ConfigMap, a Secret, and the packaged
`application.yml`. The packaged file states a default for every key that has one, so the ConfigMap is where
a deployment *states* a value rather than where it invents one.

<!-- evidence: services/shopping/k8s/base/configmap.yaml:1-28, services/shopping/src/main/resources/application.yml:1-47 -->

### 3.1 The keys, and what each one changes when it is wrong

Which environment variable reads which ConfigMap key is chapter 2's table, because the Deployment states it
and the extractor reads it. What a table of wiring cannot tell an operator is the symptom, so that is what
this one carries.

| Property | Symptom of a wrong value |
|---|---|
| `basket.currency` | every basket opens in the wrong currency, and every offer in the real one is refused as a mismatch |
| `basket.delivery.rate` | every shipment is priced wrong |
| `basket.delivery.freeWhenSmart` | the SMART channel either pays for delivery or gives it away |
| `basket.estimate.default-days.from`, `.to` | every delivery range is wrong, at one end or both |
| `basket.estimate.zone` | the range is counted in the wrong calendar, so a midnight boundary moves it |
| `basket.estimate.fallback` | an absent estimate stops being the named non-answer |
| the issuer URI | no token validates: every request is `401` |
| the offer seed | the catalogue the basket reads is empty, or the pod refuses to start |
| the profile | the wrong profile's defaults load |

: A wrong value, and how a person notices.

The safe direction of each failure is deliberate. An absent `basket.estimate.*` block answers `unknown`
rather than defaulting to `2…4`, so a lost key hides the delivery line instead of inventing one; a
half-stated `basket.delivery` block falls back per key, so a stated rate cannot silently free a delivery it
never meant to free; and a malformed currency or seed stops the process while the bean is created, not on the
first request.

<!-- evidence: services/shopping/src/main/resources/application.yml:21-40, services/shopping/src/main/java/shop/shopping/basket/BasketConfiguration.java:59-108, services/shopping/src/main/java/shop/shopping/basket/BasketConfiguration.java:158-176 -->

### 3.2 What arrives from a Secret

Three values, all from `shopping-db`: the JDBC URL, the user and the password. Nothing else in the service
reads a secret, and no value of the Secret appears in the repository — the overlays generate it, the base
Deployment references three keys of it, and the service reads them through the framework's own datasource
properties.

<!-- evidence: services/shopping/k8s/base/deployment.yaml:106-120, services/shopping/k8s/overlays/k3d/kustomization.yaml:34-40 -->

### 3.3 The offer seed is configuration, and it is load-bearing

The seed is the ConfigMap key `offer-fixture.yaml`, mounted at `/etc/shopping/offer-fixture.yaml` and bound
to `basket.offer-fixture.offers` through `SPRING_CONFIG_ADDITIONAL_LOCATION`. Two consequences an operator
must not be surprised by:

- **The mount is not `optional`.** A pod that lost the seed would not fail to start; it would answer `404
  OFFER_NOT_FOUND` to every add, which is a much harder symptom to read than a pod that refuses to start.
- **The seed's read counters are one process's.** Each offer carries an ordered queue of stock answers whose
  last entry repeats, so a scaled-out deployment would split the sequence across pods, and a rolling restart
  starts it over. `replicas: 1` is therefore load-bearing, not a default.

<!-- evidence: services/shopping/k8s/base/deployment.yaml:47-57, services/shopping/k8s/base/deployment.yaml:104-105, services/shopping/docs/offer-fixture.md:19-30, services/shopping/docs/offer-fixture.md:100-110 -->

### 3.4 What the service never reads

No broker address, no offer-read endpoint and no feature flag: the service consumes no event channel, the
offer read is an in-process adapter in this increment, and the `features_configuration` table is read only by
the tools layer that every scaffolded service of this shop carries. A key for any of them would be a key
nothing reads.

<!-- evidence: services/shopping/k8s/base/configmap.yaml:9-10, services/shopping/src/main/java/shop/shopping/tools/FeatureConfiguration.java:19-30 -->
