# microservice-java-spring — example

Reference implementation extracted from `ai-craft-be-java`. Part of the `microservice-java-spring` blueprint.

## What's included

- **Architecture documentation** — full `../docs/arch/` with domain model, ports, adapters, security, testing, ArchUnit
- **Security** — OAuth2 / Keycloak integration, SpotBugs + Trivy tooling
- **Bounded contexts** — code structure with context boundaries, mediators, projections
- **Persistence** — event-sourcing, document, normalizing adapters
- **Developer tooling** — Hurl end-to-end scenarios (`e2e/devices.hurl`) with `scripts/trace.sh`, and SpotBugs configuration in `config/spotbugs`
- **K8s manifests** — base + overlay deployments (dev, k3s)

## Architecture docs

Architecture and implementation guidance is available at `../docs/arch/`. System documentation for
this application — HTTP interface and persistence model, composed from authored and generated
sections — is in `../docs/system-documentation.qmd`.

## Mandatory Workflow

This repository uses architecture-first development. Before coding, load the relevant docs from `../docs/arch` and follow them strictly.

1. Identify the task type (domain change, API, persistence, projection, cross-context flow, tests).
2. Read matching doc files listed below.
3. Implement only through existing architectural patterns.
4. Add or update tests in the same context package.
5. Verify ArchUnit constraints remain valid.
6. Run `sqt check .` from this directory — the whole-project gate: metrics bands, domain checks, and the bounded-context dependency rules.

If a task touches multiple contexts, read `../docs/arch/context-boundaries.md` and `../docs/arch/adapter-mediator.md` first.

### Document Map

| File | What it covers |
|------|----------------|
| `../docs/arch/index.md` | Entry point and recommended reading order |
| `../docs/arch/code-structure.md` | Package layout, naming conventions, module organization |
| `../docs/arch/ports.md` | Primary/secondary ports and mediator-based cross-context contracts |
| `../docs/arch/domain-model.md` | Aggregates, value objects, domain events, invariant placement |
| `../docs/arch/policy.md` | Business calculations and decision policies |
| `../docs/arch/adapter-http-command.md` | Write-side HTTP controllers and command DTO mapping |
| `../docs/arch/adapter-http-query.md` | Read-side HTTP endpoints and pagination patterns |
| `../docs/arch/adapter-persistence-event-sourcing.md` | Event-sourcing repositories and replay/save flow |
| `../docs/arch/adapter-persistence-document.md` | Document snapshot persistence with history |
| `../docs/arch/adapter-persistence-normalizing.md` | Normalized relational mapping adapter |
| `../docs/arch/adapter-projection.md` | Event-driven projection/read-model update patterns |
| `../docs/arch/adapter-mediator.md` | Cross-context orchestration adapter rules |
| `../docs/arch/context-boundaries.md` | Bounded contexts, shared kernel, dependency policy |
| `../docs/arch/security.md` | Current security posture and authN/authZ decisions |
| `../docs/arch/testing.md` | Test pyramid, fixtures, integration tests, custom asserts |
| `../docs/arch/arch-unit.md` | Architecture rules and how to add context architecture tests |
| `../docs/arch/adding-a-bounded-context.md` | Procedure for adding a new bounded context |

### Hard Constraints

- Keep bounded context internals package-private by default.
- Access the aggregate lifecycle through services, not directly from adapters.
- Do not call sibling context repositories/services directly; use mediator + contract.
- Do not expose new shared kernel types without an explicit architecture test update.
- Keep controllers thin: validation + mapping + service call only.
- Keep business invariants in aggregates.
- Keep repository ports domain-oriented (no JPA entities in port signatures).

### Implementation NOGO

- No cross-context imports of internal model classes.
- No business logic inside controllers, projections, or mediators.
- No direct write-model queries for read API when projection exists.
- No weakening ArchUnit rules to make code compile.
- No security assumptions from dependencies only; each new endpoint needs explicit security decision.

### Testing NOGO

- No skipping architecture tests for new contexts.
- No testing domain logic only through integration tests.
- No mutable shared fixture instances across tests.

### Quick Task Routing

- Add or change aggregate behavior → `domain-model.md`, `ports.md`, `testing.md`
- Add or change business calculation → `policy.md`, `domain-model.md`, `testing.md`
- Add or change REST write endpoint → `adapter-http-command.md`, `security.md`, `testing.md`
- Add or change REST read endpoint → `adapter-http-query.md`, `adapter-projection.md`, `testing.md`
- Add new persistence strategy → matching `adapter-persistence-*.md`, `ports.md`, `testing.md`
- Add cross-context use-case → `adapter-mediator.md`, `context-boundaries.md`, `arch-unit.md`
- Add new bounded context → `code-structure.md`, `context-boundaries.md`, `arch-unit.md`, `testing.md`

## Running

```sh
./gradlew build
./gradlew test
./gradlew bootRun
```

## Docker / K8s

```sh
# Docker
./gradlew jibDockerBuild

# K8s (k3s)
cd k8s
kustomize build overlays/k3s | kubectl apply -f -
```
