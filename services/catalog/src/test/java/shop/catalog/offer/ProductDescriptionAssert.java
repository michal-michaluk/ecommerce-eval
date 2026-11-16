package shop.catalog.offer;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import shop.catalog.offer.ProductDescription.Condition;
import shop.catalog.offer.ProductDescription.Parameter;
import shop.catalog.offer.ProductDescription.Parameter.ValueType;

public class ProductDescriptionAssert {

    private final ProductDescription description;

    private ProductDescriptionAssert(ProductDescription description) {
        this.description = description;
    }

    public static ProductDescriptionAssert assertThat(ProductDescription actual) {
        Assertions.assertThat(actual).isNotNull();
        return new ProductDescriptionAssert(actual);
    }

    public static void assertRefused(OfferException.Error expected, ThrowingCallable call) {
        Assertions.assertThatThrownBy(call)
                .isInstanceOfSatisfying(OfferException.class,
                        refused -> Assertions.assertThat(refused.error()).isEqualTo(expected));
    }

    public ProductDescriptionAssert hasTitle(String expected) {
        Assertions.assertThat(description.title()).isEqualTo(expected);
        return this;
    }

    public ProductDescriptionAssert hasProductNumber(String expected) {
        Assertions.assertThat(description.identifiers().productNumber()).isEqualTo(expected);
        return this;
    }

    public ProductDescriptionAssert hasEanGtin(String expected) {
        Assertions.assertThat(description.identifiers().eanGtin()).isEqualTo(expected);
        return this;
    }

    public ProductDescriptionAssert hasBrand(String name, boolean verified) {
        Assertions.assertThat(description.brand())
                .isEqualTo(new ProductDescription.Brand(name, verified));
        return this;
    }

    public ProductDescriptionAssert hasCondition(Condition expected) {
        Assertions.assertThat(description.condition()).isEqualTo(expected);
        return this;
    }

    public ProductDescriptionAssert hasCategoryPath(String... expected) {
        Assertions.assertThat(description.categoryPath()).containsExactly(expected);
        return this;
    }

    public ProductDescriptionAssert hasParameterCount(int expected) {
        Assertions.assertThat(description.parameters()).hasSize(expected);
        return this;
    }

    public ProductDescriptionAssert hasParameterValueType(String code, ValueType expected) {
        Assertions.assertThat(description.parameters())
                .filteredOn(parameter -> parameter.code().equals(code))
                .extracting(Parameter::valueType)
                .containsExactly(expected);
        return this;
    }

    public ProductDescriptionAssert hasBlockCount(int expected) {
        Assertions.assertThat(description.blocks()).hasSize(expected);
        return this;
    }

    public ProductDescriptionAssert isEqualTo(ProductDescription expected) {
        Assertions.assertThat(description).isEqualTo(expected).hasSameHashCodeAs(expected);
        return this;
    }

    public ProductDescriptionAssert isNotEqualTo(ProductDescription other) {
        Assertions.assertThat(description).isNotEqualTo(other);
        return this;
    }
}
