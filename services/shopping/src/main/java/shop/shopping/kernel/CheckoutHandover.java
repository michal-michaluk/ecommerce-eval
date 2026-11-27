package shop.shopping.kernel;

import java.util.Objects;

/**
 * What the checkout slice accepted: the handover it took, the revision that handover carried and the identity checkout gave
 * the resulting checkout (element 09 §Shape of record).
 *
 * <p>{@code checkoutId} stays inside this service: the receiving slice answers it, the basket returns it to its own caller and
 * never publishes it on the wire, which is why the basket's HTTP answer (steps 18–19 of the contract) has no such field (D41).
 *
 * @param handoverId the handover checkout accepted, echoed back so the basket need not map anything
 * @param revision   the revision of the accepted handover — the one checkout now holds for that basket (D35)
 * @param checkoutId checkout's own identity of the checkout this handover opened
 */
public record CheckoutHandover(String handoverId, long revision, String checkoutId) {

    public CheckoutHandover {
        Objects.requireNonNull(handoverId, "handoverId");
        Objects.requireNonNull(checkoutId, "checkoutId");
    }
}
