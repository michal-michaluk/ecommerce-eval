package shop.catalog.review;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shop.catalog.offer.Offer;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * The review context's primary port: every use case of 04 is one load, one command on {@link ReviewProcess} and one save, and
 * nothing else. The rules live in the process, the decision in {@link PublishDecision}, the storage and the publication in the
 * adapter; this class holds the lifecycle and the transaction boundary only ({@code port.primary-thin}).
 *
 * <p>{@link #decide} is where 09 meets 04 inside <em>one</em> transaction (D34): the acceptance, the decision, the freeze through
 * {@link OfferPublication} and the {@code publishedOfferVersion} record commit or roll back together, so an accepted review and an
 * offer version can never disagree. The offer is read through {@link OfferDraftSnapshot} for the state 09 R6/R7 ask about — never
 * for the content, which the candidate already froze at the request (RULE-3, D38).
 *
 * <p>{@code Optional} is the whole "not found" vocabulary for a review: a command on an unknown review answers with
 * {@code Optional.empty()} and invents no second one. An offer this service cannot find is a different matter — an accepted review
 * cannot be recorded without its version — so it is refused with this context's failure instead of a fabricated offer state.
 *
 * <p>The candidate arrives already frozen in {@link ReviewRequest}: the capture is the caller's two questions to
 * {@link OfferDraftSnapshot} and {@link QualityFindings}, and the process's own admission rules (RULE-1, RULE-7) read the offer's
 * attempts through the repository.
 */
@Service
@Transactional
public class ReviewService {

    private final ReviewRepository reviews;
    private final OfferDraftSnapshot offers;
    private final OfferPublication publications;
    private final ReviewConfiguration.OverrideSettings settings;
    private final Clock clock;

    ReviewService(ReviewRepository reviews, OfferDraftSnapshot offers, OfferPublication publications,
                  ReviewConfiguration.OverrideSettings settings, Clock clock) {
        this.reviews = reviews;
        this.offers = offers;
        this.publications = publications;
        this.settings = settings;
        this.clock = clock;
    }

    public Review request(ReviewRequest request) {
        ReviewProcess review = ReviewProcess.requestReview(
                request, reviews.reviewsOf(request.offerId()), settings.allowsOverride());
        reviews.save(review);
        return review.toReview();
    }

    /**
     * Accepts the review, takes 09's decision and publishes the frozen candidate (RULE-4, D34).
     *
     * <p>A review that already published its version answers with it and publishes nothing: 09 R6 makes the replay of one acceptance
     * a no-op, so a retried decision can never freeze a second version (D15).
     */
    public Optional<Review> decide(String reviewId, String reviewer) {
        return reviews.get(reviewId).map(review -> replayOrDecide(review, reviewer));
    }

    public Optional<Review> reject(String reviewId, String reviewer, String reason) {
        return reviews.get(reviewId).map(review -> rejected(review, reviewer, reason));
    }

    public Optional<Review> withdraw(String reviewId, String requester) {
        return reviews.get(reviewId).map(review -> withdrawn(review, requester));
    }

    /** The review 12 decides by {@code offerId}: at most one is open per offer (RULE-1), so it either exists or the offer has none. */
    @Transactional(readOnly = true)
    public Optional<Review> openReviewOf(String offerId) {
        return reviews.reviewsOf(offerId).stream().filter(Review::isOpen).findFirst();
    }

    private Review replayOrDecide(ReviewProcess review, String reviewer) {
        Review decided = review.toReview();
        return decided.isPublished() ? decided : accepted(review, reviewer);
    }

    private Review accepted(ReviewProcess review, String reviewer) {
        Instant now = Instant.now(clock);
        review.accept(reviewer, now);
        publish(review, reviewer, now);
        reviews.save(review);
        return review.toReview();
    }

    private void publish(ReviewProcess review, String reviewer, Instant at) {
        Review accepted = review.toReview();
        PublishDecision decision = PublishDecision.decide(
                accepted, accepted.isPublished(), offerStateOf(accepted.offerId()));
        switch (decision.outcome()) {
            case PUBLISH -> review.recordPublication(
                    publications.publish(accepted.offerId(), accepted.candidate(), at, reviewer));
            case BLOCK -> review.recordBlock(decision.reason());
            case NO_OP -> throw new ReviewException(ReviewException.Error.DECISION_NOT_APPLICABLE, accepted.reviewId());
        }
    }

    private Offer.State offerStateOf(String offerId) {
        return offers.snapshotOf(offerId)
                .map(OfferSnapshot::state)
                .orElseThrow(() -> new ReviewException(ReviewException.Error.OFFER_UNKNOWN, offerId));
    }

    private Review rejected(ReviewProcess review, String reviewer, String reason) {
        review.reject(reviewer, Instant.now(clock), reason);
        reviews.save(review);
        return review.toReview();
    }

    private Review withdrawn(ReviewProcess review, String requester) {
        review.withdraw(requester);
        reviews.save(review);
        return review.toReview();
    }
}
