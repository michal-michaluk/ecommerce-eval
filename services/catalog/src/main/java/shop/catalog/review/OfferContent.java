package shop.catalog.review;

import shop.catalog.offer.ProductDescription;

import java.time.LocalDate;
import java.util.List;

/**
 * What the offer currently holds, handed to the review context so a candidate can be frozen from it: the description, the media
 * and the availability date (04 RULE-3, D38).
 *
 * <p>The description arrives as the frozen {@link ProductDescription} value, not as the offer's draft: a draft never leaves the
 * offer context (01 RULE-4), and the provider is the only place that can turn its draft into a value — refusing an incomplete
 * draft with its own failure instead of a foreign exception. The review context therefore never names a draft type.
 *
 * <p>This is the review context's contract in its own language, so the offer context can be replaced without touching it
 * ({@code context-boundaries.md}). It is also the answer {@code OfferDraftSnapshot} gives and the question
 * {@code QualityFindings} is asked about: 08 judges exactly the content a candidate will freeze.
 */
public record OfferContent(ProductDescription description, List<ReviewMedia> media, LocalDate availabilityStart) {

    public OfferContent {
        ReviewException.ensureValue(description, "description");
        media = List.copyOf(media);
        ReviewException.ensureValue(availabilityStart, "availabilityStart");
    }
}
