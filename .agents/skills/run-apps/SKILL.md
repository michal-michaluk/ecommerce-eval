---
name: run-apps
description: >-
  Deploy, inspect, rebuild and tear down the shop-wide local k3s cluster and the two services on it —
  catalog and fulfillment — k3s in a privileged podman container, because this machine has no Docker
  daemon and k3d cannot start. Use when asked to run the apps, bring the environment up or down,
  restart it, rebuild one service's image after a code change, read pod logs, mint the scoped Keycloak
  tokens of both realms, check cluster/pod/database/health status, or run a service's hurl contract
  against the deployed service. Trigger words: run-apps, run the app, run the apps, deploy catalog,
  deploy fulfillment, start the environment, bring up the cluster, stop the cluster, restart, rebuild
  catalog, rebuild fulfillment, pod logs, k3s status, mint tokens, run the hurl suite, e2e against the
  cluster, add a service database, shop cluster, shop-k3s, postgres list of databases.
---

# run-apps

One shop-wide cluster, one shop-wide Postgres instance, one Keycloak, one namespace per service, one
host port-forward per service. Everything `catalog` and `fulfillment` need to boot and to answer their
contracts, and nothing that has to be started by hand. The cluster, the database, Kafka and Keycloak
are **shop-wide**; a new service adds a namespace, an image, a forward and a realm — not an
infrastructure stack.

## Commands

`.agents/skills/run-apps/scripts/run-apps <command>` — run from the ecommerce root (`eval/ecommerce`).

| Command | What it does |
| --- | --- |
| `deploy [catalog\|fulfillment]` | podman machine → legacy cleanup → cluster container → kubeconfig → node Ready → infra apply/rollout → per service: `jibBuildTar` → `ctr images import` → app apply → rollout → port-forward → health. No argument = both, catalog first |
| `deploy --force-build` / `--skip-build` | rebuild every named service's image even if nothing changed / reuse its `build/jib-image.tar` |
| `status [--json]` | podman machine, cluster container, node, the databases the instance carries, **both** services' pods, deployments, health and forwards, the shared infra, GREEN/RED/NONE verdict |
| `logs [pod\|service] [--tail N] [--since 10m] [--follow] [--infra]` | bounded `kubectl logs`; a service name → that service's app pods, a pod name → that pod (namespace is looked up), no argument → every app pod of both services, `--infra` → the shared infra pods |
| `rebuild [catalog\|fulfillment]` | `jibBuildTar` → import → `rollout restart` → wait Ready → health; touches only the named service. No argument = both, sequentially |
| `restart [catalog\|fulfillment]` | `stop` then `deploy` |
| `stop` | kill port-forwards (tracked and orphaned) → `podman rm -f shop-k3s` (and any legacy `catalog-k3s`) → free 8080/8081/18081/6443 → clear state and tokens |
| `auth [--json] [--reuse]` | mint the scoped tokens of **both** realms (catalog: author, reviewer, photographer, sales; fulfillment: employee, other-employee, resolver, customer, shopping), printed as `export` lines and written to `.agents/runtime/tokens.env` |
| `test [catalog\|fulfillment] [--suite] [--base-url]` | `hurl --test` over that service's suite with its own tokens; no argument = both, sequentially |

Every command is idempotent. `deploy` on a deployed environment reuses the cluster, skips an unchanged
image and an unchanged rollout; `stop` on a stopped environment reports `none running` and exits 0.

`status` exits 0 for GREEN and for NONE (nothing running at all — the state `stop` leaves), and exits 1
only for a half-state: cluster up while a service is not 1/1 or not healthy.

## What it manages

