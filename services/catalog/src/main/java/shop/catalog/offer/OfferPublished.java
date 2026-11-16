package shop.catalog.offer;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Element 13 — the snapshot of one frozen offer version (RULE-2). The default payload carries <em>every</em> component of the value the
 * publication froze: the description by value, the media in the author's order and the availability date. That is what RULE-10 asks for,
 * because only a payload that carries the whole value lets a consumer recompute {@code descriptionHash} and recognise equal content.
 *
 * <p>The payload is a wire shape, not the domain's: a parameter's value travels as its own JSON value next to the {@code valueType} it
 * declares, a description block names its {@code type}, and the enums that are words on a page are written as words
 * ({@code condition: new}). {@link #valueOf} is the one mapping from the frozen version, so the adapter and the tests see one shape.
 *
 * <p>The hash is a promise over {@link Value}: the payload without {@code productId}, {@code offerVersion} and {@code descriptionHash}.
 * A consumer reads the payload, drops those three fields and hashes the rest — the declarations of {@link Value} are in the payload's own
 * order for exactly that reason. A media item travels with its location ({@code url}) besides {@code id}, {@code kind}, {@code position},
 * {@code primary} and {@code format}, because that is how the shop resolves a file (RULE-2a, D17, D47).
 *
 * <p>{@link #thin} is the documented variant for a consumer that prefers to pull the content: identity and version only, every other
 * component absent. Nothing of the draft, the review or the quality findings is reachable from either shape (RULE-1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferPublished(
        String productId,
        int offerVersion,
        String descriptionHash,
        Identifiers identifiers,
        LocalDate availabilityStart,
        String title,
        Brand brand,
        String condition,
        List<String> categoryPath,
        List<Parameter> parameters,
        Description description,
        List<MediaItem> media) {

    public static final String TYPE = "OfferPublished";

    public OfferPublished {
        productId = Objects.requireNonNull(productId, "productId");
        categoryPath = categoryPath == null ? null : List.copyOf(categoryPath);
        parameters = parameters == null ? null : List.copyOf(parameters);
        media = media == null ? null : List.copyOf(media);
    }

    /** The default snapshot: the whole value and the hash over it. */
    static OfferPublished of(String productId, int offerVersion, Value value, String descriptionHash) {
        return new OfferPublished(productId, offerVersion, descriptionHash, value.identifiers(), value.availabilityStart(),
                value.title(), value.brand(), value.condition(), value.categoryPath(), value.parameters(),
                value.description(), value.media());
    }

    /** The measured variant: a consumer that fetches the content itself is told only which version to fetch. */
    static OfferPublished thin(String productId, int offerVersion) {
        return new OfferPublished(productId, offerVersion, null, null, null, null, null, null, null, null, null, null);
    }

    /** The value the hash is promised over, in the order the payload declares its components (RULE-10, D39). */
    static Value valueOf(OfferVersion version) {
        ProductDescription description = version.description();
        return new Value(
                identifiersOf(description.identifiers()),
                version.availabilityStart(),
                description.title(),
                brandOf(description.brand()),
                word(description.condition()),
                description.categoryPath(),
                description.parameters().stream().map(OfferPublished::parameterOf).toList(),
                new Description(description.blocks().stream().map(OfferPublished::blockOf).toList()),
                version.media().stream().map(OfferPublished::mediaOf).toList());
    }

    private static Identifiers identifiersOf(ProductDescription.Identifiers identifiers) {
        return new Identifiers(identifiers.productNumber(), identifiers.eanGtin(), identifiers.setName());
    }

    private static Brand brandOf(ProductDescription.Brand brand) {
        return new Brand(brand.name(), brand.verified());
    }

    private static Parameter parameterOf(ProductDescription.Parameter parameter) {
        return new Parameter(parameter.code(), parameter.label(), valueOf(parameter.value()),
                word(parameter.valueType()));
    }

    /** One carrier per value type, written as the JSON value itself; {@code rangeFrom} keeps its threshold and its unit. */
    private static Object valueOf(ProductDescription.Parameter.Value value) {
        return switch (value) {
            case ProductDescription.Parameter.Value.Text text -> text.text();
            case ProductDescription.Parameter.Value.Whole whole -> whole.value();
            case ProductDescription.Parameter.Value.Decimal decimal -> decimal.value();
            case ProductDescription.Parameter.Value.RangeFrom range -> new Range(range.from(), range.unit());
            case ProductDescription.Parameter.Value.Flag flag -> flag.value();
            case ProductDescription.Parameter.Value.DateValue date -> date.date();
            case ProductDescription.Parameter.Value.Choices choices -> choices.values();
            case ProductDescription.Parameter.Value.Items items -> items.values();
        };
    }

    private static Block blockOf(ProductDescription.DescriptionBlock block) {
        return switch (block) {
            case ProductDescription.DescriptionBlock.Heading heading ->
                    new Block("heading", heading.level(), heading.text(), null, null);
            case ProductDescription.DescriptionBlock.Paragraph paragraph ->
                    new Block("paragraph", null, paragraph.text(), null, null);
            case ProductDescription.DescriptionBlock.BulletList list ->
                    new Block("bullet_list", null, null, list.items(), null);
            case ProductDescription.DescriptionBlock.Image image ->
                    new Block("image", null, null, null, image.mediaId());
        };
    }

    private static MediaItem mediaOf(Media media) {
        return new MediaItem(media.mediaId(), word(media.kind()), media.position(), media.primary(), media.format(),
                media.url());
    }

    private static String word(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /** The frozen value as it travels: the payload's components in the payload's order, and the input of the hash. */
    public record Value(
            Identifiers identifiers,
            LocalDate availabilityStart,
            String title,
            Brand brand,
            String condition,
            List<String> categoryPath,
            List<Parameter> parameters,
            Description description,
            List<MediaItem> media) {

        public Value {
            categoryPath = List.copyOf(categoryPath);
            parameters = List.copyOf(parameters);
            media = List.copyOf(media);
        }
    }

    /** The two population-wide identifiers RULE-10 makes the shop's business: equal content must travel with them. */
    public record Identifiers(String productNumber, String eanGtin, String setName) {
    }

    public record Brand(String name, boolean verified) {
    }

    /** One attribute as the page shows it: the identifier, its label, the JSON value and the type that value declares. */
    public record Parameter(String code, String label, Object value, String valueType) {
    }

    /** "from 6 years up" travels as both numbers, because the unit is not part of the value. */
    public record Range(BigDecimal from, String unit) {
    }

    public record Description(List<Block> blocks) {

        public Description {
            blocks = List.copyOf(blocks);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Block(String type, Integer level, String text, List<String> items, String mediaId) {

        public Block {
            items = items == null ? null : List.copyOf(items);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MediaItem(String id, String kind, int position, boolean primary, String format, String url) {
    }
}
