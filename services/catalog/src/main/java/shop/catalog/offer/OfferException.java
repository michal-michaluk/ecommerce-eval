package shop.catalog.offer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The only exception the offer context raises; the error type owns the message and carries the offending values. */
class OfferException extends RuntimeException {

    enum Error {
        TITLE_INVALID("title must be 1-200 characters, got '%s'"),
        PRODUCT_NUMBER_MISSING("productNumber is required"),
        EAN_GTIN_INVALID("eanGtin must be 13 digits with a valid GTIN-13 check digit, got '%s'"),
        BRAND_MISSING("brand is required"),
        BRAND_NAME_MISSING("a brand name is required"),
        CONDITION_MISSING("condition is required"),
        CATEGORY_PATH_MISSING("categoryPath must name at least one category"),
        PARAMETER_CODE_MISSING("a parameter code is required"),
        PARAMETER_CODE_DUPLICATED("parameter code '%s' appears more than once"),
        PARAMETER_VALUE_MISSING("parameter '%s' carries no value"),
        DESCRIPTION_BLOCKS_MISSING("at least one description block is required"),
        PARAGRAPH_BLOCK_MISSING("at least one paragraph block is required");

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

    OfferException(Error error, Object... details) {
        super(error.format(details));
        this.error = error;
        this.details = Collections.unmodifiableList(Arrays.asList(details));
    }

    OfferException(Error error, Throwable cause, Object... details) {
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
