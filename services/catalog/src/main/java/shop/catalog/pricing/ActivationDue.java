package shop.catalog.pricing;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * RULE-9 as a pure decision: the entries due at one instant that carry no activation marker yet, in {@code validFrom}
 * order. The poll that drives it and the {@code catalog.pricing.activation-interval} behind it are an adapter — no
 * timer lives in the model.
 */
final class ActivationDue {

    private ActivationDue() {
    }

    static List<PriceEntry> decideDuePrices(List<PriceEntry> prices, Instant at) {
        return prices.stream()
                .filter(entry -> entry.isDueAt(at))
                .sorted(Comparator.comparing(entry -> entry.validity().validFrom()))
                .toList();
    }

    static List<Discount> decideDueDiscounts(List<Discount> discounts, Instant at) {
        return discounts.stream()
                .filter(discount -> discount.isDueAt(at))
                .sorted(Comparator.comparing(discount -> discount.validity().validFrom()))
                .toList();
    }
}
