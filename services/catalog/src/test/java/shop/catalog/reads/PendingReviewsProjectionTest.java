package shop.catalog.reads;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import shop.catalog.IntegrationTest;
import shop.catalog.review.Review;
import shop.catalog.review.ReviewFixture;
import shop.catalog.review.ReviewService;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.review.ReviewCandidateFixture.candidate;
import static shop.catalog.review.ReviewFixture.REQUESTED_AT;

/**
 * The pending-reviews list of 15 against a real database: a review appears while it is open, disappears from the list on every state
 * that ends it, and never shows a reviewer their own request.
 *
 * <p>The handler is called directly, so this test is about the projection and not about who publishes the fact.
 */
@IntegrationTest
class PendingReviewsProjectionTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 10);

    @Autowired
    PendingReviewsProjection projection;
    @Autowired
    PendingReviewsProjection.PendingReviews rows;
    @Autowired
    ReviewService reviews;

    @BeforeEach
    void clearsTheReadModel() {
        rows.deleteAll();
    }

    @Test
    void listsAnOpenReviewOfAnotherAuthorsRequest() {
        projection.handle(ReviewFixture.requestedReview(candidate()));

        Page<PendingReviewsReads> page = projection.findAllPending(ReadsFixture.REVIEWER, FIRST_PAGE);

        assertThat(page.getContent()).singleElement().satisfies(row -> {
            assertThat(row.reviewId()).isEqualTo(ReviewFixture.REVIEW_ID);
            assertThat(row.offerId()).isEqualTo(ReviewFixture.OFFER_ID);
            assertThat(row.requestedBy()).isEqualTo(ReviewFixture.AUTHOR);
            assertThat(row.requestedAt()).isEqualTo(REQUESTED_AT);
            assertThat(row.title()).isEqualTo(candidate().description().title());
            assertThat(row.findingsCount()).isZero();
        });
    }

    /** 15 RULE-4 and 04 RULE-2: a reviewer never sees their own request — the row is filtered, not merely disabled. */
    @Test
    void neverListsTheCallersOwnRequest() {
        projection.handle(ReviewFixture.requestedReview(candidate()));

        assertThat(projection.findAllPending(ReviewFixture.AUTHOR, FIRST_PAGE)).isEmpty();
    }

    /** 15 RULE-6: the list drops a row on every fact that ends the review, and keeps the row's state as the fact recorded it. */
    @Test
    void dropsTheRowOfAReviewThatWasDecided() {
        projection.handle(ReviewFixture.requestedReview(candidate()));
        projection.handle(ReviewFixture.acceptedReview(candidate()));
        projection.handle(ReviewFixture.rejectedReview(candidate()));
        projection.handle(ReviewFixture.withdrawnReview(candidate()));

        assertThat(projection.findAllPending(ReadsFixture.REVIEWER, FIRST_PAGE)).isEmpty();
        assertThat(rows.count()).isEqualTo(1);
    }

    @Test
    void keepsOneRowForTheSameReviewDeliveredTwice() {
        Review review = ReviewFixture.requestedReview(candidate());

        projection.handle(review);
        projection.handle(review);

        assertThat(rows.count()).isEqualTo(1);
    }

    /** 15's list order: the review that has waited longest comes first. */
    @Test
    void listsTheOldestRequestFirst() {
        String older = ReviewFixture.aNewReviewId();
        String newer = ReviewFixture.aNewReviewId();
        reviews.request(ReviewFixture.request(older, ReviewFixture.aNewOfferId(), REQUESTED_AT, candidate()));
        reviews.request(ReviewFixture.request(newer, ReviewFixture.aNewOfferId(), REQUESTED_AT.plusSeconds(60), candidate()));

        assertThat(projection.findAllPending(ReadsFixture.REVIEWER, FIRST_PAGE))
                .extracting(PendingReviewsReads::reviewId).containsExactly(older, newer);
    }
}
