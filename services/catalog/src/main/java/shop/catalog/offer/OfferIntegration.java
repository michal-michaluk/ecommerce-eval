package shop.catalog.offer;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shop.catalog.tools.EventEnvelope;
import shop.catalog.tools.EventSettings;
import shop.catalog.tools.EventStream;
import shop.catalog.tools.JsonConfiguration;

import java.time.Clock;
import java.time.Instant;

/**
 * Element 13 — the publisher of {@link OfferPublished} and {@link ProductRemoved}, the only adapter through which the offer context
 * leaves the process (D28). It is a listener on the facts the persistence adapter publishes after it has written, so the record is sent
 * inside the producing transaction, after the write, and a broker failure rolls the write back (`integration.publish-in-adapter`).
 *
 * <p>It must run <em>inside</em> that transaction or not at all: {@link Propagation#MANDATORY} refuses a save that has none, because a
 * record sent outside the write's transaction cannot be taken back and the write could then commit without its event.
 *
 * <p>It rebuilds the payload from the frozen version it reads back from its own table, because {@link OfferVersion} is what carries the
 * media and the availability date the frozen fact does not: the in-process {@code OfferVersionFrozen} names the version, and this adapter
 * reads the value 13 promises to ship (RULE-2, RULE-10). The hash is computed here, over the {@link OfferPublished.Value} it serialises,
 * so a consumer recomputes it from the payload it received (D39).
 *
 * <p>A revert publishes the same snapshot under the next version number — never a "rollback" record (RULE-2, S7) — and a draft edit or a
 * review decision publishes nothing at all (RULE-1, S9): neither is a fact this adapter listens to.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
class OfferIntegration {

    private final OfferRepository offers;
    private final EventStream events;
    private final EventSettings settings;
    private final Clock clock;

    @EventListener
    public void handle(OfferEvent.OfferVersionFrozen frozen) {
        publishOffer(frozen.offerId(), frozen.offerVersion());
    }

    @EventListener
    public void handle(OfferEvent.OfferVersionReverted reverted) {
        publishOffer(reverted.offerId(), reverted.offerVersion());
    }

    @EventListener
    public void handle(OfferEvent.OfferRemoved removed) {
        Instant removedAt = Instant.now(clock);
        events.publish(EventEnvelope.of(ProductRemoved.TYPE, removedAt, removed.offerId(),
                new ProductRemoved(removed.offerId(), removedAt)));
    }

    private void publishOffer(String offerId, int offerVersion) {
        OfferVersion version = frozenVersion(offerId, offerVersion);
        events.publish(EventEnvelope.of(OfferPublished.TYPE, version.publishedAt(), offerId,
                published(offerId, version)));
    }

    private OfferPublished published(String offerId, OfferVersion version) {
        if (settings.payload() == EventSettings.Payload.THIN) {
            return OfferPublished.thin(offerId, version.number());
        }
        OfferPublished.Value value = OfferPublished.valueOf(version);
        return OfferPublished.of(offerId, version.number(), value,
                DescriptionHash.of(JsonConfiguration.OBJECT_MAPPER.writeValueAsString(value)));
    }

    private OfferVersion frozenVersion(String offerId, int offerVersion) {
        return offers.get(offerId)
                .map(offer -> offer.version(offerVersion))
                .orElseThrow(() -> new OfferException(OfferException.Error.OFFER_VERSION_MISSING, offerId, offerVersion));
    }
}
