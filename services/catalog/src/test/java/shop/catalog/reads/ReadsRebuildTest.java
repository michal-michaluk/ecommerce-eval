package shop.catalog.reads;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import shop.catalog.IntegrationTest;
import shop.catalog.offer.OfferService;
import shop.catalog.offer.ProductDescriptionDraftFixture;
import shop.catalog.review.ReviewFixture;
import shop.catalog.review.ReviewService;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.review.ReviewCandidateFixture.candidate;

/**
 * The rebuild path of a read model: its rows are derived, so dropping them and replaying the producer's own state reproduces them
 * exactly. A projection that could not rebuild would be a second source of truth, and the two copies would drift the first time a
 * deployment loses a migration.
 *
 * <p>The row is dropped behind the persistence context on purpose — {@link EntityManager#clear()} detaches what the projection just
 * wrote, so what is replayed is read from the database and not from the session that still remembers its own writes.
 */
@IntegrationTest
class ReadsRebuildTest {

    @Autowired
    OfferService offers;
    @Autowired
    ReviewService reviews;
    @Autowired
    OfferDraftReadsProjection drafts;
    @Autowired
    PendingReviewsProjection pendingReviews;
    @Autowired
    JdbcTemplate jdbc;
    @PersistenceContext
    EntityManager entityManager;

    @BeforeEach
    void clearsTheReadModels() {
        jdbc.update("delete from offer_draft_reads");
        jdbc.update("delete from pending_reviews_reads");
        entityManager.clear();
    }

    @Test
    void rebuildsTheDraftScreenFromTheOffersOwnState() {
        String offerId = ReviewFixture.aNewOfferId();
        offers.draftOffer(offerId, ProductDescriptionDraftFixture.draftOfInstanceA());
        OfferDraftReads before = drafts.findDraft(offerId).orElseThrow();

        drop("offer_draft_reads", "offer_id", offerId);
        assertThat(drafts.findDraft(offerId)).isEmpty();

        drafts.handle(offers.getOffer(offerId).orElseThrow());

        assertThat(drafts.findDraft(offerId).orElseThrow()).isEqualTo(before);
    }

    /**
     * Every stored component of the row is replayed; {@code waitingFor} is the one the list computes per render from the clock
     * (15 RULE-1), so it is compared by {@link PendingReviewsReadsTest} with an injected {@code now} instead of across two renders.
     */
    @Test
    void rebuildsThePendingListFromTheReviewsOwnState() {
        String offerId = ReviewFixture.aNewOfferId();
        String reviewId = ReviewFixture.aNewReviewId();
        reviews.request(ReviewFixture.request(reviewId, offerId, candidate()));
        PendingReviewsReads before = listedReview(reviewId);

        drop("pending_reviews_reads", "review_id", reviewId);
        assertThat(listedReviewOrNull(reviewId)).isNull();

        pendingReviews.handle(reviews.openReviewOf(offerId).orElseThrow());

        assertThat(listedReview(reviewId))
                .usingRecursiveComparison()
                .ignoringFields("waitingFor")
                .isEqualTo(before);
    }

    private void drop(String table, String keyColumn, String key) {
        jdbc.update("delete from " + table + " where " + keyColumn + " = ?", key);
        entityManager.clear();
    }

    private PendingReviewsReads listedReview(String reviewId) {
        PendingReviewsReads listed = listedReviewOrNull(reviewId);
        assertThat(listed).isNotNull();
        return listed;
    }

    private PendingReviewsReads listedReviewOrNull(String reviewId) {
        return pendingReviews.findAllPending(ReadsFixture.REVIEWER, PageRequest.of(0, 100)).getContent().stream()
                .filter(row -> row.reviewId().equals(reviewId))
                .findFirst()
                .orElse(null);
    }
}
