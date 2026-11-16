package shop.catalog.offer;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One offer as the read side serves it after a write: its identity, its lifecycle state, the number of the version currently in
 * force, the date the product becomes available (02 Instance A, 11 R1–R5) and the editable content the draft screen renders (14).
 *
 * <p>Public because it leaves the context: it is the snapshot an in-process listener of a sibling context reads — pricing keeps only
 * the fact that the offer exists, {@code reads} copies the surface for its draft read model — while 13 publishes
 * {@link OfferPublished} — the frozen version, not this snapshot — to the shop. The editor that produces it stays package-private
 * (17, {@code structure.editor-package-private}).
 *
 * <p>{@code description} is the draft in builder form, never a frozen {@link ProductDescription}: an incomplete listing is a legal
 * state and is exactly what the author is still writing (01 RULE-4, 02 RULE-7). It travels with {@code media} because a read model
 * copies what it is published and never asks again ({@code integration.payload-complete}).
 */
public record Offer(String offerId, State state, int version, LocalDate availabilityStart,
                    ProductDescription.Draft description, List<Media> media) {

    public Offer {
        offerId = Objects.requireNonNull(offerId, "offerId");
        state = Objects.requireNonNull(state, "state");
        description = Objects.requireNonNull(description, "description");
        media = List.copyOf(media);
    }

    /** The lifecycle of 02: a draft is reviewed, published as a version, and removal is final for the product (RULE-6, Q11). */
    public enum State {
        DRAFT,
        PENDING_REVIEW,
        PUBLISHED,
        REMOVED
    }
}
