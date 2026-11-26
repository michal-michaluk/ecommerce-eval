package shop.shopping.kernel;

import java.util.Objects;

/**
 * How orderable an item's offer is, as element 06 decided it: the outcome, the reason behind it, and whether the read that
 * produced it is stale.
 *
 * <p>It lives in {@code kernel/} because the handover payload crosses into the checkout slice and both slices may only share
 * this package (element 09 §Recommendations 1, {@code structure.kernel-public}). It is data — the basket never re-derives
 * it, because a second derivation is a second opinion about the same offer (element 06 §Recommendations 2).
 *
 * <p>{@code stale} alone never blocks: a row is {@code available} with {@code stale: true} when the offer answered and only
 * the stock did not, and that row crosses the handover. {@code outOfStock} and {@code unavailable} block (D33), which is the
 * whole blocking set and lives here rather than in the view or in the handover (element 06 §Blocking set).
 *
 * @param status the outcome element 06 decided
 * @param reason why it decided so — a status without its reason cannot tell a removed offer from an unread one
 * @param stale  whether the observation behind the outcome could not be read
 */
public record Availability(Status status, Reason reason, boolean stale) {

    /** The three outcomes of element 06 — the vocabulary the API carries, never re-computed by a client. */
    public enum Status {
        available,
        outOfStock,
        unavailable
    }

    /** Why the status is what it is (element 06 §Interface: {@code stillOnSale | removedFromOffer | noStock | notRead}). */
    public enum Reason {
        stillOnSale,
        removedFromOffer,
        noStock,
        notRead
    }

    public Availability {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
    }

    /** The offer answered with stock: counted, orderable, blocking nothing (A1). */
    public static Availability available() {
        return new Availability(Status.available, Reason.stillOnSale, false);
    }

    /** The offer answered and only its stock did not: counted and handable, because {@code stale} alone never blocks (A5). */
    public static Availability availableWithoutStockRead() {
        return new Availability(Status.available, Reason.stillOnSale, true);
    }

    /** The offer answered with no stock: counted while selected and blocking the handover (A2, D33). */
    public static Availability outOfStock() {
        return new Availability(Status.outOfStock, Reason.noStock, false);
    }

    /** The offer is gone: excluded from the totals, kept as a greyed row, and blocking the handover (A3, D18). */
    public static Availability removed() {
        return new Availability(Status.unavailable, Reason.removedFromOffer, false);
    }

    /** The offer could not be read: fail-closed, so the row is greyed and blocks the handover (A4, D26). */
    public static Availability unreadable() {
        return new Availability(Status.unavailable, Reason.notRead, true);
    }

    /**
     * Whether this outcome blocks the handover — the two non-available statuses, never {@code stale} on its own (D33).
     *
     * @return true when the row must be deselected or removed before the basket can be handed over
     */
    public boolean blocksHandover() {
        return status != Status.available;
    }
}
