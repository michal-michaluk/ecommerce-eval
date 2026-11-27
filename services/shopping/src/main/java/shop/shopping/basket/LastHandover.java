package shop.shopping.basket;

import java.time.Instant;
import java.util.Objects;

/**
 * The identity of the handover a basket last produced (D41): its revision, the digest of the content it carried, the
 * handover's own identity and the moment it was composed.
 *
 * <p>It belongs to this slice and to nothing else: it is one field of the basket's own JSONB snapshot, and the checkout slice
 * never reads it — what crosses is {@link shop.shopping.kernel.Handover}, whose every component is a value the other slice may
 * name. There is therefore no edge that would justify it in {@code kernel/}, and this slice owns it.
 *
 * <p>It is the only derived field of the handover the basket keeps, and it is what makes a repeat of an unchanged basket
 * answer the same handover without the basket ever reading checkout's state (element 09 HAND-4). The digest is the
 * identity: same digest means the content did not change, so the same {@code handoverId} and the same {@code revision}.
 *
 * @param revision      the basket revision the handover was composed at (D35)
 * @param contentDigest the digest of the payload that crossed
 * @param handoverId    the handover's identity, which is the basket's identity (D32)
 * @param createdAt     when the handover was composed
 */
record LastHandover(long revision, String contentDigest, String handoverId, Instant createdAt) {

    LastHandover {
        Objects.requireNonNull(contentDigest, "contentDigest");
        Objects.requireNonNull(handoverId, "handoverId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
