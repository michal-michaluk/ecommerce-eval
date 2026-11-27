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
 * What the basket hands over to the checkout slice: its identity, the revision the payload was composed at, the selected rows
 * with the prices the basket computed, the four totals of element 04 and the delivery range of element 05 (element 09, D32,
 * D35).
 *
 * <p>It lives in {@code kernel/} because both slices read it and no slice type may cross (element 09 §Recommendations 1,
 * {@code structure.kernel-public}). It is a payload, not a lifecycle: a row is only here because the aggregate already
 * refused the handover otherwise (HAND-1), and an unselected row is absent rather than zero-quantity. The basket keeps exactly
 * one derived field of it — its own {@code LastHandover} — so a repeat of an unchanged basket never asks checkout again
 * (HAND-4).
 *
 * <p>The content digest is the handover's identity (D41, HAND-4) and the key a receiver keys a repeat on: it is computed over
 * the concatenation of the payload's own content, so two handovers of the same selection answer the same digest however the
 * basket reached it. Three things are not part of it. The revision and the creation instant are derived from it, and a digest
 * that included them could never be repeated. The delivery estimate is clock-derived — the same untouched basket handed over on
 * either side of a Warsaw midnight answers another range — so including it would make DoD30 and step 19 hold only within one
 * calendar day, while the client never sees the range (D41) and checkout re-prices the handover anyway (D31, HAND-5, D64). What
 * is covered is what a client does see: the rows and the totals, a delivery rate or a discount included.
 *
 * @param handoverId       the handover's identity, which is the basket's (D32)
 * @param basketId         the basket the handover belongs to
 * @param customerId       the customer the basket belongs to, taken from the **aggregate** — the token's subject only
 *                         decides whether this caller may see the basket (D68), never what the payload says
 * @param currency         the one currency the basket holds (element 03 RULE-2)
 * @param revision         the basket revision this handover was composed at (D35)
 * @param createdAt        when the handover was composed
 * @param contentDigest    the identity of the content below, which a repeat is recognised by (D41)
 * @param selection        the rows that cross, one per selected item
 * @param totals           what the customer pays for that selection, as element 04 computed it (HAND-5)
 * @param deliveryEstimate when the shipment arrives — the latest {@code to} across the seller shipments (element 05)
 */
