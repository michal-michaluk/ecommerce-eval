package shop.fulfillment.customer;

import java.time.Instant;
import java.util.List;

import shop.fulfillment.kernel.Address;
import shop.fulfillment.kernel.DeliveryLocation;
import shop.fulfillment.kernel.Order;
import shop.fulfillment.kernel.OrderLine;
import shop.fulfillment.kernel.ProcessingState;

/**
 * The customer's view of one order — element 18's payload: the order summary he already knows (what was ordered and where it
 * goes), the processing state in the contract's own vocabulary, and the delivery legs the carrier reported, oldest first.
 *
 * <p>{@code assignee} and {@code issue} are not here, so they cannot be served: the customer never learns who holds the order,
 * and a hold reaches him only as {@code state = onHold} (a paused marker without its kind). The invoicing party, the payment
 * method, the waybill, the position progress and any money are absent for the same reason — element 18 makes each of them a
 * default of "not shown" (spec D5, Q-18-1, Q-18-2).
 *
 * <p>{@code at} is when *we* received the event, never the carrier's own timestamp: the carrier's clock is not mapped anywhere.
 *
 * @param state    the processing state as element 11 spells it; any customer-facing wording is presentational only
 * @param lines    what was ordered, as the order itself lists it
 * @param delivery where the parcel goes and which service the customer bought
 * @param legs     every delivery leg reached, in leg order
 */
record CustomerState(String state, List<Line> lines, Delivery delivery, List<Step> legs) {

    CustomerState {
        lines = List.copyOf(lines);
        legs = List.copyOf(legs);
    }

    /**
     * One ordered line.
     *
     * @param productNumber the product, as checkout named it
     * @param name          the name the customer saw
     * @param quantity      how many units
     */
    record Line(String productNumber, String name, int quantity) {

        static Line of(OrderLine line) {
            return new Line(line.productNumber(), line.name(), line.quantity());
        }
    }

    /**
     * Where the parcel goes — the customer's own delivery target, with a pickup point only as the opaque id the order carries
     * (no name and no address exists anywhere for it, element 01 R-01-1).
     *
     * @param methodCode the delivery method the customer bought, an opaque code
     * @param location   the one place the parcel may go
     */
    record Delivery(String methodCode, Location location) {

        static Delivery of(shop.fulfillment.kernel.Delivery delivery) {
            return new Delivery(delivery.methodCode(), Location.of(delivery.location()));
        }
    }

    /**
     * The delivery target itself: exactly one of a pickup point and a street address, as the order guarantees.
     *
     * @param kind    {@code PICKUP_POINT} or {@code ADDRESS}
     * @param pointId the pickup point's opaque id, or {@code null} for a street address
     * @param address the street address, or {@code null} for a pickup point
     */
    record Location(String kind, String pointId, Address address) {

        static Location of(DeliveryLocation location) {
            return new Location(location.kind().name(), location.pointId(), location.address());
        }
    }

    /**
     * One step of the delivery timeline.
     *
     * @param leg the leg with our own name, {@code L1}…{@code L6}
     * @param at  when the event that told us reached this context
     */
    record Step(String leg, Instant at) {
    }

    /**
     * The payload of one processing, or {@code null} when the row cannot be represented — a state nobody wrote yet, or a row
     * whose handover has not arrived, which has no order summary to show ({@code projection.absent-read-model-is-null}).
     *
     * @param row  the stored row of this processing
     * @param legs the legs of the parcel carrying it, oldest first
     * @return the customer's view, or {@code null} when the row cannot be represented
     */
    static CustomerState ofNullable(CustomerStateProjection.CustomerStateRow row, List<Step> legs) {
        Order order = row.order();
        if (row.state() == null || order == null) {
            return null;
        }
        return new CustomerState(ProcessingState.valueOf(row.state()).wireName(),
                order.lines().stream().map(Line::of).toList(), Delivery.of(order.delivery()), legs);
    }
}
