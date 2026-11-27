#!/usr/bin/env bash
# The basket contract against the local k3s cluster: one command, no hand-typed secret, no hard-coded
# date. It starts nothing — the cluster must already be GREEN (`.agents/skills/run-apps`).
#
#   prove-local.sh [--dry-run] [--help] [prove run arguments]
#
# `--dry-run` does the whole environment half — cluster verdict and port, token mint, date derivation,
# the variable-tier assertion and both route probes — then prints the prove command line with the
# tokens masked, without executing it. `--dry-run` and `--help` are stripped wherever they appear;
# everything else is forwarded to `prove run` verbatim, so `--format json`, `--keep-going` and
# `--step '<heading>'` work unchanged. Scenario files resolve against docs/specs/basket (the directory
# the contract's own command block runs from), then against the caller's directory, and a file argument
# replaces the default prototypes/basket-api.prove.md.
#
# A dry run is NOT side-effect free: the first probe is step 1's own `GET /baskets`, which auto-creates
# the customer's basket (D29). It is the same request prove would send a second later.
#
# Why every value travels as `--var`: prove resolves a variable as capture > `--var` > the document's
# `**vars:**` > `env.<name>.vars`, so the defaults in basket-api.prove.md (base_url :8080, a fixed
# 2026-11-28/2026-11-30, `override-with---var-*` placeholders) would win over prove.yaml. The document
# is the spec and is not edited; the environment is supplied from outside it — and `check_tiers` proves
# on every run that the two tiers still rank that way, so a prove release that flipped the order fails
# here instead of silently sending the contract to catalog :8080.
#
# Two hazards this file cannot remove:
# - prove 0.1.0 has no file or environment indirection for a variable, so both tokens reach the
#   process table in argv: `ps` shows them to every local user for the life of the run.
# - est_from/est_to are derived once, at start. A run that crosses midnight in Europe/Warsaw computes
#   the next day inside the service and mismatches every deliveryEstimate golden — rerun; it is not a
#   contract bug.
set -euo pipefail

here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
workspace=$(cd -- "$here/../../../.." && pwd)
run_apps="$workspace/.agents/skills/run-apps/scripts/run-apps"
run_apps_label=.agents/skills/run-apps/scripts/run-apps
tokens_env="$workspace/.agents/runtime/tokens.env"
kubeconfig="$workspace/.agents/runtime/kubeconfig-shop.yaml"
document=prototypes/basket-api.prove.md
base_url=http://127.0.0.1:8082
caller_dir=$PWD

die() {
    printf 'prove-local: %s\n' "$1" >&2
    exit 1
}

usage() {
    cat <<'EOF'
usage: prove-local.sh [--dry-run] [--help] [prove run arguments]

  --dry-run   check the cluster and its shopping port, mint the tokens, derive the dates, verify the
              variable tiers, probe the basket route with both tokens, print the masked prove command
              line and stop; prove is not invoked. The first probe is step 1's own GET, which
              auto-creates the customer's basket (D29) — a dry run is not side-effect free.
  --help      this text

Everything else is forwarded to `prove run`: `--format json`, `--keep-going`, `--step '<heading>'`, and
scenario files, which resolve against docs/specs/basket first and the caller's directory second (a
file argument replaces the default prototypes/basket-api.prove.md). The exit code is prove's own:
0 pass, 1 mismatch, 2 config, 3 input, 4 rate limit.

Requires a GREEN `.agents/skills/run-apps` cluster with the 127.0.0.1:8082 bridge to shopping, and a
deployment that carries the basket routes — the probes name a stale image instead of letting step 1,
or step 24, report it.
EOF
}

probe() {
    PROBE_TOKEN="$1" python3 -c 'import os, sys, urllib.error, urllib.request

request = urllib.request.Request(sys.argv[1], headers={"Authorization": "Bearer " + os.environ["PROBE_TOKEN"]})
try:
    with urllib.request.urlopen(request, timeout=30) as response:
        print(response.status)
except urllib.error.HTTPError as refusal:
    print(refusal.code)
except TimeoutError:
    print("timeout")
except urllib.error.URLError as failure:
    print("timeout" if isinstance(failure.reason, TimeoutError) else "unreachable")
except Exception:
    print("unreachable")' "$base_url/baskets"
}

