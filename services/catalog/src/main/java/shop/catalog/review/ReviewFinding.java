package shop.catalog.review;

/**
 * One reason 08 refused the content under review, stated in the review context's own language: the rule that reported it, the
 * field that rule named, the severity in force and the message a reviewer reads.
 *
 * <p>The quality context answers through a contract port and keeps {@code QualityFindings} and its {@code Finding} inside its own
 * package, so the review context declares the four facts it needs and the mediator maps one into the other. {@code rule} is the
 * published label of the 08 rule ({@code "QP-1"}), never a copy of the quality enum: a copy would be a second vocabulary with no
 * compiler between the two.
 *
 * <p>{@link Severity#BLOCKING} is what 09 R2 refuses to publish; a {@link Severity#WARNING} never blocks (08 QP-8, QP-12), which
 * is why the severity travels with the finding and is not recomputed at acceptance.
 */
public record ReviewFinding(String rule, String field, Severity severity, String message) {

    public ReviewFinding {
        ReviewException.ensureValue(rule, "rule");
        ReviewException.ensureValue(field, "field");
        ReviewException.ensureValue(severity, "severity");
        ReviewException.ensureValue(message, "message");
    }

    public boolean isBlocking() {
        return severity == Severity.BLOCKING;
    }

    public enum Severity {
        BLOCKING,
        WARNING
    }
}
