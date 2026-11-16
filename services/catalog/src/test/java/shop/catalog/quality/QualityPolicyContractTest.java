package shop.catalog.quality;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import shop.catalog.offer.Media;
import shop.catalog.offer.ProductDescription;

import java.util.ArrayList;
import java.util.List;

import static shop.catalog.quality.QualityAssert.assertThat;
import static shop.catalog.quality.QualityConfigFixture.legoPolicy;
import static shop.catalog.quality.QualityFindings.Rule.QP_2;
import static shop.catalog.quality.QualityFindings.Rule.QP_3;
import static shop.catalog.quality.QualityFindings.Rule.QP_4;
import static shop.catalog.quality.QualityFindings.Rule.QP_5;
import static shop.catalog.quality.QualityFindings.Rule.QP_6;
import static shop.catalog.quality.QualityFixture.LEGO_CATEGORY;
import static shop.catalog.quality.QualityFixture.TITLE;
import static shop.catalog.quality.QualityFixture.draftWithBulletsOnly;
import static shop.catalog.quality.QualityFixture.legoDraft;
import static shop.catalog.quality.QualityFixture.workedExampleMedia;

/**
 * The contract of the call rather than of one scenario: findings are reported rule-major, an incomplete draft is answered instead
 * of refused, and neither input is mutated.
 */
class QualityPolicyContractTest {

    final QualityPolicy policy = legoPolicy();

    @Test
    void findingsAreOrderedByRuleNumberNotByTheOrderTheGroupsAreEvaluatedIn() {
        QualityFindings findings = policy.calculateFindings(
                draftWithBulletsOnly(12, 1025), List.of(), LEGO_CATEGORY);

        assertThat(findings)
                .hasRules(QP_4, QP_5, QP_6)
                .hasFields("media", "media.primary", "description.blocks");
    }

    @Test
    void anEmptyDraftReportsFindingsInsteadOfThrowing() {
        ProductDescription.Draft emptyDraft = ProductDescription.Draft.builder().title(TITLE).build();

        QualityFindings findings = policy.calculateFindings(emptyDraft, null, null);

        assertThat(findings)
                .hasRules(QP_2, QP_3, QP_4, QP_5, QP_6, QP_6)
                .hasFields("description.ids.eanGtin", "description.ids.productNumber", "media", "media.primary",
                        "description.blocks", "description.blocks")
                .hasNoMatchedCategory()
                .isNotPublishable();
    }

    @Test
    void theCallLeavesItsInputsAsItFoundThem() {
        List<Media> media = new ArrayList<>(workedExampleMedia().reversed());
        List<Media> asGiven = List.copyOf(media);

        QualityFindings findings = policy.calculateFindings(legoDraft(), media, LEGO_CATEGORY);

        assertThat(findings).hasNoFindings();
        Assertions.assertThat(media).containsExactlyElementsOf(asGiven);
    }
}
