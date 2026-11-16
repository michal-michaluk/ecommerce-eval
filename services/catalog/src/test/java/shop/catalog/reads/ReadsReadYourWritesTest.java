package shop.catalog.reads;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import shop.catalog.IntegrationTest;
import shop.catalog.offer.Offer;
import shop.catalog.offer.OfferService;
import shop.catalog.offer.ProductDescriptionDraftFixture;
import shop.catalog.review.ReviewFixture;
import shop.catalog.review.ReviewService;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.review.ReviewCandidateFixture.candidate;

/**
 * 17 RULE-5 and {@code integration.listener-joins-transaction}: a write through a producer's primary port is already visible through
 * the read side inside the same transaction, so a {@code PATCH} reply and the screen that follows it can never disagree.
 *
 * <p>Nothing here commits between the write and the read: the proof is that the two happen in one transaction and the row is there.
 */
@IntegrationTest
class ReadsReadYourWritesTest {

    @Autowired
    OfferService offers;
    @Autowired
    ReviewService reviews;
    @Autowired
    OfferDraftReadsProjection drafts;
    @Autowired
    PendingReviewsProjection pendingReviews;
    @Autowired
    PendingReviewsProjection.PendingReviews pendingRows;

    @BeforeEach
    void clearsThePendingList() {
        pendingRows.deleteAll();
    }

    @Test
    void showsADraftedOfferAndItsEditInTheSameTransaction() {
        String offerId = ReviewFixture.aNewOfferId();

        offers.draftOffer(offerId, ProductDescriptionDraftFixture.draftOfInstanceA());
        offers.updateDescription(offerId, ProductDescriptionDraftFixture.draftWithTitle("LEGO Star Wars 75423 (nowy tytuł)"));

        OfferDraftReads draft = drafts.findDraft(offerId).orElseThrow();
        assertThat(draft.state()).isEqualTo(Offer.State.DRAFT);
        assertThat(draft.description().title()).isEqualTo("LEGO Star Wars 75423 (nowy tytuł)");
        assertThat(draft.media()).isEqualTo(offers.getOffer(offerId).orElseThrow().media());
    }

    @Test
    void showsARequestedReviewInTheSameTransaction() {
        String reviewId = ReviewFixture.aNewReviewId();
        reviews.request(ReviewFixture.request(reviewId, ReviewFixture.aNewOfferId(), candidate()));

        assertThat(pendingReviews.findAllPending(ReadsFixture.REVIEWER, PageRequest.of(0, 10)))
                .extracting(PendingReviewsReads::reviewId).contains(reviewId);    }
}
