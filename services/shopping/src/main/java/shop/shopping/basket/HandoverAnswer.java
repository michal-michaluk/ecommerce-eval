package shop.shopping.basket;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import shop.shopping.kernel.Handover;

/**
 * The answer of {@code PATCH /baskets/{basketId}/checkout} — the shape of record of element 09 §Shape of record, fixed by steps
 * 18–19 of the {@code prove} scenario (D41).
 *
 * <p>It is a projection of the payload and not the payload itself: {@code selection} is the <strong>offer ids</strong> that
 * crossed, not the line rows — the rows' sellers, availability and unit prices are what the totals already sum, and a client of
 * this route reads the summed answer (D41). The {@code deliveryEstimate} the payload carries is not published either, and
 * {@code checkoutId} stays inside the service: the answer to a customer is what the basket hands over, not what checkout called
 * it (D41).
 *
 * @param handoverId the handover's identity, which is the basket's (D32)
 * @param basketId   the basket it was composed from
 * @param createdAt  when it was composed — the same instant for a repeated handover (HAND-4)
 * @param customerId the customer the basket belongs to, as the aggregate stores it — never read from the token, because a token
 *                   only says who is asking (element 07 §Ownership, D68), and the answer is only ever given to that owner
 * @param currency   the one currency the basket holds
 * @param selection  the offers that crossed, sorted by offer id, so two answers of one content are one answer (HAND-2)
 * @param revision   the revision of the content that crossed, echoed on a repeat (D35, DoD30)
 * @param totals     what the customer pays for that selection (element 04)
 */
record HandoverAnswer(
        String handoverId,
        String basketId,
        Instant createdAt,
        String customerId,
        String currency,
        List<String> selection,
        long revision,
        Handover.Totals totals) {

    HandoverAnswer {
        Objects.requireNonNull(handoverId, "handoverId");
        Objects.requireNonNull(basketId, "basketId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(totals, "totals");
        selection = List.copyOf(selection);
    }

    /**
     * The payload projected onto the answer.
     *
     * @param handover what crossed to checkout
     * @return the answer the client reads
     */
    static HandoverAnswer of(Handover handover) {
        return new HandoverAnswer(handover.handoverId(), handover.basketId(), handover.createdAt(),
                handover.customerId(), handover.currency(),
                handover.selection().stream().map(Handover.Line::offerId).sorted().toList(),
                handover.revision(), handover.totals());
    }
}
