package shop.catalog.review;

/**
 * One item of the reviewed media, as the review context reads it: an id, a kind, a position, the primary flag and the stored
 * format.
 *
 * <p>The offer context declares its own media value ({@code offer.Media}) and this one is the review's, because a consumer states the
 * fields its own language names ({@code context-boundaries.md}) — the same reason the quality context declares {@code MediaItem}.
 * The candidate freezes them at request (RULE-3), so the freeze is lossless: the mediator that joins the two contexts maps these
 * back into the offer's media when the accepted candidate is published (D38).
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
