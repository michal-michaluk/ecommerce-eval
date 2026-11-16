package shop.catalog.review;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import shop.catalog.IntegrationTest;
import shop.catalog.offer.Offer;
import shop.catalog.offer.OfferDocumentFixture;
import shop.catalog.offer.OfferFixture;
import shop.catalog.offer.OfferService;
import shop.catalog.offer.ProductDescription;
import shop.catalog.offer.ProductDescriptionDraftFixture;
import shop.catalog.quality.QualityFixture;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.catalog.TestTransaction.transactional;

/**
 * D38 and D34 across two contexts: the request freezes the candidate from the offer as it stands, the author edits the draft
 * afterwards, and accepting the review publishes the <em>candidate</em> — one version, once — through the offer context's own
 * {@code OfferService}. Nothing here reaches an offer editor: the exchange goes through the offer's contract ports.
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
        transactional(() -> offers.updateDescription(offerId, anEditedTitle()));

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
    void leavesTheOffersReviewToTheFreezeSoALiveOfferIsRevertableAfterwards() {
        String offerId = aPublishedOffer();
        Review requested = reviews.requestFor(offerId, ReviewFixture.AUTHOR, List.of());

        transactional(() -> reviews.decide(requested.reviewId(), ReviewFixture.REVIEWER)).orElseThrow();

        assertThat(versionsOf(offerId)).isEqualTo(2);
        assertThat(transactional(() -> offers.revert(offerId, 1, ReviewFixture.REVIEWED_AT, ReviewFixture.REVIEWER)))
                .isPresent();
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

    /** A live offer awaiting its next version: the freeze of an acceptance must be the only thing that clears its review (D37). */
    private String aPublishedOffer() {
        String offerId = ReviewFixture.aNewOfferId();
        transactional(() -> offers.draftOffer(offerId, QualityFixture.draftWithIdentifiers(
                OfferDocumentFixture.uniqueProductNumber(), OfferDocumentFixture.uniqueEanGtin())));
        transactional(() -> offers.scheduleAvailability(offerId, ReviewCandidateFixture.AVAILABILITY_START));
        transactional(() -> offers.updateMedia(offerId, OfferFixture.mediaOfInstanceA()));
        transactional(() -> offers.publish(offerId, ReviewFixture.REVIEWED_AT, ReviewFixture.REVIEWER));
        return offerId;
    }

    /**
     * RULE-9's partial edit: the change names the title and nothing else, so the offer keeps the identifiers it was drafted with.
     * A commit-time test cannot borrow instance A's identifiers — the suite shares one database, and a second offer carrying them
     * would break RULE-11 for whoever committed first.
     */
    private static ProductDescription.Draft anEditedTitle() {
        return ProductDescription.Draft.builder().title(ReviewCandidateFixture.EDITED_TITLE).build();
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
