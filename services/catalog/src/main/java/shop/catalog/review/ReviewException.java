package shop.catalog.review;

import shop.catalog.DomainRefusal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The only exception the review context raises; the error type owns the message and carries the offending values.
 *
 * <p>It is a {@link DomainRefusal} because a refused review command is what 12 answers with `409 {reason}`: the transport reads the
 * constant's name, never the message.
 */
class ReviewException extends RuntimeException implements DomainRefusal {

    enum Error {
        VALUE_MISSING("%s is required"),
        REVIEW_ALREADY_OPEN("offer %s is already under review %s; at most one review is open per offer (RULE-1)"),
        REVIEW_ID_REUSED("review %s already exists; a new attempt is a new reviewId (RULE-7)"),
        REVIEW_NOT_OPEN("review %s is %s, so no decision is left to record"),
        REVIEWER_IS_REQUESTER("review %s was requested by %s, who cannot also review it (RULE-2)"),
        REJECTION_REASON_MISSING("review %s needs a free-text reason to be rejected (RULE-5)"),
        WITHDRAWAL_NEEDS_REQUESTER("review %s was requested by %s; only that author may withdraw it, got %s (RULE-6)"),
        FINDINGS_NEED_OVERRIDE("review %s carries blocking findings, so the author must push it with an override (RULE-8)"),
        OVERRIDE_INCOMPLETE("override of review %s must name every blocking finding it ignores (RULE-8)"),
        OVERRIDE_NOT_BY_REQUESTER("override of review %s was recorded by %s, and only the author %s may push a review (RULE-8)"),
        OVERRIDE_DISABLED("override of review %s is refused because the override is not enabled for this catalog (D27)"),
        PUBLICATION_NEEDS_ACCEPTANCE("review %s is %s, so no publication can be recorded for it (RULE-4)"),
        PUBLICATION_ALREADY_RECORDED("review %s already published offer version %s (RULE-4)"),
        OFFER_UNKNOWN("offer %s does not exist, so this review cannot be published"),
        DECISION_NOT_APPLICABLE("review %s was accepted now, so 09 has no completed acceptance to replay (D15)"),
        OFFER_VERSION_INVALID("offer version numbers start at 1, got %s"),
        DECISION_INCOMPLETE("%s must carry a reason when it blocks and nothing when it does not (09)");

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

    ReviewException(Error error, Object... details) {
        super(error.format(details));
        this.error = error;
        this.details = Collections.unmodifiableList(Arrays.asList(details));
    }

    /** The null check every value object and command of this context opens with, so a missing value is a domain refusal. */
    static void ensureValue(Object value, String name) {
        requireValue(value, name);
    }

    /** The same check for a component the caller copies: it answers the value, so a constructor stays one statement. */
    static <T> T requireValue(T value, String name) {
        if (value == null) {
            throw new ReviewException(Error.VALUE_MISSING, name);
        }
        return value;
    }

    Error error() {
        return error;
    }

    List<Object> details() {
        return details;
    }

    @Override
    public String reason() {
        return error.name();
    }
}
