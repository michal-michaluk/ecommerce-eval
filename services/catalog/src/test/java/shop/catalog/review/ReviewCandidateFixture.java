package shop.catalog.review;

import shop.catalog.offer.Media;
import shop.catalog.offer.ProductDescription;
import shop.catalog.offer.ProductDescription.Brand;
import shop.catalog.offer.ProductDescription.Condition;
import shop.catalog.offer.ProductDescription.DescriptionBlock.BulletList;
import shop.catalog.offer.ProductDescription.DescriptionBlock.Heading;
import shop.catalog.offer.ProductDescription.DescriptionBlock.Paragraph;
import shop.catalog.offer.ProductDescription.Identifiers;
import shop.catalog.offer.ProductDescription.Parameter;
import shop.catalog.offer.ProductDescription.Parameter.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The content a review decides on: the LEGO sample of 01 as the offer holds it, the same offer after the author edited it, the
 * media, the findings 08 reports and the candidates the 04 rules talk about.
 *
 * <p>The two descriptions are the point of RULE-3: same offer, different content, so a candidate that re-read the offer instead of
 * freezing what it saw differs from the reviewed value.
 */
public class ReviewCandidateFixture {

    public static final String TITLE = "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423";
    public static final String EDITED_TITLE = "LEGO Star Wars SMART Play X-Wing 75423 (po korekcie autora)";
    public static final String PRODUCT_NUMBER = "75423";
    public static final String EAN_GTIN = "5702017817705";
    public static final List<String> LEGO_CATEGORY_PATH =
            List.of("Dziecko", "Zabawki", "Klocki", "LEGO", "Zestawy", "Star Wars");
    public static final LocalDate AVAILABILITY_START = LocalDate.of(2026, 7, 1);
    public static final Instant OVERRIDE_AT = Instant.parse("2025-06-14T07:20:00Z");
    public static final String LOCATION = "https://cdn.example/";

    private ReviewCandidateFixture() {
    }

    public static ProductDescription descriptionOfInstanceA() {
        return new ProductDescription(
                TITLE, identifiers(), new Brand("LEGO", true), Condition.NEW, LEGO_CATEGORY_PATH,
                parameters(), blocks());
    }

    public static ProductDescription editedDescription() {
        return new ProductDescription(
                EDITED_TITLE, identifiers(), new Brand("LEGO", true), Condition.NEW, LEGO_CATEGORY_PATH,
                parameters(), List.of(new Paragraph("Opis po korekcie autora, wpisany po zgłoszeniu do przeglądu.")));
    }

    public static List<Media> mediaOfInstanceA() {
        return List.of(
                new Media("M-01", Media.Kind.IMAGE, 1, true, "jpg", LOCATION + "M-01.jpg"),
                new Media("M-02", Media.Kind.IMAGE, 2, false, "jpg", LOCATION + "M-02.jpg"),
                new Media("M-03", Media.Kind.VIDEO, 3, false, "mp4", LOCATION + "M-03.mp4"));
    }

    public static OfferContent content() {
        return new OfferContent(descriptionOfInstanceA(), mediaOfInstanceA(), AVAILABILITY_START);
    }

    public static OfferContent contentWithMedia(ProductDescription description, List<Media> media) {
        return new OfferContent(description, media, AVAILABILITY_START);
    }

    public static OfferContent editedContent() {
        return new OfferContent(editedDescription(), mediaOfInstanceA(), AVAILABILITY_START);
    }

    public static ReviewCandidate candidate() {
        return candidateOf(content());
    }

    public static ReviewCandidate candidateOf(OfferContent content) {
        return candidateOf(content, List.of(), null);
    }

    public static ReviewCandidate candidateOf(OfferContent content, List<ReviewFinding> findings) {
        return candidateOf(content, findings, null);
    }

    public static ReviewCandidate candidateOf(
            OfferContent content, List<ReviewFinding> findings, ReviewOverride override) {
        return new ReviewCandidate(
                content.description(), content.media(), content.availabilityStart(), findings, override);
    }

