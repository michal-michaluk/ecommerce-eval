package shop.catalog.mediators;

import org.springframework.stereotype.Component;
import shop.catalog.offer.Media;
import shop.catalog.offer.ProductDescription;
import shop.catalog.quality.QualityFindings.Finding;
import shop.catalog.quality.QualityPolicy;
import shop.catalog.reads.DraftFindings;
import shop.catalog.reads.QualityFinding;

import java.util.List;

/**
 * Joins the {@code reads → quality} edge of 17: the port the draft screen declares (14), answered by the policy of 08.
 *
 * <p>Pattern 3 of {@code context-boundaries.md}, and one of the places both contexts are named (17 RULE-1). It maps the two shapes
 * the sides state differently — 08's severity word into the read side's, and the rule constant into the label the panel shows — and
 * nothing else: no rule, no default, no branch beyond that mapping. The draft arrives as the value 08 already takes, so the screen
 * judges a draft exactly as the editing path and the review request do.
 *
 * <p>{@code categoryPath} is 08's own argument and not a component read off the draft (08, Q1c): the path the draft carries is the
 * category it is listed in, so that is what the panel is judged against.
 */
@Component
class QualityToReadsMediator implements DraftFindings {

    private final QualityPolicy policy;

    QualityToReadsMediator(QualityPolicy policy) {
        this.policy = policy;
    }

    @Override
    public List<QualityFinding> findingsOf(ProductDescription.Draft draft, List<Media> media) {
        return policy.calculateFindings(draft, media, draft.categoryPath()).findings().stream()
                .map(QualityToReadsMediator::qualityFindingOf)
                .toList();
    }

    private static QualityFinding qualityFindingOf(Finding finding) {
        return new QualityFinding(finding.rule().label(), finding.field(),
                QualityFinding.Severity.valueOf(finding.severity().name()), finding.message());
    }
}
