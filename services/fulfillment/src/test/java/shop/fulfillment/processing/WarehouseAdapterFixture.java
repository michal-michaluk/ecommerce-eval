package shop.fulfillment.processing;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import shop.fulfillment.kernel.ReservationId;
import shop.fulfillment.kernel.WarehouseStock;

/**
 * The warehouse boundary's collaborators, built where a test is allowed to build a wide constructor: the adapter with its
 * stubbed HTTP layer, the pick list's query handler and the stock-change job, over the two owned-state stores.
 */
public class WarehouseAdapterFixture {

    private WarehouseAdapterFixture() {
    }

    public static InvenTreeWarehouseClient.Settings settings() {
        return settings(10);
    }

    public static InvenTreeWarehouseClient.Settings settings(int pageSize) {
        return new InvenTreeWarehouseClient.Settings("http://inventree.test", "t0ken", pageSize);
    }

    public static InvenTreeWarehouseClient invenTree(RestClient http, InvenTreeWarehouseClient.Settings settings,
                                                     WarehouseReservationRepository reservations, Clock clock) {
        return new InvenTreeWarehouseClient(http, settings, reservations, clock);
    }

    public static PickListProjection pickList(OrderProcessingRepository processings, Optional<WarehouseStock> stock,
                                              Clock clock) {
        return pickList(processings, stock, clock, new MockEnvironment());
    }

    public static PickListProjection pickList(OrderProcessingRepository processings, Optional<WarehouseStock> stock,
                                              Clock clock, Environment configuration) {
        return new PickListProjection(processings, stock, clock, configuration);
    }

    public static StockChangePollingIntegration stockChanges(Optional<WarehouseStock> stock,
                                                             StockCursorRepository cursors,
                                                             ApplicationEventPublisher publisher, Clock clock) {
        return new StockChangePollingIntegration(stock, cursors, publisher, clock);
    }

    public static WarehouseReservation aReservation(int orderPk, Integer... allocationPks) {
        return WarehouseReservation.builder()
                .reservationId(new ReservationId("RSV-0001"))
                .orderPk(orderPk)
                .allocationPks(List.of(allocationPks))
                .build();
    }
}
