package shop.catalog.review;

import java.time.Instant;
import java.util.List;

/**
 * The author's push past the quality gate (04 RULE-8): who recorded it, when, and the findings it takes responsibility for.
 *
 * <p>It exists only at the review <em>request</em> and only for the author — {@code actor} is the requester, and the aggregate
 * refuses an override recorded by anyone else. {@link #ignoredFindings} must name every blocking finding it overrides, so the
 * record explains itself without re-reading the report.
 *
 * <p>It is not an authorisation to publish: 09 R2 blocks an accepted review whose candidate still carries a blocking finding, so
 * an override can open a review and never force one through (D27, F15). The reviewer has no counterpart to this type, and the
 * whole mechanism can be switched off for a catalog ({@code catalog.publish.override.enabled}, D27/D29).
 */
public record ReviewOverride(String actor, Instant recordedAt, List<ReviewFinding> ignoredFindings) {

    public ReviewOverride {
        ReviewException.ensureValue(actor, "actor");
        ReviewException.ensureValue(recordedAt, "recordedAt");
        ignoredFindings = List.copyOf(ReviewException.requireValue(ignoredFindings, "ignoredFindings"));
    }
}
