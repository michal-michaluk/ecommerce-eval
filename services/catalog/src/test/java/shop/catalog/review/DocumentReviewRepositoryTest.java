package shop.catalog.review;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import shop.catalog.IntegrationTest;
import shop.catalog.JsonAssert;
import shop.catalog.RecordedEvents;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.catalog.TestTransaction.transactional;
import static shop.catalog.review.ReviewCandidateFixture.candidate;
import static shop.catalog.review.ReviewCandidateFixture.candidatePushedWithAMixedReport;
import static shop.catalog.review.ReviewCandidateFixture.editedContent;
import static shop.catalog.review.ReviewFixture.AUTHOR;
import static shop.catalog.review.ReviewFixture.FIRST_OFFER_VERSION;
import static shop.catalog.review.ReviewFixture.REASON;
import static shop.catalog.review.ReviewFixture.REVIEWER;
import static shop.catalog.review.ReviewFixture.REVIEWED_AT;
import static shop.catalog.review.ReviewFixture.aNewOfferId;
import static shop.catalog.review.ReviewFixture.aNewReviewId;
import static shop.catalog.review.ReviewFixture.request;

/**
 * The document-with-history adapter, one round trip per shape the schema can lose: the frozen candidate with its media and its
 * availability, the findings and the override, the decision and the version it published, and the events the write emitted. The write
 * is committed in its own transaction and read in another, so the read leaves Hibernate's first-level cache behind.
 */
@IntegrationTest
class DocumentReviewRepositoryTest {

    @Autowired
    DocumentReviewRepository repository;
    @Autowired
    DocumentReviewRepository.ReviewHistory history;
    @Autowired
    RecordedEvents recorded;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void savesAndReadsARequestedReviewWithItsFrozenCandidate() {
        String reviewId = aNewReviewId();
        ReviewProcess saved = requested(reviewId, aNewOfferId(), candidate());
        transactional(() -> repository.save(saved));

        ReviewProcess read = transactional(() -> repository.get(reviewId)).orElseThrow();

        ReviewAssert.assertThat(read)
                .hasReviewId(reviewId)
                .hasState(Review.State.REQUESTED)
                .isOpen()
                .wasRequestedBy(ReviewFixture.AUTHOR, ReviewFixture.REQUESTED_AT)
                .hasNoReviewer()
                .hasCandidate(candidate());
    }

    @Test
    void savesTheFindingsOfTheRequestAndTheOverrideThatIgnoresThem() {
        String reviewId = aNewReviewId();
        ReviewCandidate pushed = candidatePushedWithAMixedReport(AUTHOR);
        transactional(() -> repository.save(requested(reviewId, aNewOfferId(), pushed)));

        ReviewProcess read = transactional(() -> repository.get(reviewId)).orElseThrow();

        assertThat(read.toReview().candidate()).isEqualTo(pushed);
        assertThat(read.toReview().candidate().override().ignoredFindings())
                .isEqualTo(ReviewCandidateFixture.blockingAndWarningFindings());
        assertThat(findingsOf(reviewId)).containsExactly("IGNORED", "IGNORED", "IGNORED", "REPORTED", "REPORTED", "REPORTED");
    }

    @Test
    void keepsTheCandidateOfARejectedReviewForAudit() {
        String reviewId = aNewReviewId();
        ReviewProcess saved = requested(reviewId, aNewOfferId(), candidate());
        saved.reject(REVIEWER, REVIEWED_AT, REASON);
        transactional(() -> repository.save(saved));

        ReviewProcess read = transactional(() -> repository.get(reviewId)).orElseThrow();

        ReviewAssert.assertThat(read)
                .hasState(Review.State.REJECTED)
                .hasReason(REASON)
                .wasReviewedBy(REVIEWER, REVIEWED_AT)
                .hasCandidate(candidate());
    }

    @Test
    void remembersAWithdrawalWithItsCandidate() {
        String reviewId = aNewReviewId();
        ReviewProcess saved = requested(reviewId, aNewOfferId(), editedContentCandidate());
        saved.withdraw(AUTHOR);
        transactional(() -> repository.save(saved));

        ReviewProcess read = transactional(() -> repository.get(reviewId)).orElseThrow();

        ReviewAssert.assertThat(read).hasState(Review.State.WITHDRAWN).hasCandidate(editedContentCandidate());
    }

