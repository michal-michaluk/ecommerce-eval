#!/usr/bin/env bash
# The basket HTTP contract against the local k3s cluster — the entry point this service documents.
# The harness lives with the spec it runs; this is its service-side name.
set -euo pipefail

here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
exec "$here/../../../docs/specs/basket/prototypes/prove-local.sh" "$@"
