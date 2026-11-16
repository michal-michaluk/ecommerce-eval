package shop.catalog.review;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

/**
 * {@link ReviewService} wired to its collaborators, so a test states only the fake it cares about. The clock is the moment 04
 * fixtures decide at, which keeps a decided review's {@code reviewedAt} equal to {@link ReviewFixture#REVIEWED_AT}.
 *
 * <p>The quality port defaults to a clean report: a test that is not about 08 says so by passing its own
 * {@link FakeQualityFindings}. The offer's review gate defaults to the accepting answer, so a test that is not about 02 RULE-1 needs
 * no offer of its own.
 */
public class ReviewServiceFixture {

    private static final Clock CLOCK = Clock.fixed(ReviewFixture.REVIEWED_AT, ZoneOffset.UTC);

    private ReviewServiceFixture() {
    }

    public static ReviewService service(
            ReviewRepository reviews, OfferDraftSnapshot offers, OfferPublication publications) {
        return service(reviews, offers, publications, ReviewFixture.OVERRIDE_ENABLED);
    }

    public static ReviewService service(
            ReviewRepository reviews, OfferDraftSnapshot offers, OfferPublication publications, boolean overrideEnabled) {
        return service(reviews, offers, publications, FakeOfferReview.accepting(), overrideEnabled);
    }

    public static ReviewService service(
            ReviewRepository reviews, OfferDraftSnapshot offers, OfferPublication publications,
            QualityFindings quality, boolean overrideEnabled) {
        return new ReviewService(reviews, offers, publications, FakeOfferReview.accepting(), quality,
                new ReviewConfiguration.OverrideSettings(overrideEnabled), CLOCK);
    }

    public static ReviewService service(
            ReviewRepository reviews, OfferDraftSnapshot offers, OfferPublication publications,
            OfferReview offerReviews, boolean overrideEnabled) {
        return new ReviewService(reviews, offers, publications, offerReviews,
                FakeQualityFindings.answeringWith(List.of()),
                new ReviewConfiguration.OverrideSettings(overrideEnabled), CLOCK);
    }
}
