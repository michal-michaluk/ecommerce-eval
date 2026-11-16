package shop.catalog.reads;

import shop.catalog.offer.Offer;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Element 11 — whether a customer may see an offer, given its publication state and its availability start.
 *
 * <pre>
 * Visibility(state, availabilityStart, today)
 *   published,  availabilityStart null or &lt;= today  → VISIBLE           R1
 *   published,  availabilityStart &gt; today            → HIDDEN            R2, the announce variant answers VISIBLE_SCHEDULED
 *   pendingReview | draft | removed                  → HIDDEN            R3, R4, R5
 * today = the current date in catalog.timezone, read from the injected Clock
 * </pre>
 *
 * <p>The one and only place visibility is computed (17 RULE-6): the offer carries the state and the date, nothing else, and no
 * read model stores a visible flag — R2 becomes R1 at local midnight without an event, so a stored answer would be wrong by
 * morning.
 *
 * <p>R3 hides {@code pendingReview}, and this policy reads nothing beyond the state it is given: an offer presented as
 * {@code pendingReview} is hidden whether or not it ever published a version. That is deliberate — inferring "it published once,
 * so it is still live" would need a copy of the offer's version history here, and a second place the visibility rule could drift
 * (17 RULE-6, R5). A live offer is never presented as {@code pendingReview}: D37 keeps a {@code published} offer {@code published}
 * while its next version is reviewed (02 lifecycle), so {@code pendingReview} only ever describes an unpublished offer and the shop
 * keeps serving the version in force.
 *
 * <p>It answers, it never fails and it reads no configuration of its own: the timezone and the variant arrive as values, which is
 * what lets the test supply {@code today} instead of the wall clock.
 */
record OfferVisibility(Clock clock, ZoneId timezone, Future future) {

    OfferVisibility {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(timezone, "timezone");
        Objects.requireNonNull(future, "future");
    }

    static OfferVisibility of(Clock clock, String timezone, Future future) {
        return new OfferVisibility(clock, ZoneId.of(timezone), future);
    }

    /** The decision for one offer, at the business date of the injected clock. */
    Outcome visibilityOf(Offer.State state, LocalDate availabilityStart) {
        return visibilityOf(state, availabilityStart, today());
    }

    /** The decision for one offer on a given business date — the form the scenarios of 11 state. */
    Outcome visibilityOf(Offer.State state, LocalDate availabilityStart, LocalDate today) {
        return state == Offer.State.PUBLISHED ? published(availabilityStart, today) : Outcome.HIDDEN;
    }

    private Outcome published(LocalDate availabilityStart, LocalDate today) {
        if (availabilityStart == null || !availabilityStart.isAfter(today)) {
            return Outcome.VISIBLE;
        }
        return future == Future.ANNOUNCE ? Outcome.VISIBLE_SCHEDULED : Outcome.HIDDEN;
    }

    private LocalDate today() {
        return ZonedDateTime.now(clock).withZoneSameInstant(timezone).toLocalDate();
    }

    /** The three answers of 11; the announce variant's label is rendered from the offer's own availability date. */
    enum Outcome {
        VISIBLE,
        VISIBLE_SCHEDULED,
        HIDDEN
    }

    /** {@code catalog.visibility.future}: what R2 answers before a published offer becomes available (Q10, default hidden). */
    enum Future {
        HIDDEN,
        ANNOUNCE
    }
}
