package shop.shopping.checkout;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import shop.shopping.kernel.CheckoutHandover;
import shop.shopping.kernel.CheckoutHandovers;
import shop.shopping.kernel.Handover;
import shop.shopping.kernel.ShoppingException;

/**
 * The receiving end of the basket's terminal action (element 09, D32): it takes the handover, holds what it accepted for that
 * basket and answers the receipt. The checkout slice's own process — the order it creates, the price difference it reports,
 * payment and delivery methods — belongs to its own specification (D4) and is not here.
 *
 * <p>It creates no order (HAND-3): the only thing this increment's checkout does is refuse a stale handover and record what it
 * accepted. It stores that one fact per basket and nothing else, because until D4's specification lands there is nothing else
 * to store it in — a second store would be a write model nobody has specified.
 *
 * <p><strong>Two keys, two questions.</strong> {@code revision} answers whether the handover is stale (D35): anything older
 * than the revision already held for that basket is refused as the lost race it is, so nothing has to know how old the
 * customer's edit is to be safe. The <em>content digest</em> answers whether this is the same payload (D41): a repeat of the
 * identity already held is answered with the receipt given then, so a retried command consumes no second checkout — while a
 * handover that arrives at the same revision with another digest is a payload that changed after the basket failed to store the
 * previous one, and it is accepted rather than swallowed. Keying on the revision alone would answer the old receipt for new
 * content and leave the customer's change un-ordered.
 *
 * <p>What it holds is per process and unbounded: one accepted revision, digest and receipt per basket ever handed over. D4's
 * precondition is that this becomes a durable row written in the same transaction as the order it creates — anything less and
 * a restart can lose the receipt of a checkout that was already accepted. Forgetting it on a restart today forgets an id, not a
 * customer's basket.
 */
@Service
@Transactional
class CheckoutService implements CheckoutHandovers {

    private static final String CHECKOUT_PREFIX = "CO-";

    private final Map<String, Accepted> acceptedPerBasket = new ConcurrentHashMap<>();

    @Override
    public CheckoutHandover accept(Handover handover) {
        Accepted held = acceptedPerBasket.get(handover.basketId());
        if (held != null && handover.revision() < held.revision()) {
            throw new ShoppingException(ShoppingException.Error.CONCURRENT_MODIFICATION, handover.basketId());
        }
        return held != null && held.contentDigest().equals(handover.contentDigest())
                ? held.receipt()
                : record(handover);
    }

    private CheckoutHandover record(Handover handover) {
        CheckoutHandover receipt = new CheckoutHandover(handover.handoverId(), handover.revision(), mintedCheckoutId());
        acceptedPerBasket.put(handover.basketId(), new Accepted(handover.revision(), handover.contentDigest(), receipt));
        return receipt;
    }

    private static String mintedCheckoutId() {
        return CHECKOUT_PREFIX + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * What this slice accepted for one basket: the revision the staleness check reads, the digest a repeat is recognised by, and
     * the receipt it answered.
     *
     * @param revision      the revision of the accepted handover (D35)
     * @param contentDigest the identity of the accepted payload (D41)
     * @param receipt       what {@code accept} answered for it
     */
    private record Accepted(long revision, String contentDigest, CheckoutHandover receipt) {
    }
}
