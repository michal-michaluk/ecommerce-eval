package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;

import java.util.List;

import shop.fulfillment.kernel.ProcessingFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The allocations one claim holds: every position reserved, nothing released while the claim stands, and every allocation
 * given back when the claim's transaction does not commit or the warehouse fails half way.
 */
class WarehouseReservationsTest {

    final FakeWarehouseStock warehouse = new FakeWarehouseStock();
    final FakeProcessingReservations reservations = new FakeProcessingReservations();

    @Test
    void reservesOneAllocationPerPosition() {
        WarehouseReservations.on(warehouse)
                .reserveEvery(ProcessingFixture.PROCESSING_ID,
                        List.of(ProcessingFixture.aPosition(), ProcessingFixture.aPosition("99881", 2)));

        assertThat(warehouse.allocated).containsOnlyKeys("75423", "99881");
        assertThat(warehouse.released).isEmpty();
    }

    @Test
    void keepsTheCorrelationOfEachAllocationItTook() {
        WarehouseReservations.on(warehouse)
                .reserveEvery(ProcessingFixture.PROCESSING_ID,
                        List.of(ProcessingFixture.aPosition(), ProcessingFixture.aPosition("99881", 2)))
                .forEach(reservations::record);
        List<ProcessingReservation> recorded = reservations.recorded();

        assertThat(recorded).hasSize(2).allSatisfy(reservation -> assertThat(reservation.processingId())
                .isEqualTo(ProcessingFixture.PROCESSING_ID));
        assertThat(recorded).extracting(reservation -> reservation.productNumber().value())
                .containsExactly("75423", "99881");
        assertThat(recorded).allSatisfy(reservation -> assertThat(reservation.issued()).isFalse());
    }

    @Test
    void givesEveryAllocationBackWhenTheTransactionRollsBack() {
        WarehouseReservations taken = WarehouseReservations.on(warehouse);
        taken.reserveEvery(ProcessingFixture.PROCESSING_ID, List.of(ProcessingFixture.aPosition()));

        new WarehouseReservationsIntegration.ReleaseOnFailure(taken)
                .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(warehouse.released).hasSize(1);
    }

    @Test
    void keepsTheAllocationWhenTheTransactionCommits() {
        WarehouseReservations taken = WarehouseReservations.on(warehouse);
        taken.reserveEvery(ProcessingFixture.PROCESSING_ID, List.of(ProcessingFixture.aPosition()));

        new WarehouseReservationsIntegration.ReleaseOnFailure(taken)
                .afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(warehouse.released).isEmpty();
    }

    @Test
    void givesTheAllocationsItAlreadyTookBackWhenTheNextPositionFails() {
        WarehouseReservations taken = WarehouseReservations.on(warehouse);
        warehouse.failingOnReserveNumber(2, new IllegalStateException("stock item unaddressable"));

        assertThatThrownBy(() -> taken.reserveEvery(ProcessingFixture.PROCESSING_ID,
                List.of(ProcessingFixture.aPosition(), ProcessingFixture.aPosition("99881", 2))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(warehouse.released).hasSize(1);
    }
}
