package shop.catalog.offer;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The content a version freezes: the description <em>by value</em>, the media in the author's order and the date the product becomes
 * available — the three components {@link OfferVersion} embeds (RULE-4).
 *
 * <p>It exists because there are two sources for that content and one freeze: the draft the offer holds now ({@link OfferEditor#publish})
 * and the candidate 04 accepted at request time ({@link OfferEditor#publishCandidate}, D38). Naming the second one as a value keeps the
 * freeze one code path, so a version published from a candidate is validated and stored exactly like a version published from the draft.
 *
 * <p>{@code availabilityStart} may be absent: a draft without a date is legal and the freeze is what refuses it (RULE-3).
 */
public record OfferCandidate(ProductDescription description, List<Media> media, LocalDate availabilityStart) {

    public OfferCandidate {
        description = Objects.requireNonNull(description, "description");
        media = List.copyOf(media);
    }
}
