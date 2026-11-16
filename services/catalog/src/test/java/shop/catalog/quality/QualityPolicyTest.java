package shop.catalog.quality;

import org.junit.jupiter.api.Test;

import java.util.List;

import static shop.catalog.quality.QualityAssert.assertThat;
import static shop.catalog.quality.QualityConfigFixture.STAR_WARS_CHARACTER;
import static shop.catalog.quality.QualityConfigFixture.legoAndStarWarsPolicy;
import static shop.catalog.quality.QualityConfigFixture.legoPolicy;
import static shop.catalog.quality.QualityConfigFixture.strictMediaPolicy;
import static shop.catalog.quality.QualityFindings.Rule.QP_1;
import static shop.catalog.quality.QualityFindings.Rule.QP_10;
import static shop.catalog.quality.QualityFindings.Rule.QP_11;
import static shop.catalog.quality.QualityFindings.Rule.QP_12;
import static shop.catalog.quality.QualityFindings.Rule.QP_2;
import static shop.catalog.quality.QualityFindings.Rule.QP_3;
import static shop.catalog.quality.QualityFindings.Rule.QP_4;
import static shop.catalog.quality.QualityFindings.Rule.QP_5;
import static shop.catalog.quality.QualityFindings.Rule.QP_6;
import static shop.catalog.quality.QualityFindings.Rule.QP_7;
import static shop.catalog.quality.QualityFindings.Rule.QP_8;
import static shop.catalog.quality.QualityFindings.Severity.BLOCKING;
import static shop.catalog.quality.QualityFindings.Severity.WARNING;
import static shop.catalog.quality.QualityFixture.COMPLETE_PROSE;
import static shop.catalog.quality.QualityFixture.EAN_GTIN;
import static shop.catalog.quality.QualityFixture.FASHION_CATEGORY;
import static shop.catalog.quality.QualityFixture.LEGO_CATEGORY;
import static shop.catalog.quality.QualityFixture.LEGO_STAR_WARS_CATEGORY;
import static shop.catalog.quality.QualityFixture.PRODUCT_NUMBER;
import static shop.catalog.quality.QualityFixture.draftMissingParameters;
import static shop.catalog.quality.QualityFixture.draftWithBulletsOnly;
import static shop.catalog.quality.QualityFixture.draftWithExtraBatteryType;
import static shop.catalog.quality.QualityFixture.draftWithHeadingAndParagraph;
import static shop.catalog.quality.QualityFixture.draftWithIdentifiers;
import static shop.catalog.quality.QualityFixture.draftWithImageBlock;
import static shop.catalog.quality.QualityFixture.draftWithParagraph;
import static shop.catalog.quality.QualityFixture.draftWithTextualPieceCount;
import static shop.catalog.quality.QualityFixture.draftWithTitle;
import static shop.catalog.quality.QualityFixture.draftWithoutParameters;
import static shop.catalog.quality.QualityFixture.imagesWithPositionGap;
import static shop.catalog.quality.QualityFixture.imagesWithSinglePrimary;
import static shop.catalog.quality.QualityFixture.imagesWithTwoPrimaries;
import static shop.catalog.quality.QualityFixture.legoDraft;
import static shop.catalog.quality.QualityFixture.workedExampleDraft;
import static shop.catalog.quality.QualityFixture.workedExampleMedia;

/** The eighteen scenarios of 08, its worked example, the derivation of {@code minProseChars} and the enabled variants. */
class QualityPolicyTest {

    final QualityPolicy policy = legoPolicy();

    @Test
    void s1ACompleteDraftWithOnePrimaryImageHasNoFindings() {
        QualityFindings findings = policy.calculateFindings(legoDraft(), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings)
                .hasNoFindings()
                .hasProse(COMPLETE_PROSE)
                .hasMatchedCategory(LEGO_CATEGORY)
                .isPublishable();
    }

    @Test
    void s2AMissingEanGtinIsBlocking() {
        QualityFindings findings = policy.calculateFindings(
                draftWithIdentifiers(PRODUCT_NUMBER, null), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings).hasOnlyFinding(QP_2, BLOCKING, "description.ids.eanGtin");
    }

