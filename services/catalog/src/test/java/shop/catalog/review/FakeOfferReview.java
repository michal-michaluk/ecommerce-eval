package shop.catalog.review;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The offer side of {@link OfferReview}: it records the attempts it was asked to open, and answers with the failure a test handed it.
 *
 * <p>In production the refusals are the offer's own; here the fake only carries one a test already decided on, so "asked before
 * frozen" is read off the answers the other ports never gave.
 */
class FakeOfferReview implements OfferReview {

    private final List<String> attempts = new ArrayList<>();

    private RuntimeException refusal;

    static FakeOfferReview accepting() {
        return new FakeOfferReview();
    }

    static FakeOfferReview refusing(RuntimeException refusal) {
        FakeOfferReview offerReview = new FakeOfferReview();
        offerReview.refusal = refusal;
        return offerReview;
    }

    @Override
    public void requestReview(String offerId, String reviewId, Instant at) {
        if (refusal != null) {
            throw refusal;
        }
        attempts.add(reviewId);
    }

    int asks() {
        return attempts.size();
    }

    String lastAttempt() {
        return attempts.get(attempts.size() - 1);
    }
}
