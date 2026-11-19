package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.LocationCode;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.PositionState;
import shop.fulfillment.kernel.ProductId;
import shop.fulfillment.kernel.ReservationId;
import shop.fulfillment.kernel.ProcessingFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Elements 02, 08 and 16 behind the three commands of the processing resource: one scan closes one position and starts the
 * picking — and registers the order's one parcel (register D11) — packing needs picked positions, and readiness declares the
 * order done with both of its effects, once.
 */
class ProcessingServiceTest {

    final FakeOrderProcessingRepository processings = new FakeOrderProcessingRepository();
    final FakeParcels parcels = new FakeParcels();
    final FakeProcessingReservations reservations = new FakeProcessingReservations();

    final ProcessingService work = new ProcessingService(processings, parcels,
            new WarehouseGoodsIssueIntegration(Optional.empty(), reservations),
            Clock.fixed(ProcessingFixture.REPORTED_AT, ZoneOffset.UTC));

    @Test
    void closesOnePositionByScanningIt() {
        processings.store(AggregateFixture.anAssignedProcessing());

        ProcessingSnapshot scanned = work.pick(processingId(), OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE);

        assertThat(scanned.state()).isEqualTo("picking");
        assertThat(scanned.positions()).singleElement()
                .satisfies(position -> assertThat(position.state()).isEqualTo(PositionState.PICKED.wireName()));
    }

    @Test
    void answersARepeatedScanWithTheStateItAlreadyHad() {
        processings.store(AggregateFixture.anAssignedProcessing());
        work.pick(processingId(), OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE);

        ProcessingSnapshot repeated = work.pick(processingId(), OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE);

        assertThat(repeated.state()).isEqualTo("picking");
        assertThat(events()).filteredOn(DomainEvent.PositionPicked.class::isInstance).hasSize(1);
    }

    @Test
    void registersTheOneParcelOnTheFirstScanAndOnlyThere() {
        processings.store(AggregateFixture.anAssignedProcessing());
        work.pick(processingId(), OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE);

        work.pick(processingId(), OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE);

        assertThat(parcels.registrations()).isEqualTo(1);
        assertThat(parcels.deliveries).containsExactly(OrderFixture.aDelivery());
    }

    @Test
    void servesTheRegisteredParcelOnEverySnapshot() {
        processings.store(AggregateFixture.anAssignedProcessing());

        ProcessingSnapshot scanned = work.pick(processingId(), OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE);

        assertThat(scanned.parcel()).isNotNull();
        assertThat(scanned.parcel().waybill()).isEqualTo(FakeParcels.WAYBILL.value());
        assertThat(scanned.parcel().legState()).isEqualTo("parcelRegistered");
    }

    @Test
    void packsAPickedPositionAndPacksTheOrderWithItsLastPosition() {
        processings.store(AggregateFixture.aPickingProcessing());

        ProcessingSnapshot packed = work.pack(processingId(), List.of(OrderFixture.PRODUCT_NUMBER),
                ProcessingFixture.EMPLOYEE);

        assertThat(packed.state()).isEqualTo("packed");
        assertThat(packed.positions()).singleElement()
                .satisfies(position -> assertThat(position.state()).isEqualTo(PositionState.PACKED.wireName()));
    }

    @Test
    void answersARepeatedPackWithTheStateItAlreadyHad() {
        processings.store(AggregateFixture.aPickingProcessing());
        work.pack(processingId(), List.of(OrderFixture.PRODUCT_NUMBER), ProcessingFixture.EMPLOYEE);

        ProcessingSnapshot repeated = work.pack(processingId(), List.of(OrderFixture.PRODUCT_NUMBER),
                ProcessingFixture.EMPLOYEE);

        assertThat(repeated.state()).isEqualTo("packed");
        assertThat(events()).filteredOn(DomainEvent.PositionPacked.class::isInstance).hasSize(1);
    }

    @Test
    void marksTheOrderReadyAndAsksTheCarrierToCollect() {
        parcels.register(ProcessingFixture.PROCESSING_ID, OrderFixture.aDelivery());
        processings.store(AggregateFixture.aPackedProcessing());

        ProcessingSnapshot ready = work.markReady(processingId());

        assertThat(ready.state()).isEqualTo("readyForDelivery");
        assertThat(parcels.collections).containsExactly(ProcessingFixture.PARCEL_ID.value());
        assertThat(events()).filteredOn(DomainEvent.ReadyForDeliveryConfirmed.class::isInstance).hasSize(1);
    }

    @Test
    void acceptsTheGrantTheDecisionAnsweredAndAnswersTheParcelItLeftBehind() {
        parcels.register(ProcessingFixture.PROCESSING_ID, OrderFixture.aDelivery());
        processings.store(AggregateFixture.aPackedProcessing());

        ProcessingSnapshot ready = work.markReady(processingId());

        assertThat(ready.state()).isEqualTo("readyForDelivery");
        assertThat(ready.parcel().legState()).isEqualTo("parcelReady");
    }

