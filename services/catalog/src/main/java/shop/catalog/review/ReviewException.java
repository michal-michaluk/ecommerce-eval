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

    private static final String NOT_OPEN = "not open";

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
        QUALITY_FINDINGS("review %s cannot be accepted while 08 reports %s (RULE-8, D27)"),
        OFFER_REMOVED("review %s cannot publish offer %s: the offer is removed (09 R7)"),
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

    /**
     * The refusal a decision takes when 09 refused to publish the acceptance it just recorded (09 R2, D27): the review says so in its own
     * vocabulary and carries the frozen findings, so the client reads the report the reviewer did instead of a second list (D49).
     *
     * <p>It is raised after the acceptance committed, because 09 records the block as a fact of the review ({@code PublishBlocked}), so
     * the reason is stated here and the transport adds no rule of its own. With an accepted review that published nothing 09's table
     * leaves two rows — R2, blocking findings, and R7, the offer is removed — which are the two refusals below; it is never called for a
     * published acceptance, which is no refusal at all (D15, 09 R6).
     */
    static ReviewException blockedAcceptanceOf(Review accepted) {
        return accepted.candidate().hasBlockingFindings()
                ? new ReviewException(Error.QUALITY_FINDINGS, accepted.reviewId(), accepted.candidate().blockingFindings())
                : new ReviewException(Error.OFFER_REMOVED, accepted.reviewId(), accepted.offerId());
    }

    /** 12's `REVIEW_NOT_OPEN`: the answer both to an offer that never asked and to a review that is already decided. */
    static ReviewException notOpen(String reviewId) {
        return new ReviewException(Error.REVIEW_NOT_OPEN, reviewId, NOT_OPEN);
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

    /**
     * The frozen findings a blocked acceptance carries (12's `findings?`, D49): the report 09 refuses to publish past, so the client
     * reads the same reasons the reviewer does instead of a second list invented on the wire.
     */
    @Override
    public List<?> findings() {
        return error == Error.QUALITY_FINDINGS && details.size() > 1 ? (List<?>) details.get(1) : List.of();
    }
}
