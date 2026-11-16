package shop.catalog.offer;

import org.junit.jupiter.api.Test;
import shop.catalog.offer.OfferException.Error;
import shop.catalog.offer.ProductDescription.Brand;
import shop.catalog.offer.ProductDescription.Condition;
import shop.catalog.offer.ProductDescription.DescriptionBlock;
import shop.catalog.offer.ProductDescription.DescriptionBlock.Paragraph;
import shop.catalog.offer.ProductDescription.Parameter.Value.Whole;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.catalog.offer.ProductDescriptionAssert.assertRefused;
import static shop.catalog.offer.ProductDescriptionAssert.assertThat;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithBlocks;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithBrand;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithCategoryPath;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithCondition;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithHeadingOnly;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithIdentifiers;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithParameters;
import static shop.catalog.offer.ProductDescriptionDraftFixture.draftWithTitle;

class ProductDescriptionTest {

    @Test
    void describeTheSamplePage() {
        assertThat(ProductDescriptionFixture.descriptionOfInstanceA())
                .hasTitle(ProductDescriptionFixture.INSTANCE_A_TITLE)
                .hasProductNumber("75423")
                .hasEanGtin(ProductDescriptionFixture.INSTANCE_A_EAN)
                .hasBrand("LEGO", true)
                .hasCondition(Condition.NEW)
                .hasCategoryPath("Dziecko", "Zabawki", "Klocki", "LEGO", "Zestawy", "Star Wars")
                .hasParameterCount(5)
                .hasBlockCount(3);
    }

    @Test
    void describeTheSecondPage() {
        assertThat(ProductDescriptionFixture.descriptionOfInstanceB())
                .hasTitle(ProductDescriptionFixture.INSTANCE_B_TITLE)
                .hasProductNumber("75375")
                .hasEanGtin(ProductDescriptionFixture.INSTANCE_B_EAN)
                .hasCondition(Condition.NEW)
                .hasParameterCount(3)
                .hasBlockCount(1);
    }

    @Test
    void acceptAConditionOtherThanNew() {
        assertThat(draftWithCondition(Condition.REFURBISHED).toDefinition()).hasCondition(Condition.REFURBISHED);
    }

    @Test
    void acceptATitleOf200Characters() {
        assertThat(draftWithTitle("y".repeat(200)).toDefinition())
                .hasTitle("y".repeat(200));
    }

    @Test
    void refuseTitleWithoutText() {
        assertRefused(Error.TITLE_INVALID, draftWithTitle("   ")::toDefinition);
    }

    @Test
    void refuseTitleThatWasNeverFilled() {
        assertRefused(Error.TITLE_INVALID, draftWithTitle(null)::toDefinition);
    }

    @Test
    void refuseTitleLongerThan200Characters() {
        assertRefused(Error.TITLE_INVALID, draftWithTitle("x".repeat(201))::toDefinition);
    }

    @Test
    void trimTitleWhenItIsFrozen() {
        String padded = "  " + ProductDescriptionFixture.INSTANCE_A_TITLE + "  ";

        assertThat(draftWithTitle(padded).toDefinition()).hasTitle(ProductDescriptionFixture.INSTANCE_A_TITLE);
    }

    @Test
    void acceptImageBlockThatNamesNoMediaOfThisValue() {
        assertThat(ProductDescriptionDraftFixture.draftWithImageOfUnknownMedia().toDefinition()).hasBlockCount(2);
    }

    @Test
    void refuseDescriptionWithoutBlock() {
        assertRefused(Error.DESCRIPTION_BLOCKS_MISSING, draftWithBlocks(null)::toDefinition);
    }

    @Test
    void refuseDescriptionWithNoBlocksAtAll() {
        assertRefused(Error.DESCRIPTION_BLOCKS_MISSING, draftWithBlocks(List.of())::toDefinition);
    }

    @Test
    void refuseDescriptionWithoutParagraph() {
        assertRefused(Error.PARAGRAPH_BLOCK_MISSING, draftWithHeadingOnly()::toDefinition);
    }

    @Test
    void differenceInOneComponentBreaksValueEquality() {
        assertThat(ProductDescriptionFixture.descriptionOfInstanceA())
                .isNotEqualTo(draftWithTitle("LEGO Star Wars X-Wing 75423").toDefinition())
                .isNotEqualTo(draftWithIdentifiers(ProductDescriptionFixture.identifiersOfInstanceB()).toDefinition())
                .isNotEqualTo(draftWithBrand(new Brand("Cobi", true)).toDefinition())
                .isNotEqualTo(draftWithCondition(Condition.USED).toDefinition())
                .isNotEqualTo(draftWithCategoryPath(List.of("Dziecko")).toDefinition())
                .isNotEqualTo(draftWithParameters(List.of(
                        ProductDescriptionFixture.parameter("pieceCount", null, new Whole(921)))).toDefinition())
                .isNotEqualTo(draftWithBlocks(List.of(new Paragraph("Zbuduj kultowy Sokół Millennium…"))).toDefinition());
    }

    @Test
    void notShareTheBlocksItWasGiven() {
        List<DescriptionBlock> blocks = new ArrayList<>(ProductDescriptionFixture.blocksOfInstanceA());
        ProductDescription description = ProductDescriptionFixture.descriptionWithBlocks(blocks);

        blocks.clear();

        assertThat(description).hasBlockCount(3);
        assertThatThrownBy(() -> description.blocks().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
