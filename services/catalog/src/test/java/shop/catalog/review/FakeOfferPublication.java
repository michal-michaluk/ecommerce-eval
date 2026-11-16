package shop.catalog.review;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** The offer side of {@link OfferPublication}: it records what it was asked to freeze and answers the next version number. */
class FakeOfferPublication implements OfferPublication {

    private final List<ReviewCandidate> published = new ArrayList<>();

    private final List<String> offered = new ArrayList<>();

    private int nextVersion = ReviewFixture.FIRST_OFFER_VERSION;

    @Override
    public int publish(String offerId, ReviewCandidate candidate, Instant publishedAt, String publishedBy) {
        offered.add(offerId);
        published.add(candidate);
        return nextVersion++;
    }

    ReviewCandidate lastPublished() {
        return published.get(published.size() - 1);
    }

    String lastOfferId() {
        return offered.get(offered.size() - 1);
    }

    int publications() {
        return published.size();
    }
}
