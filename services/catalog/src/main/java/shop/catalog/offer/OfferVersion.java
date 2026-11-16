package shop.catalog.offer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One frozen publication of an offer: the description embedded <em>by value</em> (RULE-4, RULE-10), the media and the availability
 * the reviewer accepted, and who published it when.
 *
 * <p>The version never re-reads a draft, so an edit made after the freeze cannot change what was published; a revert produces the
 * next version number with this content and names its source in {@code copiedFrom} (RULE-5).
 */
record OfferVersion(
        int number,
        ProductDescription description,
        List<Media> media,
        LocalDate availabilityStart,
        Instant publishedAt,
        String publishedBy,
        Integer copiedFrom) {

    OfferVersion {
        if (number < 1) {
            throw new OfferException(OfferException.Error.OFFER_VERSION_NUMBER_INVALID, number);
        }
        description = Objects.requireNonNull(description, "description");
        media = List.copyOf(media);
        availabilityStart = Objects.requireNonNull(availabilityStart, "availabilityStart");
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
        publishedBy = Objects.requireNonNull(publishedBy, "publishedBy");
    }

    /** The same content under the next version number, published now by the same acting reviewer (RULE-5). */
    OfferVersion copiedAs(int number, Instant publishedAt, String publishedBy) {
        return new OfferVersion(number, description, media, availabilityStart, publishedAt, publishedBy, this.number);
    }
}
