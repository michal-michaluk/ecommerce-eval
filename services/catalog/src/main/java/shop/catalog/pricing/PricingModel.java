package shop.catalog.pricing;

import java.util.List;

/**
 * The published state of one offer's pricing at one instant (03): {@code current} holds the open entry of every
 * currency, {@code scheduled} the entries that start later, {@code history} the entries that already ended, and
 * {@code discounts} every discount in {@code validFrom} order.
 */
public record PricingModel(
        List<PriceEntry> current,
        List<PriceEntry> scheduled,
        List<PriceEntry> history,
        List<Discount> discounts) {

    public PricingModel {
        current = List.copyOf(current);
        scheduled = List.copyOf(scheduled);
        history = List.copyOf(history);
        discounts = List.copyOf(discounts);
    }
}
