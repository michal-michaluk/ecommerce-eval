package shop.catalog.offer;

import shop.catalog.offer.ProductDescription.Brand;
import shop.catalog.offer.ProductDescription.Condition;
import shop.catalog.offer.ProductDescription.DescriptionBlock;
import shop.catalog.offer.ProductDescription.DescriptionBlock.Heading;
import shop.catalog.offer.ProductDescription.DescriptionBlock.Image;
import shop.catalog.offer.ProductDescription.DescriptionBlock.Paragraph;
import shop.catalog.offer.ProductDescription.Draft;
import shop.catalog.offer.ProductDescription.Identifiers;
import shop.catalog.offer.ProductDescription.Parameter;
import shop.catalog.offer.ProductDescription.Parameter.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Drafts edited one component at a time, each named after the 01 rule the edit puts at risk. */
public class ProductDescriptionDraftFixture {

    public static Draft draftOfInstanceA() {
        return Draft.builder()
                .title(ProductDescriptionFixture.INSTANCE_A_TITLE)
                .identifiers(ProductDescriptionFixture.identifiersOfInstanceA())
                .brand(ProductDescriptionFixture.brandLego())
                .condition(Condition.NEW)
                .categoryPath(ProductDescriptionFixture.LEGO_CATEGORY_PATH)
                .parameters(ProductDescriptionFixture.parametersOfInstanceA())
                .blocks(ProductDescriptionFixture.blocksOfInstanceA())
                .build();
    }

    public static Draft blankDraft() {
        return Draft.blank(ProductDescriptionFixture.INSTANCE_A_TITLE, ProductDescriptionFixture.identifiersOfInstanceA());
    }

    public static Draft draftWithTitle(String title) {
        return draftOfInstanceA().toBuilder().title(title).build();
    }

    public static Draft draftWithIdentifiers(Identifiers identifiers) {
        return draftOfInstanceA().toBuilder().identifiers(identifiers).build();
    }

    public static Draft draftWithIdentifiers(String productNumber, String eanGtin) {
        return draftWithIdentifiers(new Identifiers(productNumber, eanGtin, "SMART Play X-Wing"));
    }

    public static Draft draftWithoutIdentifiers() {
        return draftWithIdentifiers(null);
    }

    public static Draft draftWithoutProductNumber() {
        return draftWithIdentifiers(new Identifiers(null, ProductDescriptionFixture.INSTANCE_A_EAN, "SMART Play X-Wing"));
    }

    public static Draft draftWithBlankProductNumber() {
        return draftWithIdentifiers(new Identifiers("  ", ProductDescriptionFixture.INSTANCE_A_EAN, "SMART Play X-Wing"));
    }

    public static Draft draftWithoutEanGtin() {
        return draftWithIdentifiers(new Identifiers("75423", null, "SMART Play X-Wing"));
    }

    public static Draft draftWithEanGtin(String eanGtin) {
        return draftWithIdentifiers(new Identifiers("75423", eanGtin, "SMART Play X-Wing"));
    }

    public static Draft draftWithBrand(Brand brand) {
        return draftOfInstanceA().toBuilder().brand(brand).build();
    }

    public static Draft draftWithoutBrand() {
        return draftWithBrand(null);
    }

    public static Draft draftWithCondition(Condition condition) {
        return draftOfInstanceA().toBuilder().condition(condition).build();
    }

    public static Draft draftWithoutCondition() {
        return draftWithCondition(null);
    }

    public static Draft draftWithCategoryPath(List<String> categoryPath) {
        return draftOfInstanceA().toBuilder().categoryPath(categoryPath).build();
    }

    public static Draft draftWithParameters(List<Parameter> parameters) {
        return draftOfInstanceA().toBuilder().parameters(parameters).build();
    }

    public static Draft draftWithBlocks(List<DescriptionBlock> blocks) {
        return draftOfInstanceA().toBuilder().blocks(blocks).build();
    }

    public static Draft draftWithHeadingOnly() {
        return draftWithBlocks(List.of(new Heading(3, ProductDescriptionFixture.INSTANCE_A_TITLE)));
    }

    public static Draft draftWithImageOfUnknownMedia() {
        return draftWithBlocks(List.of(
                new Paragraph("Zbuduj kultowy Sokół Millennium…"),
                new Image("M-99")));
    }

    public static Draft draftWithDuplicatedParameterCode() {
        return draftWithParameters(List.of(
                ProductDescriptionFixture.parameter("pieceCount", "Liczba elementów", new Value.Whole(581)),
                ProductDescriptionFixture.parameter("pieceCount", "Liczba elementów", new Value.Whole(921))));
    }

    public static Draft draftWithEveryValueType() {
        return draftWithParameters(List.of(
                ProductDescriptionFixture.parameter("textParam", null,
                        new Value.Text("Nie nadaje się dla dzieci w wieku poniżej 36 miesięcy")),
                ProductDescriptionFixture.parameter("integerParam", null, new Value.Whole(581)),
                ProductDescriptionFixture.parameter("decimalParam", null, new Value.Decimal(new BigDecimal("12.5"))),
                ProductDescriptionFixture.parameter("rangeParam", null,
                        new Value.RangeFrom(new BigDecimal("6"), "lat")),
                ProductDescriptionFixture.parameter("booleanParam", null, new Value.Flag(true)),
                ProductDescriptionFixture.parameter("dateParam", null, new Value.DateValue(LocalDate.of(2026, 7, 1))),
                ProductDescriptionFixture.parameter("enumSetParam", null,
                        new Value.Choices(List.of("boys", "girls", "unisex"))),
                ProductDescriptionFixture.parameter("listParam", null, new Value.Items(List.of("CE", "EN 71")))));
    }

    public static Parameter parameterWithCode(String code) {
        return ProductDescriptionFixture.parameter(code, null, new Value.Text("6+"));
    }

    public static Parameter parameterWithoutValue() {
        return ProductDescriptionFixture.parameter("childAge", "Wiek dziecka", null);
    }
}
