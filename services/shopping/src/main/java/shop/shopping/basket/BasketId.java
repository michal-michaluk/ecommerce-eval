package shop.shopping.basket;

import java.util.Objects;
import java.util.UUID;

/**
 * The basket's synthetic identity — minted by the service when the customer has no active basket, and never derived from
 * the customer (element 02 RULE-1).
 *
 * @param value the {@code B-} identity as it appears in every API path
 */
record BasketId(String value) {

    private static final String PREFIX = "B-";

    BasketId {
        Objects.requireNonNull(value, "value");
    }

    /** Mints a new identity: the basket does not exist before the service creates one for a customer who has none. */
    static BasketId minted() {
        return new BasketId(PREFIX + UUID.randomUUID().toString().substring(0, 8));
    }
}
