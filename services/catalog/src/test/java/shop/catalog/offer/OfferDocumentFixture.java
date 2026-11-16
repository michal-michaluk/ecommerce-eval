package shop.catalog.offer;

import shop.catalog.offer.ProductDescription.Identifiers;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The offers the persistence tests store. Every instance carries its own identity and its own identifiers, because a stored offer
 * keeps them for good: the integration tests commit their writes, so a fixture shared between two tests would collide with itself on
 * the document's primary key or on the RULE-11 index.
 */
public class OfferDocumentFixture {

    public static OfferEditor draftOffer() {
        return draftOffer(uniqueOfferId(), uniqueProductNumber(), uniqueEanGtin());
    }

    public static OfferEditor draftOffer(String offerId, String productNumber, String eanGtin) {
        Identifiers identifiers = new Identifiers(productNumber, eanGtin, "SMART Play X-Wing");
        return OfferEditor.draftAnOffer(offerId, ProductDescriptionDraftFixture.draftWithIdentifiers(identifiers),
                OfferFixture.freeIdentifiers());
    }

    public static OfferEditor draftOfferWithMedia() {
        OfferEditor offer = draftOffer();
        offer.updateMedia(OfferFixture.mediaOfInstanceA());
        return offer;
    }

    public static OfferEditor publishedOffer() {
        return publish(draftOffer());
    }

    public static OfferEditor publishedOffer(String offerId, String productNumber, String eanGtin) {
        return publish(draftOffer(offerId, productNumber, eanGtin));
    }

    public static OfferEditor offerPendingReview() {
        OfferEditor offer = draftOfferWithMedia();
        offer.scheduleAvailability(OfferFixture.AVAILABILITY_START);
        offer.requestReview();
        return offer;
    }

    public static OfferEditor removedOffer() {
        OfferEditor offer = publishedOffer();
        offer.remove();
        return offer;
    }

    public static OfferEditor removedOffer(String offerId, String productNumber, String eanGtin) {
        OfferEditor offer = publish(draftOffer(offerId, productNumber, eanGtin));
        offer.remove();
        return offer;
    }

    public static String uniqueOfferId() {
        return "PRD-" + uniqueSuffix();
    }

    public static String uniqueProductNumber() {
        return "PN-" + uniqueSuffix();
    }

    /** A GTIN-13 with a valid check digit, so the same value is a legal draft identifier and a legal frozen one (01 RULE-2). */
    public static String uniqueEanGtin() {
        String body = "%012d".formatted(ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
        return body + checkDigit(body);
    }

    private static OfferEditor publish(OfferEditor draft) {
        draft.updateMedia(OfferFixture.mediaOfInstanceA());
        draft.scheduleAvailability(OfferFixture.AVAILABILITY_START);
        draft.requestReview();
        draft.publish(OfferFixture.now(), OfferFixture.REVIEWER, OfferFixture.freeIdentifiers());
        return draft;
    }

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static int checkDigit(String body) {
        int weighted = 0;
        for (int position = 0; position < body.length(); position++) {
            int digit = body.charAt(position) - '0';
            weighted += position % 2 == 0 ? digit : digit * 3;
        }
        return (10 - weighted % 10) % 10;
    }
}
