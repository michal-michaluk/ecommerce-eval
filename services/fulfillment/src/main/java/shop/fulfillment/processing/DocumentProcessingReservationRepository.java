package shop.fulfillment.processing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import shop.fulfillment.kernel.LocationCode;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ProductId;
import shop.fulfillment.kernel.ReservationId;

/**
 * The {@link ProcessingReservationRepository} adapter: one row per reservation a claim took, keyed by the id we minted.
 *
 * <p>The row is what makes the correlation survive the request that created it — the claim's own list of ids lives for the
 * life of one command — and it is written in the claim's transaction on purpose, so a claim that rolls back leaves no
 * reservation of ours behind (the warehouse side is compensated separately, phase-1 decision 2.5).
 */
@Repository
class DocumentProcessingReservationRepository implements ProcessingReservationRepository {

    private final Reservations reservations;

    DocumentProcessingReservationRepository(Reservations reservations) {
        this.reservations = reservations;
    }

    @Override
    public void record(ProcessingReservation reservation) {
        String id = reservation.reservationId().value();
        reservations.save(reservations.findById(id)
                .orElseGet(() -> new ProcessingReservationEntity(id))
                .set(reservation));
    }

    @Override
    public List<ProcessingReservation> findFor(OrderProcessingId processingId) {
        return reservations.findAllByProcessingIdOrderByProductNumberAsc(processingId.value()).stream()
                .map(ProcessingReservationEntity::toReservation)
                .toList();
    }

    interface Reservations extends JpaRepository<ProcessingReservationEntity, String> {

        List<ProcessingReservationEntity> findAllByProcessingIdOrderByProductNumberAsc(String processingId);
    }

    @Entity
    @Table(name = "processing_reservation")
    static class ProcessingReservationEntity {

        @Id
        @Column(name = "reservation_id")
        private String reservationId;
        @Column(name = "processing_id")
        private String processingId;
        @Column(name = "product_number")
        private String productNumber;
        @Column(name = "location")
        private String location;
        @Column(name = "quantity")
        private int quantity;
        @Column(name = "issue_document")
        private String issueDocument;
        @Column(name = "issue_date")
        private LocalDate issueDate;

        protected ProcessingReservationEntity() {
        }

        ProcessingReservationEntity(String reservationId) {
            this.reservationId = reservationId;
        }

        ProcessingReservation toReservation() {
            return new ProcessingReservation(new OrderProcessingId(processingId), new ProductId(productNumber),
                    new LocationCode(location), quantity, new ReservationId(reservationId), issueDocument, issueDate);
        }

        ProcessingReservationEntity set(ProcessingReservation reservation) {
            this.processingId = reservation.processingId().value();
            this.productNumber = reservation.productNumber().value();
            this.location = reservation.location().value();
            this.quantity = reservation.quantity();
            this.issueDocument = reservation.issueDocument();
            this.issueDate = reservation.issueDate();
            return this;
        }
    }
}
