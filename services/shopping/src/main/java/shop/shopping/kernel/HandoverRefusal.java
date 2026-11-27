package shop.shopping.kernel;

import java.util.Objects;

/**
 * One row that refuses the handover, narrowed to the three facts the API reports about it: the offer, the status element 06
 * decided and whether the read behind it is stale (element 07 §Error contract, element 09 §Failure outcomes, D33).
 *
 * <p>It lives in {@code kernel/} because it is a detail of a kernel exception: {@code BLOCKED_BY_ITEMS} carries the offending
 * rows, and a slice type in that list would be a basket type crossing into the one package both slices read. It is also the
 * exact wire shape of an entry of {@code reasons[]}, so the error advice renders what the refusal already holds instead of
 * re-deriving it.
 *
 * @param offerId the offer that blocked the handover
 * @param status  its availability as the basket last decided it — never {@code available}, since only a blocked row lands here
 * @param stale   whether the answer behind the status could not be read; it alone never blocks (HAND-1)
 */
public record HandoverRefusal(String offerId, Availability.Status status, boolean stale) {

    public HandoverRefusal {
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(status, "status");
    }

    /**
     * The block one row's availability states.
     *
     * @param offerId      the offer
     * @param availability what the basket decided about it
     * @return the row as the refusal and the API report it
     */
    public static HandoverRefusal of(String offerId, Availability availability) {
        Objects.requireNonNull(availability, "availability");
        return new HandoverRefusal(offerId, availability.status(), availability.stale());
    }
}
