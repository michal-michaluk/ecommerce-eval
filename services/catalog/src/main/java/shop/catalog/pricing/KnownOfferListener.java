package shop.catalog.pricing;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import shop.catalog.offer.Offer;

/**
 * Pattern 1 of context-boundaries: pricing keeps one local fact about the offer it may price — "this offer exists" —
 * and nothing else (17 RULE-4). It reads the exported {@link Offer} snapshot, writes its own table and returns; it
 * enforces no invariant, never throws and asks the offer context no second question, because it runs inside the
 * producer's transaction and a refusal there would roll back a write that already succeeded.
 *
 * <p>The write is a find-or-create, so a re-publication of the same offer rewrites the same row and no arrival order
 * can produce a second one.
 */
@Component
@Transactional
@RequiredArgsConstructor
class KnownOfferListener {

    private final KnownOffers knownOffers;

    @EventListener
    public void handle(Offer offer) {
        knownOffers.save(knownOffers.findById(offer.offerId())
                .orElseGet(() -> new KnownOfferEntity(offer.offerId())));
    }

    interface KnownOffers extends CrudRepository<KnownOfferEntity, String> {
    }

    @Entity
    @Table(name = "known_offer")
    static class KnownOfferEntity {

        @Id
        private String offerId;
        @Version
        private long version;

        protected KnownOfferEntity() {
        }

        KnownOfferEntity(String offerId) {
            this.offerId = offerId;
        }
    }
}
