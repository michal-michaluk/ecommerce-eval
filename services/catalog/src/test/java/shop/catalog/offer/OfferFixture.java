package shop.catalog.offer;

import shop.catalog.offer.ProductDescription.DescriptionBlock;
import shop.catalog.offer.ProductDescription.Draft;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/** The offers the 02 rules talk about: one per lifecycle state, plus the drafts and media that must be refused. */
public class OfferFixture {

    public static final String OFFER_ID = "PRD-75423";
    public static final String OTHER_OFFER_ID = "PRD-75375";
    public static final String REVIEWER = "reviewer.kowalska";
    public static final LocalDate AVAILABILITY_START = LocalDate.of(2026, 7, 1);
    public static final Instant PUBLISHED_AT = Instant.parse("2025-06-15T08:00:00Z");

    public static Clock fixedClock() {
        return Clock.fixed(PUBLISHED_AT, ZoneOffset.ofHours(2));
    }

    public static Instant now() {
        return Instant.now(fixedClock());
    }

    public static LocalDate today() {
        return LocalDate.now(fixedClock());
    }

    public static FakeOfferRegistry freeIdentifiers() {
        return new FakeOfferRegistry();
    }

    public static OfferEditor draftOffer() {
        return OfferEditor.draftAnOffer(OFFER_ID, ProductDescriptionDraftFixture.draftOfInstanceA(), freeIdentifiers());
    }

    public static OfferEditor draftOfferWithMedia() {
        OfferEditor offer = draftOfferWithMediaOnly();
        offer.scheduleAvailability(AVAILABILITY_START);
        return offer;
    }

    public static OfferEditor draftOfferWithMediaOnly() {
        OfferEditor offer = draftOffer();
        offer.updateMedia(mediaOfInstanceA());
        return offer;
    }

    public static OfferEditor offerPendingReview() {
        OfferEditor offer = draftOfferWithMedia();
        offer.requestReview();
        return offer;
    }

    public static OfferEditor publishedOffer() {
        OfferEditor offer = offerPendingReview();
        offer.publish(now(), REVIEWER, freeIdentifiers());
        return offer;
    }

    public static OfferEditor removedOffer() {
        OfferEditor offer = publishedOffer();
        offer.remove();
        return offer;
    }

    public static Draft aDifferentDraft() {
        return ProductDescriptionDraftFixture.draftWithBlocks(List.of(new DescriptionBlock.Paragraph("Wersja druga opisu.")));
    }

    public static List<Media> mediaOfInstanceA() {
        return List.of(image("M-01", 1, true), image("M-02", 2, false), image("M-03", 3, false));
    }

    public static List<Media> mediaWithoutImage() {
        return List.of(video("V-01", 1, true));
    }

    public static List<Media> mediaWithTwoPrimaries() {
        return List.of(image("M-01", 1, true), image("M-02", 2, true));
    }

    public static List<Media> mediaWithoutPrimary() {
        return List.of(image("M-01", 1, false), image("M-02", 2, false));
    }

    public static List<Media> mediaWithPrimaryVideo() {
        return List.of(video("V-01", 1, true), image("M-01", 2, false));
    }

    public static List<Media> mediaWithPositionGap() {
        return List.of(image("M-01", 1, true), image("M-03", 3, false));
    }

    public static List<Media> mediaWithTwoVideos() {
        return List.of(image("M-01", 1, true), video("V-01", 2, false), video("V-02", 3, false));
    }

    public static Media image(String mediaId, int position, boolean primary) {
        return new Media(mediaId, Media.Kind.IMAGE, position, primary, "jpg");
    }

    public static Media video(String mediaId, int position, boolean primary) {
        return new Media(mediaId, Media.Kind.VIDEO, position, primary, "mp4");
    }

    public static OfferEvent.OfferVersionFrozen versionFrozen(int offerVersion, ProductDescription description) {
        return versionFrozen(OFFER_ID, offerVersion, description);
    }

    public static OfferEvent.OfferVersionFrozen versionFrozen(String offerId, int offerVersion, ProductDescription description) {
        return new OfferEvent.OfferVersionFrozen(offerId, offerVersion, description);
    }

    public static OfferEvent.OfferVersionReverted versionReverted(int offerVersion, int copiedFrom) {
        return new OfferEvent.OfferVersionReverted(OFFER_ID, offerVersion, copiedFrom);
    }

    public static OfferVersion versionWithNumber(int number) {
        return new OfferVersion(number, ProductDescriptionFixture.descriptionOfInstanceA(), mediaOfInstanceA(),
                AVAILABILITY_START, now(), REVIEWER, null);
    }
}
