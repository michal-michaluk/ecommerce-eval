package shop.fulfillment.processing;

import java.util.regex.Pattern;

/**
 * The identity of one reported issue — {@code ISS-YYYY-NNNN}, the format the element examples use.
 */
record IssueId(String value) {

    private static final Pattern FORMAT = Pattern.compile("ISS-\\d{4}-\\d{4}");

    IssueId {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new FulfillmentException(FulfillmentException.Error.ISSUE_ID_MALFORMED, value);
        }
    }
}
