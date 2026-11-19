package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.OrderPosition;
import shop.fulfillment.kernel.PositionState;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.ProcessingState;
import shop.fulfillment.kernel.ProductId;
import shop.fulfillment.kernel.StockAtLocation;
import shop.fulfillment.kernel.WarehouseFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Element 10's calculation: one stop per (position, location), the walk ordered by the only key the warehouse gives us, a
 * shortfall for what the shelf cannot cover, and no list at all when the warehouse answered nothing about a product.
 */
class PickListCalculationTest {

    static final Instant GENERATED_AT = Instant.parse("2026-11-19T12:00:00Z");
    static final String ORCHID = "99881";

    @Test
    void walksThePendingPositionsInLocationOrder() {
        PickList pickList = calculate(ProcessingFixture.aPosition(ORCHID, 6),
                List.of(WarehouseFixture.stockAt("B-02-02", 5), WarehouseFixture.stockAt("A-01-01", 5)));

        assertThat(pickList.stops()).extracting(stop -> stop.location().value())
                .containsExactly("A-01-01", "B-02-02");
    }

    @Test
    void numbersTheWalkFromOne() {
        PickList pickList = calculate(ProcessingFixture.aPosition(ORCHID, 6),
                List.of(WarehouseFixture.stockAt("B-02-02", 5), WarehouseFixture.stockAt("A-01-01", 5)));

        assertThat(pickList.stops()).extracting(PickList.Stop::walkOrder).containsExactly(1, 2);
    }

    @Test
    void splitsAQuantityAcrossThePlacesThatHoldIt() {
        PickList pickList = calculate(ProcessingFixture.aPosition(ORCHID, 4),
                List.of(WarehouseFixture.stockAt("A-01-01", 1), WarehouseFixture.stockAt("B-02-02", 3)));

        assertThat(pickList.stops()).extracting(PickList.Stop::quantityToTake).containsExactly(1, 3);
    }

    @Test
    void takesEverythingFromOnePlaceWhenTheVariantSaysSo() {
        PickListCalculation singleLocation = new PickListCalculation(
                PickListCalculation.PickListOrder.LOCATION_CODE, PickListCalculation.PickListSplit.SINGLE_LOCATION);

        PickList pickList = singleLocation.calculate(
                processing(ProcessingFixture.aPosition(ORCHID, 4)),
                stock(ORCHID, List.of(WarehouseFixture.stockAt("A-01-01", 1), WarehouseFixture.stockAt("B-02-02", 5))),
                GENERATED_AT);

        assertThat(pickList.stops()).singleElement()
                .satisfies(stop -> assertThat(stop.quantityToTake()).isEqualTo(4));
    }

    @Test
    void walksNothingWhenNoSinglePlaceHoldsTheWholeQuantity() {
        PickListCalculation singleLocation = new PickListCalculation(
                PickListCalculation.PickListOrder.LOCATION_CODE, PickListCalculation.PickListSplit.SINGLE_LOCATION);

        PickList pickList = singleLocation.calculate(
                processing(ProcessingFixture.aPosition(ORCHID, 4)),
                stock(ORCHID, List.of(WarehouseFixture.stockAt("A-01-01", 1))),
                GENERATED_AT);

        assertThat(pickList.stops()).isEmpty();
        assertThat(pickList.shortfalls()).singleElement()
                .satisfies(shortfall -> assertThat(shortfall.missingQuantity()).isEqualTo(4));
    }

    @Test
    void namesWhatTheShelvesCannotCover() {
        PickList pickList = calculate(ProcessingFixture.aPosition(ORCHID, 4),
                List.of(WarehouseFixture.stockAt("A-01-01", 1)));

        assertThat(pickList.shortfalls()).singleElement()
                .satisfies(shortfall -> {
                    assertThat(shortfall.productNumber()).isEqualTo(ORCHID);
                    assertThat(shortfall.missingQuantity()).isEqualTo(3);
                });
    }

    @Test
    void walksNoPositionThatIsAlreadyConfirmed() {
        OrderPosition packed = ProcessingFixture.aPosition(ORCHID, 1).picked().packed();

        PickList pickList = PickListCalculation.defaultRules().calculate(
                processing(packed), stock(ORCHID, List.of(WarehouseFixture.stockAt("A-01-01", 5))), GENERATED_AT);

        assertThat(pickList.stops()).isEmpty();
        assertThat(pickList.shortfalls()).isEmpty();
    }

    @Test
    void stampsTheListWithTheInstantItWasGenerated() {
        assertThat(calculate(ProcessingFixture.aPosition(ORCHID, 1),
                List.of(WarehouseFixture.stockAt("A-01-01", 5))).generatedAt())
                .isEqualTo(GENERATED_AT);
    }

    @Test
    void refusesToInventStockForAProductTheWarehouseDidNotAnswerFor() {
        PickListCalculation calculation = PickListCalculation.defaultRules();
        OrderProcessing processing = processing(ProcessingFixture.aPosition(ORCHID, 1));

        assertThatThrownBy(() -> calculation.calculate(processing, Map.of(), GENERATED_AT))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.STOCK_OBSERVATION_MISSING));
    }

    @Test
    void putsTheLastUnitsFirstWhenTheScarcityVariantIsSelected() {
        PickListCalculation scarcityFirst = new PickListCalculation(
                PickListCalculation.PickListOrder.SCARCITY_FIRST, PickListCalculation.PickListSplit.MULTI_LOCATION);
        Map<ProductId, List<StockAtLocation>> stock = new LinkedHashMap<>();
        stock.put(new ProductId(ORCHID), List.of(WarehouseFixture.stockAt("A-01-01", 10)));
        stock.put(new ProductId(OrderFixture.PRODUCT_NUMBER), List.of(WarehouseFixture.stockAt("B-02-02", 1)));

        PickList pickList = scarcityFirst.calculate(
                processing(ProcessingFixture.aPosition(ORCHID, 1), ProcessingFixture.aPosition()), stock, GENERATED_AT);

        assertThat(pickList.stops()).first()
                .satisfies(stop -> assertThat(stop.location().value()).isEqualTo("B-02-02"));
    }

    @Test
    void namesTheProductAndThePositionStateOnEveryStop() {
        PickList pickList = calculate(ProcessingFixture.aPosition(ORCHID, 1),
                List.of(WarehouseFixture.stockAt("A-01-01", 5)));

        assertThat(pickList.stops()).singleElement().satisfies(stop -> {
            assertThat(stop.productNumber()).isEqualTo(ORCHID);
            assertThat(stop.name()).isEqualTo("LEGO " + ORCHID);
            assertThat(stop.positionState()).isEqualTo(PositionState.PENDING);
        });
    }

    private static PickList calculate(OrderPosition position, List<StockAtLocation> places) {
        return PickListCalculation.defaultRules().calculate(
                processing(position), stock(position.productNumber(), places), GENERATED_AT);
    }

    private static OrderProcessing processing(OrderPosition... positions) {
        return OrderProcessing.builder()
                .processingId(ProcessingFixture.PROCESSING_ID)
                .state(ProcessingState.PICKING)
                .positions(List.of(positions))
                .issues(List.of())
                .build();
    }

    private static Map<ProductId, List<StockAtLocation>> stock(String productNumber, List<StockAtLocation> places) {
        Map<ProductId, List<StockAtLocation>> stock = new LinkedHashMap<>();
        stock.put(new ProductId(productNumber), places);
        return stock;
    }
}
