package shop.catalog.mediators;

import org.springframework.stereotype.Component;
import shop.catalog.offer.ProductDescription;
import shop.catalog.quality.QualityFindings.Finding;
import shop.catalog.quality.QualityPolicy;
import shop.catalog.review.OfferContent;
import shop.catalog.review.QualityFindings;
import shop.catalog.review.ReviewFinding;

import java.util.List;

/**
 * Joins the {@code review → quality} edge of 17: the contract port 04 declares, answered by the policy of 08.
 *
 * <p>Pattern 3 of {@code context-boundaries.md}, and one of the two places both contexts are named (17 RULE-1). Its one provider is
 * the policy itself, because 08's whole answer <em>is</em> that policy — the quality context has no service and no aggregate to
 * put in front of it (17). The mediator maps the two shapes the sides state differently — the content a candidate freezes into
 * the draft and path 08 reads, and 08's finding into the review's — and nothing else: no rule, no default, no branch beyond that
 * mapping. The rules stay in {@link QualityPolicy} and its configuration, so a draft is judged identically here and on the draft
 * screen.
 *
 * <p>The one vocabulary left to bridge is the severity word: each context owns a closed two-constant set, and translating between
 * them is the price of the review answering in its own language ({@code contract.own-value-objects}). It is a bridge of two
 * constants, not a copy of a value object — the media copy D45 removed carried five components and its own kind enum.
 *
 * <p>It answers, it never fails: content that breaks a rule is a legal answer — a list of findings — and not an exception, which
 * is the port's whole failure vocabulary (08).
 */
@Component
class ReviewToQualityMediator implements QualityFindings {

    private final QualityPolicy policy;

    ReviewToQualityMediator(QualityPolicy policy) {
        this.policy = policy;
    }

    @Override
    public List<ReviewFinding> findingsFor(OfferContent content) {
        return policy.calculateFindings(draftOf(content.description()), content.media(),
                        content.description().categoryPath()).findings().stream()
                .map(ReviewToQualityMediator::reviewFindingOf)
                .toList();
    }

    private static ProductDescription.Draft draftOf(ProductDescription description) {
        return new ProductDescription.Draft(description.title(), description.identifiers(), description.brand(),
                description.condition(), description.categoryPath(), description.parameters(), description.blocks());
    }

    private static ReviewFinding reviewFindingOf(Finding finding) {
        return new ReviewFinding(finding.rule().label(), finding.field(),
                ReviewFinding.Severity.valueOf(finding.severity().name()), finding.message());
    }
}
