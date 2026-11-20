package shop.fulfillment.processing;

import java.util.Optional;

import shop.fulfillment.kernel.ReservationId;

/**
 * The state this boundary owns about its own reservations — element 13 state S1: which allocation of the warehouse a
 * {@link ReservationId} addresses, and whether the goods were already booked out for it.
 *
 * <p>The warehouse cannot hold this: an allocation pk is not returned by the allocate call as declared, and
 * {@code release} and {@code issue} are addressed by an id we minted, so without this correlation a reservation is
 * orphaned and, with no TTL at the warehouse, permanently eats the stock it allocated (R-13-1).
 *
 * <p>It is a secondary port of the warehouse adapter, not of the context: nothing outside the adapter reads it, and the
 * warehouse's own vocabulary never leaves {@link InvenTreeWarehouseClient}.
 */
interface WarehouseReservationRepository {

    Optional<WarehouseReservation> find(ReservationId reservationId);

    void record(WarehouseReservation reservation);

    void forget(ReservationId reservationId);
}
