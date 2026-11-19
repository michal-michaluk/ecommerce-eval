package shop.fulfillment.processing;

import java.util.regex.Pattern;

/**
 * The synthetic identity of one order processing, minted at intake — {@code OP-YYYY-NNNN}, the format the element
 * examples use.
 *
 * <p>The order's own content has no identity (element 01 RULE-1); this id is what a claim, a pick list, a parcel and
 * a customer view are addressed by, while the {@code orderHash} stays the idempotency key.
 */
record OrderProcessingId(String value) {

    private static final Pattern FORMAT = Pattern.compile("OP-\\d{4}-\\d{4}");

    OrderProcessingId {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new FulfillmentException(FulfillmentException.Error.PROCESSING_ID_MALFORMED, value);
        }
    }
}
