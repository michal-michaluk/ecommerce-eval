package shop.fulfillment.processing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.OrderPosition;
import shop.fulfillment.kernel.PositionState;
import shop.fulfillment.kernel.ProductId;
import shop.fulfillment.kernel.StockAtLocation;

/**
 * The ordered walk a picker performs, computed from the processing's positions and the warehouse's stock observations
 * (element 10).
 *
 * <p>Pure and stateless: the stock arrives as a parameter (never a call made from here), the generation instant arrives
 * as a parameter (never a clock read), and running it twice on the same inputs produces the same list. A port failure
 * yields no list at all, never a partial one.
 *
 * <p>The two variant settings — walk order and quantity split — are values of this record, and the computation takes
 * them as parameters, so no rule reads configuration from inside. {@link #defaultRules()} selects the documented
 * defaults: one pass ordered by location code ascending, a quantity split across places in that same order.
 *
 * <p>Everything the sources call "optimised" beyond that is a default, not a fact: no route, distance metric, aisle
 * graph or start point is invented here, and the walk order stays advisory — nothing blocks a scan out of sequence.
 */
record PickListCalculation(PickListOrder order, PickListSplit split) {

    /** How the walk is ordered. */
    enum PickListOrder {
        LOCATION_CODE,
        SCARCITY_FIRST
    }

    /** How a quantity is distributed when the product sits in several places. */
    enum PickListSplit {
        MULTI_LOCATION,
        SINGLE_LOCATION
    }

    /** The documented defaults: no configuration selects a variant. */
    static PickListCalculation defaultRules() {
        return new PickListCalculation(PickListOrder.LOCATION_CODE, PickListSplit.MULTI_LOCATION);
    }

    /**
     * Generates the list with this calculation's own variants.
     *
     * <p>The stock map must hold one observation per product of a pending position: the warehouse port is a read and a
     * failed read yields no list at all, so an absent product is rejected instead of being read as zero stock.
     *
     * @param processing the processing whose pending positions are walked
     * @param stock      the warehouse's answer per product, taken as given
     * @param at         the generation instant the list is stamped with
     * @return one list per processing, one stop per (position, location) pair
     */
    PickList calculate(OrderProcessing processing, Map<ProductId, List<StockAtLocation>> stock, Instant at) {
        return calculate(processing, stock, at, order, split);
    }

    private static PickList calculate(OrderProcessing processing, Map<ProductId, List<StockAtLocation>> stock,
                                      Instant at, PickListOrder order, PickListSplit split) {
        List<PickList.Stop> planned = plannedStops(processing.positions(), stock, split);
        return new PickList(processing.processingId(), at, ordered(planned, order),
                shortfalls(processing.positions(), planned));
    }

    private static List<PickList.Stop> plannedStops(List<OrderPosition> positions,
                                                    Map<ProductId, List<StockAtLocation>> stock,
                                                    PickListSplit split) {
        return positions.stream()
                .filter(position -> position.state() == PositionState.PENDING)
                .flatMap(position -> stopsOf(position, stock, split).stream())
                .toList();
    }

    private static List<PickList.Stop> stopsOf(OrderPosition position,
                                               Map<ProductId, List<StockAtLocation>> stock,
                                               PickListSplit split) {
        List<StockAtLocation> places = placesOf(position.productNumber(), stock);
        if (split == PickListSplit.SINGLE_LOCATION) {
            return places.stream()
                    .filter(place -> place.available() >= position.quantity())
                    .findFirst()
                    .map(place -> List.of(stopAt(position, place, position.quantity())))
                    .orElse(List.of());
        }
        return walk(position, places);
    }

    private static List<PickList.Stop> walk(OrderPosition position, List<StockAtLocation> places) {
        List<PickList.Stop> stops = new ArrayList<>();
        int left = position.quantity();
        for (StockAtLocation place : places) {
            if (left == 0) {
                break;
            }
            int take = Math.min(left, place.available());
            stops.add(stopAt(position, place, take));
            left = left - take;
        }
        return stops;
    }

    private static List<StockAtLocation> placesOf(String productNumber,
                                                  Map<ProductId, List<StockAtLocation>> stock) {
        List<StockAtLocation> observations = stock.get(new ProductId(productNumber));
        if (observations == null) {
            throw new FulfillmentException(FulfillmentException.Error.STOCK_OBSERVATION_MISSING, productNumber);
        }
        return observations.stream()
                .filter(place -> place.available() > 0)
                .sorted(Comparator.comparing(StockAtLocation::code))
                .toList();
    }

    private static PickList.Stop stopAt(OrderPosition position, StockAtLocation place, int take) {
        return PickList.Stop.builder()
                .walkOrder(0)
                .productNumber(position.productNumber())
                .name(position.name())
                .location(place.code())
                .quantityToTake(take)
                .available(place.available())
                .reserved(place.reserved())
                .positionState(position.state())
                .build();
    }

    private static List<PickList.Stop> ordered(List<PickList.Stop> stops, PickListOrder order) {
        List<PickList.Stop> sorted = new ArrayList<>(stops);
        sorted.sort(comparatorFor(order));
        List<PickList.Stop> walk = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            walk.add(sorted.get(index).atWalkOrder(index + 1));
        }
        return List.copyOf(walk);
    }

    private static Comparator<PickList.Stop> comparatorFor(PickListOrder order) {
        Comparator<PickList.Stop> byCode = Comparator.comparing(PickList.Stop::location)
                .thenComparing(PickList.Stop::productNumber);
        if (order == PickListOrder.SCARCITY_FIRST) {
            return Comparator.comparing(PickList.Stop::takesLastUnits).reversed().thenComparing(byCode);
        }
        return byCode;
    }

    private static List<PickList.Shortfall> shortfalls(List<OrderPosition> positions,
                                                      List<PickList.Stop> stops) {
        List<PickList.Shortfall> shortfalls = new ArrayList<>();
        for (OrderPosition position : positions) {
            int planned = plannedQuantity(position, stops);
            if (position.state() == PositionState.PENDING && planned < position.quantity()) {
                shortfalls.add(new PickList.Shortfall(
                        position.productNumber(), position.quantity() - planned));
            }
        }
        return shortfalls;
    }

    private static int plannedQuantity(OrderPosition position, List<PickList.Stop> stops) {
        return stops.stream()
                .filter(stop -> stop.productNumber().equals(position.productNumber()))
                .mapToInt(PickList.Stop::quantityToTake)
                .sum();
    }
}
