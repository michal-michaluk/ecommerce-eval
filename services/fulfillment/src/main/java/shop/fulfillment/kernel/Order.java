package shop.fulfillment.kernel;

import lombok.Builder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The immutable document describing what the customer bought and under which terms — content only, produced by
 * checkout and never edited here (element 01, RULE-1…RULE-8).
 *
 * <p>The order carries no identity, no status, no history and no money. Two orders with equal content are the same
 * order, so equality is the canonical {@code orderHash} and lines are held in product-number order, whatever order the
 * handover listed them in.
 *
 * <p>{@code delivery.methodCode} and {@code payment.methodCode} are opaque strings: this context never enumerates or
 * re-validates them, because checkout's own guard already did.
 */
@Builder
public record Order(
        String orderHash,
        Instant placedAt,
        List<OrderLine> lines,
        Delivery delivery,
        Payment payment,
        Invoicing invoicing) {

    /** The version of the canonical form, so a later change to the form is a different form rather than a silent one. */
    private static final String CANONICAL_FORM = "shop.fulfillment.order/v1";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final String DIGEST_PREFIX = "sha256:";
    private static final String ABSENT = "-";

    public Order {
        List<String> missing = new ArrayList<>();
        addIfMissing(missing, orderHash == null || orderHash.isBlank(), "orderHash");
        addIfMissing(missing, placedAt == null, "placedAt");
        addIfMissing(missing, lines == null || lines.isEmpty(), "lines");
        addIfMissing(missing, delivery == null, "delivery");
        addIfMissing(missing, payment == null, "payment");
        addIfMissing(missing, invoicing == null, "invoicing");
        ensureNothingIsMissing(missing);
        lines = List.copyOf(lines.stream().sorted(Comparator.comparing(OrderLine::productNumber)).toList());
        ensureLinesAreDistinct(lines);
    }

    /**
     * Two orders are the same order when their canonical content hashes agree, whatever else the two handovers
     * carried.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Order order && Objects.equals(orderHash, order.orderHash);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(orderHash);
    }

    /**
     * The identity of this order's own content: the digest of its canonical form, so two handovers carrying the same order
     * answer the same value however they were written and whatever {@code orderHash} they claimed (element 01 RULE-1). It is
     * what lets intake tell a repeated delivery from a second, different order sent under a hash that is already in use.
     *
     * <p>The canonical form is the concatenation of name-tagged records, in this order: the form's own version, {@code placedAt}
     * with its ISO-8601 instant, one {@code line} record per line carrying product number, name and quantity (ascending product
     * number, which the constructor already guarantees), {@code delivery} with its method code, its location kind, its point id
     * and the four components of its address, {@code payment} with its method code, and {@code invoicing} with the buyer name,
     * the tax id and the four components of its address.
     *
     * <p>Every component is written either as {@code -} when it is absent or as {@code <length>:<text>}, where the length counts
     * the UTF-16 units of the text. That encoding is what makes the form injective: a component can never be read as another
     * component or as another record, whatever characters it carries, and absent is never the empty text ({@code 0:}). No
     * component is rejected or escaped for the digest's sake, so two orders whose content differs encode differently — only
     * SHA-256 itself could make their digests agree. {@code orderHash} is not part of the form — it is the idempotency key of
     * the handover, not content, and neither its {@code sha256:} prefix nor its length is assumed.
     *
     * @return the {@code sha256:} digest of this order's canonical form
     */
    public String canonicalDigest() {
        try {
            MessageDigest sha256 = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return DIGEST_PREFIX + HexFormat.of().formatHex(sha256.digest(canonicalForm().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(DIGEST_ALGORITHM + " is a digest every Java runtime carries", impossible);
        }
    }

    private String canonicalForm() {
        StringBuilder canonical = new StringBuilder(record("form", CANONICAL_FORM));
        canonical.append(record("placedAt", placedAt.toString()));
        lines.forEach(line -> canonical.append(record("line", line.productNumber(), line.name(),
                Integer.toString(line.quantity()))));
        canonical.append(record("delivery", delivery.methodCode(), delivery.location().kind().name(),
                delivery.location().pointId()));
        canonical.append(address("delivery.address", delivery.location().address()));
        canonical.append(record("payment", payment.methodCode()));
        canonical.append(record("invoicing", invoicing.buyerName(), invoicing.nip()));
        canonical.append(address("invoicing.address", invoicing.address()));
        return canonical.toString();
    }

    /** One record: its components in their fixed order, each self-delimiting, so no content can pass for another component. */
    private static String record(String... components) {
        return String.join("", Arrays.stream(components)
                .map(component -> component == null ? ABSENT : component.length() + ":" + component)
                .toList());
    }

    private static String address(String name, Address address) {
        return address == null
                ? record(name, null, null, null, null)
                : record(name, address.street(), address.city(), address.postalCode(), address.country());
    }

    private static void ensureNothingIsMissing(List<String> missing) {
        if (!missing.isEmpty()) {
            throw new FulfillmentException(FulfillmentException.Error.ORDER_INCOMPLETE, missing);
        }
    }

    private static void addIfMissing(List<String> missing, boolean absent, String name) {
        if (absent) {
            missing.add(name);
        }
    }

    private static void ensureLinesAreDistinct(List<OrderLine> lines) {
        Set<String> products = new HashSet<>();
        for (OrderLine line : lines) {
            if (!products.add(line.productNumber())) {
                throw new FulfillmentException(
                        FulfillmentException.Error.ORDER_LINE_DUPLICATE, line.productNumber());
            }
        }
    }
}
