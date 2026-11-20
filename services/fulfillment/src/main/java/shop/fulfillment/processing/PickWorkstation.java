package shop.fulfillment.processing;

import lombok.Builder;

import java.util.List;

import shop.fulfillment.kernel.OrderPosition;
import shop.fulfillment.kernel.PositionState;

/**
 * The pick workstation of element 16 — one screen holding the positions and the processing state together (W1).
 *
 * <p>Every position of the order is here, not only the ones still to walk: the screen has to distinguish positions already
 * picked from positions already packed at the same time (W2), so a confirmed row stays visible and is rendered differently
 * rather than removed. A position the pick list no longer walks carries no stop — {@code locations} is an empty array, never
 * an absent key.
 *
 * <p>No field the doc does not name: product, quantity, location(s), position state, processing state, {@code n/m packed},
 * assignee. No price, total, weight or currency (spec D5), no delivery leg and no waybill (view 18), no customer data.
 *
 * @param processingId      the order being worked
 * @param state             the processing state, as the contract spells it
 * @param assignee          the employee holding the order, or an explicit {@code null} while unclaimed
 * @param positionProgress  {@code n/m packed}, derived from the position states and never a stored field
 * @param positions         one row per order line, from the order onwards
 */
@Builder
record PickWorkstation(
        String processingId,
        String state,
        String assignee,
        String positionProgress,
        List<Position> positions) {

    PickWorkstation {
        positions = List.copyOf(positions);
    }

    /**
     * One row of the screen: the position, where to take it from and how far it got.
     *
     * @param productNumber the product of the line
     * @param name          the product name of the line
     * @param quantity      the quantity the order pinned
     * @param state         {@code awaitingScan}, {@code picked} or {@code packed}
     * @param locations     the stops of the pick list for this position, empty once nothing is left to walk
     */
    @Builder
    record Position(String productNumber, String name, int quantity, String state, List<PickList.Stop> locations) {

        Position {
            locations = List.copyOf(locations);
        }

        static Position of(OrderPosition position, PickList pickList) {
            return new Position(position.productNumber(), position.name(), position.quantity(),
                    position.state().wireName(), stopsOf(pickList, position.productNumber()));
        }

        private static List<PickList.Stop> stopsOf(PickList pickList, String productNumber) {
            return pickList.lines().stream()
                    .filter(line -> line.productNumber().equals(productNumber))
                    .findFirst()
                    .map(PickList.PickListLine::locations)
                    .orElse(List.of());
        }
    }

    /**
     * The screen as one payload.
     *
     * @param processing the processing the employee works
     * @param pickList   the walk generated for it in the same request
     * @return the read model of element 16
     */
    static PickWorkstation of(OrderProcessing processing, PickList pickList) {
        return new PickWorkstation(processing.processingId().value(), processing.state().wireName(),
                processing.assignee(), progressOf(processing),
                processing.positions().stream().map(position -> Position.of(position, pickList)).toList());
    }

    private static String progressOf(OrderProcessing processing) {
        long packed = processing.positions().stream()
                .filter(position -> position.state() == PositionState.PACKED)
                .count();
        return packed + "/" + processing.positions().size() + " packed";
    }
}
