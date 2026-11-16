package shop.catalog.pricing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The primary port of the pricing context: load the model of one offer — the empty model for an offer that has none
 * yet — apply exactly one command, save it back. Every rule lives in {@link PricingModelEditor}; this class holds the
 * lifecycle and the transaction boundary, nothing else.
 *
 * <p>{@link #activateDue} is the command the RULE-9 poll drives: it activates what became due at {@code at} and saves
 * the model, whose marker and {@code *Activated} facts then commit together.
 *
 * <p>{@link #activePrice} is the only read this class answers: it is the question 10 owns, and the pricing view of 16 asks it through
 * a mediator instead of restating the stacking rules in a template.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PricingService {

    private final PricingRepository models;
    private final ActivePriceCalculation calculation;
    private final KnownOffer knownOffer;
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

    /**
     * 12's one pricing edit (D18): every component of {@code edits} applied to the same model inside one transaction, so a removal the
     * state refuses takes the prices and discounts of the same request out of the model with it.
     *
     * <p>It refuses an offer this context has never heard of: the edit would otherwise open the pricing model of an identity that does
     * not exist, and 12 answers an unknown offer with `404` rather than with a model it just invented (17 RULE-4, 12).
     */
    public PricingModel edit(String offerId, PricingEdits edits) {
        ensureOfferIsKnown(offerId);
        return updated(offerId, pricing -> edits.applyTo(pricing, now()));
    }

    /** The whole model at this instant — what 12's pricing read renders, and the answer a write returns (D14). */
    @Transactional(readOnly = true)
    public Optional<PricingModel> modelOf(String offerId) {
        return models.get(offerId).map(pricing -> pricing.toPricingModel(now()));
    }

    /** An offer without a pricing model has nothing to activate, so activation never opens one. */
    public void activateDue(String offerId, Instant at) {
        models.get(offerId).ifPresent(pricing -> activated(pricing, at));
    }

    /**
     * What the offer costs in {@code currency} at {@code at} — 10's answer, asked by the pricing view of 16 for a date the user picks
     * (RULE-4). An offer without a model answers {@link ActivePrice} {@code ABSENT}, never the amount {@code 0}.
     *
     * <p>The calculation is the bean 10 configures ({@code catalog.pricing.order}, {@code catalog.pricing.stacking}), so the preview can
     * never disagree with what the shop would charge.
     */
    @Transactional(readOnly = true)
    public Optional<ActivePrice> activePrice(String offerId, Instant at, String currency) {
        return models.get(offerId).map(pricing -> {
            PricingModel model = pricing.toPricingModel(at);
            return calculation.calculateActivePrice(allPricesOf(model), model.discounts(), at, currency);
        });
    }

    private static List<PriceEntry> allPricesOf(PricingModel model) {
        return Stream.of(model.current(), model.scheduled(), model.history()).flatMap(List::stream).toList();
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

    private void ensureOfferIsKnown(String offerId) {
        if (!knownOffer.knows(offerId)) {
            throw new PricingException(PricingException.Error.OFFER_UNKNOWN, offerId);
        }
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
