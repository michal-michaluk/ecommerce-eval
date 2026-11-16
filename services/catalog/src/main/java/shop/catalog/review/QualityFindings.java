package shop.catalog.review;

import java.util.List;

/**
 * The contract port that asks 08 what is wrong with the content a candidate is about to freeze (17, 08, 04 RULE-8).
 *
 * <p>The answer is a list of findings in the review context's own language (rule label, field, severity, message), so the quality
 * context stays replaceable: the mediator maps 08's report into {@link ReviewFinding} and the frozen candidate carries the review's
 * shape, never 08's. The findings are frozen into the candidate, which is why 09 can decide at acceptance without asking 08 again.
 *
 * <p>It answers, it never fails: content that violates the rules is a legal answer — a list of findings — and not an exception.
 */
public interface QualityFindings {

    List<ReviewFinding> findingsFor(OfferContent content);
}
