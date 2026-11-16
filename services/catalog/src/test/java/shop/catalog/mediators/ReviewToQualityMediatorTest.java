package shop.catalog.mediators;

import org.junit.jupiter.api.Test;
import shop.catalog.offer.Media;
import shop.catalog.offer.ProductDescription;
import shop.catalog.review.OfferContent;
import shop.catalog.review.QualityFindings;
import shop.catalog.review.ReviewFinding;
import shop.catalog.review.ReviewRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.catalog.mediators.QualityContentFixture.candidateOf;
import static shop.catalog.mediators.QualityContentFixture.contentOf;
import static shop.catalog.quality.QualityConfigFixture.legoPolicy;
import static shop.catalog.quality.QualityFixture.imagesWithPositionGap;
import static shop.catalog.quality.QualityFixture.imagesWithSinglePrimary;
import static shop.catalog.quality.QualityFixture.imagesWithTwoPrimaries;
import static shop.catalog.quality.QualityFixture.legoDraft;
import static shop.catalog.review.ReviewFixture.request;

/**
 * The {@code review → quality} edge of 17 through the port that carries it: the review asks {@link QualityFindings} with the
 * content a candidate freezes, and the real mediator answers over the real policy of 08 and its shipped configuration. Nothing here
 * asserts that a call was delegated — the findings are the proof.
 */
class ReviewToQualityMediatorTest {

    private final QualityFindings quality = new ReviewToQualityMediator(legoPolicy());

    @Test
    void answersCleanContentWithNoFinding() {
        assertThat(quality.findingsFor(contentOf(lego(), imagesWithSinglePrimary()))).isEmpty();
    }

    @Test
    void answersContentThatBreaksARuleWithThatBlockingFinding() {
        List<ReviewFinding> findings = quality.findingsFor(contentOf(lego(), imagesWithTwoPrimaries()));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.rule()).isEqualTo("QP-5");
            assertThat(finding.field()).isEqualTo("media.primary");
            assertThat(finding.severity()).isEqualTo(ReviewFinding.Severity.BLOCKING);
        });
    }

    @Test
    void keepsAWarningAWarningSoItCannotBlockAPublication() {
        List<ReviewFinding> findings = quality.findingsFor(contentOf(lego(), imagesWithPositionGap()));

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.rule()).isEqualTo("QP-8");
            assertThat(finding.severity()).isEqualTo(ReviewFinding.Severity.WARNING);
            assertThat(finding.isBlocking()).isFalse();
            assertThat(finding.message()).contains("[1, 3]");
        });
    }

    @Test
    void judgesTheCategoryTheFrozenContentCarries() {
        OfferContent content = contentOf(draftWithoutParameters(), imagesWithSinglePrimary());

        assertThat(quality.findingsFor(content)).hasSize(6)
                .allSatisfy(finding -> assertThat(finding.rule()).isEqualTo("QP-10"));
    }

    @Test
    void aReviewRequestFreezesTheReportAndTheMediaInTheOrderTheOfferHoldsThem() {
        List<Media> media = imagesWithTwoPrimaries();
        List<Media> asTheOfferHoldsThem = List.of(media.get(1), media.get(0));

        OfferContent content = contentOf(lego(), asTheOfferHoldsThem);
        ReviewRequest request = request(candidateOf(content, quality.findingsFor(content)));

        assertThat(request.candidate().hasBlockingFindings()).isTrue();
        assertThat(request.candidate().media()).containsExactlyElementsOf(asTheOfferHoldsThem);
        assertThat(request.candidate().media()).extracting(Media::primary).containsExactly(true, true);
    }

    private static ProductDescription lego() {
        return legoDraft().toDefinition();
    }

    private static ProductDescription draftWithoutParameters() {
        return legoDraft().toBuilder().parameters(List.of()).build().toDefinition();
    }
}
