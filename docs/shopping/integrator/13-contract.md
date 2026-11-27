## 13 The contract of record

The contract is one command. It runs twenty-four steps against a running cluster, it mints its own tokens,
it derives its own dates, and it prints the step that failed rather than a summary.

```sh
cd docs/specs/basket
./prototypes/prove-local.sh
```

<!-- evidence: docs/specs/basket/prototypes/basket-api.prove.md:11-15, services/shopping/scripts/prove.sh:1-7 -->

### 13.1 Why this document cannot quote it

Two facts make the executable contract invisible to the pipeline that renders these pages, and a reader who
expects per-operation transcripts should know them:

- The service's own end-to-end file is a **healthcheck**: three requests to the management surface, run by
  `hurl`, and nothing about a basket. It is the scaffolded smoke test, not the contract.
- The contract is a `prove` document: a markdown scenario whose steps are prose headings, an HTTP request
  block, a response block and a golden body. No extractor in this repository reads that format, so the
  operation chapters quote no request and no response, and the field tables in chapter 11 are read from the
  handler records instead.

That is a limit of this docset, not of the contract. The contract itself is precise: `prove explain` reports
twenty-four steps, and `prove validate` reports nothing at all when every variable and every block is
well-formed.

<!-- evidence: services/shopping/e2e/healthcheck.hurl:1-2, docs/specs/basket/prove-of-done.md:5-8, docs/specs/basket/prove-of-done.md:28 -->

### 13.2 What the runner does for you

| Step | Why it exists |
|---|---|
| checks the cluster is green and finds the service's port | the contract is not a simulation: it talks to the deployed service |
| mints both realm-`shopping` tokens | no token is typed by hand, and the `403` case needs the non-customer identity |
| derives `est_from`/`est_to` as today+2 and today+4 in `Europe/Warsaw` | the estimate is a date range, so a golden cannot pin it |
| asserts that a `--var` still outranks the document's own `**vars:**` block | a `prove` release that flipped the tier order would silently send the contract to the wrong service |
| probes `GET /baskets` with both tokens before the run | a stale image or a misconfigured client is named instead of surfacing as step 1 or step 24 |

<!-- evidence: docs/specs/basket/prototypes/prove-local.sh:1-26, services/shopping/AGENTS.md:15-17 -->

`--dry-run` performs the whole environment half and prints the command line with the tokens masked, so the
environment can be checked without running the contract. It is not side-effect free: the first probe is
step 1's own request, and that request creates the customer's basket.

<!-- evidence: docs/specs/basket/prototypes/prove-local.sh:18-20 -->

### 13.3 What the contract covers

The twenty-four steps walk the whole interface once, in the order a client uses it: the list, the empty
basket, two adds, a repeated add, a quantity, its refusals, the currency mismatch, the unknown offer, three
selection commands, a refused handover, the row that goes out of stock, the deselect that unblocks it, the
handover itself, its repeat, the described removal, the blocked handover it causes, an unauthenticated call
and a service token on a customer route.

Two properties of that sequence are load-bearing and are easy to break by changing a route:

- The fixture's stock answers are a per-offer queue calibrated to the steps: the row that goes out of stock
  at step 15 does so because that step's pull is the tenth read of that offer. A route that starts or stops
  pulling moves the transition by a step and shows up as a golden mismatch.
- The repeated handover at step 19 compares against the revision the first one returned, so the handover's
  identity must be content-derived and must not move when nothing about the content moved.

<!-- evidence: services/shopping/docs/offer-fixture.md:62-80, services/shopping/docs/offer-fixture.md:85-95, docs/specs/basket/prove-of-done.md:118-119 -->

### 13.4 What the contract does not cover

Two rows of the specification's acceptance list have no wire surface and are proven inside the service
instead: applying the same observation twice, and discarding an observation older than the row's read. Both
run through the same command the routes use, so the behaviour is the deployed one even though no request can
reach it. The specification's own evidence report states which halves of which rows are observed and which
are inferences, and this guide does not repeat a claim the report does not make.

<!-- evidence: docs/specs/basket/prove-of-done.md:58-59, docs/specs/basket/prove-of-done.md:128-131 -->
