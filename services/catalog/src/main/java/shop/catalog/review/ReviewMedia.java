package shop.catalog.review;

/**
 * One item of the reviewed media, as the review context reads it: an id, a kind, a position, the primary flag and the stored
 * format.
 *
 * <p>The offer context keeps its own media type inside the package-private {@code OfferVersion} and publishes no value for it, so
 * the review context declares the five fields its candidate must carry to be published unchanged — the same reason the quality
 * context declares its own {@code MediaItem} ({@code context-boundaries.md}, "carry only the fields your own language names").
 * The candidate freezes them at request (RULE-3), so the freeze is lossless: a mediator maps these back into the offer's media
 * when the accepted candidate is published (D38).
 *
 * <p>A gap in {@code position} and two primaries are legal here — 02 RULE-1 and RULE-2 report them and the offer refuses the
 * request before a candidate ever exists, so this type refuses only what it cannot represent at all.
 */
public record ReviewMedia(String mediaId, Kind kind, int position, boolean primary, String format) {

    public ReviewMedia {
        ReviewException.ensureValue(mediaId, "mediaId");
        ReviewException.ensureValue(kind, "kind");
    }

    public enum Kind {
        IMAGE,
        VIDEO
    }
}
