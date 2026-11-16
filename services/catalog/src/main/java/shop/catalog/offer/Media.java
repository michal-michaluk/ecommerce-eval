package shop.catalog.offer;

import java.util.Objects;

/**
 * One picture or film of an offer, in the author's order. A draft may carry a position gap or two primaries: RULE-1 and RULE-2 are
 * enforced when a review is requested and again when a version is frozen, so the violating draft stays a legal draft (RULE-8).
 *
 * <p>It is a value of this context like {@link ProductDescription}, and public for the same reason: a version embeds it by value, and
 * a mediator that hands a reviewed candidate over has to name the shape it maps into ({@code mediator.mapping}). It carries no
 * identity of its own — {@code mediaId} names the file, not the item — so two versions may share one.
 *
 * <p>{@code url} is the item's location: the shop resolves a file through it and never fetches it, so a media item without one is
 * unusable and refused here rather than at the freeze (RULE-2a, D17, D47).
 */
public record Media(String mediaId, Kind kind, int position, boolean primary, String format, String url) {

    public Media {
        mediaId = Objects.requireNonNull(mediaId, "mediaId");
        kind = Objects.requireNonNull(kind, "kind");
        url = checkedUrl(url);
    }

    private static String checkedUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new OfferException(OfferException.Error.MEDIA_URL_MISSING, url);
        }
        return url;
    }

    boolean isImage() {
        return kind == Kind.IMAGE;
    }

    boolean isVideo() {
        return kind == Kind.VIDEO;
    }

    /** The two kinds 02 accepts; a position and a primary flag are the rules' business, not the kind's (RULE-1, RULE-2). */
    public enum Kind {
        IMAGE,
        VIDEO
    }
}
