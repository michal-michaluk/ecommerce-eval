package shop.fulfillment.processing;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The only exception this context raises. An unchecked failure carries the error type — which owns the message — and
 * the domain values a caller or an adapter may need, so no caller matches on a message string and no domain error
 * needs its own class.
 */
class FulfillmentException extends RuntimeException {

    /** The context's whole error vocabulary, each constant owning its message template. */
    enum Error {
        ORDER_INCOMPLETE("the order is incomplete, missing %s"),
        ISSUE_INCOMPLETE("the issue is incomplete, missing %s"),
        ORDER_LINE_DUPLICATE("product %s appears more than once in the order"),
        QUANTITY_NOT_POSITIVE("quantity must be at least 1, got %s"),
        DELIVERY_LOCATION_AMBIGUOUS("delivery location %s of kind %s must carry a pickup point or an address, never both"),
        COMMAND_VALUE_MISSING("%s is required for this command"),
        PROCESSING_ID_MALFORMED("%s is not an order processing id of the form OP-YYYY-NNNN"),
        PARCEL_ID_MALFORMED("%s is not a parcel id of the form PCL-YYYY-NNNN"),
        ISSUE_ID_MALFORMED("%s is not an issue id of the form ISS-YYYY-NNNN"),
        PARCEL_REFERENCE_MALFORMED("%s is not a parcel reference of at most 50 characters"),
        CARRIER_IDENTITY_MALFORMED("%s is not a carrier identity"),
        PRODUCT_NUMBER_MALFORMED("%s is not a product number"),
        LOCATION_CODE_MALFORMED("%s is not a warehouse location code"),
        WEIGHT_NOT_POSITIVE("parcel weight must be at least one gram, got %s"),
        PARCEL_SEQUENCE_IMPOSSIBLE("parcel %s of %s is not a position in the sequence"),
        PARCEL_SPLIT_UNSUPPORTED("order %s owns exactly one parcel, registration asked for %s"),
        PARCEL_REFERENCE_MISMATCH("carrier answered %s for reference %s"),
        ASSIGNMENT_REFUSED("claim on %s refused: %s"),
        READINESS_REFUSED("order %s cannot be marked ready for delivery, reasons %s"),
        HOLD_REFUSED("issue on %s refused: %s"),
        ISSUES_REMAIN("%s still accounts for open issues %s"),
        ISSUE_UNKNOWN("%s is not an issue of %s"),
        ISSUE_NOT_REPORTED_BY_ASSIGNEE("%s does not hold %s, only the assigned employee reports a warehouse issue"),
        POSITION_UNKNOWN("order %s has no position for product %s"),
        STOCK_OBSERVATION_MISSING("no stockAt observation for product %s"),
        WORK_NOT_STARTED("order %s is not assigned to anybody, %s is refused until an employee claims it"),
        POSITION_NOT_PICKED("position %s is %s, only a picked position can be packed"),
        WORK_IS_ON_HOLD("order %s is on hold, %s is refused until the hold is lifted");

        private final String template;

        Error(String template) {
            this.template = template;
        }

        String format(Object... details) {
            return template.formatted(details);
        }
    }

    private final Error error;
    private final List<Object> details;

    FulfillmentException(Error error, Object... details) {
        super(error.format(details));
        this.error = error;
        this.details = Collections.unmodifiableList(Arrays.asList(details));
    }

    FulfillmentException(Error error, Throwable cause, Object... details) {
        super(error.format(details), cause);
        this.error = error;
        this.details = Collections.unmodifiableList(Arrays.asList(details));
    }

    Error error() {
        return error;
    }

    List<Object> details() {
        return details;
    }
}
