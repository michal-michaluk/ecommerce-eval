package shop.shopping.basket;

import java.util.List;
import java.util.Objects;

import shop.shopping.kernel.Availability;
import shop.shopping.kernel.Money;

/**
 * Element 04: what the customer pays for what is <em>selected</em>, in one pure calculation.
 *
 * <p>It computes no discount rule (D22, D40): the catalog flattens a price and a discount per item, and this calculation only
 * multiplies each by its quantity and sums — {@code currentPrice} is never recomputed from a price and a percentage, and
 * nothing is rounded here, because every amount stays in the fine unit and {@link Money} owns the single rounding step
 * (element 03 RULE-4, RULE-5).
 *
 * <p>The selection in force <em>is</em> the {@code selected} flag of each row (element 02 RULE-9, RULE-10): there is no second
 * parameter for it, because a second source of truth could disagree with the aggregate that stores it. An {@code unavailable}
 * row is excluded from every sum (D18), an {@code outOfStock} row counts while it is selected — the price is real and blocking
 * the handover is element 09's business (D33, S4) — and a deselected row counts for nothing (S12).
 *
 * <p>Delivery is one rate per seller shipment that holds at least one counted row, so a multi-seller selection sums the rates
 * (S8) and a shipment whose every row is unselected or {@code unavailable} is not a shipment at all; the SMART channel makes
 * it {@code 0,00} and no threshold exists (D23). Nothing selected answers {@code 0,00 zł} of the basket's currency (element 03
 * RULE-8, S9).
 *
 * <p>The calculation is pure: no annotation, no clock, no configuration utility, no repository — the rates arrive in the
 * constructor and the channel in the call. It answers its own questions and refuses nothing itself: the only failures reachable
 * through it are the value types' invariants — a zero quantity never becomes a row ({@link BasketItem}), and a row in another
 * currency never adds to the basket's zero ({@link Money}) — so S10 and S11 assert those types, and this calculation neither
 * re-checks nor re-implements them.
 */
record BasketPriceCalculation(DeliveryRates delivery) {

    BasketPriceCalculation {
        Objects.requireNonNull(delivery, "delivery");
    }

    /**
     * Prices the basket's current selection.
     *
     * @param basket  the basket snapshot, carrying its rows, their quantities, their selection and their availability
     * @param channel how the customer arrives, which decides whether delivery is free
     * @return the four amounts of element 04
     */
    BasketPrice calculate(Basket basket, Channel channel) {
        List<BasketItem> counted = countedItems(basket);
        Money productValue = counted.stream()
                .map(item -> item.lastKnown().currentPrice().times(item.quantity()))
                .reduce(Money.zero(basket.currency()), Money::plus);
        Money discountValue = counted.stream()
                .map(item -> item.lastKnown().discount().times(item.quantity()))
                .reduce(Money.zero(basket.currency(), Money.Kind.discount), Money::plus);
        Money deliveryValue = deliveryValueOf(counted, basket, channel);
        return new BasketPrice(productValue, discountValue, deliveryValue, productValue.plus(deliveryValue));
    }

    private Money deliveryValueOf(List<BasketItem> counted, Basket basket, Channel channel) {
        if (counted.isEmpty()) {
            return Money.zero(basket.currency());
        }
        List<String> shipments = counted.stream().map(BasketItem::seller).distinct().toList();
        return delivery.rateFor(channel, basket.currency()).times(shipments.size());
    }

    private static List<BasketItem> countedItems(Basket basket) {
        return basket.items().stream()
                .filter(item -> item.selected() && item.availability().status() != Availability.Status.unavailable)
                .toList();
    }
}
