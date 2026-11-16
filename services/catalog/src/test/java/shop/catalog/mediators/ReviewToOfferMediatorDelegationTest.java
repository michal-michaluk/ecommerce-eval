package shop.catalog.mediators;

import org.junit.jupiter.api.Test;
import shop.catalog.offer.OfferService;
import shop.catalog.review.OfferReview;
import shop.catalog.review.ReviewFixture;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The {@code review → offer} edge at its seam: every command of {@link OfferReview} reaches the offer's primary port with the values
 * the review named, and the mediator adds no rule of its own. The fake is the port's collaborator, not the offer's state, so what is
 * proven here is the hop — the behaviour behind it is proven where the offer decides.
 */
class ReviewToOfferMediatorDelegationTest {

    final OfferService offers = mock(OfferService.class);

    final OfferReview offerReviews = new ReviewToOfferMediator(offers);

    @Test
    void passesTheAttemptTheReviewOpensToTheOfferService() {
        offerReviews.requestReview(ReviewFixture.OFFER_ID, ReviewFixture.REVIEW_ID, ReviewFixture.REQUESTED_AT);

        verify(offers).requestReview(ReviewFixture.OFFER_ID);
    }

    @Test
    void passesTheAttemptTheReviewClosesToTheOfferService() {
        offerReviews.closeReview(ReviewFixture.OFFER_ID, ReviewFixture.REVIEW_ID);

        verify(offers).closeReview(ReviewFixture.OFFER_ID, ReviewFixture.REVIEW_ID);
    }
}
