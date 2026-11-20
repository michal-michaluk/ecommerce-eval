---
name: run-apps
description: >-
  Deploy, inspect, rebuild and tear down the shop-wide local k3s cluster and the catalog service on
  it — k3s in a privileged podman container, because this machine has no Docker daemon and k3d cannot
  start. Use when asked to run the app, bring the environment up or down, restart it, rebuild the image
  after a code change, read pod logs, mint the four scoped Keycloak tokens, check
  cluster/pod/database/health status, or run the element-12 hurl contract against a deployed service.
  Trigger words: run-apps, run the app, deploy catalog, start the environment, bring up the cluster,
  stop the cluster, restart, rebuild catalog, pod logs, k3s status, mint tokens, run the hurl suite,
  e2e against the cluster, add a service database, shop cluster, shop-k3s, postgres list of databases.
---

# run-apps

One shop-wide cluster, one shop-wide Postgres instance, one namespace per service, one host
port-forward. Everything the catalog service needs to boot and to answer its contract, and nothing
that has to be started by hand. The cluster and the database are **shop-wide** — shopping,
fulfillment and the rest will follow the catalog into the same cluster and the same instance.

## Commands

`.agents/skills/run-apps/scripts/run-apps <command>` — run from the ecommerce root (`eval/ecommerce`).

| Command | What it does |
| --- | --- |
| `deploy` | podman machine → legacy cleanup → cluster container → kubeconfig → node Ready → infra apply/rollout → `jibBuildTar` → `ctr images import` → app apply → rollout → port-forward → health |
| `deploy --force-build` / `--skip-build` | rebuild the image even if nothing changed / reuse `build/jib-image.tar` |
| `status [--json]` | podman machine, cluster container, node, pods, deployments, services, endpoints, the databases the instance carries, host bridges, health, GREEN/RED verdict (exit 1 when RED) |
| `logs [pod] [--tail N] [--since 10m] [--follow] [--infra]` | bounded `kubectl logs`; no pod → every app pod; `--follow` is opt-in |
| `rebuild [catalog]` | `jibBuildTar` → import → `rollout restart` → wait Ready → health; touches only the named service |
| `restart` | `stop` then `deploy` |
| `stop` | kill port-forwards (tracked and orphaned) → `podman rm -f shop-k3s` (and any legacy `catalog-k3s`) → free 8080/18081/6443 → clear state |
| `auth [--json] [--reuse]` | four scoped tokens (author, reviewer, photographer, sales) from the in-cluster Keycloak, printed as `export` lines and written to `.agents/runtime/catalog-tokens.env` |
| `test [suite] [--base-url]` | `hurl --test` over `e2e/catalog-http-api.hurl` with the four tokens and the run-relative dates the Java harness uses |

Every command is idempotent. `deploy` on a deployed environment reuses the cluster, skips an
unchanged image and an unchanged rollout; `stop` on a stopped environment reports "none running".

## What it manages

```
podman machine (applehv, 5 CPU / 8 GiB, rootful)
└── container shop-k3s           rancher/k3s:v1.35.5-k3s1, privileged, API 127.0.0.1:6443
    └── namespace catalog
        ├── postgresql             postgres:16, SHOP-WIDE ONE INSTANCE  5432  (manifests/infra/postgres.yaml)
        │     databases: catalog   ← one logical database per service, see below
        ├── kafka                  apache/kafka:4.1.0 KRaft         9092  (manifests/infra/kafka.yaml)
        ├── keycloak               keycloak:26.6.1, realm `catalog` 8080  (manifests/infra/keycloak.yaml)
        ├── otel-collector         from the service's k8s/infra/otel-collector.yaml
        └── catalog                catalog:latest (Jib)             8080  (service k8s/overlays/k3d)
host bridges (pids in .agents/runtime/processes.json)
├── 127.0.0.1:8080  → svc/catalog:80
└── 127.0.0.1:18081 → svc/catalog-keycloak:8080     (only while `auth`/`test` need it)
```

State: `.agents/runtime/processes.json` (0600). Kubeconfig: `.agents/runtime/kubeconfig-shop.yaml`
(context `shop-k3s`). Port-forward logs: `.agents/runtime/logs/`.

The old per-service names (`catalog-k3s`, `catalog-db-postgresql`, `kubeconfig-catalog.yaml`) are
gone. A machine that still carries the stopped `catalog-k3s` container: `deploy` announces it and
removes it, `stop` removes it too, `status` prints a `legacy container` line while it exists — never
two clusters at once.