public record Handover(
        String handoverId,
        String basketId,
        String customerId,
        String currency,
        long revision,
        Instant createdAt,
        String contentDigest,
        List<Line> selection,
        Totals totals,
        Estimate deliveryEstimate) {

    /**
     * The version of the canonical form the digest is computed over. It moved to v2 when the payload gained the availability
     * reason and narrowed the identities to their wire text, and to v3 when the payload gained every line's total and the four
     * totals: a digest that stayed at an earlier version would report "same content" for content that changed. What v3 covers
     * is exactly the rows and the totals — widening it again is a {@code v4} (D51, D64).
     */
    private static final String CANONICAL_FORM = "basket-handover-v3";

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
     * @param lineTotal    {@code currentPrice} at {@code quantity}, which is what the row is worth here
     */
    public record Line(
            String offerId,
            String seller,
            int quantity,
            Availability availability,
            Money currentPrice,
            Money discount,
            Money lineTotal) {

        public Line {
            Objects.requireNonNull(offerId, "offerId");
            Objects.requireNonNull(seller, "seller");
            Objects.requireNonNull(availability, "availability");
            Objects.requireNonNull(currentPrice, "currentPrice");
            Objects.requireNonNull(discount, "discount");
            Objects.requireNonNull(lineTotal, "lineTotal");
        }
    }

    /**
     * The four amounts of element 04 as they cross: what the selection is worth, the catalog discounts of the same rows
     * reported beside it and never subtracted twice, one delivery rate per seller shipment, and the sum of the two.
     *
     * <p>{@code delivery} is named as the API names it (element 09 §Contract) although element 04 calls the calculation's own
     * answer {@code deliveryValue}: a payload a client reads uses the vocabulary a client reads.
     *
     * @param productValue      the selected rows, each at its quantity
     * @param discountValue     the catalog discounts of the same rows, of kind {@code discount}
     * @param delivery          one rate per seller shipment with at least one selected row
     * @param totalWithDelivery the product value plus the delivery
     */
    public record Totals(
            Money productValue,
            Money discountValue,
            Money delivery,
            Money totalWithDelivery) {

        public Totals {
            Objects.requireNonNull(productValue, "productValue");
            Objects.requireNonNull(discountValue, "discountValue");
            Objects.requireNonNull(delivery, "delivery");
            Objects.requireNonNull(totalWithDelivery, "totalWithDelivery");
        }
    }

    public Handover {
        Objects.requireNonNull(handoverId, "handoverId");
        Objects.requireNonNull(basketId, "basketId");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(contentDigest, "contentDigest");
        Objects.requireNonNull(totals, "totals");
        Objects.requireNonNull(deliveryEstimate, "deliveryEstimate");
        selection = List.copyOf(selection);
        ensureDigestIdentifiesTheContent(basketId, customerId, contentDigest, selection, totals);
    }

    /**
     * The identity of the handover that this content would produce: the digest of its canonical form.
     *
     * <p>The canonical form is the concatenation of name-tagged records — the form's own version, the basket's identity, its
     * customer, then one {@code line} record per selected row carrying offer, seller, quantity, the availability outcome with
     * its reason and its stale flag, the current price, the discount and the line total, and finally the four totals. Every
     * component is written either as {@code -} when absent or as {@code <length>:<text>}, so no component can be read as
     * another and absent is never the empty text — the same encoding {@code Order.canonicalDigest()} uses as its idempotency
     * key. The one currency of the basket is not a record of its own: every amount below states it. The delivery estimate is
     * not in the form at all, for the reason the type states (D64).
     *
     * <p>A runtime without SHA-256 fails here with {@link IllegalStateException}: it is an impossible state, not a refusal of
     * a business value, and D48 names no code for it. That is the one record of the carve-out — {@code
     * Order.canonicalDigest()} answers the same way, and {@code error.domain-throws-context-type} is aggregate-scoped, so
     * nothing but this paragraph keeps the exception honest.
     *
     * @param basketId   the basket the content belongs to
     * @param customerId the customer the basket belongs to
     * @param selection  the rows that would cross
     * @param totals     what the selection is worth, as element 04 computed it
     * @return the {@code sha256:} digest of that content
     */
    public static String contentDigestOf(String basketId, String customerId, List<Line> selection, Totals totals) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] digest = sha256.digest(
                    canonicalFormOf(basketId, customerId, selection, totals).getBytes(StandardCharsets.UTF_8));
            return DIGEST_PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(DIGEST_ALGORITHM + " is a digest every Java runtime carries", impossible);
        }
    }

    private static String canonicalFormOf(String basketId, String customerId, List<Line> selection, Totals totals) {
        StringBuilder canonical = new StringBuilder(record("form", CANONICAL_FORM));
        canonical.append(record("basket", basketId));
        canonical.append(record("customer", customerId));
        selection.stream().sorted(Handover::byOffer).forEach(line -> canonical.append(canonicalLine(line)));
        canonical.append(canonicalTotals(totals));
        return canonical.toString();
    }

    private static int byOffer(Line line, Line other) {
        return line.offerId().compareTo(other.offerId());
    }

    private static String canonicalLine(Line line) {
        return record("line", line.offerId(), line.seller(), Integer.toString(line.quantity()),
                line.availability().status().name(), line.availability().reason().name(),
                Boolean.toString(line.availability().stale()))
                + record("currentPrice", amountOf(line.currentPrice()))
                + record("discount", amountOf(line.discount()))
                + record("lineTotal", amountOf(line.lineTotal()));
    }

    private static String canonicalTotals(Totals totals) {
        return record("productValue", amountOf(totals.productValue()))
                + record("discountValue", amountOf(totals.discountValue()))
                + record("delivery", amountOf(totals.delivery()))
                + record("totalWithDelivery", amountOf(totals.totalWithDelivery()));
    }

    private static String amountOf(Money amount) {
        return record("amount", Long.toString(amount.amount()), amount.currency(), amount.kind().name());
    }

    /** One record: its components in their fixed order, each self-delimiting, so no content can pass for another component. */
    private static String record(String... components) {
        return String.join("", Stream.of(components)
                .map(component -> component == null ? ABSENT : component.length() + ":" + component)
                .toList());
    }

    /**
     * A digest that is not the digest of the content beside it is a programming error — the payload would claim one identity
     * while carrying another — so it is refused with a JDK type, never with {@link ShoppingException}: no client builds this
     * payload. It reads the parameters and not the fields, because a compact constructor assigns them only after it returns.
     */
    private static void ensureDigestIdentifiesTheContent(String basketId, String customerId, String contentDigest,
            List<Line> selection, Totals totals) {
        if (!contentDigest.equals(contentDigestOf(basketId, customerId, selection, totals))) {
            throw new IllegalArgumentException(
                    "content digest " + contentDigest + " is not the digest of the payload it is carried by");
        }
    }
}
