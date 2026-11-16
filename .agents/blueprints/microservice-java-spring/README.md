# ddd-devices

Reference implementation of a modular monolith for device configuration using Domain-Driven Design and Prots and Adapters patterns.

## Architecture Documentation

Architecture and implementation guidance for developers and coding agents is in `../docs/arch/`
(the blueprint repository, parent directory of this `example/`).

- [`index.md`](../docs/arch/index.md)
- [`code-structure.md`](../docs/arch/code-structure.md)
- [`ports.md`](../docs/arch/ports.md)
- [`domain-model.md`](../docs/arch/domain-model.md)
- [`policy.md`](../docs/arch/policy.md)
- [`adapter-http-command.md`](../docs/arch/adapter-http-command.md)
- [`adapter-http-query.md`](../docs/arch/adapter-http-query.md)
- [`adapter-persistence-event-sourcing.md`](../docs/arch/adapter-persistence-event-sourcing.md)
- [`adapter-persistence-document.md`](../docs/arch/adapter-persistence-document.md)
- [`adapter-persistence-normalizing.md`](../docs/arch/adapter-persistence-normalizing.md)
- [`adapter-projection.md`](../docs/arch/adapter-projection.md)
- [`adapter-mediator.md`](../docs/arch/adapter-mediator.md)
- [`context-boundaries.md`](../docs/arch/context-boundaries.md)
- [`security.md`](../docs/arch/security.md)
- [`testing.md`](../docs/arch/testing.md)
- [`arch-unit.md`](../docs/arch/arch-unit.md)
- [`adding-a-bounded-context.md`](../docs/arch/adding-a-bounded-context.md)

System documentation for this application — its external interface and persistence model — is composed
from authored and generated sections in [`../docs/system-documentation.qmd`](../docs/system-documentation.qmd).

Before implementing changes, read task-specific guidance in `AGENTS.md`.
