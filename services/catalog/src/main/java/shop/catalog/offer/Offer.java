package shop.catalog.offer;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One offer as the read side serves it after a write: its identity, its lifecycle state, the number of the version currently in
 * force and the date the product becomes available (02 Instance A, 11 R1–R5).
 *
 * <p>Public because it leaves the context: it is the snapshot an in-process listener of a sibling context reads (pricing keeps only the
 * fact that the offer exists), while 13 publishes {@link OfferPublished} — the frozen version, not this summary — to the shop. The editor
 * that produces it stays package-private (17, {@code structure.editor-package-private}).
 */
public record Offer(String offerId, State state, int version, LocalDate availabilityStart) {

    public Offer {
        offerId = Objects.requireNonNull(offerId, "offerId");
        state = Objects.requireNonNull(state, "state");
    }

    /** The lifecycle of 02: a draft is reviewed, published as a version, and removal is final for the product (RULE-6, Q11). */
    public enum State {
        DRAFT,
        PENDING_REVIEW,
        PUBLISHED,
        REMOVED
    }
}
