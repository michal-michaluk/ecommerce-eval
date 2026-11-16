package shop.catalog.review;

import shop.catalog.offer.Media;
import shop.catalog.offer.ProductDescription;

import java.time.LocalDate;
import java.util.List;

/**
 * What is being reviewed, frozen at the moment the review was requested (04 RULE-3, D38): the description <em>by value</em>, the
 * media, the availability date, the findings 08 reported and — when the author pushed a review past the gate — the override that
 * records it (RULE-8).
 *
 * <p>Freezing is what makes "what was reviewed" and "what gets published" the same content: drafts carry no version number, so a
 * candidate that re-read the offer would drift the moment the author edits after requesting. The value components are immutable
 * and the lists are copied, so no later edit of the offer can reach this object. The media is the offer's own value: one
 * {@link Media} in the service, carried through the edge by the mediator instead of mapped into a copy (D45).
 *
 * <p>{@code findings} are the frozen report, not a re-evaluation: 09 R2 reads them unchanged at acceptance, so a rule or a
 * configuration that changed in between cannot turn an accepted review into a different decision.
 */
public record ReviewCandidate(
        ProductDescription description,
        List<Media> media,
        LocalDate availabilityStart,
        List<ReviewFinding> findings,
        ReviewOverride override) {

    public ReviewCandidate {
        ReviewException.ensureValue(description, "description");
        ReviewException.ensureValue(availabilityStart, "availabilityStart");
        media = List.copyOf(ReviewException.requireValue(media, "media"));
        findings = List.copyOf(ReviewException.requireValue(findings, "findings"));
    }

    /** A request may carry findings only together with an override (RULE-8); this is the fact that decides it. */
    public boolean hasBlockingFindings() {
        return !blockingFindings().isEmpty();
    }

    public List<ReviewFinding> blockingFindings() {
        return findings.stream().filter(ReviewFinding::isBlocking).toList();
    }

    public boolean hasOverride() {
        return override != null;
    }

    /** An override that ignores nothing is no override at all: it must name every blocking finding it takes responsibility for. */
    public boolean overrideCoversBlockingFindings() {
        return hasOverride() && override.ignoredFindings().containsAll(blockingFindings());
    }
}