```
podman machine (applehv, 5 CPU / 8 GiB, rootful)
└── container shop-k3s               rancher/k3s:v1.35.5-k3s1, privileged, API 127.0.0.1:6443
    ├── namespace catalog            SHARED INFRA, ONE INSTANCE EACH
    │   ├── postgresql               postgres:16                       5432  (manifests/infra/postgres.yaml)
    │   │     databases: catalog, fulfillment   ← one logical database per service, see below
    │   ├── kafka                    apache/kafka:4.1.0 KRaft          9092  (manifests/infra/kafka.yaml)
    │   ├── keycloak                 keycloak:26.6.1, realms catalog + iot
    │   │                                                           8080  (manifests/infra/keycloak.yaml)
    │   ├── otel-collector           catalog's collector              4317  (services/catalog/k8s/infra)
    │   └── catalog                  catalog:latest (Jib)             8080  (services/catalog/k8s/overlays/k3d)
    └── namespace fulfillment
        ├── otel-collector           fulfillment's collector          4317  (services/fulfillment/k8s/infra)
        └── fulfillment              fulfillment:latest (Jib)         8080  (services/fulfillment/k8s/overlays/k3d)
host bridges (pids in .agents/runtime/processes.json)
├── 127.0.0.1:8080  → catalog/svc/catalog:80
├── 127.0.0.1:8081  → fulfillment/svc/fulfillment:80
└── 127.0.0.1:18081 → catalog/svc/catalog-keycloak:8080     (only while `auth`/`test` need it)
```

State: `.agents/runtime/processes.json` (0600). Kubeconfig: `.agents/runtime/kubeconfig-shop.yaml`
(context `shop-k3s`). Tokens: `.agents/runtime/tokens.env` (0600, replaced the old
`catalog-tokens.env`; `stop` removes both). Port-forward logs: `.agents/runtime/logs/`.

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
| fulfillment | `rollout status` + `GET /actuator/health` → `200 {"status":"UP"}` on 127.0.0.1:8081 | 420 s + 180 s |
| keycloak realms | `GET /realms/catalog` and `GET /realms/iot` on 127.0.0.1:18081 → 200 | 90 s |