    @Test
    void savesAndReadsTheVersionAnAcceptancePublished() {
        String reviewId = aNewReviewId();
        transactional(() -> repository.save(published(requested(reviewId, aNewOfferId(), candidate()))));

        ReviewAssert.assertThat(transactional(() -> repository.get(reviewId)).orElseThrow())
                .hasState(Review.State.ACCEPTED)
                .wasReviewedBy(REVIEWER, REVIEWED_AT)
                .hasPublishedVersion(FIRST_OFFER_VERSION);
    }

    @Test
    void publishesTheSnapshotOnceAndEveryEmittedEventOnce() {
        String reviewId = aNewReviewId();
        String offerId = aNewOfferId();
        ReviewProcess saved = published(requested(reviewId, offerId, candidate()));
        transactional(() -> repository.save(saved));

        assertThat(recorded.of(Review.class)).containsExactly(saved.toReview());
        assertThat(recorded.of(ReviewEvent.class))
                .containsExactly(ReviewFixture.aPublishedHistory(offerId, reviewId, FIRST_OFFER_VERSION));
    }

    @Test
    void publishesTheBlockAnAcceptanceRecordedInsteadOfAVersion() {
        String reviewId = aNewReviewId();
        String offerId = aNewOfferId();
        ReviewProcess blocked = requested(reviewId, offerId, candidate());
        blocked.accept(REVIEWER, REVIEWED_AT);
        blocked.recordBlock(PublishDecision.BlockReason.FINDINGS);
        transactional(() -> repository.save(blocked));

        assertThat(recorded.of(ReviewEvent.class)).containsExactly(
                ReviewFixture.aBlockedHistory(offerId, reviewId, PublishDecision.BlockReason.FINDINGS));
        ReviewAssert.assertThat(transactional(() -> repository.get(reviewId)).orElseThrow())
                .hasState(Review.State.ACCEPTED)
                .hasNoPublishedVersion();
    }

    @Test
    void publishesNothingForASaveThatEmittedNothing() {
        String reviewId = aNewReviewId();
        transactional(() -> repository.save(requested(reviewId, aNewOfferId(), candidate())));
        ReviewProcess rehydrated = transactional(() -> repository.get(reviewId)).orElseThrow();

        transactional(() -> repository.save(rehydrated));

        assertThat(recorded.of(ReviewEvent.class))
                .containsExactly(new ReviewEvent.ReviewRequested(rehydrated.toReview().offerId(), reviewId));
        assertThat(recorded.of(Review.class)).hasSize(1);
    }

    @Test
    void recordsEveryEmittedEventInTheHistory() {
        String reviewId = aNewReviewId();
        transactional(() -> repository.save(published(requested(reviewId, aNewOfferId(), candidate()))));

        List<String> recordedTypes = transactional(() -> history.findByReviewIdOrderById(reviewId)).stream()
                .map(DocumentReviewRepository.ReviewHistoryEntity::eventType)
                .toList();

        assertThat(recordedTypes).containsExactly("ReviewRequested", "ReviewAccepted", "ReviewPublicationRecorded");
        JsonAssert.assertThat(payloadOf(reviewId, "ReviewPublicationRecorded"))
                .hasFieldsLike("""
                        { "reviewId": "%s", "offerVersion": %s }
                        """.formatted(reviewId, FIRST_OFFER_VERSION));
    }

    @Test
    void storesTheFrozenCandidateAsReadableJson() {
        String reviewId = aNewReviewId();
        transactional(() -> repository.save(requested(reviewId, aNewOfferId(), candidate())));

        JsonAssert.assertThat(candidateOf(reviewId)).hasFieldsLike("""
                { "description": { "title": "%s", "identifiers": { "productNumber": "%s" } },
                  "media": [ { "mediaId": "M-01", "position": 1, "primary": true },
                             { "mediaId": "M-02", "position": 2, "primary": false },
                             { "mediaId": "M-03", "kind": "VIDEO", "position": 3 } ] }
                """.formatted(ReviewCandidateFixture.TITLE, ReviewCandidateFixture.PRODUCT_NUMBER));
    }

