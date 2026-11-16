package shop.catalog.offer;

import shop.catalog.offer.OfferVersion.Media;

import java.util.List;

/**
 * The editing content of an offer — the description in builder form and the media in the author's order (02 Instance A). It is the
 * half of the aggregate no snapshot carries: the identity, the state, the version number and the availability date are
 * {@link Offer}, so they are not repeated here.
 *
 * <p>The persistence adapter writes it into the offer document. It stays inside the context: a consumer that needs the same content
 * declares its own record for it ({@code review.OfferContent}), because only {@link Offer} and {@link ProductDescription} are exposed.
 */
record OfferSurface(ProductDescription.Draft description, List<Media> media) {

    OfferSurface {
        media = List.copyOf(media);
    }
}
