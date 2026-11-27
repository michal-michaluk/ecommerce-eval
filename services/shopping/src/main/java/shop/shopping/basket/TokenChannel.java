package shop.shopping.basket;

/**
 * The channel a bearer token names, as element 04 pays delivery for it: {@code smart} frees the rate, and every other claim —
 * absent, misspelled, or a value nobody specified — is the default arrival (element 04 §Variants, D54).
 *
 * <p>It is one value and never an exception: a token that names no channel is a customer who arrived the ordinary way, not a
 * refused request. The claim's name is stated here and nowhere else, so the routes read one constant and this class owns the
 * whole mapping.
 */
final class TokenChannel {

    /** The claim the deployment's identity states; nothing else in the service reads it (D13's boundary). */
    static final String CLAIM = "channel";

    private static final String SMART = "smart";

    private TokenChannel() {
    }

    /**
     * The channel of one claim value.
     *
     * @param claim what the token said, {@code null} when it said nothing
     * @return {@code smart} for the one value that changes a price, the default arrival otherwise
     */
    static Channel claimedAs(String claim) {
        return SMART.equalsIgnoreCase(claim) ? Channel.smart : Channel.standard;
    }
}
