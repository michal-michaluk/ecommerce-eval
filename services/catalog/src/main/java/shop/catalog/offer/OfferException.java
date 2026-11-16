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
        PARAGRAPH_BLOCK_MISSING("at least one paragraph block is required"),
        COMMAND_VALUE_MISSING("%s is required for this command"),
        OFFER_REMOVED("offer %s is removed and can never be changed again"),
        REMOVAL_NEEDS_PUBLICATION("only a published offer can be removed, offer %s is %s"),
        PRODUCT_NUMBER_TAKEN("productNumber '%s' is already carried by another offer"),
        EAN_GTIN_TAKEN("eanGtin '%s' is already carried by another offer"),
        MEDIA_IMAGE_MISSING("offer %s needs at least one image before a review can be requested"),
        MEDIA_PRIMARY_AMBIGUOUS("offer %s needs exactly one primary image, got %s"),
        MEDIA_POSITIONS_NOT_CONTIGUOUS("offer %s needs media positions contiguous from 1, got %s"),
        MEDIA_VIDEO_COUNT("offer %s allows at most one video, got %s"),
        OFFER_VERSION_MISSING("offer %s has no version %s"),
        AVAILABILITY_START_MISSING("offer %s needs an availabilityStart before a version can be frozen"),
        REVIEW_OPEN("offer %s is under review; published content cannot change until the review is decided"),
        OFFER_VERSION_NUMBER_INVALID("offer version numbers start at 1, got %s");

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
