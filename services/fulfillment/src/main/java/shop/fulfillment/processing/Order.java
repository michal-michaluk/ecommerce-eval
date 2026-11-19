package shop.fulfillment.processing;

import lombok.Builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The immutable document describing what the customer bought and under which terms — content only, produced by
 * checkout and never edited here (element 01, RULE-1…RULE-8).
 *
 * <p>The order carries no identity, no status, no history and no money. Two orders with equal content are the same
 * order, so equality is the canonical {@code orderHash} and lines are held in product-number order, whatever order the
 * handover listed them in.
 *
 * <p>{@code delivery.methodCode} and {@code payment.methodCode} are opaque strings: this context never enumerates or
 * re-validates them, because checkout's own guard already did.
 */
@Builder
record Order(
        String orderHash,
        Instant placedAt,
        List<OrderLine> lines,
        Delivery delivery,
        Payment payment,
        Invoicing invoicing) {

    Order {
        List<String> missing = new ArrayList<>();
        addIfMissing(missing, orderHash == null || orderHash.isBlank(), "orderHash");
        addIfMissing(missing, placedAt == null, "placedAt");
        addIfMissing(missing, lines == null || lines.isEmpty(), "lines");
        addIfMissing(missing, delivery == null, "delivery");
        addIfMissing(missing, payment == null, "payment");
        addIfMissing(missing, invoicing == null, "invoicing");
        ensureNothingIsMissing(missing);
        lines = lines.stream().sorted(Comparator.comparing(OrderLine::productNumber)).toList();
        ensureLinesAreDistinct(lines);
    }

    /**
     * Two orders are the same order when their canonical content hashes agree, whatever else the two handovers
     * carried.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Order order && Objects.equals(orderHash, order.orderHash);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(orderHash);
    }

    private static void ensureNothingIsMissing(List<String> missing) {
        if (!missing.isEmpty()) {
            throw new FulfillmentException(FulfillmentException.Error.ORDER_INCOMPLETE, missing);
        }
    }

    private static void addIfMissing(List<String> missing, boolean absent, String name) {
        if (absent) {
            missing.add(name);
        }
    }

    private static void ensureLinesAreDistinct(List<OrderLine> lines) {
        Set<String> products = new HashSet<>();
        for (OrderLine line : lines) {
            if (!products.add(line.productNumber())) {
                throw new FulfillmentException(
                        FulfillmentException.Error.ORDER_LINE_DUPLICATE, line.productNumber());
            }
        }
    }
}
