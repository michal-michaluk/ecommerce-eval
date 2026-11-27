package shop.shopping.basket;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import shop.shopping.kernel.Availability;
import shop.shopping.kernel.Estimate;
import shop.shopping.kernel.Handover;
import shop.shopping.kernel.Money;

/**
 * The whole basket as the screen reads it — content, totals and the delivery estimate in one payload (element 07 §Read shape,
 * element 01, D10). It is the shape of record of steps 2–22 of {@code prototypes/basket-api.prove.md}: every route that
 * answers a basket answers this record, so no client ever composes two calls to render one screen.
 *
 * <p>What it renders is what the aggregate stores, never a second opinion: the rows in the order the basket holds them, the
 * price and the flattened discount of each with their kinds stated (D22, D40), the line total the row is worth here, and the
 * availability element 06 decided — narrowed to {@code status} and {@code stale}, because the reason behind a status is the
 * basket's business and not the client's (element 06 §Interface).
 *
 * <p>A {@code shipment} is one seller with at least one counted row (RULE-7, D8), and a counted row is what element 04 counts:
 * selected and not {@code unavailable} (D18, D33). The two must agree or the shipments would not add up to the totals, so the
 * filter restates element 04's own rule and nothing else. {@code delivery} is the rate of that one shipment, and the basket's
 * {@code delivery} is the sum of those rates.
 *
 * <p>{@code deliveryEstimate} is the range element 05 answers or {@code null} — a named non-answer, never an empty range
 * (Q22, D24): nothing handable over means nothing to say.
 *
 * @param basketId         the identity every path carries (RULE-1)
 * @param state            whether the basket is still editable
 * @param currency         the one currency the basket holds (element 03 RULE-2)
 * @param items            every row the basket holds, selected or not, in the order they were added
 * @param shipments        one seller group per counted seller, in the order the basket first names it
 * @param totals           what the selection is worth, as element 04 computed it
 * @param deliveryEstimate when the selection arrives, or {@code null} when there is nothing to estimate
 */
record BasketContent(
        String basketId,
        BasketState state,
        String currency,
        List<Item> items,
        List<Shipment> shipments,
        Handover.Totals totals,
        Range deliveryEstimate) {

    BasketContent {
        Objects.requireNonNull(basketId, "basketId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(shipments, "shipments");
        Objects.requireNonNull(totals, "totals");
        items = List.copyOf(items);
        shipments = List.copyOf(shipments);
    }

    /**
     * The payload of one basket.
     *
     * @param basket           the aggregate's own snapshot
     * @param price            element 04's four amounts for the same snapshot
     * @param estimate         element 05's answer for the same rows
     * @param shipmentDelivery what one seller shipment costs on the channel the customer arrived by
     * @return the payload every basket route answers
     */
    static BasketContent of(Basket basket, BasketPrice price, Estimate estimate, Money shipmentDelivery) {
        Objects.requireNonNull(basket, "basket");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(estimate, "estimate");
        Objects.requireNonNull(shipmentDelivery, "shipmentDelivery");
        return new BasketContent(basket.basketId().value(), basket.state(), basket.currency(),
                basket.items().stream().map(BasketContent::itemOf).toList(),
                shipmentsOf(basket.items(), shipmentDelivery), totalsOf(price), Range.ofNullable(estimate));
    }

    private static Item itemOf(BasketItem item) {
        Money currentPrice = item.lastKnown().currentPrice();
        return new Item(item.offerId(), item.seller(), item.lastKnown().title(), item.lastKnown().picture(),
                item.quantity(), item.selected(), AvailabilityView.of(item.availability()), currentPrice,
                item.lastKnown().discount(), currentPrice.times(item.quantity()));
    }

    private static List<Shipment> shipmentsOf(List<BasketItem> items, Money shipmentDelivery) {
        return counted(items).entrySet().stream()
                .map(shipment -> shipmentOf(shipment, shipmentDelivery))
                .toList();
    }

    private static Map<String, List<BasketItem>> counted(List<BasketItem> items) {
        return items.stream()
                .filter(BasketContent::isCounted)
                .collect(Collectors.groupingBy(BasketItem::seller, LinkedHashMap::new, Collectors.toList()));
    }

    private static boolean isCounted(BasketItem item) {
        return item.selected() && item.availability().status() != Availability.Status.unavailable;
    }

    private static Shipment shipmentOf(Map.Entry<String, List<BasketItem>> shipment, Money delivery) {
        List<BasketItem> rows = shipment.getValue();
        return new Shipment(shipment.getKey(), rows.stream().map(BasketItem::offerId).toList(),
                rows.stream()
                        .map(row -> row.lastKnown().currentPrice().times(row.quantity()))
                        .reduce(Money.zero(delivery.currency()), Money::plus),
                delivery);
    }

    private static Handover.Totals totalsOf(BasketPrice price) {
        return new Handover.Totals(price.productValue(), price.discountValue(), price.deliveryValue(),
                price.totalWithDelivery());
    }

    /**
     * One row as the screen renders it (element 01 RULE-3): the offer, the shipment it belongs to, one title, one picture, the
     * quantity, whether the customer selected it, what element 06 decided, and the three amounts of the line. {@code lineTotal}
     * is the price at the quantity — what the row is worth here, not a unit price (D8).
     *
     * @param offerId      the offer the row stands for
     * @param seller       the seller, which is the shipment the row is grouped into
     * @param title        the product title
     * @param picture      the one picture the row shows
     * @param quantity     how many units, always at least one
     * @param selected     whether the customer's selection covers this row
     * @param availability the outcome and its freshness, never re-derived by a client
     * @param currentPrice the price one unit is sold at
     * @param discount     the catalog's flattened discount, reported and never subtracted twice
     * @param lineTotal    {@code currentPrice} at {@code quantity}
     */
    record Item(
            String offerId,
            String seller,
            String title,
            String picture,
            int quantity,
            boolean selected,
            AvailabilityView availability,
            Money currentPrice,
            Money discount,
            Money lineTotal) {
    }

    /**
     * One seller's shipment: whose it is, which rows cross, what they are worth and what delivering them costs (RULE-7, D8).
     *
     * @param seller       the seller every row of the group belongs to
     * @param items        the offers that cross, in the order the basket holds them
     * @param productValue the rows' line totals, summed
     * @param delivery     one rate for this shipment
     */
    record Shipment(String seller, List<String> items, Money productValue, Money delivery) {
    }

    /**
     * Element 06's decision as the wire carries it: the outcome and whether the read behind it is stale. The reason stays in
     * the service — a client renders a status, it never branches on why (element 01 §States).
     *
     * @param status the outcome
     * @param stale  whether what the row shows is last-known rather than freshly read
     */
    record AvailabilityView(Availability.Status status, boolean stale) {

        static AvailabilityView of(Availability availability) {
            return new AvailabilityView(availability.status(), availability.stale());
        }
    }

    /**
     * The delivery estimate as the wire carries it: an inclusive range of calendar days (D43). Absent when there is nothing
     * handable over to estimate, which the payload says with {@code null} rather than with a range nobody should trust (Q22).
     *
     * @param from the first day, inclusive
     * @param to   the last day, inclusive
     */
    record Range(LocalDate from, LocalDate to) {

        static Range ofNullable(Estimate estimate) {
            return estimate.isARange() ? new Range(estimate.from(), estimate.to()) : null;
        }
    }
}
