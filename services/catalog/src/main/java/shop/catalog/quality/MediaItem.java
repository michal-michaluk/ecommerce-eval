package shop.catalog.quality;

import java.util.Objects;

/**
 * One item of an offer's media as 08 reads it: an id, a kind, a position and the primary flag.
 *
 * <p>This is the consumer's own contract, not a copy of a producer's exposed snapshot: the offer context keeps its media type
 * inside the package-private {@code OfferVersion} and publishes neither a port nor a value for it, so the quality context declares
 * the four fields its rules name and nothing more (`context-boundaries.md`, "carry only the fields your own language names").
 *
 * <p>A gap in {@code position} and two primaries are legal here — QP-5 and QP-8 exist to report them, so the type refuses only
 * what it cannot represent at all.
 */
record MediaItem(String mediaId, Kind kind, int position, boolean primary) {

    MediaItem {
        Objects.requireNonNull(mediaId, "mediaId");
        Objects.requireNonNull(kind, "kind");
    }

    boolean isImage() {
        return kind == Kind.IMAGE;
    }

    boolean isVideo() {
        return kind == Kind.VIDEO;
    }

    enum Kind {
        IMAGE,
        VIDEO
    }
}
