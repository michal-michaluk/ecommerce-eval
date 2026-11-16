package shop.catalog.reads;

import java.util.Objects;

/**
 * One reason 08 refuses a draft, in the read side's own language: the published rule label, the field it names, the severity in
 * force and the message the panel of 14 renders.
 *
 * <p>It is the read side's own closed vocabulary and not a copy of another context's finding ({@code contract.own-value-objects}):
 * the mediator of the {@code reads → quality} edge maps 08's report into it, exactly as the {@code review → quality} edge maps the
 * same report into its own. {@code rule} is the label ({@code "QP-6"}) a reader traces to the spec, never a constant of the quality
 * enum.
 */
public record QualityFinding(String rule, String field, Severity severity, String message) {

    public QualityFinding {
        rule = Objects.requireNonNull(rule, "rule");
        field = Objects.requireNonNull(field, "field");
        severity = Objects.requireNonNull(severity, "severity");
        message = Objects.requireNonNull(message, "message");
    }

    /** The two severities 08 states: only {@link #BLOCKING} disables the review request of 14. */
    public enum Severity {
        BLOCKING,
        WARNING
    }
}