    @Test
    void asksTheCarrierOnceWhenReadinessIsReplayed() {
        parcels.register(ProcessingFixture.PROCESSING_ID, OrderFixture.aDelivery());
        processings.store(AggregateFixture.aPackedProcessing());
        work.markReady(processingId());

        ProcessingSnapshot repeated = work.markReady(processingId());

        assertThat(repeated.state()).isEqualTo("readyForDelivery");
        assertThat(parcels.collections).containsExactly(ProcessingFixture.PARCEL_ID.value());
        assertThat(events()).filteredOn(DomainEvent.ReadyForDeliveryConfirmed.class::isInstance).hasSize(1);
    }

    @Test
    void refusesReadinessWithoutAParcelAndCollectsNothing() {
        processings.store(AggregateFixture.aPackedProcessing());

        assertThatThrownBy(() -> work.markReady(processingId()))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> {
                    assertThat(refused.error()).isEqualTo(FulfillmentException.Error.READINESS_REFUSED);
                    assertThat(refused.details()).contains(List.of("noParcel"));
                });
        assertThat(parcels.collections).isEmpty();
    }

    @Test
    void refusesReadinessWhileAPositionIsNotPacked() {
        parcels.register(ProcessingFixture.PROCESSING_ID, OrderFixture.aDelivery());
        processings.store(AggregateFixture.aPickingProcessing());

        assertThatThrownBy(() -> work.markReady(processingId()))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> {
                    assertThat(refused.error()).isEqualTo(FulfillmentException.Error.READINESS_REFUSED);
                    assertThat(refused.details()).contains(List.of("notPacked"));
                });
        assertThat(parcels.collections).isEmpty();
    }

    @Test
    void refusesReadinessWhileTheOrderIsOnHold() {
        parcels.register(ProcessingFixture.PROCESSING_ID, OrderFixture.aDelivery());
        processings.store(AggregateFixture.aHeldProcessing());

        assertThatThrownBy(() -> work.markReady(processingId()))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.details())
                        .contains(List.of("onHold")));
    }

    @Test
    void refusesAnEmployeeWhoDoesNotHoldTheOrder() {
        processings.store(AggregateFixture.anAssignedProcessing());

        assertThatThrownBy(() -> work.pick(processingId(), OrderFixture.PRODUCT_NUMBER,
                ProcessingFixture.OTHER_EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.ACTOR_NOT_ALLOWED));
        assertThatThrownBy(() -> work.pack(processingId(), List.of(OrderFixture.PRODUCT_NUMBER),
                ProcessingFixture.OTHER_EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.ACTOR_NOT_ALLOWED));
    }

    @Test
    void refusesToPackAPositionThatWasNotPicked() {
        processings.store(AggregateFixture.anAssignedProcessing());

        assertThatThrownBy(() -> work.pack(processingId(), List.of(OrderFixture.PRODUCT_NUMBER),
                ProcessingFixture.EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.POSITION_NOT_PICKED));
    }

    @Test
    void refusesBothCommandsWhileTheOrderIsOnHold() {
        processings.store(AggregateFixture.aHeldProcessing());

        assertThatThrownBy(() -> work.pick(processingId(), OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.WORK_IS_ON_HOLD));
        assertThatThrownBy(() -> work.pack(processingId(), List.of(OrderFixture.PRODUCT_NUMBER),
                ProcessingFixture.EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.WORK_IS_ON_HOLD));
    }

    @Test
    void refusesACommandOnAnOrderNobodyWasAssigned() {
        processings.store(AggregateFixture.aPendingProcessing());

        assertThatThrownBy(() -> work.pick(processingId(), OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.WORK_NOT_STARTED));
    }

    @Test
    void refusesAPositionTheOrderDoesNotHave() {
        processings.store(AggregateFixture.anAssignedProcessing());

        assertThatThrownBy(() -> work.pick(processingId(), "40404", ProcessingFixture.EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.POSITION_UNKNOWN));
    }

    @Test
    void answersAnUnknownProcessingWithItsOwnRefusal() {
        assertThatThrownBy(() -> work.pick("OP-2026-9999", OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.PROCESSING_NOT_FOUND));
        assertThatThrownBy(() -> work.markReady("OP-2026-9999"))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.PROCESSING_NOT_FOUND));
    }

    private List<DomainEvent> events() {
        return List.copyOf(processings.stored(ProcessingFixture.PROCESSING_ID).events);
    }

    private static String processingId() {
        return ProcessingFixture.PROCESSING_ID.value();
    }
}
