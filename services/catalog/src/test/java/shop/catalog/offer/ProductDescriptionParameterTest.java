package shop.catalog.offer;

import org.junit.jupiter.api.Test;
import shop.catalog.offer.OfferException.Error;
import shop.catalog.offer.ProductDescription.Parameter.ValueType;

import static shop.catalog.offer.ProductDescriptionAssert.assertRefused;
import static shop.catalog.offer.ProductDescriptionAssert.assertThat;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithDuplicatedParameterCode;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithEveryValueType;
import static shop.catalog.offer.ProductDescriptionDraftFixture.parameterWithCode;
import static shop.catalog.offer.ProductDescriptionDraftFixture.parameterWithoutValue;

class ProductDescriptionParameterTest {

    @Test
    void deriveTheValueTypeFromTheParameterValue() {
        assertThat(draftWithEveryValueType().toDefinition())
                .hasParameterValueType("textParam", ValueType.TEXT)
                .hasParameterValueType("integerParam", ValueType.INTEGER)
                .hasParameterValueType("decimalParam", ValueType.DECIMAL)
                .hasParameterValueType("rangeParam", ValueType.RANGE_FROM)
                .hasParameterValueType("booleanParam", ValueType.BOOLEAN)
                .hasParameterValueType("dateParam", ValueType.DATE)
                .hasParameterValueType("enumSetParam", ValueType.ENUM_SET)
                .hasParameterValueType("listParam", ValueType.LIST);
    }

    @Test
    void refuseDuplicatedParameterCode() {
        assertRefused(Error.PARAMETER_CODE_DUPLICATED, draftWithDuplicatedParameterCode()::toDefinition);
    }

    @Test
    void refuseParameterWithBlankCode() {
        assertRefused(Error.PARAMETER_CODE_MISSING, () -> parameterWithCode(" "));
    }

    @Test
    void refuseParameterWithoutCode() {
        assertRefused(Error.PARAMETER_CODE_MISSING, () -> parameterWithCode(null));
    }

    @Test
    void refuseParameterWithoutValue() {
        assertRefused(Error.PARAMETER_VALUE_MISSING, ProductDescriptionDraftFixture::parameterWithoutValue);
    }
}
