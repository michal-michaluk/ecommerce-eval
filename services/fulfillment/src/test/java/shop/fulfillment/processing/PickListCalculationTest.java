package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.OrderPosition;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.ProcessingState;
import shop.fulfillment.kernel.ProductId;
import shop.fulfillment.kernel.StockAtLocation;
import shop.fulfillment.kernel.WarehouseFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Element 10's calculation: one line per pending position, one stop per (position, location) numbered across the whole
 * walk, a shortfall for what the shelf cannot cover, and no list at all when the warehouse answered nothing about a
 * product.
 */
class PickListCalculationTest {

    static final Instant GENERATED_AT = Instant.parse("2026-11-19T12:00:00Z");
    static final String ORCHID = "99881";

    @Test
    void listsOneLinePerPendingPosition() {
        PickList pickList = calculate(ProcessingFixture.aPosition(ORCHID, 6),
                List.of(WarehouseFixture.stockAt("B-02-02", 5), WarehouseFixture.stockAt("A-01-01", 5)));

        assertThat(pickList.lines()).singleElement()
                .satisfies(line -> assertThat(line.productNumber()).isEqualTo(ORCHID));
    }

    @Test
    void walksTheStopsOfALineInLocationOrder() {
        PickList pickList = calculate(ProcessingFixture.aPosition(ORCHID, 6),
                List.of(WarehouseFixture.stockAt("B-02-02", 5), WarehouseFixture.stockAt("A-01-01", 5)));

        assertThat(stopsOf(pickList)).extracting(PickList.Stop::location)
                .containsExactly("A-01-01", "B-02-02");
    }

    @Test
    void numbersTheWalkFromOne() {
        PickList pickList = calculate(ProcessingFixture.aPosition(ORCHID, 6),
                List.of(WarehouseFixture.stockAt("B-02-02", 5), WarehouseFixture.stockAt("A-01-01", 5)));

        assertThat(stopsOf(pickList)).extracting(PickList.Stop::walkOrder).containsExactly(1, 2);
    }

    @Test
    void splitsAQuantityAcrossThePlacesThatHoldIt() {
        PickList pickList = calculate(ProcessingFixture.aPosition(ORCHID, 4),
                List.of(WarehouseFixture.stockAt("A-01-01", 1), WarehouseFixture.stockAt("B-02-02", 3)));

        assertThat(stopsOf(pickList)).extracting(PickList.Stop::quantityToTake).containsExactly(1, 3);
    }

    @Test
    void takesEverythingFromOnePlaceWhenTheVariantSaysSo() {
        PickListCalculation singleLocation = new PickListCalculation(
                PickListCalculation.PickListOrder.LOCATION_CODE, PickListCalculation.PickListSplit.SINGLE_LOCATION);

        PickList pickList = singleLocation.calculate(
                processing(ProcessingFixture.aPosition(ORCHID, 4)),
                stock(ORCHID, List.of(WarehouseFixture.stockAt("A-01-01", 1), WarehouseFixture.stockAt("B-02-02", 5))),
                GENERATED_AT);

        assertThat(stopsOf(pickList)).singleElement()
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

        assertThat(pickList.lines()).singleElement()
                .satisfies(line -> assertThat(line.locations()).isEmpty());
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

        assertThat(pickList.lines()).isEmpty();
        assertThat(pickList.shortfalls()).isEmpty();
    }

    @Test
    void stampsTheListWithTheInstantItWasGenerated() {
        assertThat(calculate(ProcessingFixture.aPosition(ORCHID, 1),
                List.of(WarehouseFixture.stockAt("A-01-01", 5))).generatedAt())
                .isEqualTo(GENERATED_AT);
    }

    @Test
    void walksNoLocationThatHasNothingFree() {
        PickList pickList = calculate(ProcessingFixture.aPosition(ORCHID, 2),
                List.of(WarehouseFixture.stock("A-01-01", 0, 3)));

        assertThat(pickList.lines()).singleElement()
                .satisfies(line -> assertThat(line.locations()).isEmpty());
        assertThat(pickList.shortfalls()).singleElement()
                .satisfies(shortfall -> assertThat(shortfall.missingQuantity()).isEqualTo(2));
    }

    @Test
    void informsTheStopWithTheAllocatedUnitsWithoutGatingOnThem() {
        PickList pickList = calculate(ProcessingFixture.aPosition(ORCHID, 1),
                List.of(WarehouseFixture.stock("A-01-01", 3, 1)));

        assertThat(stopsOf(pickList)).singleElement().satisfies(stop -> {
            assertThat(stop.available()).isEqualTo(3);
            assertThat(stop.reserved()).isEqualTo(1);
            assertThat(stop.quantityToTake()).isEqualTo(1);
        });
    }

    @Test
    void namesThePositionItWalks() {
        PickList pickList = calculate(ProcessingFixture.aPosition(ORCHID, 2),
                List.of(WarehouseFixture.stockAt("A-01-01", 5)));

        assertThat(pickList.processingId()).isEqualTo(ProcessingFixture.PROCESSING_ID.value());
        assertThat(pickList.lines()).singleElement().satisfies(line -> {
            assertThat(line.name()).isEqualTo("LEGO " + ORCHID);
            assertThat(line.quantity()).isEqualTo(2);
            assertThat(line.state()).isEqualTo("awaitingScan");
        });
    }

    @Test
    void numbersOneWalkAcrossTwoLines() {
        PickList pickList = PickListCalculation.defaultRules().calculate(
                processing(ProcessingFixture.aPosition(OrderFixture.PRODUCT_NUMBER, 1),
                        ProcessingFixture.aPosition(ORCHID, 1)),
                twoProducts(List.of(WarehouseFixture.stockAt("A-01-01", 2)),
                        List.of(WarehouseFixture.stockAt("B-02-02", 2))),
                GENERATED_AT);

        assertThat(pickList.lines()).extracting(PickList.PickListLine::productNumber)
                .containsExactly(OrderFixture.PRODUCT_NUMBER, ORCHID);
        assertThat(stopsOf(pickList)).extracting(PickList.Stop::walkOrder).containsExactly(1, 2);
    }

    @Test
    void keepsTwoProductsAtOneShelfInTwoStops() {
        PickList pickList = PickListCalculation.defaultRules().calculate(
                processing(ProcessingFixture.aPosition(OrderFixture.PRODUCT_NUMBER, 1),
                        ProcessingFixture.aPosition(ORCHID, 1)),
                twoProducts(List.of(WarehouseFixture.stockAt("A-01-01", 2)),
                        List.of(WarehouseFixture.stockAt("A-01-01", 2))),
                GENERATED_AT);

        assertThat(stopsOf(pickList)).hasSize(2);
        assertThat(stopsOf(pickList)).extracting(PickList.Stop::productNumber)
                .containsExactly(OrderFixture.PRODUCT_NUMBER, ORCHID);
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

        assertThat(stopsOf(pickList)).first()
                .satisfies(stop -> assertThat(stop.location()).isEqualTo("B-02-02"));
    }

    private static List<PickList.Stop> stopsOf(PickList pickList) {
        return pickList.lines().stream()
                .flatMap(line -> line.locations().stream())
                .sorted((first, second) -> Integer.compare(first.walkOrder(), second.walkOrder()))
                .toList();
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

    private static Map<ProductId, List<StockAtLocation>> twoProducts(
            List<StockAtLocation> first, List<StockAtLocation> second) {
        Map<ProductId, List<StockAtLocation>> stock = stock(OrderFixture.PRODUCT_NUMBER, first);
        stock.put(new ProductId(ORCHID), second);
        return stock;
    }
}
