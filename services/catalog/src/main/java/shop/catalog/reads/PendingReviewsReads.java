package shop.catalog.reads;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * One row of the pending-reviews list of 15: the review, the offer it is about, who asked, when, how long the reviewer has been
 * waiting, and how many findings the frozen candidate carries.
 *
 * <p>{@code waitingFor} is a rendered duration, not the instant it was derived from: the list exists to be triaged, so the waiting
 * time is computed once per render from the request instant and {@code now} (15 RULE-1) and handed over as an RFC 3339 duration
 * ({@code PT16H30M}) instead of a second timestamp the view would have to subtract again.
 *
 * <p>{@code title} is the frozen candidate's own title, not the offer's current draft: the row describes the review, and the review
 * froze its content at the request (04 RULE-3).
 */
record PendingReviewsReads(
        String reviewId,
        String offerId,
        String title,
        String requestedBy,
        Instant requestedAt,
        String waitingFor,
        int findingsCount) {

    /**
     * The row as one render shows it, or {@code null} when the stored row is not a row of this list at all: a review nobody has
     * requested yet has no request instant to wait from.
     */
    static PendingReviewsReads ofNullable(PendingReviewsProjection.PendingReviewRow row, Instant now) {
        if (row.requestedAt() == null) {
            return null;
        }
        return new PendingReviewsReads(row.reviewId(), row.offerId(), row.title(), row.requestedBy(), row.requestedAt(),
                waitingFor(row.requestedAt(), now), row.findingsCount());
    }

    /** A request that lies in the future has waited no time at all; the duration is never negative, and never finer than a second. */
    private static String waitingFor(Instant requestedAt, Instant now) {
        Duration waited = Duration.between(requestedAt, now);
        return (waited.isNegative() ? Duration.ZERO : waited).truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
