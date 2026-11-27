package shop.shopping.basket;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import shop.shopping.kernel.Estimate;

/**
 * Element 05 §Granularity: the one range the basket publishes — the latest {@code to} across the seller shipments of the rows
 * that cross, and {@code unknown} when no shipment answered a range (Q22, I4/I7).
 *
 * <p>Element 05's policy answers <strong>one</strong> shipment, so the grouping is here and never in the policy: the rows are
 * grouped by {@link BasketItem#seller()}, each group is asked, and only the answers that hold a range are compared. An
 * {@code unknown} shipment hides its own line and contributes no date, which is why it can neither win nor be the fallback of
 * a basket another shipment did answer for.
 *
 * <p>The seam is a function rather than the policy itself so the aggregation is testable on its own terms: today every
 * shipment answers the same days (the range depends on the clock and the zone only), so a test that could not answer two
 * shipments differently could not tell "the latest {@code to}" from "the first answer".
 *
 * @param ofShipment one seller shipment's range, as element 05's policy answers it
 */
record ShipmentEstimates(Function<BasketDeliveryEstimate.Request, Estimate> ofShipment) {

    ShipmentEstimates {
        Objects.requireNonNull(ofShipment, "ofShipment");
    }

    /** The aggregation over the deployment's own policy — what the wiring builds. */
    static ShipmentEstimates answeredBy(BasketDeliveryEstimate policy) {
        Objects.requireNonNull(policy, "policy");
        return new ShipmentEstimates(policy::estimate);
    }

    /**
     * The range the basket publishes for these rows.
     *
     * @param items   every row of the basket, selected or not — the policy is the one that filters them
     * @param channel how the customer arrives; SMART changes the price, never the range (I6)
     * @param now     the instant the range counts from
     * @return the latest {@code to} of the shipments that answered a range, or the named non-answer
     */
    Estimate latestTo(List<BasketItem> items, Channel channel, Instant now) {
        Objects.requireNonNull(items, "items");
        return items.stream()
                .collect(Collectors.groupingBy(BasketItem::seller))
                .values().stream()
                .map(shipment -> ofShipment.apply(new BasketDeliveryEstimate.Request(shipment, channel, now)))
                .filter(Estimate::isARange)
                .max(Comparator.comparing(Estimate::to))
                .orElseGet(() -> Estimate.unknown(Estimate.Source.configured));
    }
}
