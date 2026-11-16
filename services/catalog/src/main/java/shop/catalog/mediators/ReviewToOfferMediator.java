package shop.catalog.mediators;

import org.springframework.stereotype.Component;
import shop.catalog.offer.OfferCandidate;
import shop.catalog.offer.OfferDraft;
import shop.catalog.offer.OfferService;
import shop.catalog.review.OfferContent;
import shop.catalog.review.OfferDraftSnapshot;
import shop.catalog.review.OfferPublication;
import shop.catalog.review.OfferReview;
import shop.catalog.review.OfferSnapshot;
import shop.catalog.review.ReviewCandidate;

import java.time.Instant;
import java.util.Optional;

/**
 * Joins the {@code review → offer} edge of 17: the three contract ports that edge carries, answered over the offer's primary port.
 *
 * <p>All three are one edge, so they are one mediator (17 RULE-1, {@code boundary.one-wire-per-edge}): the same field, the same
 * provider port, the same removal from the day a projection can answer one of the questions. One bean per port would repeat
 * {@link OfferService} three times and turn a single edge into three places to keep in step.
 *
 * <p>Pattern 3 and 4 of {@code context-boundaries.md}, and the only place both contexts are named. It maps the one shape the two
 * sides name differently — the review's {@link OfferContent} and the offer's {@link OfferCandidate}, which hold the same components
 * — and does nothing else: no rule, no default, no branch beyond that mapping. The media travels by value unchanged, because the
 * service has one media type (D45).
 *
 * <p>Every question is answered by the owner of the rule: the offer keeps the media gate and the freeze (17 RULE-3, 02 RULE-1,
 * D38), so its refusal reaches the caller as its own {@code reason} (D49). A missing offer answers the read with
 * {@link Optional#empty()} — the port's whole "not found" vocabulary — while a command answers it with the offer's own failure (the
 * freeze) or is followed by a read this context already makes (the review request); both roll the caller's transaction back, which is
 * the point of D34.
 */
@Component
class ReviewToOfferMediator implements OfferDraftSnapshot, OfferPublication, OfferReview {

    private final OfferService offers;

    ReviewToOfferMediator(OfferService offers) {
        this.offers = offers;
    }

    @Override
    public Optional<OfferSnapshot> snapshotOf(String offerId) {
        return offers.draftOf(offerId).map(ReviewToOfferMediator::snapshotOf);
    }

    @Override
    public int publish(String offerId, ReviewCandidate candidate, Instant publishedAt, String publishedBy) {
        return offers.publishCandidate(offerId, candidateOf(candidate), publishedAt, publishedBy).version();
    }

    @Override
    public void requestReview(String offerId, String reviewId, Instant at) {
        offers.requestReview(offerId);
    }

    private static OfferSnapshot snapshotOf(OfferDraft draft) {
        return new OfferSnapshot(draft.state(), contentOf(draft.candidate()));
    }

    private static OfferContent contentOf(OfferCandidate candidate) {
        return new OfferContent(candidate.description(), candidate.media(), candidate.availabilityStart());
    }

    private static OfferCandidate candidateOf(ReviewCandidate candidate) {
        return new OfferCandidate(candidate.description(), candidate.media(), candidate.availabilityStart());
    }
}
