package shop.catalog.review;

import shop.catalog.offer.Offer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The offer side of {@link OfferDraftSnapshot} as a plain map: the snapshot it holds per offer, and how often it was asked. The
 * read count is the seam's proof — a candidate frozen at request is built from one answer, and nothing re-reads the content later
 * (RULE-3, D38); the state may be asked again, because 09 decides on the offer as it is then.
 */
class FakeOfferDraftSnapshot implements OfferDraftSnapshot {

    private final Map<String, OfferSnapshot> offers = new HashMap<>();

    private int reads;

    static FakeOfferDraftSnapshot holding(OfferContent content) {
        FakeOfferDraftSnapshot snapshot = new FakeOfferDraftSnapshot();
        snapshot.offer(ReviewFixture.OFFER_ID, content);
        return snapshot;
    }

    void offer(String offerId, OfferContent content) {
        offer(offerId, Offer.State.DRAFT, content);
    }

    void offer(String offerId, Offer.State state, OfferContent content) {
        offers.put(offerId, new OfferSnapshot(state, content));
    }

    @Override
    public Optional<OfferSnapshot> snapshotOf(String offerId) {
        reads++;
        return Optional.ofNullable(offers.get(offerId));
    }

    int reads() {
        return reads;
    }
}
