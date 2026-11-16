package shop.catalog.offer;

import org.junit.jupiter.api.Test;
import shop.catalog.offer.OfferException.Error;
import shop.catalog.offer.ProductDescription.DescriptionBlock;
import shop.catalog.offer.ProductDescription.Draft;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static shop.catalog.offer.ProductDescriptionAssert.assertRefused;
import static shop.catalog.offer.ProductDescriptionAssert.assertThat;
import static shop.catalog.offer.ProductDescriptionDraftFixture.blankDraft;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftOfInstanceA;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithBlocks;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithParameters;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithTitle;

class ProductDescriptionDraftTest {

    @Test
    void keepAnIncompleteDraftEditable() {
        List<DescriptionBlock> noBlocks = List.of();

        assertThatCode(() -> draftWithBlocks(noBlocks)).doesNotThrowAnyException();
    }

    @Test
    void refuseABlankDraftUntilItIsFilled() {
        assertRefused(Error.BRAND_MISSING, blankDraft()::toDefinition);
    }

    @Test
    void freezeADraftIntoTheValueItDescribes() {
        assertThat(draftOfInstanceA().toDefinition()).isEqualTo(ProductDescriptionFixture.descriptionOfInstanceA());
    }

    @Test
    void freezeADraftThatWasFilledInOverANumberOfEdits() {
        Draft published = blankDraft()
                .merge(draftOfInstanceA())
                .merge(draftWithParameters(ProductDescriptionFixture.parametersOfInstanceA()));

        assertThat(published.toDefinition()).isEqualTo(ProductDescriptionFixture.descriptionOfInstanceA());
    }

    @Test
    void acceptDraftWithoutParameters() {
        assertThat(draftWithParameters(null).toDefinition()).hasParameterCount(0);
    }

    @Test
    void mergeAChangeWithoutLosingWhatItDoesNotTouch() {
        Draft edited = draftOfInstanceA().merge(draftWithTitle("LEGO Star Wars X-Wing 75423"));

        assertThat(edited.toDefinition())
                .hasTitle("LEGO Star Wars X-Wing 75423")
                .hasProductNumber("75423")
                .hasEanGtin(ProductDescriptionFixture.INSTANCE_A_EAN);
    }
}
