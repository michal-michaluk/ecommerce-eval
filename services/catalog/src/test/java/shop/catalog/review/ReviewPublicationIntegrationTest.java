package shop.catalog.review;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import shop.catalog.IntegrationTest;
import shop.catalog.offer.Offer;
import shop.catalog.offer.OfferDocumentFixture;
import shop.catalog.offer.OfferService;
import shop.catalog.offer.ProductDescriptionDraftFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.catalog.TestTransaction.transactional;

/**
 * D38 and D34 across two contexts: the request freezes the candidate from the offer as it stands, the author edits the draft
 * afterwards, and accepting the review publishes the <em>candidate</em> — one version, once — through the offer context's own
 * {@code OfferService}. Nothing here reaches an offer editor: the exchange goes through the two contract ports 04 declares.
 */
@IntegrationTest
class ReviewPublicationIntegrationTest {

    @Autowired
    ReviewService reviews;
    @Autowired
    OfferService offers;
    @Autowired
    OfferDraftSnapshot offerDrafts;
    @Autowired
    OfferPublication publications;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void publishesTheCandidateTheRequestFrozeWhileTheLiveDraftMovedOn() {
        String offerId = anOfferUnderEditing();
        ReviewCandidate frozen = freezes(offerId);
        String reviewId = ReviewFixture.aNewReviewId();
        transactional(() -> reviews.request(ReviewFixture.request(reviewId, offerId, frozen)));
        transactional(() -> offers.updateDescription(offerId,
                ProductDescriptionDraftFixture.draftWithTitle(ReviewCandidateFixture.EDITED_TITLE)));

        Review published = transactional(() -> reviews.decide(reviewId, ReviewFixture.REVIEWER)).orElseThrow();

        ReviewAssert.assertThat(published).hasState(Review.State.ACCEPTED).hasPublishedVersion(1);
        assertThat(versionsOf(offerId)).isEqualTo(1);
        assertThat(publishedTitleOf(offerId)).isEqualTo(frozen.description().title());
        assertThat(publishedTitleOf(offerId)).isNotEqualTo(ReviewCandidateFixture.EDITED_TITLE);
        assertThat(publishedMediaOf(offerId)).isEqualTo(frozen.media().size());
        assertThat(draftedTitleOf(offerId)).isEqualTo(ReviewCandidateFixture.EDITED_TITLE);
        assertThat(offerStateOf(offerId)).isEqualTo(Offer.State.PUBLISHED.name());
    }

    @Test
    void freezesOneVersionPerAcceptanceAndAnswersAReplayWithIt() {
        String offerId = anOfferUnderEditing();
        String reviewId = ReviewFixture.aNewReviewId();
        ReviewCandidate frozen = freezes(offerId);
        transactional(() -> reviews.request(ReviewFixture.request(reviewId, offerId, frozen)));

        Review first = transactional(() -> reviews.decide(reviewId, ReviewFixture.REVIEWER)).orElseThrow();
        Review replay = reviews.decide(reviewId, ReviewFixture.REVIEWER).orElseThrow();

        assertThat(replay.publishedOfferVersion()).isEqualTo(first.publishedOfferVersion());
        assertThat(versionsOf(offerId)).isEqualTo(1);
    }

    @Test
    void leavesTheAcceptanceUnrecordedWhenTheFreezeFails() {
        String offerId = anOfferUnderEditing();
        String reviewId = ReviewFixture.aNewReviewId();
        ReviewCandidate unpublishable = ReviewCandidateFixture.candidateWithUnpublishableMedia();
        transactional(() -> reviews.request(ReviewFixture.request(reviewId, offerId, unpublishable)));

        assertThatThrownBy(() -> transactional(() -> reviews.decide(reviewId, ReviewFixture.REVIEWER)))
                .isInstanceOf(RuntimeException.class);

        assertThat(stateOf(reviewId)).isEqualTo(Review.State.REQUESTED.name());
        assertThat(publishedVersionOf(reviewId)).isNull();
        assertThat(versionsOf(offerId)).isZero();
        assertThat(offerStateOf(offerId)).isEqualTo(Offer.State.DRAFT.name());
    }

    @Test
    void refusesACandidateForAnOfferWhoseAvailabilityIsNotScheduled() {
        String offerId = ReviewFixture.aNewOfferId();
        var draft = ProductDescriptionDraftFixture.draftWithIdentifiers(
                OfferDocumentFixture.uniqueProductNumber(), OfferDocumentFixture.uniqueEanGtin());
        transactional(() -> offers.draftOffer(offerId, draft));

        assertThatThrownBy(() -> transactional(() -> offerDrafts.snapshotOf(offerId)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void answersAnOfferItDoesNotKnowWithNothingAndRefusesToFreezeForIt() {
        assertThat(transactional(() -> offerDrafts.snapshotOf(ReviewFixture.aNewOfferId()))).isEmpty();
        assertThatThrownBy(() -> transactional(() -> publications.publish(
                ReviewFixture.aNewOfferId(), ReviewCandidateFixture.candidate(), ReviewFixture.REVIEWED_AT,
                ReviewFixture.REVIEWER)))
                .isInstanceOf(RuntimeException.class);
    }

    private String anOfferUnderEditing() {
        String offerId = ReviewFixture.aNewOfferId();
        var draft = ProductDescriptionDraftFixture.draftWithIdentifiers(
                OfferDocumentFixture.uniqueProductNumber(), OfferDocumentFixture.uniqueEanGtin());
        transactional(() -> offers.draftOffer(offerId, draft));
        transactional(() -> offers.scheduleAvailability(offerId, ReviewCandidateFixture.AVAILABILITY_START));
        return offerId;
    }

    private ReviewCandidate freezes(String offerId) {
        OfferSnapshot snapshot = transactional(() -> offerDrafts.snapshotOf(offerId)).orElseThrow();
        OfferContent content = ReviewCandidateFixture.contentWithMedia(
                snapshot.content().description(), ReviewCandidateFixture.mediaOfInstanceA());
        return ReviewCandidateFixture.candidateOf(content);
    }

    private int versionsOf(String offerId) {
        Integer count = jdbc.queryForObject("select count(*) from offer_version where offer_id = ?", Integer.class, offerId);
        return count == null ? 0 : count;
    }

    private int publishedMediaOf(String offerId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from offer_version_media where offer_id = ?", Integer.class, offerId);
        return count == null ? 0 : count;
    }

    private String publishedTitleOf(String offerId) {
        return jdbc.queryForObject("select description #>> '{title}' from offer_version where offer_id = ?",
                String.class, offerId);
    }

    private String draftedTitleOf(String offerId) {
        return jdbc.queryForObject("select content #>> '{description,title}' from offer_document where offer_id = ?",
                String.class, offerId);
    }

    private String offerStateOf(String offerId) {
        return jdbc.queryForObject("select state from offer_document where offer_id = ?", String.class, offerId);
    }

    private String stateOf(String reviewId) {
        return jdbc.queryForObject("select state from review_process where review_id = ?", String.class, reviewId);
    }

    private Integer publishedVersionOf(String reviewId) {
        return jdbc.queryForObject("select published_offer_version from review_process where review_id = ?",
                Integer.class, reviewId);
    }
}