    @Test
    void s3BulletProseWithoutAnyParagraphBlockIsBlocking() {
        QualityFindings findings = policy.calculateFindings(
                draftWithBulletsOnly(12, 1025), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings)
                .hasOnlyFinding(QP_6, BLOCKING, "description.blocks")
                .hasProse(1025)
                .hasMessages("at least one paragraph block is required: prose counts paragraphs and bullet lists only");
    }

    @Test
    void s4OneLongParagraphSatisfiesTheProseTotal() {
        QualityFindings findings = policy.calculateFindings(
                draftWithParagraph(1025), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings).hasProse(1025).hasNoFindings();
    }

    @Test
    void s5ProseBelowTheThresholdIsBlocking() {
        QualityFindings findings = policy.calculateFindings(
                draftWithParagraph(800), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings)
                .hasOnlyFinding(QP_6, BLOCKING, "description.blocks")
                .hasProse(800)
                .hasMessages("prose 800 < 1000 (minProseChars)");
    }

    @Test
    void s6AHeadingAndBulletsWithoutAParagraphBlockIsBlocking() {
        QualityFindings findings = policy.calculateFindings(
                draftWithBulletsOnly(4, 1000), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings).hasProse(1000).hasOnlyFinding(QP_6, BLOCKING, "description.blocks");
    }

    @Test
    void s7AnImageBlockOfUnknownMediaIsBlocking() {
        QualityFindings findings = policy.calculateFindings(
                draftWithImageBlock("M-99"), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings).hasOnlyFinding(QP_7, BLOCKING, "description.blocks");
    }

    @Test
    void s8EmptyMediaBreaksBothTheImageAndThePrimaryRule() {
        QualityFindings findings = policy.calculateFindings(legoDraft(), List.of(), LEGO_CATEGORY);

        assertThat(findings)
                .hasRules(QP_4, QP_5)
                .hasFields("media", "media.primary")
                .hasSeverities(BLOCKING, BLOCKING);
    }

    @Test
    void s9TwoPrimaryMediaItemsAreBlocking() {
        QualityFindings findings = policy.calculateFindings(
                legoDraft(), imagesWithTwoPrimaries(), LEGO_CATEGORY);

        assertThat(findings).hasOnlyFinding(QP_5, BLOCKING, "media.primary");
    }

    @Test
    void s10AMediaPositionGapIsAWarning() {
        QualityFindings findings = policy.calculateFindings(
                legoDraft(), imagesWithPositionGap(), LEGO_CATEGORY);

        assertThat(findings).hasOnlyFinding(QP_8, WARNING, "media");
    }

    @Test
    void s11AMissingRequiredParameterOfTheCategoryIsBlocking() {
        QualityFindings findings = policy.calculateFindings(
                draftMissingParameters("pieceCount"), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings).hasOnlyFinding(QP_10, BLOCKING, "parameters.pieceCount");
    }

    @Test
    void s12ARequiredParameterOfAnotherValueTypeIsBlocking() {
        QualityFindings findings = policy.calculateFindings(
                draftWithTextualPieceCount(), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings)
                .hasOnlyFinding(QP_11, BLOCKING, "parameters.pieceCount")
                .hasMessages("parameter 'pieceCount' declares value type TEXT, the category expects INTEGER");
    }

    @Test
    void s13AParameterTheCategoryDoesNotNameIsAWarning() {
        QualityFindings findings = policy.calculateFindings(
                draftWithExtraBatteryType(), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings).hasOnlyFinding(QP_12, WARNING, "parameters.batteryType");
    }

    @Test
    void s14AnUnconfiguredCategoryAppliesNoCategoryRule() {
        QualityFindings findings = policy.calculateFindings(
                draftWithoutParameters(), imagesWithSinglePrimary(), FASHION_CATEGORY);

        assertThat(findings).hasNoFindings().hasNoMatchedCategory();
    }

