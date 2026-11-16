package shop.catalog.reads;

import org.junit.jupiter.api.Test;
import shop.catalog.review.ReviewFixture;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.review.ReviewCandidateFixture.candidate;

/**
 * The one component of the pending-reviews row that is computed rather than copied: the waiting time of 15 RULE-1, as an RFC 3339
 * duration from the request instant to the moment the list is rendered.
 */
class PendingReviewsReadsTest {

    @Test
    void rendersTheWaitingTimeAsAnRfc3339Duration() {
        Instant now = ReviewFixture.REQUESTED_AT.plus(Duration.ofHours(16).plusMinutes(30));

        PendingReviewsReads row = PendingReviewsReads.ofNullable(rowOfTheRequestedReview(), now);

        assertThat(row.waitingFor()).isEqualTo("PT16H30M");
        assertThat(row.requestedAt()).isEqualTo(ReviewFixture.REQUESTED_AT);
        assertThat(row.findingsCount()).isZero();
    }

    @Test
    void rendersAWholeDayAsTwentyFourHours() {
        Instant now = ReviewFixture.REQUESTED_AT.plus(Duration.ofDays(1));

        assertThat(PendingReviewsReads.ofNullable(rowOfTheRequestedReview(), now).waitingFor()).isEqualTo("PT24H");
    }

    /** A request whose instant lies in the future has been waited for no time at all: the duration is never negative. */
    @Test
    void neverRendersANegativeWaitingTime() {
        Instant before = ReviewFixture.REQUESTED_AT.minus(Duration.ofHours(2));

        assertThat(PendingReviewsReads.ofNullable(rowOfTheRequestedReview(), before).waitingFor()).isEqualTo("PT0S");
    }

    /** A row nobody has requested yet is not a row of this list, so it is absent instead of half filled. */
    @Test
    void isAbsentWhenTheRowCarriesNoRequestInstant() {
        assertThat(PendingReviewsReads.ofNullable(new PendingReviewsProjection.PendingReviewRow("RV-12"), Instant.now()))
                .isNull();
    }

    private static PendingReviewsProjection.PendingReviewRow rowOfTheRequestedReview() {
        PendingReviewsProjection.PendingReviewRow row = new PendingReviewsProjection.PendingReviewRow(ReviewFixture.REVIEW_ID);
        return row.copyOf(ReviewFixture.requestedReview(candidate()));
    }
}
