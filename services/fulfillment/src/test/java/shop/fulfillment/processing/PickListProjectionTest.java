package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.IssueNote;
import shop.fulfillment.kernel.LocationCode;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.PickedItem;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.ProductId;
import shop.fulfillment.kernel.ReservationId;
import shop.fulfillment.kernel.StockAtLocation;
import shop.fulfillment.kernel.StockChangePage;
import shop.fulfillment.kernel.StockCursor;
import shop.fulfillment.kernel.WarehouseFixture;
import shop.fulfillment.kernel.WarehouseStock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Element 10's query handler: one warehouse read per position still to be walked, the pure calculation behind it, and no
 * list at all when the warehouse fails, answers nothing or is not wired.
 */
class PickListProjectionTest {

    static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    final FakeOrderProcessingRepository processings = new FakeOrderProcessingRepository();
    final FakeWarehouseStock warehouse = new FakeWarehouseStock();

    @Test
    void readsTheStockOfEveryPositionStillToWalk() {
        processings.store(AggregateFixture.pendingProcessingOf(OrderFixture.twoLineOrder()));

        PickList pickList = projection(warehouse).findPickList(processingId());

        assertThat(warehouse.calls).containsExactly("stockAt " + OrderFixture.PRODUCT_NUMBER, "stockAt 99881");
        assertThat(pickList.lines()).hasSize(2);
        assertThat(pickList.lines()).first()
                .satisfies(line -> assertThat(line.locations()).isNotEmpty());
    }

    @Test
    void stampsTheListWithTheInjectedClock() {
        processings.store(AggregateFixture.anAssignedProcessing());

        assertThat(projection(warehouse).findPickList(processingId()).generatedAt()).isEqualTo(NOW);
    }

    @Test
    void asksTheWarehouseAboutNothingOnceEveryPositionIsPicked() {
        processings.store(AggregateFixture.aPickingProcessing());

        PickList pickList = projection(warehouse).findPickList(processingId());

        assertThat(pickList.lines()).isEmpty();
        assertThat(warehouse.calls).isEmpty();
    }

    @Test
    void refusesToServeAListWhenAProductHasNoObservation() {
        processings.store(AggregateFixture.anAssignedProcessing());

        assertThatThrownBy(() -> projection(new AnsweringNothing()).findPickList(processingId()))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.STOCK_OBSERVATION_MISSING));
    }

    @Test
    void surfacesAWarehouseFailureInsteadOfAPartialList() {
        processings.store(AggregateFixture.anAssignedProcessing());

        assertThatThrownBy(() -> projection(new Failing()).findPickList(processingId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void splitsAQuantityAcrossLocationsByDefault() {
        processings.store(AggregateFixture.pendingProcessingOf(OrderFixture.orderWithQuantity(3)));

        PickList pickList = projection(new OneProductTwoShelves()).findPickList(processingId());

        assertThat(pickList.lines()).singleElement()
                .satisfies(line -> assertThat(line.locations()).hasSize(2));
    }

    @Test
    void walksOneLocationWhenTheDeploymentConfiguredTheNoSplitVariant() {
        processings.store(AggregateFixture.pendingProcessingOf(OrderFixture.orderWithQuantity(3)));
        MockEnvironment configuration = new MockEnvironment()
                .withProperty("fulfillment.picklist.split", "single-location");

        PickList pickList = WarehouseAdapterFixture.pickList(
                processings, Optional.of(new OneProductTwoShelves()), CLOCK, configuration)
                .findPickList(processingId());

        assertThat(pickList.lines()).singleElement().satisfies(line -> {
            assertThat(line.locations()).singleElement().satisfies(stop -> {
                assertThat(stop.location()).isEqualTo("B-02-02");
                assertThat(stop.quantityToTake()).isEqualTo(3);
            });
            assertThat(pickList.shortfalls()).isEmpty();
        });
    }

    @Test
    void walksTheScarcestLocationFirstWhenTheDeploymentConfiguredThatVariant() {
        processings.store(AggregateFixture.pendingProcessingOf(OrderFixture.twoLineOrder()));
        MockEnvironment configuration = new MockEnvironment()
                .withProperty("fulfillment.picklist.order", "scarcity-first");

        PickList pickList = WarehouseAdapterFixture.pickList(
                processings, Optional.of(new TwoProducts()), CLOCK, configuration)
                .findPickList(processingId());

        assertThat(pickList.lines()).first()
                .satisfies(line -> assertThat(line.locations()).singleElement()
                        .satisfies(stop -> assertThat(stop.walkOrder()).isEqualTo(2)));
        assertThat(pickList.lines()).last()
                .satisfies(line -> assertThat(line.locations()).singleElement()
                        .satisfies(stop -> assertThat(stop.walkOrder()).isEqualTo(1)));
    }

    @Test
    void refusesAListWhenNoWarehouseAdapterIsWired() {
        processings.store(AggregateFixture.anAssignedProcessing());

        assertThatThrownBy(() -> projection(Optional.empty()).findPickList(processingId()))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.WAREHOUSE_NOT_WIRED));
    }

    @Test
    void answersAnUnknownProcessingWithItsOwnRefusal() {
        assertThatThrownBy(() -> projection(warehouse).findPickList("OP-2026-9999"))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.PROCESSING_NOT_FOUND));
    }

    private PickListProjection projection(WarehouseStock stock) {
        return projection(Optional.of(stock));
    }

    private PickListProjection projection(Optional<WarehouseStock> stock) {
        return WarehouseAdapterFixture.pickList(processings, stock, CLOCK);
    }

    private static String processingId() {
        return ProcessingFixture.PROCESSING_ID.value();
    }

    /** One product on two shelves: a single location cannot cover three units, both together can. */
    static class OneProductTwoShelves extends FakeWarehouseStock {

        @Override
        public List<StockAtLocation> stockAt(ProductId productId) {
            return List.of(WarehouseFixture.stock("A-01-01", 1, 0), WarehouseFixture.stock("B-02-02", 5, 0));
        }
    }

    /** Two products, one shelf each: the second one holds exactly the units its position asks for. */
    static class TwoProducts extends FakeWarehouseStock {

        @Override
        public List<StockAtLocation> stockAt(ProductId productId) {
            return OrderFixture.PRODUCT_NUMBER.equals(productId.value())
                    ? List.of(WarehouseFixture.stock("A-01-01", 10, 0))
                    : List.of(WarehouseFixture.stock("B-02-02", 3, 0));
        }
    }

    /** A warehouse that answers no observation at all for a product — a protocol failure, never "no stock". */
    static class AnsweringNothing extends FakeWarehouseStock {

        @Override
        public List<StockAtLocation> stockAt(ProductId productId) {
            return null;
        }
    }

    /** A warehouse that cannot be reached. */
    static class Failing implements WarehouseStock {

        @Override
        public List<StockAtLocation> stockAt(ProductId productId) {
            throw new IllegalStateException("warehouse unreachable");
        }

        @Override
        public ReservationId reserve(ProductId productId, LocationCode location, int quantity) {
            throw new IllegalStateException("warehouse unreachable");
        }

        @Override
        public void release(ReservationId reservationId) {
            throw new IllegalStateException("warehouse unreachable");
        }

        @Override
        public IssueNote issue(ReservationId reservationId, List<PickedItem> picked) {
            throw new IllegalStateException("warehouse unreachable");
        }

        @Override
        public StockChangePage stockChanges(StockCursor cursor) {
            throw new IllegalStateException("warehouse unreachable");
        }
    }
}
