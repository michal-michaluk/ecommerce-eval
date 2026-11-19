package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.StockChangePage;
import shop.fulfillment.kernel.StockCursor;
import shop.fulfillment.kernel.WarehouseFixture;
import shop.fulfillment.kernel.WarehouseStock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Element 13's stock-change read as one job: the page after the stored watermark, one in-process {@code stock changed} per
 * observation, the watermark moved only after the page was published, and nothing at all without a warehouse.
 *
 * <p>It is a unit test and not an {@code @IntegrationTest}: the job holds nothing but the port and the watermark, so the two
 * doubles are the whole world it needs.
 */
class StockChangePollingTest {

    static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final StockCursor STORED = new StockCursor("2026-11-19T10:00:00Z");
    static final StockCursor NEXT = new StockCursor("2026-11-19T11:30:00Z");

    final FakeWarehouseState state = new FakeWarehouseState();
    final FakeWarehouseStock warehouse = new FakeWarehouseStock();
    final List<Object> published = new ArrayList<>();
    final ApplicationEventPublisher publisher = published::add;

    @Test
    void readsAfterTheStoredWatermarkAndRaisesOneEventPerObservation() {
        state.write(STORED);
        warehouse.withStockChanges(new StockChangePage(NEXT, List.of(WarehouseFixture.aStockChange())));

        poller(Optional.of(warehouse)).poll();

        assertThat(warehouse.calls).containsExactly("stockChanges " + STORED.value());
        assertThat(published).singleElement()
                .isInstanceOfSatisfying(DomainEvent.StockChanged.class, change -> {
                    assertThat(change.productId().value()).isEqualTo(OrderFixture.PRODUCT_NUMBER);
                    assertThat(change.available()).isEqualTo(10);
                    assertThat(change.reserved()).isEqualTo(2);
                });
        assertThat(state.cursor).isEqualTo(NEXT);
    }

    @Test
    void startsReadingAtTheMomentOfTheFirstPoll() {
        warehouse.withStockChanges(new StockChangePage(STORED, List.of()));

        poller(Optional.of(warehouse)).poll();

        assertThat(warehouse.calls).containsExactly("stockChanges " + NOW);
        assertThat(state.cursor).isEqualTo(STORED);
    }

    @Test
    void raisesOneEventForAnObservationThatArrivedTwiceInOnePage() {
        state.write(STORED);
        warehouse.withStockChanges(new StockChangePage(NEXT,
                List.of(WarehouseFixture.aStockChange(), WarehouseFixture.aStockChange())));

        poller(Optional.of(warehouse)).poll();

        assertThat(published).hasSize(1);
        assertThat(state.cursor).isEqualTo(NEXT);
    }

    @Test
    void doesNothingAtAllWithoutAWarehouse() {
        poller(Optional.empty()).poll();

        assertThat(warehouse.calls).isEmpty();
        assertThat(published).isEmpty();
        assertThat(state.cursor).isNull();
    }

    @Test
    void leavesTheWatermarkWhereItWasWhenTheReadFails() {
        state.write(STORED);

        assertThatThrownBy(() -> failingPoller().poll()).isInstanceOf(IllegalStateException.class);
        assertThat(state.cursor).isEqualTo(STORED);
    }

    private StockChangePollingIntegration poller(Optional<WarehouseStock> stock) {
        return WarehouseAdapterFixture.stockChanges(stock, state, publisher, CLOCK);
    }

    private StockChangePollingIntegration failingPoller() {
        return poller(Optional.of(new PickListProjectionTest.Failing()));
    }
}
