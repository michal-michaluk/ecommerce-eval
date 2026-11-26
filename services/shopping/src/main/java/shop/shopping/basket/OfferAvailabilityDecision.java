package shop.shopping.basket;

import java.util.Objects;

import shop.shopping.kernel.Availability;

/**
 * Element 06: one total, pure function from the observation of an offer to what the basket is allowed to say about it.
 *
 * <p>The read that failed is answered before anything else (element 06 §Recommendations 1): an offer nobody managed to read
 * is {@code unavailable} and {@code stale}, whatever stock it would have had (A4), and an offer that answered while only its
 * stock did not is {@code available} and {@code stale} (A5). Then a known removal, which is data and not a guess (A3), and
 * last the two readings of a readable offer (A1, A2).
 *
 * <p>Fail-closed by construction: the item's previous {@link Availability} is not a parameter, so a failed read has no way to
 * keep an earlier {@code available} — there is nothing to consult, not even to break a tie (A4, I4, I6, D26). Element 06
 * writes the interface as {@code decide(item, observation)}, and the item is deliberately left out: all it would contribute is
 * its {@code offerId}, which the observation already carries, and its {@code lastKnown}, which is what a row renders and never
 * an input to a calculation (element 02 RULE-8). Handing the item in would make the previous outcome consultable again, which
 * is the exact hole this rule closes, so it must not be "restored".
 *
 * <p>Total: every observation the record admits maps to one of the three statuses — no {@code Optional}, no {@code null}, no
 * exception for any value the types admit, and no default branch, because both switches are exhaustive over the states they
 * cover. An absent component is a programming error and stays an {@link java.util.Objects#requireNonNull}
 * {@link NullPointerException}, as it does in {@link shop.shopping.kernel.Money}. Whether the outcome blocks the handover is a
 * property of the {@link Availability} it returns and is not decided a second time here (element 06 §Recommendations 2, D33).
 */
final class OfferAvailabilityDecision {

    private OfferAvailabilityDecision() {
    }

    /**
     * What this observation means for the item it answers for.
     *
     * @param observation the offer as the last read saw it, never absent
     * @return the outcome element 06 decided, carrying its own blocking set
     */
    static Availability decide(OfferObservation observation) {
        Objects.requireNonNull(observation, "observation");
        return switch (observation.offerState()) {
            case notRead -> Availability.unreadable();
            case removed -> Availability.removed();
            case onSale -> onSale(observation.stock());
        };
    }

    private static Availability onSale(OfferObservation.Stock stock) {
        return switch (stock) {
            case OfferObservation.Stock.NotRead notRead -> Availability.availableWithoutStockRead();
            case OfferObservation.Stock.Quantity quantity -> quantity.someLeft()
                    ? Availability.available()
                    : Availability.outOfStock();
        };
    }
}
