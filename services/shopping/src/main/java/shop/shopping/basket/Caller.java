package shop.shopping.basket;

import java.util.Objects;

/**
 * Who is calling, as the bearer token states it: the customer a basket must belong to (element 07 §Ownership, D68) and the
 * channel element 04 prices delivery by (D54).
 *
 * <p>The two facts are one value because every route but the list needs both, and because reading them is the adapter's whole
 * reading of the request envelope — the subject is the customer, and the channel claim is {@code smart} only when it says so:
 * an absent, misspelled or unrecognised claim is the default arrival, never an exception (element 04 §Variants). The claim's
 * name is stated here and nowhere else, so no route spells it and no route maps it twice.
 *
 * @param customerId the token's subject, which is the {@code customerId} a basket has to carry (D68)
 * @param channel    how the customer arrives, which decides whether delivery is charged (element 04 S6)
 */
record Caller(CustomerId customerId, Channel channel) {

    /** The claim the deployment's identity states for the channel; nothing else in the service reads it (D13's boundary). */
    static final String CHANNEL_CLAIM = "channel";

    private static final String SMART = "smart";

    Caller {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(channel, "channel");
    }

    /**
     * The caller one token states.
     *
     * @param subject      the token's subject, which is the customer the basket belongs to
     * @param channelClaim what the token said about the channel, {@code null} when it said nothing
     * @return the caller every basket route acts for
     */
    static Caller of(String subject, String channelClaim) {
        return new Caller(new CustomerId(subject), SMART.equalsIgnoreCase(channelClaim) ? Channel.smart : Channel.standard);
    }
}
