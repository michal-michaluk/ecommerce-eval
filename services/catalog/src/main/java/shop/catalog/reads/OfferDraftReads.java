package shop.catalog.reads;

import shop.catalog.offer.Media;
import shop.catalog.offer.Offer;
import shop.catalog.offer.ProductDescription;

import java.time.LocalDate;
import java.util.List;

/**
 * What the draft screen of 14 renders, denormalized into one row: the offer's state and availability date, the description in
 * builder form with its media in the author's order, the findings 08 reports on that draft, and whether a customer can see it today.
 *
 * <p>Nothing here is the offer context's aggregate, entity or snapshot — the values are the shared kernel's ({@link Offer.State},
 * {@link ProductDescription.Draft}, {@link Media}), because a draft carries no identity or lifecycle of its own and 17 exports it
 * for exactly this. The two answers that are not stored are the ones that depend on something other than the last write: the
 * findings are 08's answer about the draft as it is now, and visibility is a question about the clock (11).
 *
 * <p>{@link Offer.State} is a value object and not a persistence type: the draft screen enables its actions on the lifecycle 02
 * owns (14 "Action enablement"), so a local copy of the enum would be the {@code contract.no-local-copy} mistake —
 * {@code http.no-persistence-type-in-api} bars entities, snapshots and repositories, and this is none of them.
 */
record OfferDraftReads(
        String offerId,
        Offer.State state,
        LocalDate availabilityStart,
        ProductDescription.Draft description,
        List<Media> media,
        List<QualityFinding> findings,
        OfferVisibility.Outcome visibility) {

    OfferDraftReads {
        media = List.copyOf(media);
        findings = List.copyOf(findings);
    }

    /**
     * The whole read model in one place, or {@code null} when the stored row cannot produce one: a row without a description is a
     * row no screen can render, and an empty form would lie about the draft instead of saying it is absent.
     */
    static OfferDraftReads ofNullable(
            OfferDraftReadsProjection.OfferDraftRow row, List<QualityFinding> findings, OfferVisibility.Outcome visibility) {
        ProductDescription.Draft description = row.description();
        if (description == null) {
            return null;
        }
        return new OfferDraftReads(row.offerId(), row.state(), row.availabilityStart(), description, row.media(),
                findings, visibility);
    }

    /** The findings that stop the review request of 14; the panel renders them above the warnings (08 QP-8, QP-12). */
    List<QualityFinding> blockingFindings() {
        return findings.stream().filter(finding -> finding.severity() == QualityFinding.Severity.BLOCKING).toList();
    }
}
