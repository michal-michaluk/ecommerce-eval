package shop.catalog.mediators;

import org.springframework.stereotype.Component;
import shop.catalog.pricing.ActivePrice;
import shop.catalog.pricing.PricingService;
import shop.catalog.reads.EffectivePrices;

import java.time.Instant;
import java.util.Optional;

/**
 * Joins the {@code reads → pricing} edge of 17: the question the pricing view of 16 asks, answered by 10's calculation over the
 * pricing context's primary port.
 *
 * <p>Pattern 3 of {@code context-boundaries.md}. It maps 10's answer into the read side's own record — the amount and its currency —
 * and adds nothing: no rule, no default, no branch beyond "priced or absent". The stacking and precision rules stay in
 * {@link shop.catalog.pricing.ActivePriceCalculation}, so the preview a sales person sees is the price the shop would charge
 * (16 RULE-4).
 */
@Component
class PricingToReadsMediator implements EffectivePrices {

    private final PricingService pricing;

    PricingToReadsMediator(PricingService pricing) {
        this.pricing = pricing;
    }

    @Override
    public Optional<Result> effectivePriceOf(String offerId, String currency, Instant at) {
        return pricing.activePrice(offerId, at, currency)
                .filter(price -> price.state() == ActivePrice.State.PRICED)
                .map(price -> new Result(price.amount().amountMinor(), price.amount().currency()));
    }
}
