package shop.shopping.basket;

import java.util.List;
import java.util.Objects;

import shop.shopping.kernel.Handover;

/**
 * What the basket aggregate answers about a handover: the rows that cross, the identity of the content they form and whether
 * that content had already been handed over (element 09, D41, HAND-4).
 *
 * <p>The totals and the delivery range are identified by {@code identity} but are not carried here: they are element 04's and
 * element 05's answers, which the caller composes onto the one {@link Handover} that crosses.
 *
 * @param selection the selected rows, each with the price the basket read (HAND-5)
 * @param identity  the revision, digest, identity and instant of this content (D41)
 * @param repeated  whether that content is exactly the one already handed over, so nothing is sent again (HAND-4)
 */
record HandoverDraft(List<Handover.Line> selection, LastHandover identity, boolean repeated) {

    HandoverDraft {
        selection = List.copyOf(selection);
        Objects.requireNonNull(identity, "identity");
    }
}
