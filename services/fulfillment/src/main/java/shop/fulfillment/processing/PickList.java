package shop.fulfillment.processing;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

import shop.fulfillment.kernel.FulfillmentException;

/**
 * The ordered walk an assigned employee performs, generated at one instant: one line per pending position, one stop per
 * (position, location) pair actually walked, and one shortfall per position the shelf cannot cover.
 *
 * <p>It is the read model of {@code GET /processing/{processingId}/pick-list}, replaced per request, and it is shaped the
 * way the contract writes it: a line carries the position (product, name, quantity, state) and its own stops, so a stop is
 * self-describing and the payload groups by position while {@code walkOrder} still interleaves products.
 *
 * <p>{@code lines} and every line's {@code locations} are present-and-empty, never absent and never null. No price, amount,
 * currency or total appears anywhere here.
 *
 * @param processingId the processing the list belongs to, as the wire names it
 * @param generatedAt  the moment the snapshot was taken
 * @param lines        one line per position still to be walked
 * @param shortfalls   what the warehouse cannot deliver for the pinned order
 */
record PickList(String processingId, Instant generatedAt, List<PickListLine> lines, List<Shortfall> shortfalls) {

    PickList {
        lines = List.copyOf(lines);
        shortfalls = List.copyOf(shortfalls);
    }

    /**
     * One position to walk and the places it is taken from.
     *
     * @param productNumber the product to take
     * @param name          the product name, for the screen only
     * @param quantity      the quantity the order pinned (never what the shelf happens to hold)
     * @param state         the position state when the list was generated, as the wire names it
     * @param locations     the stops of this line, empty when the shelf cannot cover the position
     */
    @Builder
    record PickListLine(String productNumber, String name, int quantity, String state, List<Stop> locations) {

        PickListLine {
            locations = List.copyOf(locations);
        }

        /** The same line, carrying its stops at their final position in the walk. */
        PickListLine withLocations(List<Stop> locations) {
            return new PickListLine(productNumber, name, quantity, state, locations);
        }

        /** How many units this line takes from the shelves. */
        int quantityPlanned() {
            return locations.stream().mapToInt(Stop::quantityToTake).sum();
        }
    }

    /**
     * One place to walk to for one position. Stops are never merged: the workstation confirms positions, not shelves.
     *
     * <p>A stop under construction carries {@code walkOrder = 0}; the calculation numbers the published walk from 1.
     *
     * @param walkOrder      position of the stop in the whole walk, from 1
     * @param productNumber  the product to take
     * @param location       the warehouse location, opaque and used verbatim
     * @param quantityToTake how many units to take here
     * @param available      the units free at this location, as the warehouse reported them
     * @param reserved       the units allocated at this location, as the warehouse reported them
     */
    @Builder(toBuilder = true)
    record Stop(
            int walkOrder,
            String productNumber,
            String location,
            int quantityToTake,
            int available,
            int reserved) {

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
     * @param productNumber   the product that is short
     * @param missingQuantity how many units are missing
     */
    record Shortfall(String productNumber, int missingQuantity) {

        Shortfall {
            if (missingQuantity < 1) {
                throw new FulfillmentException(FulfillmentException.Error.QUANTITY_NOT_POSITIVE, missingQuantity);
            }
        }
    }
}
