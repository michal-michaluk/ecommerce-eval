package shop.fulfillment.processing;

import lombok.Builder;

import java.time.LocalDate;

import shop.fulfillment.kernel.IssueNote;
import shop.fulfillment.kernel.LocationCode;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.PickedItem;
import shop.fulfillment.kernel.ProductId;
import shop.fulfillment.kernel.ReservationId;

/**
 * What one claim took at the warehouse, kept beside the order it was taken for — the correlation register D11's third write
 * trigger needs.
 *
 * <p>The aggregate deliberately carries no reservation: a reservation is a boundary fact, and a processing is a business
 * one. Without a store of its own the correlation would exist only in the memory of the claim (which is gone once the
 * request returns), and {@code issue} at readiness would have nothing to address — the gap recorded as phase-3 decision 18.
 *
 * <p>The row also keeps the goods issue note the warehouse answered, so a later reader (the WZ of element 05, out of the
 * plan until an invoicing contract exists) reads the document we numbered instead of asking the warehouse again.
 *
 * @param processingId  the processing this reservation was taken for
 * @param productNumber the position it covers
 * @param location      the location the units were allocated from
 * @param quantity      how many units, at least one
 * @param reservationId the id we minted and the only key a caller has
 * @param issueDocument the goods issue document the warehouse answered, or {@code null} while the goods have not left
 * @param issueDate     the day the goods left, or {@code null} while they have not
 */
@Builder
record ProcessingReservation(
        OrderProcessingId processingId,
        ProductId productNumber,
        LocationCode location,
        int quantity,
        ReservationId reservationId,
        String issueDocument,
        LocalDate issueDate) {

    /**
     * One allocation a claim just took: the reservation id, what it covers and where the units came from.
     *
     * @param processingId  the processing the claim took it for
     * @param productNumber the position it covers
     * @param location      the location the units were allocated from
     * @param quantity      how many units, at least one
     * @param reservationId the id the warehouse answered, and the only key a caller has
     * @return the reservation, with no goods issue note yet
     */
    static ProcessingReservation takenFor(OrderProcessingId processingId, ProductId productNumber,
                                          LocationCode location, int quantity, ReservationId reservationId) {
        return new ProcessingReservation(processingId, productNumber, location, quantity, reservationId, null, null);
    }

    /** Whether this reservation was already booked out — {@code issue} is at most once per reservation (element 13). */
    boolean issued() {
        return issueDocument != null;
    }

    /** What a goods issue books out: the units this reservation holds, at the location it holds them. */
    PickedItem pickedItem() {
        return new PickedItem(productNumber, location, quantity);
    }

    /** The same reservation, stamped with the note the warehouse answered. */
    ProcessingReservation issuedAs(IssueNote note) {
        return new ProcessingReservation(processingId, productNumber, location, quantity, reservationId,
                note.documentNumber(), note.date());
    }
}
