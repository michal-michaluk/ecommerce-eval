package shop.catalog.offer;

import shop.catalog.offer.ProductDescription.Brand;
import shop.catalog.offer.ProductDescription.Condition;
import shop.catalog.offer.ProductDescription.DescriptionBlock;
import shop.catalog.offer.ProductDescription.DescriptionBlock.BulletList;
import shop.catalog.offer.ProductDescription.DescriptionBlock.Heading;
import shop.catalog.offer.ProductDescription.DescriptionBlock.Paragraph;
import shop.catalog.offer.ProductDescription.Identifiers;
import shop.catalog.offer.ProductDescription.Parameter;
import shop.catalog.offer.ProductDescription.Parameter.Value;

import java.math.BigDecimal;
import java.util.List;

/** Instance A is the sample LEGO page, instance B the Millennium Falcon from that page's own carousel (01). */
public class ProductDescriptionFixture {

    public static final String INSTANCE_A_TITLE = "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423";
    public static final String INSTANCE_B_TITLE = "LEGO Star Wars Sokół Millennium 75375 + LEGO Brelok na dzień Ojca";
    public static final String INSTANCE_A_EAN = "5702017817705";
    public static final String INSTANCE_B_EAN = "5702017584065";
    public static final List<String> LEGO_CATEGORY_PATH =
            List.of("Dziecko", "Zabawki", "Klocki", "LEGO", "Zestawy", "Star Wars");

    public static ProductDescription descriptionOfInstanceA() {
        return new ProductDescription(
                INSTANCE_A_TITLE,
                identifiersOfInstanceA(),
                brandLego(),
                Condition.NEW,
                LEGO_CATEGORY_PATH,
                parametersOfInstanceA(),
                blocksOfInstanceA());
    }

    public static ProductDescription descriptionOfInstanceB() {
        return new ProductDescription(
                INSTANCE_B_TITLE,
                identifiersOfInstanceB(),
                brandLego(),
                Condition.NEW,
                LEGO_CATEGORY_PATH,
                List.of(
                        parameter("childAge", "Wiek dziecka", new Value.RangeFrom(new BigDecimal("18"), "lat")),
                        parameter("pieceCount", "Liczba elementów", new Value.Whole(921)),
                        parameter("gender", "Płeć", new Value.Choices(List.of("unisex")))),
                List.of(new Paragraph("Zbuduj kultowy Sokół Millennium…")));
    }

    public static ProductDescription descriptionWithBlocks(List<DescriptionBlock> blocks) {
        return new ProductDescription(
                INSTANCE_A_TITLE,
                identifiersOfInstanceA(),
                brandLego(),
                Condition.NEW,
                LEGO_CATEGORY_PATH,
                parametersOfInstanceA(),
                blocks);
    }

    public static Identifiers identifiersOfInstanceA() {
        return new Identifiers("75423", INSTANCE_A_EAN, "SMART Play X-Wing Czerwona Piątka Luke'a");
    }

    public static Identifiers identifiersOfInstanceB() {
        return new Identifiers("75375", INSTANCE_B_EAN, "Sokół Millennium");
    }

    public static Brand brandLego() {
        return new Brand("LEGO", true);
    }

    public static Brand brandWithoutName() {
        return new Brand("   ", true);
    }

    public static List<Parameter> parametersOfInstanceA() {
        return List.of(
                parameter("childAge", "Wiek dziecka", new Value.RangeFrom(new BigDecimal("6"), "lat")),
                parameter("pieceCount", "Liczba elementów", new Value.Whole(581)),
                parameter("gender", "Płeć", new Value.Choices(List.of("boys", "girls", "unisex"))),
                parameter("certificates", "Certyfikaty, opinie, atesty", new Value.Items(List.of("CE", "EN 71"))),
                parameter("safetyInfo", "Informacje dot. bezpieczeństwa i zgodności produktu",
                        new Value.Text("Nie nadaje się dla dzieci w wieku poniżej 36 miesięcy")));
    }

    public static List<DescriptionBlock> blocksOfInstanceA() {
        return List.of(
                new Heading(3, INSTANCE_A_TITLE),
                new Paragraph("Odkryj nową galaktykę LEGO® Star Wars™ dzięki interaktywnej zabawce konstrukcyjnej …"),
                new BulletList(List.of(
                        "Interaktywna zabawka konstrukcyjna ożywia X-winga…",
                        "SMART klocek umożliwia…")));
    }

    public static Parameter parameter(String code, String label, Value value) {
        return new Parameter(code, label, value);
    }
}
