package shop.shopping.basket;

import java.util.ArrayList;
import java.util.List;

import shop.shopping.kernel.CheckoutHandover;
import shop.shopping.kernel.CheckoutHandovers;
import shop.shopping.kernel.Handover;
import shop.shopping.kernel.HandoverFixture;

/**
 * The kernel's handover port backed by a list: what the basket handed over and how often it asked, without the checkout slice.
 *
 * <p>A basket test asserts on the payload that crossed and on the number of crossings — the second half is what proves a repeat
 * of unchanged content is not sent again (HAND-4, DoD30).
 */
class FakeCheckoutHandovers implements CheckoutHandovers {

    final List<Handover> accepted = new ArrayList<>();

    @Override
    public CheckoutHandover accept(Handover handover) {
        accepted.add(handover);
        return HandoverFixture.aCheckoutHandover(handover.handoverId(), handover.revision(), "CO-" + accepted.size());
    }
}
