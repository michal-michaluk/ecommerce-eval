package shop.catalog.review;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

/**
 * {@link ReviewService} wired to its collaborators, so a test states only the fake it cares about. The clock is the moment 04
 * fixtures decide at, which keeps a decided review's {@code reviewedAt} equal to {@link ReviewFixture#REVIEWED_AT}.
 *
 * <p>The quality port defaults to a clean report: a test that is not about 08 says so by passing its own
 * {@link FakeQualityFindings}.
 */
public class ReviewServiceFixture {

    private ReviewServiceFixture() {
    }

    public static ReviewService service(
            ReviewRepository reviews, OfferDraftSnapshot offers, OfferPublication publications) {
        return service(reviews, offers, publications, ReviewFixture.OVERRIDE_ENABLED);
    }

    public static ReviewService service(
            ReviewRepository reviews, OfferDraftSnapshot offers, OfferPublication publications, boolean overrideEnabled) {
        return service(reviews, offers, publications, FakeQualityFindings.answeringWith(List.of()), overrideEnabled);
    }

    public static ReviewService service(
            ReviewRepository reviews, OfferDraftSnapshot offers, OfferPublication publications,
            QualityFindings quality, boolean overrideEnabled) {
        return new ReviewService(reviews, offers, publications, quality,
                new ReviewConfiguration.OverrideSettings(overrideEnabled),
                Clock.fixed(ReviewFixture.REVIEWED_AT, ZoneOffset.UTC));
    }
}
