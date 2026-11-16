package shop.catalog.offer;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.Optional.ofNullable;

/**
 * The content of a product listing — a value, so it carries no identity and no lifecycle of its own: two instances with
 * equal components are the same value (01 RULE-3, RULE-7).
 *
 * <p>Every component participates in {@code equals}/{@code hashCode}, which is what lets a published payload be deduplicated by
 * {@code descriptionHash} (RULE-7). Uniqueness of {@code productNumber}/{@code eanGtin} across products is not a value-object
 * concern and is enforced by the 02 Offer aggregate (RULE-8).
 *
 * <p>Drafting uses {@link Draft}, whose {@link Draft#toDefinition()} is the only producer of this value (01 RULE-4, 02 RULE-10).
 *
 * <p>The list components are copied on construction and again on read, so no caller can change a value in place (RULE-3).
 */
public record ProductDescription(
        String title,
        Identifiers identifiers,
        Brand brand,
        Condition condition,
        List<String> categoryPath,
        List<Parameter> parameters,
        List<DescriptionBlock> blocks) {

    private static final int TITLE_MAX_LENGTH = 200;

    public ProductDescription {
        title = checkedTitle(title);
        identifiers = checkedIdentifiers(identifiers);
        brand = required(brand, OfferException.Error.BRAND_MISSING);
        condition = required(condition, OfferException.Error.CONDITION_MISSING);
        categoryPath = checkedCategoryPath(categoryPath);
        parameters = checkedParameters(parameters);
        blocks = checkedBlocks(blocks);
    }

    @Override
    public List<String> categoryPath() {
        return List.copyOf(categoryPath);
    }

    @Override
    public List<Parameter> parameters() {
        return List.copyOf(parameters);
    }

    @Override
    public List<DescriptionBlock> blocks() {
        return List.copyOf(blocks);
    }

    private static String checkedTitle(String title) {
        String trimmed = title == null ? "" : title.strip();
        if (trimmed.isEmpty() || trimmed.length() > TITLE_MAX_LENGTH) {
            throw new OfferException(OfferException.Error.TITLE_INVALID, title);
        }
        return trimmed;
    }

    private static Identifiers checkedIdentifiers(Identifiers identifiers) {
        if (identifiers == null || !identifiers.hasProductNumber()) {
            throw new OfferException(OfferException.Error.PRODUCT_NUMBER_MISSING);
        }
        if (!identifiers.hasValidEanGtin()) {
            throw new OfferException(OfferException.Error.EAN_GTIN_INVALID, identifiers.eanGtin());
        }
        return identifiers;
    }

    private static <T> T required(T value, OfferException.Error missing) {
        if (value == null) {
            throw new OfferException(missing);
        }
        return value;
    }

    private static List<String> checkedCategoryPath(List<String> categoryPath) {
        if (categoryPath == null || categoryPath.isEmpty()) {
            throw new OfferException(OfferException.Error.CATEGORY_PATH_MISSING);
        }
        return List.copyOf(categoryPath);
    }

    private static List<Parameter> checkedParameters(List<Parameter> parameters) {
        if (parameters == null) {
            return List.of();
        }
        List<Parameter> declared = List.copyOf(parameters);
        Set<String> codes = new HashSet<>();
        for (Parameter parameter : declared) {
            if (!codes.add(parameter.code())) {
                throw new OfferException(OfferException.Error.PARAMETER_CODE_DUPLICATED, parameter.code());
            }
        }
        return declared;
    }

    private static List<DescriptionBlock> checkedBlocks(List<DescriptionBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            throw new OfferException(OfferException.Error.DESCRIPTION_BLOCKS_MISSING);
        }
        if (blocks.stream().noneMatch(DescriptionBlock.Paragraph.class::isInstance)) {
            throw new OfferException(OfferException.Error.PARAGRAPH_BLOCK_MISSING);
        }
        return List.copyOf(blocks);
    }

    /** The two population-wide identifiers a value cannot police (RULE-2, RULE-8); {@code setName} is display content (A1). */
    public record Identifiers(String productNumber, String eanGtin, String setName) {

        public boolean hasProductNumber() {
            return productNumber != null && !productNumber.isBlank();
        }

        public boolean hasValidEanGtin() {
            if (eanGtin == null || eanGtin.length() != 13) {
                return false;
            }
            if (!eanGtin.chars().allMatch(digit -> digit >= '0' && digit <= '9')) {
                return false;
            }
            int weighted = 0;
            for (int position = 0; position < 12; position++) {
                int digit = eanGtin.charAt(position) - '0';
                weighted += position % 2 == 0 ? digit : digit * 3;
            }
            return (10 - weighted % 10) % 10 == eanGtin.charAt(12) - '0';
        }
    }

    /** Display metadata: {@code verified} states who vouched for the brand and carries no rule (RULE-9). */
    public record Brand(String name, boolean verified) {

        public Brand {
            if (name == null || name.isBlank()) {
                throw new OfferException(OfferException.Error.BRAND_NAME_MISSING);
            }
        }
    }

    public enum Condition {
        NEW,
        USED,
        REFURBISHED
    }

    /** One attribute of the listing; {@code label} is display text, {@code code} is the identifier (RULE-5, A2). */
    public record Parameter(String code, String label, Value value) {

        public Parameter {
            if (code == null || code.isBlank()) {
                throw new OfferException(OfferException.Error.PARAMETER_CODE_MISSING);
            }
            if (value == null) {
                throw new OfferException(OfferException.Error.PARAMETER_VALUE_MISSING, code);
            }
        }

        /**
         * The {@code valueType} the value declares by its own shape, so the two cannot disagree (RULE-5); {@link Value} is sealed,
         * which makes the switch exhaustive and a new carrier a compile error here.
         */
        public ValueType valueType() {
            return switch (value) {
                case Value.Text ignored -> ValueType.TEXT;
                case Value.Whole ignored -> ValueType.INTEGER;
                case Value.Decimal ignored -> ValueType.DECIMAL;
                case Value.RangeFrom ignored -> ValueType.RANGE_FROM;
                case Value.Flag ignored -> ValueType.BOOLEAN;
                case Value.DateValue ignored -> ValueType.DATE;
                case Value.Choices ignored -> ValueType.ENUM_SET;
                case Value.Items ignored -> ValueType.LIST;
            };
        }

        public enum ValueType {
            TEXT,
            INTEGER,
            DECIMAL,
            RANGE_FROM,
            BOOLEAN,
            DATE,
            ENUM_SET,
            LIST
        }

        /** The value of a parameter, one carrier per {@link ValueType}; a type that cannot disagree with its own value type. */
        public sealed interface Value {

            record Text(String text) implements Value {
                public Text {
                    Objects.requireNonNull(text, "text");
                }
            }

            record Whole(int value) implements Value {
            }

            record Decimal(BigDecimal value) implements Value {
                public Decimal {
                    Objects.requireNonNull(value, "value");
                }
            }

            /** "from 6 years up": the threshold plus the unit it is expressed in. */
            record RangeFrom(BigDecimal from, String unit) implements Value {
                public RangeFrom {
                    Objects.requireNonNull(from, "from");
                }
            }

            record Flag(boolean value) implements Value {
            }

            record DateValue(LocalDate date) implements Value {
                public DateValue {
                    Objects.requireNonNull(date, "date");
                }
            }

            /** An enumerated multi-value: one of the named options the category offers. */
            record Choices(List<String> values) implements Value {
                public Choices {
                    values = List.copyOf(values);
                }
            }

            /** A free multi-value list, such as certificates and test attestations. */
            record Items(List<String> values) implements Value {
                public Items {
                    values = List.copyOf(values);
                }
            }
        }
    }

    /** Structured content instead of HTML (A3); {@link Image} references media owned by 02 Offer, so its existence is QP-7's rule. */
    public sealed interface DescriptionBlock {

        record Heading(int level, String text) implements DescriptionBlock {
            public Heading {
                Objects.requireNonNull(text, "text");
            }
        }

        record Paragraph(String text) implements DescriptionBlock {
            public Paragraph {
                Objects.requireNonNull(text, "text");
            }
        }

        record BulletList(List<String> items) implements DescriptionBlock {
            public BulletList {
                items = List.copyOf(items);
            }
        }

        record Image(String mediaId) implements DescriptionBlock {
            public Image {
                Objects.requireNonNull(mediaId, "mediaId");
            }
        }
    }

    /**
     * The description in builder form: the same components and the same nested shapes, nullable and without an invariant, because a
     * draft tolerates what a frozen value refuses (02 RULE-7, RULE-8). Editing produces a new draft through {@link #merge(Draft)}.
     *
     * <p>It is read in-process by the quality policy of 17 and never travels in an event or a query (01 RULE-4; 13 RULE-1).
     */
    @Builder(toBuilder = true)
    public record Draft(
            String title,
            Identifiers identifiers,
            Brand brand,
            Condition condition,
            List<String> categoryPath,
            List<Parameter> parameters,
            List<DescriptionBlock> blocks) {

        public Draft {
            categoryPath = categoryPath == null ? null : List.copyOf(categoryPath);
            parameters = parameters == null ? null : List.copyOf(parameters);
            blocks = blocks == null ? null : List.copyOf(blocks);
        }

        /** The draft a listing starts as: a title and its identifiers, nothing else yet (01 Behaviour). */
        public static Draft blank(String title, Identifiers identifiers) {
            return builder().title(title).identifiers(identifiers).build();
        }

        public Draft merge(Draft change) {
            DraftBuilder merged = toBuilder();
            ofNullable(change.title).ifPresent(merged::title);
            ofNullable(change.identifiers).ifPresent(merged::identifiers);
            ofNullable(change.brand).ifPresent(merged::brand);
            ofNullable(change.condition).ifPresent(merged::condition);
            ofNullable(change.categoryPath).ifPresent(merged::categoryPath);
            ofNullable(change.parameters).ifPresent(merged::parameters);
            ofNullable(change.blocks).ifPresent(merged::blocks);
            return merged.build();
        }

        public ProductDescription toDefinition() {
            return new ProductDescription(title, identifiers, brand, condition, categoryPath, parameters, blocks);
        }
    }
}
