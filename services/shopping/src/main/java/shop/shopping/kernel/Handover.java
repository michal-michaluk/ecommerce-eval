package shop.shopping.kernel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * What the basket hands over to the checkout slice: its identity, the revision the payload was composed at, and the selected
 * rows — nothing else crosses (element 09 HAND-2, D32, D35).
 *
 * <p>It lives in {@code kernel/} because both slices read it and no slice type may cross (element 09 §Recommendations 1,
 * {@code structure.kernel-public}). It is a payload, not a lifecycle: a row is only here because the aggregate already
 * refused the handover otherwise (HAND-1), and an unselected row is absent rather than zero-quantity. Totals and the delivery
 * estimate are composed by the elements that own them.
 *
 * <p>The content digest is the handover's identity (D41, HAND-4): it is computed over the concatenation of the payload's own
 * content, so two handovers of the same selection answer the same digest however the basket reached it. The revision and the
 * creation instant are not part of it — they are derived from it, and a digest that included them could never be repeated.
 *
 * @param handoverId the handover's identity, which is the basket's (D32)
 * @param basketId   the basket the handover belongs to
 * @param customerId the customer the basket belongs to, taken from the token
 * @param currency   the one currency the basket holds (element 03 RULE-2)
 * @param revision   the basket revision this handover was composed at (D35)
 * @param createdAt  when the handover was composed
 * @param selection  the rows that cross, one per selected item
 */
public record Handover(
        String handoverId,
        String basketId,
        String customerId,
        String currency,
        long revision,
        Instant createdAt,
        List<Line> selection) {

    /**
     * The version of the canonical form the digest is computed over. It moved to v2 when the payload gained the availability
     * reason and narrowed the identities to their wire text: a digest that stayed at v1 would report "same content" for
     * content that changed.
     */
    private static final String CANONICAL_FORM = "basket-handover-v2";

    private static final String DIGEST_ALGORITHM = "SHA-256";

    private static final String DIGEST_PREFIX = "sha256:";

    private static final String ABSENT = "-";

    /**
     * One selected row as checkout receives it: the identity of the offer, the shipment it belongs to, the quantity, the
     * availability as element 06 decided it and the prices the basket read — a snapshot, never a claim that the price is
     * final (HAND-5).
     *
     * @param offerId      the offer to order
     * @param seller       the seller the shipment is grouped by
     * @param quantity     how many units crossed
     * @param availability the outcome and its reason, so checkout need not guess
     * @param currentPrice the offer's current price, one unit
     * @param discount     the offer's flattened discount of kind {@code discount}
     */
    public record Line(
            String offerId,
            String seller,
            int quantity,
            Availability availability,
            Money currentPrice,
            Money discount) {

        public Line {
            Objects.requireNonNull(offerId, "offerId");
            Objects.requireNonNull(seller, "seller");
            Objects.requireNonNull(availability, "availability");
            Objects.requireNonNull(currentPrice, "currentPrice");
            Objects.requireNonNull(discount, "discount");
        }
    }

    public Handover {
        Objects.requireNonNull(handoverId, "handoverId");
        Objects.requireNonNull(basketId, "basketId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(createdAt, "createdAt");
        selection = List.copyOf(selection);
    }

    /**
     * The identity of the handover that this content would produce: the digest of its canonical form.
     *
     * <p>The canonical form is the concatenation of name-tagged records — the form's own version, the basket's identity, its
     * customer and its currency, then one {@code line} record per selected row carrying offer, seller, quantity, the
     * availability outcome with its reason and its stale flag, and the current price and discount. Every component is written
     * either as {@code -} when absent or as {@code <length>:<text>}, so no component can be read as another and absent is
     * never the empty text — the same encoding {@code Order.canonicalDigest()} uses as its idempotency key.
     *
     * <p>A runtime without SHA-256 fails here with {@link IllegalStateException}: it is an impossible state, not a refusal of
     * a business value, and D48 names no code for it. That is the one record of the carve-out — {@code
     * Order.canonicalDigest()} answers the same way, and {@code error.domain-throws-context-type} is aggregate-scoped, so
     * nothing but this paragraph keeps the exception honest.
     *
     * @param basketId   the basket the content belongs to
     * @param customerId the customer the basket belongs to
     * @param currency   the one currency the basket holds
     * @param selection  the rows that would cross
     * @return the {@code sha256:} digest of that content
     */
    public static String contentDigestOf(String basketId, String customerId, String currency, List<Line> selection) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] digest = sha256.digest(canonicalFormOf(basketId, customerId, currency, selection)
                    .getBytes(StandardCharsets.UTF_8));
            return DIGEST_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(DIGEST_ALGORITHM + " is a digest every Java runtime carries", impossible);
        }
    }

    private static String canonicalFormOf(String basketId, String customerId, String currency, List<Line> selection) {
        StringBuilder canonical = new StringBuilder(record("form", CANONICAL_FORM));
        canonical.append(record("basket", basketId));
        canonical.append(record("customer", customerId));
        canonical.append(record("currency", currency));
        selection.stream().sorted(Handover::byOffer).forEach(line -> canonical.append(canonicalLine(line)));
        return canonical.toString();
    }

    private static int byOffer(Line line, Line other) {
        return line.offerId().compareTo(other.offerId());
    }

    private static String canonicalLine(Line line) {
        return record("line", line.offerId(), line.seller(), Integer.toString(line.quantity()),
                line.availability().status().name(), line.availability().reason().name(),
                Boolean.toString(line.availability().stale()))
                + record("currentPrice", Long.toString(line.currentPrice().amount()),
                line.currentPrice().currency(), line.currentPrice().kind().name())
                + record("discount", Long.toString(line.discount().amount()),
                line.discount().currency(), line.discount().kind().name());
    }

    /** One record: its components in their fixed order, each self-delimiting, so no content can pass for another component. */
    private static String record(String... components) {
        return String.join("", Stream.of(components)
                .map(component -> component == null ? ABSENT : component.length() + ":" + component)
                .toList());
    }
}
