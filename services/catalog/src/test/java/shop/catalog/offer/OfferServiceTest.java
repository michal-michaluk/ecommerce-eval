package shop.catalog.offer;

import org.junit.jupiter.api.Test;
import shop.catalog.offer.Offer.State;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The primary port against a fake repository: every use case is one load, one command and one save, and the command surface of
 * {@link OfferEditor} is reachable with nothing added and nothing decided (ports.md, {@code port.primary-thin}).
 */
class OfferServiceTest {

    final Map<String, OfferEditor> offers = new HashMap<>();
    final OfferService service = new OfferService(new FakeOfferRepository(), new FakeOfferRegistry());

    @Test
    void draftsAnOffer() {
        Offer drafted = service.draftOffer(OfferFixture.OFFER_ID, ProductDescriptionDraftFixture.draftOfInstanceA());

        OfferAssert.assertThat(drafted).hasOfferId(OfferFixture.OFFER_ID).hasState(State.DRAFT).hasVersion(0);
        assertThat(offers).containsKey(OfferFixture.OFFER_ID);
    }

    @Test
    void refusesToDraftAnOfferThatAlreadyExists() {
        offers.put(OfferFixture.OFFER_ID, OfferFixture.publishedOffer());

        OfferAssert.assertRefused(OfferException.Error.OFFER_ALREADY_DRAFTED,
                () -> service.draftOffer(OfferFixture.OFFER_ID, ProductDescriptionDraftFixture.draftOfInstanceA()));
        OfferAssert.assertThat(offers.get(OfferFixture.OFFER_ID)).hasState(State.PUBLISHED).hasVersion(1);
    }

    @Test
    void updatesTheDescription() {
        offers.put(OfferFixture.OFFER_ID, OfferFixture.draftOffer());

        Optional<Offer> updated = service.updateDescription(OfferFixture.OFFER_ID, OfferFixture.aDifferentDraft());

        assertThat(updated).isPresent();
        assertThat(offers.get(OfferFixture.OFFER_ID).toSurface().description().blocks())
                .isEqualTo(OfferFixture.aDifferentDraft().blocks());
    }

    @Test
    void replacesTheMedia() {
        offers.put(OfferFixture.OFFER_ID, OfferFixture.draftOffer());

        Optional<Offer> updated = service.updateMedia(OfferFixture.OFFER_ID, OfferFixture.mediaOfInstanceA());

        assertThat(updated).isPresent();
        assertThat(offers.get(OfferFixture.OFFER_ID).toSurface().media()).isEqualTo(OfferFixture.mediaOfInstanceA());
    }

    @Test
    void schedulesTheAvailability() {
        offers.put(OfferFixture.OFFER_ID, OfferFixture.draftOffer());

        Optional<Offer> scheduled = service.scheduleAvailability(OfferFixture.OFFER_ID, OfferFixture.AVAILABILITY_START);

        assertThat(scheduled).isPresent();
        OfferAssert.assertThat(scheduled.orElseThrow()).hasAvailabilityStart(OfferFixture.AVAILABILITY_START);
    }

    @Test
    void requestsAReview() {
        offers.put(OfferFixture.OFFER_ID, OfferFixture.draftOfferWithMedia());

        Optional<Offer> requested = service.requestReview(OfferFixture.OFFER_ID);

        OfferAssert.assertThat(requested.orElseThrow()).hasState(State.PENDING_REVIEW);
    }

    @Test
    void closesAReview() {
        offers.put(OfferFixture.OFFER_ID, OfferFixture.offerPendingReview());

        Offer closed = service.closeReview(OfferFixture.OFFER_ID, OfferFixture.REVIEW_ID);

        OfferAssert.assertThat(closed).hasState(State.DRAFT).hasVersion(0);
    }

    @Test
    void refusesToCloseAReviewOfAnOfferItDoesNotKnow() {
        OfferAssert.assertRefused(OfferException.Error.OFFER_UNKNOWN,
                () -> service.closeReview(OfferFixture.OTHER_OFFER_ID, OfferFixture.REVIEW_ID));
    }

    @Test
    void publishes() {
        offers.put(OfferFixture.OFFER_ID, OfferFixture.offerPendingReview());

        Optional<Offer> published = service.publish(OfferFixture.OFFER_ID, OfferFixture.now(), OfferFixture.REVIEWER);

        OfferAssert.assertThat(published.orElseThrow()).hasState(State.PUBLISHED).hasVersion(1);
    }

    @Test
    void revertsToTheNextVersion() {
        offers.put(OfferFixture.OFFER_ID, OfferFixture.publishedOffer());

        Optional<Offer> reverted = service.revert(OfferFixture.OFFER_ID, 1, OfferFixture.now(), OfferFixture.REVIEWER);

        OfferAssert.assertThat(reverted.orElseThrow()).hasVersion(2);
        assertThat(offers.get(OfferFixture.OFFER_ID).version(2).copiedFrom()).isEqualTo(1);
    }

    @Test
    void removes() {
        offers.put(OfferFixture.OFFER_ID, OfferFixture.publishedOffer());

        Optional<Offer> removed = service.removeOffer(OfferFixture.OFFER_ID);

        OfferAssert.assertThat(removed.orElseThrow()).hasState(State.REMOVED);
    }

    @Test
    void answersAnUnknownOfferWithNothing() {
        assertThat(service.getOffer(OfferFixture.OTHER_OFFER_ID)).isEmpty();
        assertThat(service.publish(OfferFixture.OTHER_OFFER_ID, OfferFixture.now(), OfferFixture.REVIEWER)).isEmpty();
    }

    @Test
    void passesTheAggregatesRefusalThrough() {
        offers.put(OfferFixture.OFFER_ID, OfferFixture.draftOfferWithMediaOnly());

        OfferAssert.assertRefused(OfferException.Error.AVAILABILITY_START_MISSING,
                () -> service.publish(OfferFixture.OFFER_ID, OfferFixture.now(), OfferFixture.REVIEWER));
        OfferAssert.assertThat(offers.get(OfferFixture.OFFER_ID)).hasState(State.DRAFT);
    }

    class FakeOfferRepository implements OfferRepository {

        @Override
        public Optional<OfferEditor> get(String offerId) {
            return Optional.ofNullable(offers.get(offerId));
        }

        @Override
        public List<OfferEditor> findAll(int page, int size) {
            List<OfferEditor> all = List.copyOf(offers.values());
            int from = Math.min(page * size, all.size());
            return all.subList(from, Math.min(from + size, all.size()));
        }

        @Override
        public long count() {
            return offers.size();
        }

        @Override
        public void save(OfferEditor offer) {
            offers.put(offer.offerId, offer);
        }
    }
}
