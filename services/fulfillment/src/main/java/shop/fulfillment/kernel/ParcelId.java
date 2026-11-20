package shop.fulfillment.kernel;

import java.util.regex.Pattern;

/**
 * The identity of one parcel — {@code PCL-YYYY-NNNN}, the format the element examples use.
 *
 * <p>It is our id, minted when the parcel record is created; it never appears on the carrier's paper.
 */
public record ParcelId(String value) {

    private static final Pattern FORMAT = Pattern.compile("PCL-\\d{4}-\\d{4}");

    public ParcelId {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new FulfillmentException(FulfillmentException.Error.PARCEL_ID_MALFORMED, value);
        }
    }
}
