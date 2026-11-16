package shop.catalog.pricing;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import shop.catalog.tools.EventEnvelope;
import shop.catalog.tools.EventStream;

import java.time.Instant;
import java.util.List;

/**
 * Element 13 — the publisher of {@link PricesChanged}, the one adapter through which the pricing context leaves the process (D28).
 *
 * <p>It listens to the activation facts only: {@link PricingEvent.PriceActivated} and {@link PricingEvent.DiscountActivated} are the
 * moment a price or a discount became what a customer pays (RULE-3), while the {@code *Scheduled} and {@code *Removed} facts are
 * bookkeeping that never leaves the context (03 RULE-4). It must run inside the producing transaction or not at all
 * ({@link Propagation#MANDATORY}), because a record cannot be taken back once sent.
 *
 * <p>What travels is the price the offer has <em>open</em> at the poll's instant, never the entry the fact names: 03 RULE-9 also activates
 * an entry that started and ended while the service was down, and RULE-7 forbids carrying a price for a currency the offer has no open
 * entry for. The same rule decides a discount activation — an ended discount changes nothing the shop is shown, so it publishes nothing,
 * and a live one publishes for every open currency it applies to. The envelopes's {@code occurredAt} is the instant the fact recorded, so
 * a catch-up poll reports the entry's own {@code validFrom} as {@code effectiveFrom} and the poll time as {@code occurredAt} (RULE-8).
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
@RequiredArgsConstructor
class PricesIntegration {

    private final PricingRepository models;
    private final EventStream events;

    @EventListener
    public void handle(PricingEvent.PriceActivated activated) {
        PricingModel model = modelAt(activated.offerId(), activated.occurredAt());
        openPrices(model, activated.price().currency()).forEach(entry -> publish(activated.offerId(),
                activated.occurredAt(), PricesChanged.of(activated.offerId(), entry.price(), entry.validity().validFrom(),
                        discountsOf(model, activated.occurredAt(), entry.price().currency()))));
    }

    @EventListener
    public void handle(PricingEvent.DiscountActivated activated) {
        PricingModel model = modelAt(activated.offerId(), activated.occurredAt());
        openPrices(model, activated).forEach(entry -> publish(activated.offerId(),
                activated.occurredAt(), PricesChanged.of(activated.offerId(), entry.price(), activated.effectiveFrom(),
                        discountsOf(model, activated.occurredAt(), entry.price().currency()))));
    }

    private void publish(String offerId, Instant occurredAt, PricesChanged changed) {
        events.publish(EventEnvelope.of(PricesChanged.TYPE, occurredAt, offerId, changed));
    }

    private PricingModel modelAt(String offerId, Instant at) {
        return models.get(offerId)
                .map(pricing -> pricing.toPricingModel(at))
                .orElseThrow(() -> new PricingException(PricingException.Error.PRICING_MODEL_UNKNOWN, offerId));
    }

    private static List<PriceEntry> openPrices(PricingModel model, String currency) {
        return model.current().stream()
                .filter(entry -> entry.price().isIn(currency))
                .toList();
    }

    private static List<PriceEntry> openPrices(PricingModel model, PricingEvent.DiscountActivated activated) {
        return model.current().stream()
                .filter(entry -> isActive(model, activated, entry.price().currency()))
                .toList();
    }

    private static boolean isActive(PricingModel model, PricingEvent.DiscountActivated activated, String currency) {
        return model.discounts().stream()
                .filter(discount -> discount.discountId().equals(activated.discountId()))
                .anyMatch(discount -> discount.isActiveIn(currency, activated.occurredAt()));
    }

    private static List<Discount> discountsOf(PricingModel model, Instant at, String currency) {
        return model.discounts().stream()
                .filter(discount -> discount.isActiveIn(currency, at))
                .toList();
    }
}
