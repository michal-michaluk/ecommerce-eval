package shop.fulfillment.processing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import shop.fulfillment.kernel.ReservationId;

/**
 * The {@link WarehouseReservationRepository} adapter: one row per reservation this boundary took at the warehouse.
 *
 * <p>The allocation pks live as the comma-separated list the allocate call answered them with, and the goods issue
 * document is stamped here so a repeated {@code issue} answers the same note instead of booking the goods out twice
 * (RULE-5). Nothing of the warehouse's own model is stored beside them: an order pk and an allocation pk are opaque
 * numbers this table correlates, never a type of the warehouse's.
 */
@Repository
class DocumentWarehouseReservationRepository implements WarehouseReservationRepository {

    private final Reservations reservations;

    DocumentWarehouseReservationRepository(Reservations reservations) {
        this.reservations = reservations;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<WarehouseReservation> find(ReservationId reservationId) {
        return reservations.findById(reservationId.value()).map(WarehouseReservationEntity::toReservation);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(WarehouseReservation reservation) {
        reservations.save(reservations.findById(reservation.reservationId().value())
                .orElseGet(() -> new WarehouseReservationEntity(reservation.reservationId().value()))
                .set(reservation));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forget(ReservationId reservationId) {
        reservations.deleteById(reservationId.value());
    }

    interface Reservations extends JpaRepository<WarehouseReservationEntity, String> {
    }

    @Entity
    @Table(name = "warehouse_reservation")
    static class WarehouseReservationEntity {

        @Id
        @Column(name = "reservation_id")
        private String reservationId;
        @Column(name = "order_pk")
        private int orderPk;
        @Column(name = "allocation_pks")
        private String allocationPks;
        @Column(name = "issue_document")
        private String issueDocument;
        @Column(name = "issue_date")
        private LocalDate issueDate;

        protected WarehouseReservationEntity() {
        }

        WarehouseReservationEntity(String reservationId) {
            this.reservationId = reservationId;
        }

        WarehouseReservation toReservation() {
            return new WarehouseReservation(new ReservationId(reservationId), orderPk, allocationPks(),
                    issueDocument, issueDate);
        }

        WarehouseReservationEntity set(WarehouseReservation reservation) {
            this.orderPk = reservation.orderPk();
            this.allocationPks = reservation.allocationPks().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            this.issueDocument = reservation.issueDocument();
            this.issueDate = reservation.issueDate();
            return this;
        }

        private List<Integer> allocationPks() {
            if (allocationPks == null || allocationPks.isBlank()) {
                return List.of();
            }
            return Arrays.stream(allocationPks.split(",")).map(Integer::valueOf).toList();
        }
    }
}
