package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;

import java.util.List;

import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.PositionState;
import shop.fulfillment.kernel.ProcessingFixture;
import shop.fulfillment.kernel.ProcessingState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Elements 02 and 16 behind the two picking commands: one scan closes one position and starts the picking, a repeated scan
 * changes nothing, packing needs picked positions, and a hold refuses both.
 */
class ProcessingServiceTest {

    final FakeOrderProcessingRepository processings = new FakeOrderProcessingRepository();
    final ProcessingService work = new ProcessingService(processings);

    @Test
    void closesOnePositionByScanningIt() {
        processings.store(AggregateFixture.anAssignedProcessing());

        OrderProcessing scanned = work.pick(processingId(), OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE);

        assertThat(scanned.state()).isEqualTo(ProcessingState.PICKING);
        assertThat(scanned.positions()).singleElement()
                .satisfies(position -> assertThat(position.state()).isEqualTo(PositionState.PICKED));
    }

    @Test
    void answersARepeatedScanWithTheStateItAlreadyHad() {
        processings.store(AggregateFixture.anAssignedProcessing());
        work.pick(processingId(), OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE);

        OrderProcessing repeated = work.pick(processingId(), OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE);

        assertThat(repeated.state()).isEqualTo(ProcessingState.PICKING);
        assertThat(events()).filteredOn(DomainEvent.PositionPicked.class::isInstance).hasSize(1);
    }

    @Test
    void packsAPickedPositionAndPacksTheOrderWithItsLastPosition() {
        processings.store(AggregateFixture.aPickingProcessing());

        OrderProcessing packed = work.pack(processingId(), List.of(OrderFixture.PRODUCT_NUMBER), ProcessingFixture.EMPLOYEE);

        assertThat(packed.state()).isEqualTo(ProcessingState.PACKED);
        assertThat(packed.positions()).singleElement()
                .satisfies(position -> assertThat(position.state()).isEqualTo(PositionState.PACKED));
    }

    @Test
    void answersARepeatedPackWithTheStateItAlreadyHad() {
        processings.store(AggregateFixture.aPickingProcessing());
        work.pack(processingId(), List.of(OrderFixture.PRODUCT_NUMBER), ProcessingFixture.EMPLOYEE);

        OrderProcessing repeated = work.pack(processingId(), List.of(OrderFixture.PRODUCT_NUMBER),
                ProcessingFixture.EMPLOYEE);

        assertThat(repeated.state()).isEqualTo(ProcessingState.PACKED);
        assertThat(events()).filteredOn(DomainEvent.PositionPacked.class::isInstance).hasSize(1);
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

        assertThatThrownBy(() -> work.pack(processingId(), List.of(OrderFixture.PRODUCT_NUMBER), ProcessingFixture.EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.POSITION_NOT_PICKED));
    }

    @Test
    void refusesBothCommandsWhileTheOrderIsOnHold() {
        processings.store(AggregateFixture.aHeldProcessing());

        assertThatThrownBy(() -> work.pick(processingId(), OrderFixture.PRODUCT_NUMBER, ProcessingFixture.EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.WORK_IS_ON_HOLD));
        assertThatThrownBy(() -> work.pack(processingId(), List.of(OrderFixture.PRODUCT_NUMBER), ProcessingFixture.EMPLOYEE))
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
    }

    private List<DomainEvent> events() {
        return List.copyOf(processings.stored(ProcessingFixture.PROCESSING_ID).events);
    }

    private static String processingId() {
        return ProcessingFixture.PROCESSING_ID.value();
    }
}
