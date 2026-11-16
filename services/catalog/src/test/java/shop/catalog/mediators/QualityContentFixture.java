package shop.catalog.mediators;

import shop.catalog.offer.Media;
import shop.catalog.offer.ProductDescription;
import shop.catalog.review.OfferContent;
import shop.catalog.review.ReviewCandidate;
import shop.catalog.review.ReviewFinding;

import java.time.LocalDate;
import java.util.List;

/**
 * The content one review freezes, as the review context states it: the frozen description, the media and the availability date the
 * candidate carries. The media is the offer's own value, so a test of this edge names exactly the type the edge carries (D45).
 */
public class QualityContentFixture {

    public static final LocalDate AVAILABILITY_START = LocalDate.of(2026, 7, 1);

    private QualityContentFixture() {
    }

    public static OfferContent contentOf(ProductDescription description, List<Media> media) {
        return new OfferContent(description, media, AVAILABILITY_START);
    }

    public static ReviewCandidate candidateOf(OfferContent content, List<ReviewFinding> findings) {
        return new ReviewCandidate(content.description(), content.media(), content.availabilityStart(), findings, null);
    }
}
