package shop.fulfillment.processing;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.OrderPosition;
import shop.fulfillment.kernel.PositionState;
import shop.fulfillment.kernel.ProductId;
import shop.fulfillment.kernel.StockAtLocation;
import shop.fulfillment.kernel.WarehouseStock;

/**
 * The pick list of element 10 as a query handler: load the processing, ask the warehouse once per product still to be
 * walked, run the pure {@link PickListCalculation} and answer its list.
 *
 * <p>Every observation is mandatory and every failure surfaces: an absent {@code stockAt} answer, a warehouse that
 * cannot be reached and a deployment with no warehouse adapter all leave the caller with no list at all, never with a
 * partial one and never with a quantity we invented (element 10 RULE-8, element 13 RULE-7). A position the pick list does
 * not walk is not asked about, so a fully picked order needs the warehouse for nothing.
 *
 * <p>The two variants of element 10 are configuration, not code: {@code fulfillment.picklist.order} selects the walk order
 * and {@code fulfillment.picklist.split} the quantity split, each falling back to the documented default when the key is
 * absent or names no variant ({@code location-code}, {@code multi-location}).
 */
@Component
@Transactional
class PickListProjection {

    static final String ORDER_KEY = "fulfillment.picklist.order";
    static final String SPLIT_KEY = "fulfillment.picklist.split";

    private final OrderProcessingRepository processings;
    private final Optional<WarehouseStock> warehouse;
    private final Clock clock;
    private final PickListCalculation calculation;

    PickListProjection(OrderProcessingRepository processings, Optional<WarehouseStock> warehouse, Clock clock,
                       Environment configuration) {
        this.processings = processings;
        this.warehouse = warehouse;
        this.clock = clock;
        this.calculation = configuredCalculation(configuration);
    }

    /**
     * Generates one list for one processing, stamped with the instant it was read.
     *
     * @param processingId the processing to walk
     * @return the list of element 10, all or nothing
     */
    @Transactional(readOnly = true)
    public PickList findPickList(String processingId) {
        return findPickList(processings.get(processingId)
                .map(OrderFulfillmentProcess::toSnapshot)
                .orElseThrow(() -> new FulfillmentException(
                        FulfillmentException.Error.PROCESSING_NOT_FOUND, processingId)));
    }

    /** The same list for a caller that already holds the snapshot, so one request reads the aggregate once. */
    PickList findPickList(OrderProcessing processing) {
        return calculation.calculate(processing, stockAt(processing), Instant.now(clock));
    }

    /** One read per product still to be walked — a repeated product of two positions is one read. */
    private Map<ProductId, List<StockAtLocation>> stockAt(OrderProcessing processing) {
        Map<ProductId, List<StockAtLocation>> observations = new LinkedHashMap<>();
        processing.positions().stream()
                .filter(position -> position.state() == PositionState.PENDING)
                .map(OrderPosition::productNumber)
                .map(ProductId::new)
                .distinct()
                .forEach(product -> observations.put(product, answeringWarehouse().stockAt(product)));
        return observations;
    }

    /**
     * The calculation the deployment asked for: each dimension is selected by its own key, first match wins, and the
     * documented default is the fallback for a missing or unknown value (element 10, DEFAULT-1/DEFAULT-2).
     */
    private static PickListCalculation configuredCalculation(Environment configuration) {
        return new PickListCalculation(
                orderOf(configuration.getProperty(ORDER_KEY)),
                splitOf(configuration.getProperty(SPLIT_KEY)));
    }

    private static PickListCalculation.PickListOrder orderOf(String configured) {
        return "scarcity-first".equals(configured)
                ? PickListCalculation.PickListOrder.SCARCITY_FIRST
                : PickListCalculation.PickListOrder.LOCATION_CODE;
    }

    private static PickListCalculation.PickListSplit splitOf(String configured) {
        return "single-location".equals(configured)
                ? PickListCalculation.PickListSplit.SINGLE_LOCATION
                : PickListCalculation.PickListSplit.MULTI_LOCATION;
    }

    private WarehouseStock answeringWarehouse() {
        return warehouse.orElseThrow(() -> new FulfillmentException(
                FulfillmentException.Error.WAREHOUSE_NOT_WIRED, "reading the pick list"));
    }
}
