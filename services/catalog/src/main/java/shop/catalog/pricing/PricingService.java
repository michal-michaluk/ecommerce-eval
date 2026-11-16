package shop.catalog.pricing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * The primary port of the pricing context: load the model of one offer — the empty model for an offer that has none
 * yet — apply exactly one command, save it back. Every rule lives in {@link PricingModelEditor}; this class holds the
 * lifecycle and the transaction boundary, nothing else.
 *
 * <p>{@link #activateDue} is the command the RULE-9 poll drives: it activates what became due at {@code at} and saves
 * the model, whose marker and {@code *Activated} facts then commit together.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PricingService {

    private final PricingRepository models;
    private final Clock clock;

    public PricingModel schedulePrice(String offerId, PriceChange change) {
        return updated(offerId, pricing -> pricing.schedulePrice(change, now()));
    }

    public PricingModel removePrice(String offerId, PriceId priceId) {
        return updated(offerId, pricing -> pricing.removePrice(priceId, now()));
    }

    public PricingModel scheduleDiscount(String offerId, DiscountChange change) {
        return updated(offerId, pricing -> pricing.scheduleDiscount(change, now()));
    }

    public PricingModel removeDiscount(String offerId, DiscountId discountId) {
        return updated(offerId, pricing -> pricing.removeDiscount(discountId, now()));
    }

    /** An offer without a pricing model has nothing to activate, so activation never opens one. */
    public void activateDue(String offerId, Instant at) {
        models.get(offerId).ifPresent(pricing -> activated(pricing, at));
    }

    private PricingModel updated(String offerId, Consumer<PricingModelEditor> command) {
        PricingModelEditor pricing = models.get(offerId)
                .orElseGet(() -> PricingModelEditor.newPricingModelFor(offerId));
        command.accept(pricing);
        models.save(pricing);
        return pricing.toPricingModel(now());
    }

    private void activated(PricingModelEditor pricing, Instant at) {
        pricing.activateDueEntries(at);
        models.save(pricing);
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