`kubectl` always gets `--kubeconfig` explicitly — the cluster never becomes the ambient context.

## Readiness signals

| Component | Signal | Timeout |
| --- | --- | --- |
| cluster | node `Ready=True` (`kubectl get nodes`) | 180 s |
| postgres | `rollout status` turns green only after `databases.txt` is fully applied — the probe is TCP, the initdb server is socket-only | 420 s |
| kafka / keycloak | `kubectl rollout status deployment/<name>` | 420 s |
| catalog | `rollout status` + `GET /actuator/health` → `200 {"status":"UP"}` on 127.0.0.1:8080 | 420 s + 180 s |
| keycloak realm | `GET /realms/catalog` on 127.0.0.1:18081 → 200 | 90 s |

No command blocks unbounded: `kubectl rollout status` carries `--timeout`, the port-forward wait and
health wait carry a deadline, and every timeout prints the pods, events and last log lines of the
deployment it timed out on. `kubectl logs -f` and `kubectl get -w` exist only behind `--follow`.

## Shop-wide Postgres — one instance, one database per service

The cluster and `postgresql` are not per service. Kafka, Keycloak, the app and its port-forward are.

| Piece | Scope | Where |
| --- | --- | --- |
| `shop-k3s` cluster, kubeconfig, context | shop-wide, one | `CLUSTER` in the CLI |
| `postgresql` Deployment/Service, one superuser secret `postgresql-superuser` | shop-wide, one | `manifests/infra/postgres.yaml` |
| logical database + role + password | one per service | **one line in `databases.txt`** |
| `<service>-db` secret (here `catalog-db`) | one per service | the service's own overlay, overridden here |
| namespace, app Deployment, port-forward, tokens | one per service | `NAMESPACE` / `APP_DEPLOYMENT` in the CLI |

**A new service adds its database in exactly one place:** append a line to `databases.txt` in
`manifests/infra/postgres.yaml`:

```
database:role:password      # e.g.  shopping:shopping:shopping
```

and point that service's own `<service>-db` secret at the shared instance —
`jdbc:postgresql://postgresql:5432/<database>`, same role and password as the line. `status` prints
the databases the instance carries, so the line is verifiable without a psql session.

**Bootstrap.** The `postgresql-init` ConfigMap is mounted at `/docker-entrypoint-initdb.d`; its
`10-databases.sh` reads `databases.txt` and, for every line, checks `pg_roles` before `CREATE ROLE`
and `pg_database` before `createdb` (Postgres has no `CREATE DATABASE IF NOT EXISTS`). Running it
twice is a no-op, and it runs as the superuser over the local socket, so it needs no password.

**Init scripts only run on an empty data directory.** The data directory is an `emptyDir`, so it is
empty at every pod start — after appending a line, pick one:

- `kubectl --kubeconfig .agents/runtime/kubeconfig-shop.yaml -n catalog rollout restart deployment/postgresql`
  — fastest; the new pod starts on an empty data directory, so the init runs again (the instance is
  ephemeral by design, there is no data to keep), or
- `run-apps stop` then `run-apps deploy` (discards the container too).

If the data directory ever becomes a PVC, a restart no longer re-runs the init — create the database by
hand instead:
`kubectl -n catalog exec deploy/postgresql -- createdb -U postgres -O <role> <database>`.

Today the instance lives in the `catalog` namespace, so another service in its own namespace reaches
it at `postgresql.catalog.svc.cluster.local:5432`; a service in the `catalog` namespace uses
`postgresql:5432`.

## Environment facts this skill is built on

1. **No Docker daemon.** `docker` on PATH is the podman 6.1.0 shim; there is no
   `/var/run/docker.sock` and no Docker Desktop/OrbStack. `k3d cluster create|start` fails with
   "Cannot connect to the Docker daemon", and the `k3d-ecommerce` kubectl context is a dead leftover
   on `0.0.0.0:52333`. k3s runs as a **privileged podman container** instead — the add-run
   instruction's sanctioned path for a local cluster.
2. **Podman machine is rootful** (`podman machine inspect` → `Rootful: true`), applehv, 5 CPU / 8 GiB.
   The cluster fits: infra limits sum to ~3 GiB, the app to 1.5 GiB.