No command blocks unbounded: `kubectl rollout status` carries `--timeout`, the port-forward wait and
health wait carry a deadline, and every timeout prints the pods, events and last log lines of the
deployment it timed out on (in that service's namespace). `kubectl logs -f` and `kubectl get -w` exist
only behind `--follow`.

## Shop-wide Postgres — one instance, one database per service

The cluster and `postgresql` are not per service. Kafka, Keycloak, the app and its port-forward are
shared the same way: one Kafka broker, one Keycloak with both realms.

| Piece | Scope | Where |
| --- | --- | --- |
| `shop-k3s` cluster, kubeconfig, context | shop-wide, one | `CLUSTER` in the CLI |
| `postgresql` Deployment/Service, one superuser secret `postgresql-superuser` | shop-wide, one | `manifests/infra/postgres.yaml` |
| `kafka` Deployment + Service `catalog-kafka` | shop-wide, one | `manifests/infra/kafka.yaml` |
| `keycloak` Deployment + Service `catalog-keycloak`, ConfigMap `keycloak-realm` | shop-wide, one | `manifests/infra/keycloak.yaml` |
| logical database + role + password | one per service | **one line in `databases.txt`** |
| `<service>-db` secret (`catalog-db`, `fulfillment-db`) | one per service | the service's own overlay, overridden in `manifests/<service>/kustomization.yaml` |
| namespace, app Deployment, port-forward, realm, tokens | one per service | the `App` rows in the CLI |

**A new service adds its database in exactly one place:** append a line to `databases.txt` in
`manifests/infra/postgres.yaml`:

```
database:role:password      # e.g.  fulfillment:fulfillment:fulfillment
```

and point that service's own `<service>-db` secret at the shared instance —
`jdbc:postgresql://postgresql.catalog.svc.cluster.local:5432/<database>`, same role and password as
the line. `status` prints the databases the instance carries, so the line is verifiable without a psql
session — today it prints `catalog, fulfillment`.

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

The instance lives in the `catalog` namespace, so `catalog` reaches it at `postgresql:5432` and
`fulfillment` at `postgresql.catalog.svc.cluster.local:5432`.

## One Keycloak, one realm per service

`keycloak` imports every realm document in the directory the `keycloak-realm` ConfigMap is mounted
into (`/opt/keycloak/data/import`), so one Keycloak serves realm `catalog` and realm `iot`.

| Service | Realm | Realm document | Users | Clients |
| --- | --- | --- | --- | --- |
| catalog | `catalog` | `services/catalog/src/test/resources/catalog-realm.json` if it exists, else the copy this skill vendors at `manifests/realms/catalog-realm.json` | author, reviewer, photographer, sales | `catalog-<user>-client` |
| fulfillment | `iot` | `services/fulfillment/src/test/resources/iot-realm.json` | employee, other-employee, resolver, customer, shopping | `fulfillment-employee-client`, `fulfillment-resolver-client`, `fulfillment-customer-client`, `shopping-service-client` |

The realm document stays the service's single source of truth. The catalog document is on
`feat/catalog-domain-model` and **not on `main`**, so the skill vendors the same file and a `deploy` on
`main` still imports a realm the catalog resource server accepts; when the branch lands, the service's
own file wins and the vendored copy becomes dead weight.

Every credential here is the local dev placeholder the manifests and realm documents already carry
(`author`/`author`, `employee`/`employee`, client secret `secret`, Keycloak `admin`/`admin`). Nothing
in this skill invents or holds a secret of its own.

`--import-realm` reads the directory only at startup and skips a realm that already exists, so when the
realm set changes `deploy` restarts `deployment/keycloak` (it records a digest of the ConfigMap in the
state file and only restarts when it differs).

## Environment facts this skill is built on

1. **No Docker daemon.** `docker` on PATH is the podman 6.1.0 shim; there is no
   `/var/run/docker.sock` and no Docker Desktop/OrbStack. `k3d cluster create|start` fails with
   "Cannot connect to the Docker daemon", and the `k3d-ecommerce` kubectl context is a dead leftover
   on `0.0.0.0:52333`. k3s runs as a **privileged podman container** instead — the add-run
   instruction's sanctioned path for a local cluster.
2. **Podman machine is rootful** (`podman machine inspect` → `Rootful: true`), applehv, 5 CPU / 8 GiB.
   The cluster fits: the seven pods request 3 GiB of the 8 GiB (catalog namespace 2 GiB, fulfillment
   namespace 1 GiB).
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
   the Deployments' `imagePullPolicy: IfNotPresent` keeps kubelet off the network.
6. **`stop` discards the containerd store**, because it lives in the container's filesystem. The next
   `deploy` re-pulls postgres/kafka/keycloak/otel (~2 min) and re-imports both app images from their
   tars.

## Workarounds in the manifests (and why)

- **The services' `k8s/infra/` is a stale blueprint copy.** `kafka.yaml` and `keycloak.yaml` do carry
  Deployments, but neither is the realm or the broker this environment needs (catalog's Keycloak copy
  imports realm `iot` with users `john/jane/admin`, and there is no Postgres manifest at all). The
  skill ships its own infra under `manifests/infra/` and leaves the service trees untouched; each
  service's `k8s/infra/otel-collector.yaml` is used, in its own namespace.
- **Overrides live in `manifests/<service>/kustomization.yaml`, never in the service tree.**
  `manifests/catalog/` merges the catalog overlay, adds its collector and overrides
  `JWT_ISSUER_URI=http://catalog-keycloak.catalog.svc.cluster.local:8080/realms/catalog` and
  `jdbc-url=jdbc:postgresql://postgresql:5432/catalog`.
  `manifests/fulfillment/` does the same for fulfillment: `kafka-bootstrap-servers=
  catalog-kafka.catalog.svc.cluster.local:9092`, `JWT_ISSUER_URI=http://catalog-keycloak.catalog.
  svc.cluster.local:8080/realms/iot`, `jdbc-url=jdbc:postgresql://postgresql.catalog.svc.cluster.
  local:5432/fulfillment` with the overlay's own `fulfillment`/`fulfillment` credentials. The
  overlays keep pointing at the standalone `fulfillment-kafka`/`fulfillment-keycloak`/
  `fulfillment-db-postgresql` a standalone deployment would install, because they must stay honest.
- **The override cannot be a `kubectl apply` after the overlay.** A kustomize merge generator renames
  its object with a content hash and rewrites the Deployment's `secretKeyRef`/`configMapKeyRef` to the
  hashed name (`catalog-db-k94df5mg66`, `fulfillment-db-kfmc66555h`), so a plain object of the
  unhashed name would be read by nobody.
- **Kafka advertises a fully qualified Service name** (`catalog-kafka.catalog.svc.cluster.
  local:9092`): the broker's metadata is also read by a pod in the `fulfillment` namespace, where the
  bare name does not resolve.
- **Keycloak pins `KC_HOSTNAME=http://catalog-keycloak.catalog.svc.cluster.local:8080`.** Without
  a pinned host Keycloak derives `iss` from the request host, and a token minted through the host
  port-forward would carry `http://127.0.0.1:18081/...`, which both resource servers reject. It is
  the fully qualified name, not the bare Service name, because both services configure that string as
  their `issuer-uri` and the `iss` in a token has to match it byte for byte; only the FQDN resolves in
  the `fulfillment` namespace too.
- **Kafka needs a writable `/opt/kafka/config`.** The image's entrypoint regenerates
  `server.properties` from the `KAFKA_*` env, so an emptyDir there and at `/opt/kafka/logs` is
  required under `readOnlyRootFilesystem: true`.
- **The Ingress of each `overlays/k3d` is applied but inert**: Traefik and ServiceLB are disabled in
  the cluster, and 127.0.0.1:8080/8081 are the port-forwards, not ingresses.

## Proven on this machine

A full cycle, observed end to end with both services.

| Step | Observed |
| --- | --- |
| `deploy` from nothing (container absent, image tars present) | exit 0 in 219 s: `shop-k3s` created, postgres/kafka/keycloak Ready, both images imported, `catalog-64d94c7c84-rvwcc` and `fulfillment-7ddb4d9b76-nnlkb` 1/1, health `200 UP` on 8080 and 8081 |
| `status` | exit 0: `databases catalog, fulfillment (instance postgresql)`, both services `1/1` and `200 UP`, two LIVE forwards, `GREEN — cluster up, catalog 1/1 200 UP, fulfillment 1/1 200 UP` |
| `auth` | exit 0: 9 tokens out of the one Keycloak — realm `catalog` (author/reviewer/photographer/sales, scope `openid catalog-author`…, client `catalog-*-client`) and realm `iot` (employee/other-employee/resolver/customer/shopping, scope `openid fulfillment-employee`…, client `fulfillment-*-client`), +3600 s, written to `.agents/runtime/tokens.env` (9 lines, 0600) |
| keycloak import | `Realm 'catalog' imported`, `Realm 'iot' imported`, `Import finished successfully` in one pod |
| `test fulfillment` | executes requests 1–8 on a fresh database (order hand-over `201`, `OP-2026-0001`) and fails at request 9 (`e2e/fulfillment-api.hurl:121`) with `500 the warehouse port has no implementation` — exit 4, see *Known gaps* below |
| `test catalog` | `e2e/healthcheck.hurl` fails at request 2 (`:12`): it expects `401` on `GET /actuator/info`, the app permits the path and answers `200` — exit 4, see *Known gaps* |
| `rebuild catalog` | exit 0: `catalog-5d656df695-v5hm2 → catalog-7776ddff48-pd47q`, health `200 UP`, forward restarted, `fulfillment-6db4c5ff4d-xjzpp` untouched |
| `rebuild fulfillment` | exit 0: `fulfillment-7ddb4d9b76-nnlkb → fulfillment-d8dfb44d6-xlfvx`, health `200 UP`, `catalog-64d94c7c84-rvwcc` untouched |
| `stop` | exit 0: three forwards killed, `shop-k3s removed`, `catalog-k3s was not running`, 8080/8081/18081/6443 free, kubeconfig and `tokens.env` removed, state cleared |
| `status` after `stop` | exit 0: `NONE — nothing running (cluster absent, no host bridges)` |
| `deploy` twice | second run 11 s: `cluster already running`, both `image tar up to date`, `cluster already carries catalog:latest` and `fulfillment:latest`, both forwards `already up`, health `200 UP` |

## Known gaps in the services themselves

`run-apps` deploys and gates both services; it cannot make a service's suite pass when the service disagrees with it. Two such disagreements are visible on `main` and are reported here instead of hidden:

- **`catalog`'s `e2e/healthcheck.hurl` expects `401` on `GET /actuator/info`.**
  `services/catalog/src/main/java/shop/catalog/AppConfiguration.java:29` permits that path, so the deployed app answers `200` and the suite fails on its second request. Element 12 (offers, the four scopes) lives on `feat/catalog-domain-model`, not on `main`; the same branch carries the 106-request `e2e/catalog-http-api.hurl` that `--suite` can run once it lands.
- **`fulfillment` cannot complete its own contract without a warehouse.**
  `services/fulfillment/src/main/java/shop/fulfillment/processing/InvenTreeWarehouseIntegration.java` wires the adapter only when `fulfillment.warehouse.token` (and URL) are configured, and its class comment states that no simulator lives in `src/main` — the tests bring their own double. A deployment without them boots, answers `/orders`, and refuses the first claim with `the warehouse port has no implementation` (a 500, not the intended `WAREHOUSE_NOT_WIRED`). The suite therefore stops at request 9 of 48. A deployment that passes it needs an InvenTree-shaped endpoint (`docs/api/warehouse-api.md`) and `FULFILLMENT_WAREHOUSE_URL`/`FULFILLMENT_WAREHOUSE_TOKEN` in `manifests/fulfillment/kustomization.yaml`; the eval itself says the warehouse is never run, so this environment does not invent one.

`test` still does its job in both cases: the right suite, the right tokens, the real service, hurl's own exit code.

## Prerequisites

`podman` (machine running, rootful), `kubectl`, `hurl`, Java 25, the local image
`docker.io/rancher/k3s:v1.35.5-k3s1`, and network access on the first `deploy` of a service (Jib pulls
the base image and the build downloads the OpenTelemetry java agent). `helm` is not needed — the infra
is plain manifests.

## Tests

`run-apps test <service>` runs that service's own suite against its host port-forward, with that
service's own tokens, each suite read as the service itself documents it:

| Service | Suite | Variables |
| --- | --- | --- |
| catalog | `e2e/healthcheck.hurl` (3 requests: health 200 UP, secured endpoint 401 with no token and with a bad one) | `BASE_URL` plus the four catalog tokens and the run-relative dates, so the element-12 suite (`e2e/catalog-http-api.hurl`, 106 requests — present on `feat/catalog-domain-model`, not on `main`) runs on the same command with `--suite e2e/catalog-http-api.hurl` |
| fulfillment | `e2e/fulfillment-api.hurl` (48 requests) | `BASE_URL`, `shoppingToken`, `employeeToken`, `otherEmployeeToken`, `resolverToken`, `customerToken` |

The suites need no in-cluster DNS, so the host port-forward is the transport. `--base-url` accepts
another address. Exit code is hurl's; the request and file counts are printed with it.

Before each suite `test` sends one authenticated request of its own (catalog: `GET /actuator/info`,
fulfillment: `GET /orders`), retried up to four times, and then starts hurl. The first authenticated
request of a fresh pod is also the app's first JWKS fetch, and Keycloak's first realm-key load can
outlast the resource server's read timeout — the warm-up is what keeps a suite from failing on a 500
that a plain retry would have avoided.

`e2e/fulfillment-api.hurl` asserts `201` on the first hand-over of its order hash, so it needs a
database without that order: a `deploy` on a fresh pod, `run-apps stop` + `run-apps deploy`, or a
`rollout restart deployment/postgresql` in namespace `catalog` followed by a restart of the service
(so its Liquibase migration runs against the empty database). A second run against the same instance
fails on that assertion by design.

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `Cannot connect to the Docker daemon` from any k3d command | expected — k3d needs Docker; use this skill |
| cluster container exits right after `deploy` | `podman logs shop-k3s`; the image must be `rancher/k3s`, rootful machine, privileged container |
| `ctr: image might be filtered out` | tar arch ≠ node arch — `deploy --force-build` rebuilds with the right platform |
| pods `CreateContainerConfigError` | the `<service>-db` secret or the service's ConfigMap key is missing; re-run `deploy` |
| fulfillment logs `UnknownHostException: fulfillment-db-postgresql` | the service's own overlay was applied without `manifests/fulfillment`; re-run `deploy` |
| fulfillment logs `UnknownHostException: fulfillment-kafka` | same — the shared broker is `catalog-kafka.catalog.svc.cluster.local:9092` |
| `postgresql` pod restarts or never becomes Ready | its init script failed — `kubectl -n catalog logs deploy/postgresql --previous --tail 40`; a bad line in `databases.txt` is the usual cause |
| app logs `database "fulfillment" does not exist` | the init did not run on this data directory — `createdb` by hand as described above, or `stop` then `deploy` |
| `status` shows `legacy container catalog-k3s PRESENT` | the old per-service cluster is still on the machine; `deploy` or `stop` removes it |
| app logs `Couldn't find a token issuer` | Keycloak not Ready yet or `KC_HOSTNAME` changed; `rollout status deployment/keycloak -n catalog` |
| `port 8080/8081 is already taken by pid …` | a foreign process holds the port; `stop` refuses to kill it — free it yourself |
| `auth` 401 `invalid_grant` | the realm was imported before the ConfigMap existed, or the realm set changed without a Keycloak restart (`stop`, then `deploy`) |
| `auth` hangs on realm `iot` only | the second document was not in the ConfigMap — `deploy` prints both realms and their digest |
| first authenticated request 500, logs `Read timed out` on `…/protocol/openid-connect/certs` | Keycloak's first realm-key load outlasted the resource server's read timeout; `run-apps test` warms it, and the same request succeeds on retry |
| `test catalog` fails on `GET /actuator/info` expecting 401 | the app permits that path on `main` — see *Known gaps* |
| `test fulfillment` fails at request 9 with `the warehouse port has no implementation` | no warehouse adapter is configured — see *Known gaps* |
| pods `Pending`, `node was low on resource: ephemeral-storage` | the podman machine disk is full — `podman image prune -f`, then `podman system df`; `deploy` refuses below 3 GiB free |
| `port-forward … exited` | the API server or the pod went away; `status` then `deploy` restores it |

## Files

| Path | Purpose |
| --- | --- |
| `scripts/run-apps` | the whole CLI (Python 3, stdlib only) |
| `manifests/catalog/kustomization.yaml` | catalog `overlays/k3d` + its `catalog` realm issuer + the shop `postgresql` address + its otel collector |
| `manifests/fulfillment/kustomization.yaml` | fulfillment `overlays/k3d` + the shared Kafka/Keycloak addresses + the shop `postgresql` address + its otel collector |
| `manifests/realms/catalog-realm.json` | the vendored realm `catalog`, used while the service does not ship its own document |
| `manifests/infra/postgres.yaml` | the shop-wide `postgresql`: superuser secret, `databases.txt` (one line per service), init script, Service, Deployment |
| `manifests/infra/kafka.yaml` | the shop-wide `catalog-kafka` KRaft broker |
| `manifests/infra/keycloak.yaml` | one Keycloak, one realm document per service from the `keycloak-realm` ConfigMap |
| `.agents/runtime/processes.json` | cluster, host-process, realm-digest and token state |
| `.agents/runtime/tokens.env` | the minted tokens of both realms (0600) |
