package shop.catalog.review;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import shop.catalog.offer.ProductDescription;
import shop.catalog.review.Review.State;

import java.time.Instant;

/** The assertions of 04: state, who asked and decided, the frozen candidate, the reason and the published version. */
public class ReviewAssert {

    private final Review review;

    private ReviewAssert(Review review) {
        this.review = review;
    }

    public static ReviewAssert assertThat(ReviewProcess actual) {
        Assertions.assertThat(actual).isNotNull();
        return new ReviewAssert(actual.toReview());
    }

    public static ReviewAssert assertThat(Review actual) {
        Assertions.assertThat(actual).isNotNull();
        return new ReviewAssert(actual);
    }

    public static void assertRefused(ReviewException.Error expected, ThrowingCallable call) {
        Assertions.assertThatThrownBy(call)
                .isInstanceOfSatisfying(ReviewException.class,
                        refused -> Assertions.assertThat(refused.error()).isEqualTo(expected));
    }

    public ReviewAssert hasReviewId(String expected) {
        Assertions.assertThat(review.reviewId()).isEqualTo(expected);
        return this;
    }

    public ReviewAssert hasOfferId(String expected) {
        Assertions.assertThat(review.offerId()).isEqualTo(expected);
        return this;
    }

    public ReviewAssert hasState(State expected) {
        Assertions.assertThat(review.state()).isEqualTo(expected);
        return this;
    }

    public ReviewAssert isOpen() {
        Assertions.assertThat(review.isOpen()).isTrue();
        return this;
    }

    public ReviewAssert wasRequestedBy(String expected, Instant expectedAt) {
        Assertions.assertThat(review.requestedBy()).isEqualTo(expected);
        Assertions.assertThat(review.requestedAt()).isEqualTo(expectedAt);
        return this;
    }

    public ReviewAssert wasReviewedBy(String expected, Instant expectedAt) {
        Assertions.assertThat(review.reviewedBy()).isEqualTo(expected);
        Assertions.assertThat(review.reviewedAt()).isEqualTo(expectedAt);
        return this;
    }

    public ReviewAssert hasNoReviewer() {
        Assertions.assertThat(review.reviewedBy()).isNull();
        Assertions.assertThat(review.reviewedAt()).isNull();
        return this;
    }

    public ReviewAssert hasReason(String expected) {
        Assertions.assertThat(review.reason()).isEqualTo(expected);
        return this;
    }

    public ReviewAssert hasNoReason() {
        Assertions.assertThat(review.reason()).isNull();
        return this;
    }

    public ReviewAssert hasCandidate(ReviewCandidate expected) {
        Assertions.assertThat(review.candidate()).isEqualTo(expected);
        return this;
    }

    public ReviewAssert hasCandidateDescription(ProductDescription expected) {
        Assertions.assertThat(review.candidate().description()).isEqualTo(expected);
        return this;
    }

    public ReviewAssert hasPublishedVersion(int expected) {
        Assertions.assertThat(review.publishedOfferVersion()).isEqualTo(expected);
        Assertions.assertThat(review.isPublished()).isTrue();
        return this;
    }

    public ReviewAssert hasNoPublishedVersion() {
        Assertions.assertThat(review.publishedOfferVersion()).isNull();
        return this;
    }
}
