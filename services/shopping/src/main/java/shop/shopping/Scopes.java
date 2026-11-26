package shop.shopping;

/**
 * The one authority of element 07 (D27): the customer's own scope, spelled the way Spring Security
 * reads a token's {@code scope} claim. Package-private because its single production edge is
 * {@code AppConfiguration}'s route matrix; the identity test that consumes it lives in this package.
 */
final class Scopes {

    static final String CUSTOMER = "SCOPE_customer";

    private Scopes() {
    }
}
