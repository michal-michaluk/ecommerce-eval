package shop.catalog.offer;

import org.junit.jupiter.api.Test;
import shop.catalog.offer.OfferException.Error;
import shop.catalog.offer.ProductDescription.Brand;

import java.util.List;

import static shop.catalog.offer.ProductDescriptionAssert.assertRefused;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithoutBrand;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithoutCondition;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithoutEanGtin;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithoutIdentifiers;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithoutProductNumber;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithBlankProductNumber;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithBrand;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithCategoryPath;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithEanGtin;

class ProductDescriptionIdentifiersTest {

    @Test
    void refuseMissingIdentifiers() {
        assertRefused(Error.PRODUCT_NUMBER_MISSING, draftWithoutIdentifiers()::toDefinition);
    }

    @Test
    void refuseMissingProductNumber() {
        assertRefused(Error.PRODUCT_NUMBER_MISSING, draftWithoutProductNumber()::toDefinition);
    }

    @Test
    void refuseBlankProductNumber() {
        assertRefused(Error.PRODUCT_NUMBER_MISSING, draftWithBlankProductNumber()::toDefinition);
    }

    @Test
    void refuseMissingEanGtin() {
        assertRefused(Error.EAN_GTIN_INVALID, draftWithoutEanGtin()::toDefinition);
    }

    @Test
    void refuseEanGtinOfTwelveDigits() {
        assertRefused(Error.EAN_GTIN_INVALID, draftWithEanGtin("570201781770")::toDefinition);
    }

    @Test
    void refuseEanGtinOfThirteenDigitsWithWrongCheckDigit() {
        assertRefused(Error.EAN_GTIN_INVALID, draftWithEanGtin("5702017817706")::toDefinition);
    }

    @Test
    void refuseEanGtinThatIsNotAllDigits() {
        assertRefused(Error.EAN_GTIN_INVALID, draftWithEanGtin("57020178177O5")::toDefinition);
    }

    @Test
    void refuseEanGtinWithASeparator() {
        assertRefused(Error.EAN_GTIN_INVALID, draftWithEanGtin("57020178177-5")::toDefinition);
    }

    @Test
    void refuseMissingBrand() {
        assertRefused(Error.BRAND_MISSING, draftWithoutBrand()::toDefinition);
    }

    @Test
    void refuseBrandWithoutName() {
        assertRefused(Error.BRAND_NAME_MISSING, () -> draftWithBrand(ProductDescriptionFixture.brandWithoutName()));
    }

    @Test
    void refuseBrandWithNullName() {
        assertRefused(Error.BRAND_NAME_MISSING, () -> draftWithBrand(new Brand(null, true)));
    }

    @Test
    void refuseMissingCondition() {
        assertRefused(Error.CONDITION_MISSING, draftWithoutCondition()::toDefinition);
    }

    @Test
    void refuseCategoryPathThatWasNeverFilled() {
        assertRefused(Error.CATEGORY_PATH_MISSING, draftWithCategoryPath(null)::toDefinition);
    }

    @Test
    void refuseEmptyCategoryPath() {
        assertRefused(Error.CATEGORY_PATH_MISSING, draftWithCategoryPath(List.of())::toDefinition);
    }
}
