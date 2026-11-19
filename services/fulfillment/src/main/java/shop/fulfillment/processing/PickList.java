package shop.fulfillment.processing;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * The ordered walk an assigned employee performs, generated at one instant: one stop per (position, location) pair
 * actually walked, and one shortfall per position the shelf cannot cover.
 *
 * <p>It is a read model, replaced per request; no price, amount, currency or total appears here.
 *
 * @param processingId the processing the list belongs to
 * @param generatedAt  the moment the snapshot was taken
 * @param stops        the walk, ordered and numbered from 1
 * @param shortfalls   what the warehouse cannot deliver for the pinned order
 */
record PickList(OrderProcessingId processingId, Instant generatedAt, List<Stop> stops, List<Shortfall> shortfalls) {

    /**
     * One place to walk to for one position. Stops are never merged: the workstation confirms positions, not shelves.
     *
     * <p>A stop under construction carries {@code walkOrder = 0}; the calculation numbers the published walk from 1.
     *
     * @param walkOrder     position of the stop in the list, from 1
     * @param productNumber the product to take
     * @param name          the product name, for the screen only
     * @param location      the warehouse location, opaque and used verbatim
     * @param quantityToTake how many units to take here
     * @param available     the units free at this location, as the warehouse reported them
     * @param reserved      the units allocated at this location, as the warehouse reported them
     * @param positionState the position state when the list was generated
     */
    @Builder(toBuilder = true)
    record Stop(
            int walkOrder,
            String productNumber,
            String name,
            LocationCode location,
            int quantityToTake,
            int available,
            int reserved,
            PositionState positionState) {

        Stop {
            if (quantityToTake < 1) {
                throw new FulfillmentException(FulfillmentException.Error.QUANTITY_NOT_POSITIVE, quantityToTake);
            }
        }

        /** The same stop, placed at its final position in the walk. */
        Stop atWalkOrder(int walkOrder) {
            return toBuilder().walkOrder(walkOrder).build();
        }

        /** Whether taking this stop empties the location — the units that are there are the last ones. */
        boolean takesLastUnits() {
            return quantityToTake == available;
        }
    }

    /**
     * What the warehouse cannot cover for one position.
     *
     * @param productNumber    the product that is short
     * @param missingQuantity  how many units are missing
     */
    record Shortfall(String productNumber, int missingQuantity) {

        Shortfall {
            if (missingQuantity < 1) {
                throw new FulfillmentException(FulfillmentException.Error.QUANTITY_NOT_POSITIVE, missingQuantity);
            }
        }
    }
}
