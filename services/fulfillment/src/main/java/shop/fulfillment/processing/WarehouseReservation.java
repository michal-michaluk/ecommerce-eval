package shop.fulfillment.processing;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

import shop.fulfillment.kernel.ReservationId;

/**
 * One reservation as this boundary remembers it — element 13 state S1, the state the warehouse cannot hold.
 *
 * <p>A reservation is addressed by an id we minted, while the allocations that hold the units are the warehouse's own rows:
 * without this correlation {@code release} and {@code issue} have nothing to address, and with no TTL at the warehouse an
 * orphaned allocation eats the stock it took for ever (R-13-1).
 *
 * @param reservationId the id we minted, and the only key a caller has
 * @param orderPk       the warehouse order the units are allocated against
 * @param allocationPks the allocation rows that hold the units, read back after the allocate call
 * @param issueDocument the goods issue document we numbered, or {@code null} while the goods have not left
 * @param issueDate     the day the goods left, or {@code null} while they have not
 */
@Builder
record WarehouseReservation(
        ReservationId reservationId,
        int orderPk,
        List<Integer> allocationPks,
        String issueDocument,
        LocalDate issueDate) {

    WarehouseReservation {
        allocationPks = List.copyOf(allocationPks);
    }

    /** Whether the goods were already booked out for this reservation — the at-most-once fact of RULE-5. */
    boolean issued() {
        return issueDocument != null;
    }

    /** The same reservation, with the goods issue we numbered for it. */
    WarehouseReservation issuedAs(String issueDocument, LocalDate issueDate) {
        return new WarehouseReservation(reservationId, orderPk, allocationPks, issueDocument, issueDate);
    }
}