# Both probes repeat step 1's request, which is also the sequence's auto-creation trigger (D29); step 2
# clears the basket. 30 s, not less: step 1 is the service's first JWKS fetch, which is exactly what
# prove.yaml's request_timeout documents, so a slower probe would report a cold pod as a broken one.
preflight() {
    local answer
    answer=$(probe "$customer_token")
    case $answer in
    200) ;;
    401 | 403)
        die "GET $base_url/baskets → $answer with SHOPPING_CUSTOMER_TOKEN: the freshly minted token was refused — '$run_apps_label auth', and after a realm edit delete the realm before 'deploy'"
        ;;
    404)
        die "GET $base_url/baskets → 404: the running image carries no basket route — '$run_apps_label rebuild shopping' rebuilds, imports and rolls the deployment, which is the fix; 'deploy shopping' alone re-applies manifests and leaves the stale pod answering"
        ;;
    timeout)
        die "GET $base_url/baskets → no answer within 30s: a cold pod's first request is also its JWKS fetch and can be slow — retry, and if it persists check '$run_apps_label status' and '$run_apps_label logs shopping'"
        ;;
    *)
        die "GET $base_url/baskets → $answer: the contract needs 200 there — the service is not answering it"
        ;;
    esac

    answer=$(probe "$service_token")
    case $answer in
    403) ;;
    401)
        die "GET $base_url/baskets → 401 with SHOPPING_SERVICE_TOKEN: realm 'shopping' or client 'shopping-service-client' is misconfigured — step 24 must answer 403, and '$run_apps_label auth' shows the scope it mints"
        ;;
    404)
        die "GET $base_url/baskets → 404 with SHOPPING_SERVICE_TOKEN: the running image carries no basket route — '$run_apps_label rebuild shopping'"
        ;;
    *)
        die "GET $base_url/baskets → $answer with SHOPPING_SERVICE_TOKEN: the contract's step 24 needs 403 — the service token must be recognised and refused as a non-customer"
        ;;
    esac
}

check_tiers() {
    local shadowed overridden
    shadowed=$(prove explain "$document" --config "$here/prove.yaml" --env local --format json) ||
        die "prove explain $document failed — the contract or prove.yaml is broken"
    overridden=$(prove explain "$document" --config "$here/prove.yaml" --env local --format json "${vars[@]}") ||
        die "prove explain $document with the five --var failed — a variable name or value is rejected"
    printf '%s\n%s\n' "$shadowed" "$overridden" | python3 -c 'import json, sys

text = sys.stdin.read()
decoder = json.JSONDecoder()
shadowed, index = decoder.raw_decode(text)
overridden, _ = decoder.raw_decode(text[index:].lstrip())

def tiers(document):
    return {entry["name"]: entry["tier"] for entry in document["data"]["files"][0]["vars"]}

names = ("base_url", "customer_token", "service_token", "est_from", "est_to")
from_document, from_flag = tiers(shadowed), tiers(overridden)
wrong = [f"{name}: document={from_document.get(name)} --var={from_flag.get(name)}"
         for name in names
         if from_document.get(name) != "markdown" or from_flag.get(name) != "var"]
print("\n".join(wrong), file=sys.stderr)
raise SystemExit(1 if wrong else 0)' ||
        die "prove's variable precedence is not what the harness assumes (expected document=markdown, --var=var for all five; the offenders are listed above) — without --var winning, the run targets the document's 8080"
}

warsaw_day() {
    python3 -c 'import sys
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

try:
    day = datetime.now(ZoneInfo("Europe/Warsaw")).date() + timedelta(days=int(sys.argv[1]))
except Exception as unavailable:
    raise SystemExit(f"{type(unavailable).__name__}: {unavailable}")
print(day.isoformat())' "$1" ||
        die "cannot derive today+$1 in Europe/Warsaw — python3 needs zoneinfo and host tzdata (element 05 / D43)"
}

dry_run=0
forward=()
for arg in "$@"; do
    case $arg in
    --dry-run) dry_run=1 ;;
    --help | -h)
        usage
        exit 0
        ;;
    *) forward+=("$arg") ;;
    esac
done
set -- ${forward[@]+"${forward[@]}"}

prove_version=$(prove --version 2>/dev/null || true)
[[ $prove_version == prove\ * ]] ||
    die "'prove' on PATH is not the scenario runner (${prove_version:-nothing}); /usr/bin/prove is Perl's TAP harness"

[[ -x $run_apps ]] || die "$run_apps_label is missing — this runner drives the run-apps skill"