    @Test
    void s15TwoMissingParametersReportInTheConfigurationDeclarationOrder() {
        QualityFindings findings = policy.calculateFindings(
                draftMissingParameters("pieceCount", "safetyInfo"), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings)
                .hasRules(QP_10, QP_10)
                .hasFields("parameters.pieceCount", "parameters.safetyInfo");
    }

    @Test
    void s16AnEmptyTitleIsBlocking() {
        QualityFindings findings = policy.calculateFindings(
                draftWithTitle(""), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings).hasOnlyFinding(QP_1, BLOCKING, "description.title");
    }

    @Test
    void s17AMissingProductNumberIsBlocking() {
        QualityFindings findings = policy.calculateFindings(
                draftWithIdentifiers(null, EAN_GTIN), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings).hasOnlyFinding(QP_3, BLOCKING, "description.ids.productNumber");
    }

    @Test
    void s18AWarningOnlyDraftStaysPublishable() {
        QualityFindings findings = policy.calculateFindings(
                legoDraft(), imagesWithPositionGap(), LEGO_CATEGORY);

        assertThat(findings).hasOnlyFinding(QP_8, WARNING, "media").isPublishable();
    }

    @Test
    void workedExampleReportsProseTheMatchedCategoryAndFourFindingsInOrder() {
        QualityFindings findings = policy.calculateFindings(
                workedExampleDraft(), workedExampleMedia(), LEGO_STAR_WARS_CATEGORY);

        assertThat(findings)
                .hasProse(620)
                .hasMatchedCategory(LEGO_CATEGORY)
                .hasRules(QP_6, QP_10, QP_10, QP_10, QP_10)
                .hasFields("description.blocks", "parameters.condition", "parameters.pieceCount",
                        "parameters.certificates", "parameters.safetyInfo")
                .hasSeverities(BLOCKING, BLOCKING, BLOCKING, BLOCKING, BLOCKING)
                .hasMessages("prose 620 < 1000 (minProseChars)",
                        "required parameter missing for category LEGO",
                        "required parameter missing for category LEGO",
                        "required parameter missing for category LEGO",
                        "required parameter missing for category LEGO")
                .isNotPublishable();
    }

    @Test
    void minProseCharsExcludesHeadingText() {
        QualityFindings findings = policy.calculateFindings(
                draftWithHeadingAndParagraph(900, 400), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings).hasProse(400).hasOnlyFinding(QP_6, BLOCKING, "description.blocks");
    }

    @Test
    void aProseOfExactlyMinProseCharsPasses() {
        QualityFindings findings = policy.calculateFindings(
                draftWithParagraph(1000), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings).hasProse(1000).hasNoFindings();
    }

    @Test
    void missingParametersReportInTheDeclarationOrderWhateverOrderTheyAreNamedIn() {
        QualityFindings findings = policy.calculateFindings(
                draftMissingParameters("safetyInfo", "childAge"), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings).hasFields("parameters.childAge", "parameters.safetyInfo");
    }

    @Test
    void strictMediaVariantPromotesTheMediaSequenceWarningAndLeavesTheOtherRulesAlone() {
        QualityFindings findings = strictMediaPolicy().calculateFindings(
                draftWithExtraBatteryType(), imagesWithPositionGap(), LEGO_CATEGORY);

        assertThat(findings)
                .hasRules(QP_8, QP_12)
                .hasSeverities(BLOCKING, WARNING)
                .isNotPublishable();
    }

    @Test
    void theUniversalOnlyConfigurationAppliesTheShippedThresholdAndNoCategoryRule() {
        QualityFindings findings = QualityPolicy.withConfig(QualityConfig.defaultConfig()).calculateFindings(
                draftWithoutParameters(), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings).hasNoFindings().hasNoMatchedCategory();
    }

    @Test
    void theLongestMatchingCategoryPrefixWins() {
        QualityFindings findings = legoAndStarWarsPolicy().calculateFindings(
                legoDraft(), imagesWithSinglePrimary(), LEGO_STAR_WARS_CATEGORY);

        assertThat(findings)
                .hasMatchedCategory(LEGO_STAR_WARS_CATEGORY)
                .hasOnlyFinding(QP_10, BLOCKING, "parameters." + STAR_WARS_CHARACTER);
    }
}
