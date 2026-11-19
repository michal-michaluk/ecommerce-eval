package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.List;

import shop.fulfillment.HttpFixture;
import shop.fulfillment.IntegrationTest;
import shop.fulfillment.RecordedEvents;
import shop.fulfillment.kernel.CarrierPort;
import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.Leg;
import shop.fulfillment.kernel.ParcelId;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.WarehouseStock;
import shop.fulfillment.parcel.FakeCarrierPort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The consumed edge of element 02 end to end: the {@code delivery state notification} the parcel slice publishes reaches the
 * order, and the order answers {@code delivered} — so the state element 18 binds is reachable through the lane and not only
 * through the aggregate.
 */
@IntegrationTest
class DeliveryStateWiringTest {

    @Autowired
    OrderIntakeService orders;
    @Autowired
    AssignmentService assignments;
    @Autowired
    ProcessingService work;
    @Autowired
    ProcessingSnapshotProjection snapshots;
    @Autowired
    ApplicationEventPublisher publisher;
    @Autowired
    RecordedEvents recorded;

    @Test
    void closesTheOrderWhenTheCarrierReportsTheLastLeg() {
        String processingId = readyProcessing();
        ParcelId parcelId = registeredParcel();

        publisher.publishEvent(ProcessingFixture.aLegNotified(parcelId, Leg.L3));
        assertThat(snapshots.findProcessing(processingId).state()).isEqualTo("readyForDelivery");

        publisher.publishEvent(ProcessingFixture.aLegNotified(parcelId, Leg.L6));

        assertThat(snapshots.findProcessing(processingId).state()).isEqualTo("delivered");
    }

    /** The parcel this order carries, as the readiness command itself recorded it. */
    private ParcelId registeredParcel() {
        return recorded.of(DomainEvent.ReadyForDeliveryConfirmed.class).get(0).parcelId();
    }

    private String readyProcessing() {
        String processingId = orders.accept(OrderHandoverFixture.aHandover()).processing().processingId().value();
        assignments.claim(processingId, HttpFixture.EMPLOYEE);
        work.pick(processingId, "75423", HttpFixture.EMPLOYEE);
        work.pack(processingId, List.of("75423"), HttpFixture.EMPLOYEE);
        work.markReady(processingId);
        return processingId;
    }

    @TestConfiguration
    static class CarrierStub {

        @Bean
        CarrierPort carrierPort() {
            return new FakeCarrierPort();
        }
    }

    @TestConfiguration
    static class WarehouseStub {

        @Bean
        WarehouseStock warehouseStock() {
            return new FakeWarehouseStock();
        }
    }
}
