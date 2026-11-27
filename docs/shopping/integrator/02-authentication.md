## 2 Authentication and identity

Every basket route needs a bearer token, and the token must carry the customer scope. Nothing else is
consulted: the customer is the token's subject, and no route accepts an identity from a body, a query
parameter or a header.

<!-- evidence: services/shopping/src/main/java/shop/shopping/AppConfiguration.java:52-58, services/shopping/src/main/java/shop/shopping/Scopes.java:10 -->

### 2.1 The token

| Aspect | What the service requires |
|---|---|
| Issuer | the realm `shopping` of the deployment's Keycloak instance; the issuer URI is configuration and is validated at start |
| Signature | JWT, validated against the issuer's keys by the framework's resource server support |
| Authority | `SCOPE_customer` — one authority, spelled as Spring Security reads a token's `scope` claim |
| Session | none: the service is stateless and issues no cookie |
| CSRF | disabled, because no cookie is issued |

<!-- evidence: services/shopping/src/main/resources/application.yml:14-19, services/shopping/src/main/java/shop/shopping/AppConfiguration.java:44-51 -->

### 2.2 The two ways a request is refused before any route runs

| Situation | Status | Body |
|---|---|---|
| no token, or a token the issuer did not sign | `401` | empty, with `WWW-Authenticate: Bearer` |
| a valid token without the customer scope | `403` | empty |

Both bodies are empty on purpose: a caller who may not ask is told nothing about what it may not ask
about, and the contract asserts the emptiness with a measured byte count rather than with a status alone.

<!-- evidence: services/shopping/src/main/java/shop/shopping/AppConfiguration.java:76-87, docs/specs/basket/prove-of-done.md:66-67 -->

The service identity of the same realm is the case that makes this contract observable: a token minted for
`shopping-service-client` is valid, carries no customer scope, and is therefore refused with `403` rather
than `401` — the token is good and the authority is missing.

<!-- evidence: services/shopping/src/test/resources/shopping-realm.json:70-71, docs/specs/basket/log.md:119 -->

### 2.3 The channel claim

Delivery is priced by how the customer arrived. The token carries the answer in a claim named `channel`,
and only the value `smart` (compared without case) changes anything: SMART delivery is free, and every
other value — including none — is the standard arrival. A misspelled claim is not an error, so a client
cannot fail a read by adding a claim the issuer does not know.

<!-- evidence: services/shopping/src/main/java/shop/shopping/basket/Caller.java:19-22, services/shopping/src/main/java/shop/shopping/basket/Caller.java:36-38, services/shopping/src/main/java/shop/shopping/basket/DeliveryRates.java:39-44 -->
