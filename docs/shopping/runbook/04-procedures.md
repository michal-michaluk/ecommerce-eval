## 4 Routine procedures

Four things an operator actually does with this service. Each one names the command and the signal that
proves it worked; none of them needs a rebuild except the first.

### 4.1 Change the offer seed

The seed is the ConfigMap key `offer-fixture.yaml`, not a compiled resource, so a change is a redeploy and
not a rebuild.

1. Edit the value of `offer-fixture.yaml` in `k8s/base/configmap.yaml`.
2. Redeploy the manifests and restart the Deployment — the file is mounted, and a mount is read at container
   start.
3. Run the contract: `bash docs/specs/basket/prototypes/prove-local.sh`. Twenty-four of twenty-four steps, or
   the step that moved.

The seed is gated by a test that binds the ConfigMap itself, and the ConfigMap is declared an input of the
test task, so an edited seed re-runs that test on the next build instead of leaving an `UP-TO-DATE` green.
A seed that cannot describe a row — a missing title, picture, price or currency, a stock answer that is
neither `notRead` nor a count, a `fault` nothing implements — stops the pod while the bean is created.

<!-- evidence: services/shopping/docs/offer-fixture.md:11-30, services/shopping/docs/offer-fixture.md:112-119, services/shopping/build.gradle:64-74 -->

One warning that costs an afternoon: the fixture's stock answers are a per-offer queue calibrated to the
contract's steps. A route that starts or stops pulling moves the transition by a step, and the symptom is a
golden mismatch at step 15, not a fixture error. Re-derive the read table in the fixture document before
touching `reads:`.

<!-- evidence: services/shopping/docs/offer-fixture.md:62-80 -->

### 4.2 Rebuild and redeploy after a code change

The image is built by Jib and imported into the cluster's container runtime; the manifests carry no registry
address, and the pull policy is `IfNotPresent`, so an image that was rebuilt but not re-imported is silently
the old one.

```sh
cd services/shopping && ./gradlew jibDockerBuild
```

then import the image and redeploy the overlay. The `dev` overlay sets the pull policy to `Always`, so a
developer's machine does not hit that trap; the base and the `k3d` overlay do not.

<!-- evidence: services/shopping/build.gradle:220-249, services/shopping/k8s/base/deployment.yaml:26-29, services/shopping/k8s/overlays/dev/deployment-patch.yaml:16-18 -->

### 4.3 Migrate the schema

Liquibase runs at start from `db/db.changelog.yaml`, and Hibernate never creates a table on its own. A new
change set therefore arrives with the pod that contains it: no separate migration job exists, and a failed
migration is a pod that does not start.

Two properties follow. A rollback is a change set you write — nothing in the repository reverses one. And a
schema change and the code that needs it ship together, so a rolling deployment would briefly run old code
against a new schema; with one replica there is no rolling window, only a restart.

<!-- evidence: services/shopping/src/main/resources/application.yml:7-13, services/shopping/src/main/resources/db/db.changelog.yaml:1-36, services/shopping/k8s/base/deployment.yaml:7-8 -->

### 4.4 Restart it

A restart is `kubectl rollout restart` on the Deployment, or deleting the pod. Two facts make it uneventful
and one makes it visible:

- the startup probe gives a cold JVM ten minutes before the liveness probe takes over, so a slow start does
  not restart-loop;
- the aggregate's content is one document per basket, so nothing is lost;
- the fixture's read counters start over, so a *derived* availability may be re-decided. A run of the
  contract that spans a restart is not a contract run.

<!-- evidence: services/shopping/k8s/base/deployment.yaml:121-144, docs/specs/basket/prove-of-done.md:66-66, services/shopping/docs/offer-fixture.md:100-110 -->
