package shop.fulfillment.processing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import shop.fulfillment.IntegrationTest;
import shop.fulfillment.TestTransaction;
import shop.fulfillment.kernel.ReservationId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The S1 correlation as the row that has to outlive the caller's transaction: the order and allocation pks a reservation
 * addresses, the goods issue document stamped once, and — the reason every method runs in its own transaction — the row
 * still being readable while the transaction that wrote it is rolled back, so a compensation can release a real
 * allocation instead of finding nothing (R-13-1).
 */
@IntegrationTest
class DocumentWarehouseReservationRepositoryTest {

    static final ReservationId RESERVATION = new ReservationId("RSV-0001");

    @Autowired
    DocumentWarehouseReservationRepository reservations;
    @Autowired
    DocumentWarehouseReservationRepository.Reservations rows;

    @Test
    void roundTripsTheReservationItsAllocationsAndItsIssueNote() {
        reservations.record(WarehouseAdapterFixture.aReservation(7, 21, 22));

        assertThat(reservations.find(RESERVATION)).get().satisfies(stored -> {
            assertThat(stored.orderPk()).isEqualTo(7);
            assertThat(stored.allocationPks()).containsExactly(21, 22);
            assertThat(stored.issued()).isFalse();
        });
        forgetAfterwards();
    }

    @Test
    void stampsTheGoodsIssueSoARepeatAnswersTheSameNote() {
        reservations.record(WarehouseAdapterFixture.aReservation(7, 21));

        reservations.record(reservations.find(RESERVATION).orElseThrow()
                .issuedAs("RSV-0001", LocalDate.parse("2026-11-19")));

        assertThat(reservations.find(RESERVATION)).get().satisfies(stored -> {
            assertThat(stored.issued()).isTrue();
            assertThat(stored.issueDocument()).isEqualTo("RSV-0001");
            assertThat(stored.issueDate()).isEqualTo(LocalDate.parse("2026-11-19"));
        });
        forgetAfterwards();
    }

    @Test
    void answersAReservationItNeverTookWithNothing() {
        assertThat(reservations.find(new ReservationId("RSV-NOBODY"))).isEmpty();
    }

    @Test
    void forgetsAReleasedReservation() {
        reservations.record(WarehouseAdapterFixture.aReservation(7, 21));

        reservations.forget(RESERVATION);

        assertThat(reservations.find(RESERVATION)).isEmpty();
    }

    @Test
    void keepsTheCorrelationWhileTheClaimThatWroteItIsStillUncommitted() {
        reservations.record(WarehouseAdapterFixture.aReservation(9, 31));

        assertThat(reservations.find(RESERVATION)).get()
                .satisfies(stored -> assertThat(stored.allocationPks()).containsExactly(31));
        forgetAfterwards();
    }

    @Test
    void keepsAnEmptyAllocationListAsAnEmptyList() {
        reservations.record(WarehouseReservation.builder()
                .reservationId(RESERVATION)
                .orderPk(8)
                .allocationPks(List.of())
                .build());

        assertThat(reservations.find(RESERVATION).orElseThrow().allocationPks()).isEmpty();
        forgetAfterwards();
    }

    /** The adapter commits on its own, so a test that wrote a row deletes it in a committed transaction as well. */
    private void forgetAfterwards() {
        TestTransaction.transactional(() -> rows.deleteById(RESERVATION.value()));
    }
}