3. **The disk is shared and often nearly full.** The machine has one 100 GiB disk and other work
   (sandboxes, old eval stacks) fills it; k3s evicts every pod at its default 5 % free threshold
   (`The node was low on resource: ephemeral-storage`). The cluster is therefore created with
   `--kubelet-arg=eviction-hard=imagefs.available<1Gi,nodefs.available<1Gi`, and `deploy` refuses to
   start below 3 GiB free with the remedy in the message (`podman image prune -f`, `podman system df`).
4. **The image must be arm64.** Jib defaults to `linux/amd64` regardless of host, and `ctr images
   import` then fails with `ctr: image might be filtered out`. The CLI reads
   `.status.nodeInfo.architecture` from the node and builds with
   `-Djib.from.platforms=linux/<arch>`, verifying the tar's `config.json` afterwards.
5. **No registry.** `jibBuildTar` → `podman exec -i shop-k3s ctr -n k8s.io images import -`;
   the Deployment's `imagePullPolicy: IfNotPresent` keeps kubelet off the network.
6. **`stop` discards the containerd store**, because it lives in the container's filesystem. The next
   `deploy` re-pulls postgres/kafka/keycloak/otel (~2 min) and re-imports the app image from the tar.

## Workarounds in the manifests (and why)

- **The service's `k8s/infra/` is a stale blueprint copy.** `kafka.yaml` and `keycloak.yaml` do carry
  Deployments, but the Keycloak one imports realm `iot` with users `john/jane/admin` — the service's
  realm document is `catalog` with the four `catalog-*` scopes, and the contract suite cannot get a
  single token from `iot`. Postgres has no manifest at all. The skill therefore ships its own infra
  under `manifests/infra/` (postgres, kafka, keycloak with the real realm) and leaves the service
  tree untouched.
- **`JWT_ISSUER_URI` is `…/realms/iot` in `k8s/overlays/k3d`.** `manifests/app/kustomization.yaml`
  merges the service overlay and overrides the one key to `…/realms/catalog`, and adds the
  `otel-collector` the overlay's `OTEL_EXPORTER_OTLP_ENDPOINT` points at (that manifest exists in
  `k8s/infra/` but no kustomization includes it). Nothing else is redefined.
- **Realm config map is generated at deploy time** from
  `src/test/resources/catalog-realm.json` — the test resource stays the single source of truth.
- **Keycloak pins `KC_HOSTNAME=http://catalog-keycloak:8080`.** Without it Keycloak derives `iss`
  from the request host, and a token minted through the host port-forward would carry
  `http://127.0.0.1:18081/...`, which the resource server rejects.
- **Kafka needs a writable `/opt/kafka/config`.** The image's entrypoint regenerates
  `server.properties` from the `KAFKA_*` env, so an emptyDir there and at `/opt/kafka/logs` is
  required under `readOnlyRootFilesystem: true`.
- **The Ingress of `overlays/k3d` is applied but inert**: Traefik and ServiceLB are disabled in the
  cluster, and 127.0.0.1:8080 is the port-forward, not an ingress.
- **The service overlay's `catalog-db` secret still points at `catalog-db-postgresql`.** The shop-wide
  instance is `postgresql`, so `manifests/app/kustomization.yaml` merges `jdbc-url=
  jdbc:postgresql://postgresql:5432/catalog` into the generated secret; the role and password stay
  the service's own.

## Proven on this machine

A full cycle, observed end to end after the shop-wide rename (fresh `stop` → `deploy` 111.7 s;
`deploy` on a running environment ≈ 5 s; `rebuild` 54.5 s; `restart` 115.1 s):

