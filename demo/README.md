# catalog demo — state and how to finish it

**Status: failed.** `scenarios.md`, `terminal-demo.yaml`, `payloads/` and `record-views.cjs` are reusable; there is **no video**.

## What exists

| File | What it is |
| --- | --- |
| `scenarios.md` | the five acceptance scenarios derived from `docs/prove-of-done.md` P1–P8 |
| `terminal-demo.yaml` | four scenarios as 36 commands + narration, driven over the service's own HTTP contract |
| `payloads/*.json` | every request body the scenarios send |
| `record-views.cjs` | the storybook take for elements 14/15/16 (own static server, Playwright `recordVideo`) |

## Why it failed (three distinct causes)

1. `kcat -C -q -b $KAFKA_BOOTSTRAP -t $TOPIC -o beginning -f '%s\n' | grep -m1 … | jq` — without `-e` kcat blocks at the partition
   end, `grep -m1` never matches a record that does not exist yet, and the pipeline has no timeout. The recording node sat in this
   one call for 5 h 17 m.
2. The recursive take ran `terminal-demo` from a subshell that never `cd`'d to the repository root, so every
   `curl --json @demo/payloads/…` failed to read its file, and the boot readiness loop was waiting on port 18090 held by an orphan
   `java -jar build/libs/catalog-1.0.0.jar`.
3. The first two attempts ran the app as a jar plus ad-hoc `catalog-demo-*` containers. **Wrong runtime** — this service runs on
   podman from `services/catalog/k8s/` (`podman play kube`); `docker` on this machine is the podman shim and `k3d` cannot start
   because there is no Docker daemon.

## Recipe for a correct run

```sh
# 1. image, without a container daemon
cd services/catalog && ./gradlew jibBuildTar && podman load -i build/jib-image.tar

# 2. infra as pods (k8s YAML, no cluster)
podman play kube services/catalog/k8s/infra/kafka.yaml
podman play kube services/catalog/k8s/infra/keycloak.yaml

# 3. the app
podman play kube services/catalog/k8s/base/configmap.yaml
podman play kube services/catalog/k8s/base/deployment.yaml
podman play kube services/catalog/k8s/base/service.yaml

# 4. record — every wait bounded, no `kcat -C` without `-e`, no `logs -f`
cd ../.. && node demo/record-views.cjs      # storybook take
```

`kustomization.yaml` files are kustomize inputs, not kube objects — do not feed them to `play kube`.
