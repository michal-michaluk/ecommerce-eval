package shop.catalog.quality;

import shop.catalog.offer.Media;
import shop.catalog.offer.ProductDescription;
import shop.catalog.offer.ProductDescription.DescriptionBlock;
import shop.catalog.offer.ProductDescription.DescriptionBlock.BulletList;
import shop.catalog.offer.ProductDescription.DescriptionBlock.Heading;
import shop.catalog.offer.ProductDescription.DescriptionBlock.Image;
import shop.catalog.offer.ProductDescription.DescriptionBlock.Paragraph;
import shop.catalog.offer.ProductDescription.Identifiers;
import shop.catalog.offer.ProductDescription.Parameter;
import shop.catalog.offer.ProductDescription.Parameter.Value;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Instance A of 08 — the LEGO sample — plus every edit its scenarios need. Its prose is the measured 1 626: three paragraphs of
 * 449 / 382 / 194 characters and eight bullet items of 601 in total, while the heading's 61 characters are excluded by the rule.
 *
 * <p>A draft is edited through {@code toBuilder()}, so each factory states one business change and no test builds a wide
 * constructor by hand.
 */
public class QualityFixture {

    public static final List<String> LEGO_CATEGORY = List.of("Dziecko", "Zabawki", "Klocki", "LEGO");
    public static final List<String> LEGO_STAR_WARS_CATEGORY =
            List.of("Dziecko", "Zabawki", "Klocki", "LEGO", "Zestawy", "Star Wars");
    public static final List<String> FASHION_CATEGORY = List.of("Dziecko", "Moda dziecięca");

    public static final String TITLE = "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423";
    public static final String EAN_GTIN = "5702017817705";
    public static final String PRODUCT_NUMBER = "75423";

    public static final int COMPLETE_PROSE = 1626;

    private QualityFixture() {
    }

    public static ProductDescription.Draft legoDraft() {
        return ProductDescription.Draft.builder()
                .title(TITLE)
                .identifiers(new Identifiers(PRODUCT_NUMBER, EAN_GTIN, "SMART Play X-Wing Czerwona Piątka Luke'a"))
                .brand(new ProductDescription.Brand("LEGO", true))
                .condition(ProductDescription.Condition.NEW)
                .categoryPath(LEGO_STAR_WARS_CATEGORY)
                .parameters(legoParameters())
                .blocks(List.of(
                        new Heading(3, TITLE),
                        new Paragraph(text(449)),
                        new Paragraph(text(382)),
                        new Paragraph(text(194)),
                        new BulletList(bulletProse(8, 601))))
                .build();
    }

    /** The worked example of 08: two parameters, a heading and one 620-character paragraph. */
    public static ProductDescription.Draft workedExampleDraft() {
        return legoDraft().toBuilder()
                .parameters(List.of(
                        parameter("childAge", new Value.RangeFrom(new BigDecimal("6"), "lat")),
                        parameter("gender", new Value.Choices(List.of("boys", "girls", "unisex")))))
                .blocks(List.of(new Heading(3, TITLE), new Paragraph(text(620))))
                .build();
    }

    public static ProductDescription.Draft draftWithTitle(String title) {
        return legoDraft().toBuilder().title(title).build();
    }

    public static ProductDescription.Draft draftWithIdentifiers(String productNumber, String eanGtin) {
        return legoDraft().toBuilder()
                .identifiers(new Identifiers(productNumber, eanGtin, "SMART Play X-Wing"))
                .build();
    }

    public static ProductDescription.Draft draftMissingParameters(String... codes) {
        List<String> missing = List.of(codes);
        return draftWithParameters(legoParameters().stream()
                .filter(parameter -> !missing.contains(parameter.code()))
                .toList());
    }

    public static ProductDescription.Draft draftWithoutParameters() {
        return draftWithParameters(List.of());
    }

    public static ProductDescription.Draft draftWithTextualPieceCount() {
        return draftWithParameters(legoParameters().stream()
                .map(parameter -> parameter.code().equals("pieceCount")
                        ? parameter("pieceCount", new Value.Text("581"))
                        : parameter)
                .toList());
    }

    public static ProductDescription.Draft draftWithExtraBatteryType() {
        List<Parameter> parameters = new ArrayList<>(legoParameters());
        parameters.add(parameter("batteryType", new Value.Text("AAA")));
        return draftWithParameters(parameters);
    }

    public static ProductDescription.Draft draftWithParagraph(int length) {
        return draftWithBlocks(List.of(new Paragraph(text(length))));
    }

    /** S3 and S6: prose without a single paragraph block, counted from bullet items only. */
    public static ProductDescription.Draft draftWithBulletsOnly(int itemCount, int totalChars) {
        return draftWithBlocks(List.of(new Heading(3, TITLE), new BulletList(bulletProse(itemCount, totalChars))));
    }

    public static ProductDescription.Draft draftWithHeadingAndParagraph(int headingChars, int paragraphChars) {
        return draftWithBlocks(List.of(new Heading(3, text(headingChars)), new Paragraph(text(paragraphChars))));
    }

    public static ProductDescription.Draft draftWithImageBlock(String mediaId) {
        return draftWithBlocks(List.of(
                new Paragraph(text(449)),
                new Image(mediaId),
                new Paragraph(text(382)),
                new Paragraph(text(194))));
    }

    public static List<Media> imagesWithSinglePrimary() {
        return List.of(image("M-01", 1, true), image("M-02", 2, false), image("M-03", 3, false));
    }

    public static List<Media> imagesWithTwoPrimaries() {
        return List.of(image("M-01", 1, true), image("M-02", 2, true), image("M-03", 3, false));
    }

    public static List<Media> imagesWithPositionGap() {
        return List.of(image("M-01", 1, true), image("M-02", 3, false));
    }

    public static List<Media> workedExampleMedia() {
        return List.of(image("M-01", 1, true), image("M-02", 2, false), video("M-03", 3));
    }

    private static Media image(String mediaId, int position, boolean primary) {
        return new Media(mediaId, Media.Kind.IMAGE, position, primary, "jpg", "https://cdn.example/" + mediaId + ".jpg");
    }

    private static Media video(String mediaId, int position) {
        return new Media(mediaId, Media.Kind.VIDEO, position, false, "mp4", "https://cdn.example/" + mediaId + ".mp4");
    }

    private static List<Parameter> legoParameters() {
        return List.of(
                parameter("condition", new Value.Text("nowy")),
                parameter("childAge", new Value.RangeFrom(new BigDecimal("6"), "lat")),
                parameter("pieceCount", new Value.Whole(581)),
                parameter("gender", new Value.Choices(List.of("boys", "girls", "unisex"))),
                parameter("certificates", new Value.Items(List.of("CE", "EN 71"))),
                parameter("safetyInfo", new Value.Text("Nie nadaje się dla dzieci w wieku poniżej 36 miesięcy")));
    }

    private static Parameter parameter(String code, Value value) {
        return new Parameter(code, code, value);
    }

    private static String text(int length) {
        return "x".repeat(length);
    }

    private static List<String> bulletProse(int itemCount, int totalChars) {
        int perItem = totalChars / itemCount;
        int remainder = totalChars % itemCount;
        return IntStream.range(0, itemCount)
                .mapToObj(index -> text(perItem + (index < remainder ? 1 : 0)))
                .toList();
    }

    private static ProductDescription.Draft draftWithParameters(List<Parameter> parameters) {
        return legoDraft().toBuilder().parameters(parameters).build();
    }

    private static ProductDescription.Draft draftWithBlocks(List<DescriptionBlock> blocks) {
        return legoDraft().toBuilder().blocks(blocks).build();
    }
}