    public static ReviewCandidate candidateWithBlockingFindings() {
        return candidateOf(content(), blockingFindings());
    }

    public static ReviewCandidate candidateWithWarningsOnly() {
        return candidateOf(content(), warningsOnly());
    }

    public static ReviewCandidate candidatePushedWithAnOverride(String actor) {
        List<ReviewFinding> findings = blockingFindings();
        return candidateOf(content(), findings, overrideBy(actor, findings));
    }

    public static ReviewCandidate candidatePushedWithAMixedReport(String actor) {
        List<ReviewFinding> findings = blockingAndWarningFindings();
        return candidateOf(content(), findings, overrideBy(actor, findings));
    }

    public static ReviewCandidate candidateWithOneBlockingFinding() {
        return candidateOf(content(), List.of(missingEanGtin()));
    }

    /** Two primaries, which 02 RULE-1 refuses when a version freezes — the content that makes a publication fail. */
    public static List<Media> mediaWithTwoPrimaries() {
        return List.of(
                new Media("M-01", Media.Kind.IMAGE, 1, true, "jpg", LOCATION + "M-01.jpg"),
                new Media("M-02", Media.Kind.IMAGE, 2, true, "jpg", LOCATION + "M-02.jpg"));
    }

    public static ReviewCandidate candidateWithUnpublishableMedia() {
        return candidateOf(contentWithMedia(descriptionOfInstanceA(), mediaWithTwoPrimaries()));
    }

    public static ReviewCandidate candidateWithAStaleOverride(String actor) {
        return candidateOf(content(), List.of(), overrideBy(actor, blockingFindings()));
    }

    public static ReviewCandidate candidateWhoseOverrideIgnoresOnlyOneFinding(String actor) {
        return candidateOf(content(), blockingFindings(), overrideBy(actor, List.of(missingEanGtin())));
    }

    public static List<ReviewFinding> blockingFindings() {
        return List.of(missingEanGtin(), proseTooShort());
    }

    public static List<ReviewFinding> blockingAndWarningFindings() {
        return List.of(missingEanGtin(), proseTooShort(), mediaPositionsNotContiguous());
    }

    public static List<ReviewFinding> warningsOnly() {
        return List.of(mediaPositionsNotContiguous());
    }

    public static ReviewFinding missingEanGtin() {
        return new ReviewFinding("QP-2", "description.ids.eanGtin", ReviewFinding.Severity.BLOCKING,
                "eanGtin must be 13 digits with a valid GTIN-13 check digit");
    }

    public static ReviewFinding proseTooShort() {
        return new ReviewFinding("QP-6", "description.blocks", ReviewFinding.Severity.BLOCKING,
                "prose 640 < 1000 (minProseChars)");
    }

    public static ReviewFinding mediaPositionsNotContiguous() {
        return new ReviewFinding("QP-8", "media", ReviewFinding.Severity.WARNING,
                "media positions must be contiguous from 1, got [1, 3]");
    }

    public static ReviewOverride overrideBy(String actor, List<ReviewFinding> ignoredFindings) {
        return new ReviewOverride(actor, OVERRIDE_AT, ignoredFindings);
    }

    private static Identifiers identifiers() {
        return new Identifiers(PRODUCT_NUMBER, EAN_GTIN, "SMART Play X-Wing Czerwona Piątka Luke'a");
    }

    private static List<Parameter> parameters() {
        return List.of(
                new Parameter("childAge", "Wiek dziecka", new Value.RangeFrom(new BigDecimal("6"), "lat")),
                new Parameter("pieceCount", "Liczba elementów", new Value.Whole(581)));
    }

    private static List<ProductDescription.DescriptionBlock> blocks() {
        return List.of(
                new Heading(3, TITLE),
                new Paragraph("Odkryj nową galaktykę LEGO Star Wars dzięki interaktywnej zabawce konstrukcyjnej."),
                new BulletList(List.of("SMART klocek umożliwia zabawę bez ekranu.")));
    }
}