    @Test
    void listsTheAttemptsOfOneOfferInTheOrderTheyWereRequested() {
        String offerId = aNewOfferId();
        String decided = aNewReviewId();
        String open = aNewReviewId();
        ReviewProcess rejected = requested(decided, offerId, candidate());
        rejected.reject(REVIEWER, REVIEWED_AT, REASON);
        transactional(() -> repository.save(rejected));
        transactional(() -> repository.save(ReviewFixture.requestReview(
                request(open, offerId, ReviewFixture.REQUESTED_AT.plusSeconds(60), candidate()))));

        List<Review> attempts = transactional(() -> repository.reviewsOf(offerId));

        assertThat(attempts).extracting(Review::reviewId).containsExactly(decided, open);
        assertThat(attempts).extracting(Review::state)
                .containsExactly(Review.State.REJECTED, Review.State.REQUESTED);
    }

    @Test
    void rewritesTheFindingsOfTheFrozenCandidateWithoutDuplicatingThem() {
        String reviewId = aNewReviewId();
        ReviewCandidate pushed = candidatePushedWithAMixedReport(AUTHOR);
        transactional(() -> repository.save(requested(reviewId, aNewOfferId(), pushed)));
        ReviewProcess rehydrated = transactional(() -> repository.get(reviewId)).orElseThrow();

        transactional(() -> repository.save(published(rehydrated)));

        assertThat(findingsOf(reviewId)).hasSize(6);
        assertThat(transactional(() -> repository.get(reviewId)).orElseThrow().toReview().candidate())
                .isEqualTo(pushed);
    }

    @Test
    void recordsTheBlockOfAnAcceptanceInTheHistory() {
        String reviewId = aNewReviewId();
        String offerId = aNewOfferId();
        ReviewProcess blocked = requested(reviewId, offerId, candidatePushedWithAMixedReport(AUTHOR));
        blocked.accept(REVIEWER, REVIEWED_AT);
        blocked.recordBlock(PublishDecision.BlockReason.FINDINGS);
        transactional(() -> repository.save(blocked));

        List<String> recordedTypes = transactional(() -> history.findByReviewIdOrderById(reviewId)).stream()
                .map(DocumentReviewRepository.ReviewHistoryEntity::eventType)
                .toList();

        assertThat(recordedTypes).containsExactly("ReviewRequested", "ReviewAccepted", "ReviewPublicationBlocked");
        JsonAssert.assertThat(payloadOf(reviewId, "ReviewPublicationBlocked")).hasFieldsLike("""
                { "reviewId": "%s", "reason": "FINDINGS" }
                """.formatted(reviewId));
    }

    @Test
    void readsNothingForAnUnknownReview() {
        assertThat(transactional(() -> repository.get(aNewReviewId()))).isEmpty();
    }

    @Test
    void refusesASecondOpenReviewOfTheSameOffer() {
        String offerId = aNewOfferId();
        transactional(() -> repository.save(requested(aNewReviewId(), offerId, candidate())));
        ReviewProcess second = requested(aNewReviewId(), offerId, candidate());

        assertThatThrownBy(() -> transactional(() -> repository.save(second)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static ReviewProcess requested(String reviewId, String offerId, ReviewCandidate candidate) {
        return ReviewFixture.requestReview(request(reviewId, offerId, candidate));
    }

    private static ReviewProcess published(ReviewProcess review) {
        review.accept(REVIEWER, REVIEWED_AT);
        review.recordPublication(FIRST_OFFER_VERSION);
        return review;
    }

    private static ReviewCandidate editedContentCandidate() {
        return ReviewCandidateFixture.candidateOf(editedContent());
    }

    private List<String> findingsOf(String reviewId) {
        return jdbc.queryForList(
                "select source from review_finding where review_id = ? order by source, finding_position",
                String.class, reviewId);
    }

    private String candidateOf(String reviewId) {
        return jdbc.queryForObject("select candidate::text from review_process where review_id = ?",
                String.class, reviewId);
    }

    private String payloadOf(String reviewId, String eventType) {
        return jdbc.queryForObject("select payload::text from review_history where review_id = ? and event_type = ?",
                String.class, reviewId, eventType);
    }
}
