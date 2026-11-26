package shop.shopping.kernel;

import java.time.Instant;
import java.util.Objects;

/**
 * The identity of the handover a basket last produced (D41): its revision, the digest of the content it carried, the
 * handover's own identity and the moment it was composed.
 *
 * <p>It lives in {@code kernel/} because it is part of the basket's snapshot as the checkout slice reads it, and because the
 * payload it names does — the basket's slice owns no type the other slice may read (element 09 §Recommendations 1).
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
public record LastHandover(long revision, String contentDigest, String handoverId, Instant createdAt) {

    public LastHandover {
        Objects.requireNonNull(contentDigest, "contentDigest");
        Objects.requireNonNull(handoverId, "handoverId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
