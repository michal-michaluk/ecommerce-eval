package shop.catalog.offer;

import java.time.Instant;
import java.util.Objects;

/**
 * Element 13 — the product is withdrawn for good (RULE-1, 02 RULE-6). It is the only public fact that is not a snapshot: there is
 * nothing left to carry but the identity and the moment, and a consumer removes every row it holds for the offer.
 *
 * <p>{@code removedAt} is the instant the removal became public — the removal is recorded on the offer without a timestamp, so the
 * adapter that publishes it reads the clock once and uses that one instant for both the envelope and the payload.
 */
public record ProductRemoved(String productId, Instant removedAt) {

    public static final String TYPE = "ProductRemoved";

    public ProductRemoved {
        productId = Objects.requireNonNull(productId, "productId");
        removedAt = Objects.requireNonNull(removedAt, "removedAt");
    }
}