| Step | Observed |
| --- | --- |
| `deploy` → `status` | container `shop-k3s` RUNNING, pod `postgresql-7869f44ccb-r825v` 1/1, `catalog-64d94c7c84-drzqg` 1/1, 5 pods `1/1 Running` (otel-collector included), `databases catalog (instance postgresql)`, `GREEN — GET /actuator/health → 200 UP`, exit 0 |
| the database list | `psql -lqt` in the pod: `catalog postgres template0 template1`; `pg_stat_activity`: `catalog <- catalog x15`, `postgres <- postgres x1` |
| a real write through the shop-wide instance | `POST /offers → 201 /offers/OFR-1LEH7H68`, `GET → 200`, and `offer_document` in database `catalog` holds `OFR-1LEH7H68 | SW894838` |
| `auth` | 4 tokens, scopes `openid catalog-author|catalog-reviewer|catalog-photographer|catalog-sales`, +3600 s |
| `test` | `Executed requests: 106`, `Failed files: 0`, exit 0 |
| `rebuild catalog` | `catalog-695c777df8-krkms → catalog-7c66c87b68-m4j5j`, Ready, health 200, port-forward restarted after the pod swap |
| `restart` | stop removed `shop-k3s`, `catalog-k3s was not running`, both port-forwards killed, ports free; deploy recreated the cluster, `GREEN` |
| `stop` | `shop-k3s removed`, `podman ps -a` has neither `shop-k3s` nor `catalog-k3s`, 8080/18081/6443 free |
| `deploy` twice | second run: `cluster already running`, `image tar up to date`, `cluster already carries catalog:latest`, ready rollout, health 200 in ≈ 5 s |
| `stop` twice | second run: `none running`, `shop-k3s was not running`, exit 0 |
| legacy machine | a planted stopped `catalog-k3s`: `deploy` prints `catalog-k3s is the per-service cluster of the old layout, replaced by shop-k3s — removing it` and removes it |
| disk guard | with 2.2 GiB free `deploy` stops at `error: the podman machine disk has 2.2 GiB free — k3s will evict every pod under disk pressure` and exit 1 |

## Prerequisites

`podman` (machine running, rootful), `kubectl`, `hurl`, Java 25, the local image
`docker.io/rancher/k3s:v1.35.5-k3s1`. `helm` is not needed — the infra is plain manifests.

## Tests

`run-apps test` is the element-12 contract, not a smoke test: it mints the four tokens, computes
today-relative dates exactly as `src/test/java/shop/catalog/e2e/CatalogE2eVariables` does, and runs
`hurl --test --max-time 30` over `e2e/catalog-http-api.hurl` (106 requests) against
`http://127.0.0.1:8080`. Exit code is hurl's; the request count is printed with it.

The suite needs no in-cluster DNS, so the host port-forward is the transport. `--base-url` accepts
another address if one is ever needed.

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `Cannot connect to the Docker daemon` from any k3d command | expected — k3d needs Docker; use this skill |
| cluster container exits right after `deploy` | `podman logs shop-k3s`; the image must be `rancher/k3s`, rootful machine, privileged container |
| `ctr: image might be filtered out` | tar arch ≠ node arch — `deploy --force-build` rebuilds with the right platform |
| pods `CreateContainerConfigError` | the `catalog-db` secret or the `catalog` ConfigMap key is missing; re-run `deploy` |
| `postgresql` pod restarts or never becomes Ready | its init script failed — `kubectl -n catalog logs deploy/postgresql --previous --tail 40`; a bad line in `databases.txt` is the usual cause |
| app logs `database "catalog" does not exist` | the init did not run (data directory not empty) — `createdb` by hand as described above, or `stop` then `deploy` |
| `status` shows `legacy container catalog-k3s PRESENT` | the old per-service cluster is still on the machine; `deploy` or `stop` removes it |
| app logs `Couldn't find a token issuer` | Keycloak not Ready yet or `KC_HOSTNAME` changed; `rollout status deployment/keycloak` |
| `port 8080 is already taken by pid …` | a foreign process holds the port; `stop` refuses to kill it — free it yourself |
| `auth` 401 `invalid_grant` | the realm was imported before the config map existed (`stop`, then `deploy`) |
| pods `Pending`, `node was low on resource: ephemeral-storage` | the podman machine disk is full — `podman image prune -f`, then `podman system df`; `deploy` refuses below 3 GiB free |
| `port-forward … exited` | the API server or the pod went away; `status` then `deploy` restores it |

## Files

| Path | Purpose |
| --- | --- |
| `scripts/run-apps` | the whole CLI (Python 3, stdlib only) |
| `manifests/app/kustomization.yaml` | service `overlays/k3d` + catalog issuer + shop `postgresql` address + otel collector |
| `manifests/infra/postgres.yaml` | the shop-wide `postgresql`: superuser secret, `databases.txt` (one line per service), init script, Service, Deployment |
| `manifests/infra/kafka.yaml` | `catalog-kafka` KRaft broker |
| `manifests/infra/keycloak.yaml` | Keycloak with the `catalog` realm from the test resource |
| `.agents/runtime/processes.json` | cluster, host-process and token state |
