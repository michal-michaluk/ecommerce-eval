package shop.catalog.review;

import java.util.ArrayList;
import java.util.List;

/** The quality side of {@link QualityFindings}: it answers with a fixed report and remembers the content it judged. */
class FakeQualityFindings implements QualityFindings {

    private final List<ReviewFinding> answer;

    private final List<OfferContent> evaluated = new ArrayList<>();

    private FakeQualityFindings(List<ReviewFinding> answer) {
        this.answer = List.copyOf(answer);
    }

    static FakeQualityFindings answeringWith(List<ReviewFinding> findings) {
        return new FakeQualityFindings(findings);
    }

    @Override
    public List<ReviewFinding> findingsFor(OfferContent content) {
        evaluated.add(content);
        return answer;
    }

    List<OfferContent> evaluated() {
        return List.copyOf(evaluated);
    }
}
