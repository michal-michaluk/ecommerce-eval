package shop.catalog.quality;

import org.junit.jupiter.api.Test;
import shop.catalog.offer.ProductDescription;
import shop.catalog.quality.QualityException.Error;
import shop.catalog.quality.QualityFindings.Rule;

import static shop.catalog.quality.QualityAssert.assertRefused;
import static shop.catalog.quality.QualityAssert.assertThat;
import static shop.catalog.quality.QualityConfigFixture.STAR_WARS_CHARACTER;
import static shop.catalog.quality.QualityConfigFixture.configWithBlankParameterCode;
import static shop.catalog.quality.QualityConfigFixture.configWithMinProseChars;
import static shop.catalog.quality.QualityConfigFixture.configWithRepeatedSeverityOverride;
import static shop.catalog.quality.QualityConfigFixture.configWithoutPrefix;
import static shop.catalog.quality.QualityConfigFixture.policyWithOptionalPieceCount;
import static shop.catalog.quality.QualityConfigFixture.repeatedLegoPrefixPolicy;
import static shop.catalog.quality.QualityConfigFixture.starWarsThenLegoPolicy;
import static shop.catalog.quality.QualityFindings.Severity.BLOCKING;
import static shop.catalog.quality.QualityFixture.LEGO_CATEGORY;
import static shop.catalog.quality.QualityFixture.LEGO_STAR_WARS_CATEGORY;
import static shop.catalog.quality.QualityFixture.draftMissingParameters;
import static shop.catalog.quality.QualityFixture.draftWithTextualPieceCount;
import static shop.catalog.quality.QualityFixture.imagesWithSinglePrimary;
import static shop.catalog.quality.QualityFixture.legoDraft;

/**
 * The configuration of 08: the values it refuses, the {@code required} flag it carries, and the prefix match it drives. The
 * policy itself never throws — only a configuration that cannot be interpreted does.
 */
class QualityConfigTest {

    @Test
    void refuseAProseThresholdThatIsNotPositive() {
        assertRefused(Error.MIN_PROSE_CHARS_NOT_POSITIVE, () -> configWithMinProseChars(0));
    }

    @Test
    void refuseACategoryThatNamesNoPrefix() {
        assertRefused(Error.CATEGORY_PREFIX_MISSING, QualityConfigFixture::configWithoutPrefix);
    }

    @Test
    void refuseAParameterThatNamesNoCode() {
        assertRefused(Error.PARAMETER_CODE_MISSING, QualityConfigFixture::configWithBlankParameterCode);
    }

    @Test
    void refuseTwoSeverityOverridesOfTheSameRule() {
        assertRefused(Error.SEVERITY_DECLARED_TWICE, QualityConfigFixture::configWithRepeatedSeverityOverride);
    }

    @Test
    void aParameterThatIsNotRequiredMayBeAbsent() {
        QualityFindings findings = policyWithOptionalPieceCount()
                .calculateFindings(draftMissingParameters("pieceCount"), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings).hasNoFindings();
    }

    @Test
    void aParameterOfTheWrongValueTypeIsReportedEvenWhenItIsNotRequired() {
        QualityFindings findings = policyWithOptionalPieceCount()
                .calculateFindings(draftWithTextualPieceCount(), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings).hasOnlyFinding(Rule.QP_11, BLOCKING, "parameters.pieceCount");
    }

    @Test
    void theLongestPrefixWinsWhateverTheDeclarationOrderIs() {
        QualityFindings findings = starWarsThenLegoPolicy()
                .calculateFindings(legoDraft(), imagesWithSinglePrimary(), LEGO_STAR_WARS_CATEGORY);

        assertThat(findings)
                .hasMatchedCategory(LEGO_STAR_WARS_CATEGORY)
                .hasOnlyFinding(Rule.QP_10, BLOCKING, "parameters." + STAR_WARS_CHARACTER);
    }

    @Test
    void theFirstOfTwoEqualPrefixesIsFinal() {
        QualityFindings findings = repeatedLegoPrefixPolicy()
                .calculateFindings(legoDraft(), imagesWithSinglePrimary(), LEGO_CATEGORY);

        assertThat(findings).hasNoFindings();
    }
}
