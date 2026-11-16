package shop.catalog.review;

/**
 * One reason 08 refused the content under review, stated in the review context's own language: the rule that reported it, the
 * field that rule named, the severity in force and the message a reviewer reads.
 *
 * <p>The review context declares the four facts it needs in its own language and the mediator maps 08's report into them; 08's
 * {@code QualityFindings} and {@code Finding} are exported for that one edge and for nothing else (D42). {@code rule} is the
 * published label of the 08 rule ({@code "QP-1"}), not a copy of the quality enum: the label is what a reviewer reads and what the
 * API shows, so a renamed constant cannot silently reword a frozen finding.
 *
 * <p>{@link Severity#BLOCKING} is what 09 R2 refuses to publish; a {@link Severity#WARNING} never blocks (08 QP-8, QP-12), which
 * is why the severity travels with the finding and is not recomputed at acceptance. The two constants are the consumer's vocabulary
 * and not a copy of 08's enum ({@code contract.own-value-objects}): a closed set is what the port is allowed to answer with.
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
