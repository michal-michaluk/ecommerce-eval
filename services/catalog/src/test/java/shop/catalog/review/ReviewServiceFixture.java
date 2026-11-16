package shop.catalog.review;

import java.time.Clock;
import java.time.ZoneOffset;

/**
 * {@link ReviewService} wired to its collaborators, so a test states only the fake it cares about. The clock is the moment 04
 * fixtures decide at, which keeps a decided review's {@code reviewedAt} equal to {@link ReviewFixture#REVIEWED_AT}.
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
        return new ReviewService(reviews, offers, publications,
                new ReviewConfiguration.OverrideSettings(overrideEnabled),
                Clock.fixed(ReviewFixture.REVIEWED_AT, ZoneOffset.UTC));
    }
}
