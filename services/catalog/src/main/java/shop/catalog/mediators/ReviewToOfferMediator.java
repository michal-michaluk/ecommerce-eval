package shop.catalog.mediators;

import org.springframework.stereotype.Component;
import shop.catalog.offer.OfferCandidate;
import shop.catalog.offer.OfferDraft;
import shop.catalog.offer.OfferService;
import shop.catalog.review.OfferContent;
import shop.catalog.review.OfferDraftSnapshot;
import shop.catalog.review.OfferPublication;
import shop.catalog.review.OfferSnapshot;
import shop.catalog.review.ReviewCandidate;

import java.time.Instant;
import java.util.Optional;

/**
 * Joins the {@code review → offer} edge of 17: the two contract ports 04 declares, answered over the offer's primary port.
 *
 * <p>Pattern 3 and 4 of {@code context-boundaries.md}, and the only place both contexts are named (17 RULE-1). It maps the one
 * shape the two sides name differently — the review's {@link OfferContent} and the offer's {@link OfferCandidate}, which hold the
 * same components — and does nothing else: no rule, no default, no branch beyond that mapping. The media travels by value
 * unchanged, because the service has one media type (D45). The offer side keeps the freeze itself, so the version is still frozen
 * by its owner (17 RULE-3, D38).
 *
 * <p>A missing offer answers the read with {@link Optional#empty()} — the port's whole "not found" vocabulary — and the command with
 * the offer context's own failure; both roll the caller's transaction back, which is the point of D34.
 */
@Component
class ReviewToOfferMediator implements OfferDraftSnapshot, OfferPublication {

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