status_json=$("$run_apps" status --json 2>/dev/null || true)
verdict=$(printf '%s' "$status_json" | python3 -c '
import json, sys

try:
    report = json.load(sys.stdin)
except ValueError:
    print("unreadable unreadable none")
    raise SystemExit
port = report.get("apps", {}).get("shopping", {}).get("port")
bridge = any(
    entry.get("localPort") == port
    and entry.get("namespace") == "shopping"
    and entry.get("target") == "svc/shopping"
    and entry.get("alive")
    and entry.get("healthy")
    for entry in report.get("hostProcesses", [])
)
print(report.get("state", "unreadable"), "live" if bridge else "absent", port or "none")
')
read -r state bridge port <<<"$verdict"
[[ $state == green ]] ||
    die "the local cluster is not GREEN (state: $state) — '$run_apps_label deploy' brings it up; this runner starts nothing"
# `run-apps` owns the port map; the runner and prove.yaml only mirror it.
# The fixture's stock queue counts reads in ONE process (D72): a second replica, or a rollout mid-run,
# restarts the sequence and fails step 15 as though the golden were wrong. Ask the cluster directly —
# `run-apps status` reports pods, not the replica count.
replicas=$(kubectl --kubeconfig "$kubeconfig" -n shopping get deploy shopping \
    -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)
[[ $replicas == 1 ]] ||
    die "shopping has ${replicas:-0} ready replica(s); the contract's stock queue is calibrated to a single process (D72) — 'kubectl -n shopping scale deploy/shopping --replicas=1', then rerun"
[[ $port == 8082 ]] ||
    die "run-apps reports shopping on port $port, this runner and prove.yaml use 8082 — $run_apps_label is the source of truth, so fix the two mirrors, not the skill"
[[ $bridge == live ]] ||
    die "no live 127.0.0.1:8082 bridge to shopping/svc/shopping — '$run_apps_label deploy shopping' restores it; this runner starts nothing"

"$run_apps" auth --reuse >/dev/null ||
    die "'$run_apps_label auth --reuse' failed — its own error is above; '$run_apps_label deploy' re-imports the realms and '$run_apps_label auth --json' shows the tokens it mints"

token() {
    local line
    line=$(grep -m 1 "^export $1=" "$tokens_env") ||
        die "$tokens_env carries no $1 — '$run_apps_label auth' mints it"
    printf '%s' "${line#*=}" | tr -d "'"
}

customer_token=$(token SHOPPING_CUSTOMER_TOKEN)
service_token=$(token SHOPPING_SERVICE_TOKEN)
[[ -n $customer_token && -n $service_token ]] || die "$tokens_env carries an empty shopping token"

# Element 05 / D43: the estimate counts calendar days from the customer's day in Europe/Warsaw —
# neither UTC nor the host's zone — so the golden's range is derived, never written.
est_from=$(warsaw_day 2)
est_to=$(warsaw_day 4)

# Each --var shadows the identically named entry of the document's `**vars:**` block.
vars=(
    --var "base_url:$base_url"
    --var "customer_token:$customer_token"
    --var "service_token:$service_token"
    --var "est_from:$est_from"
    --var "est_to:$est_to"
)

cd "$here/.."

have_file=0
forward=()
for arg in "$@"; do
    case $arg in
    *.md)
        if [[ -f $arg ]]; then
            have_file=1
        elif [[ -f $caller_dir/$arg ]]; then
            arg="$caller_dir/$arg"
            have_file=1
        else
            die "no scenario file '$arg' in $PWD, $caller_dir or as an absolute path"
        fi
        ;;
    esac
    forward+=("$arg")
done
set -- ${forward[@]+"${forward[@]}"}
if [[ $have_file -eq 0 ]]; then
    set -- "$@" "$document"
fi

check_tiers
preflight

if [[ $dry_run -eq 1 ]]; then
    printf 'prove-local: environment ready, prove not invoked:\n'
    cmd=(prove run --config "$here/prove.yaml" --env local "${vars[@]}" "$@")
    for arg in "${cmd[@]}"; do
        arg=${arg//"$customer_token"/REDACTED}
        arg=${arg//"$service_token"/REDACTED}
        printf ' %q' "$arg"
    done
    printf '\n'
    exit 0
fi

exec prove run --config "$here/prove.yaml" --env local "${vars[@]}" "$@"
