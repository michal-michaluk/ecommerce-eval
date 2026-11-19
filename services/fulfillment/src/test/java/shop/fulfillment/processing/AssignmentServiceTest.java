package shop.fulfillment.processing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.ProcessingFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Element 07 with register D11: the guard decides, the service applies, and a granted claim reserves every position at the
 * warehouse — once, never for a repeat, and never leaving an allocation behind when the claim cannot be committed.
 */
class AssignmentServiceTest {

    final FakeOrderProcessingRepository processings = new FakeOrderProcessingRepository();
    final FakeWarehouseStock warehouse = new FakeWarehouseStock();
    final FakeProcessingReservations reservations = new FakeProcessingReservations();

    final AssignmentService assignments = assignmentsWith(Optional.of(warehouse));

    @BeforeEach
    void onePendingOrder() {
        processings.store(AggregateFixture.aPendingProcessing());
    }

    @Test
    void grantsTheOrderToTheClaimer() {
        OrderClaim claim = assignments.claim(ProcessingFixture.PROCESSING_ID.value(), ProcessingFixture.EMPLOYEE);

        assertThat(claim.granted()).isTrue();
        assertThat(claim.processing().assignee()).isEqualTo(ProcessingFixture.EMPLOYEE);
        assertThat(claim.processing().state().wireName()).isEqualTo("assigned");
    }

    @Test
    void reservesEveryPositionProductOnceOnAGrantedClaim() {
        assignments.claim(ProcessingFixture.PROCESSING_ID.value(), ProcessingFixture.EMPLOYEE);

        assertThat(warehouse.allocated).containsExactly(Map.entry("75423", 1));
    }

    @Test
    void reservesThePositionQuantityNotOneUnit() {
        processings.store(AggregateFixture.pendingProcessingOf(OrderFixture.orderWithQuantity(3)));

        assignments.claim(ProcessingFixture.PROCESSING_ID.value(), ProcessingFixture.EMPLOYEE);

        assertThat(warehouse.allocated).containsEntry("75423", 3);
    }

    @Test
    void reservesOneAllocationPerPositionOfAMultiPositionOrder() {
        processings.store(AggregateFixture.pendingProcessingOf(OrderFixture.twoLineOrder()));

        assignments.claim(ProcessingFixture.PROCESSING_ID.value(), ProcessingFixture.EMPLOYEE);

        assertThat(warehouse.allocated).containsOnlyKeys("75423", "99881");
    }

    @Test
    void releasesTheAllocationAlreadyTakenWhenTheNextPositionFails() {
        processings.store(AggregateFixture.pendingProcessingOf(OrderFixture.twoLineOrder()));
        warehouse.failingOnReserveNumber(2, new IllegalStateException("stock item unaddressable"));

        assertThatThrownBy(() -> assignments.claim(
                ProcessingFixture.PROCESSING_ID.value(), ProcessingFixture.EMPLOYEE))
                .isInstanceOf(IllegalStateException.class);
        assertThat(warehouse.released).isNotEmpty();
    }

    @Test
    void refusesAForeignClaimAndKeepsTheAssignee() {
        assignments.claim(ProcessingFixture.PROCESSING_ID.value(), ProcessingFixture.EMPLOYEE);

        assertThatThrownBy(() -> assignments.claim(
                ProcessingFixture.PROCESSING_ID.value(), ProcessingFixture.OTHER_EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> {
                    assertThat(refused.error()).isEqualTo(FulfillmentException.Error.ASSIGNMENT_REFUSED);
                    assertThat(refused.details()).contains(AssignmentGuard.Reason.ALREADY_ASSIGNED);
                });
        assertThat(processings.get(ProcessingFixture.PROCESSING_ID.value()).orElseThrow().toSnapshot().assignee())
                .isEqualTo(ProcessingFixture.EMPLOYEE);
    }

    @Test
    void answersARepeatedClaimWithoutASecondReservation() {
        assignments.claim(ProcessingFixture.PROCESSING_ID.value(), ProcessingFixture.EMPLOYEE);
        int reservations = warehouse.allocated.get("75423");

        OrderClaim repeated = assignments.claim(ProcessingFixture.PROCESSING_ID.value(), ProcessingFixture.EMPLOYEE);

        assertThat(repeated.granted()).isFalse();
        assertThat(warehouse.allocated).containsEntry("75423", reservations);
    }

    @Test
    void refusesAClaimOnAReadyOrderAsPastPicking() {
        processings.store(AggregateFixture.aReadyProcessing());

        assertThatThrownBy(() -> assignments.claim(
                ProcessingFixture.PROCESSING_ID.value(), ProcessingFixture.OTHER_EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused ->
                        assertThat(refused.details()).contains(AssignmentGuard.Reason.PAST_PICKING));
    }

    @Test
    void answersAnUnknownProcessingWithItsOwnRefusal() {
        assertThatThrownBy(() -> assignments.claim("OP-2026-9999", ProcessingFixture.EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.PROCESSING_NOT_FOUND));
    }

    @Test
    void surfacesAWarehouseFailureInsteadOfClaimingWithoutAReservation() {
        warehouse.failingOnReserve(new IllegalStateException("warehouse down"));

        assertThatThrownBy(() -> assignments.claim(
                ProcessingFixture.PROCESSING_ID.value(), ProcessingFixture.EMPLOYEE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("warehouse down");
    }

    @Test
    void refusesAClaimTheWarehouseCannotCover() {
        warehouse.withAvailable(0);

        assertThatThrownBy(() -> assignments.claim(
                ProcessingFixture.PROCESSING_ID.value(), ProcessingFixture.EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.STOCK_OBSERVATION_MISSING));
    }

    @Test
    void refusesAClaimWhenNoWarehouseAdapterIsWired() {
        assertThatThrownBy(() -> assignmentsWith(Optional.empty())
                .claim(ProcessingFixture.PROCESSING_ID.value(), ProcessingFixture.EMPLOYEE))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.WAREHOUSE_NOT_WIRED));
    }

    @Test
    void recordsTheReservationOfEveryPositionItTook() {
        assignments.claim(ProcessingFixture.PROCESSING_ID.value(), ProcessingFixture.EMPLOYEE);

        assertThat(reservations.recorded()).singleElement().satisfies(reservation -> {
            assertThat(reservation.processingId()).isEqualTo(ProcessingFixture.PROCESSING_ID);
            assertThat(reservation.productNumber().value()).isEqualTo(OrderFixture.PRODUCT_NUMBER);
            assertThat(reservation.issued()).isFalse();
        });
    }

    private AssignmentService assignmentsWith(Optional<shop.fulfillment.kernel.WarehouseStock> stock) {
        return new AssignmentService(processings, new WarehouseReservationsIntegration(stock, reservations));
    }
}
