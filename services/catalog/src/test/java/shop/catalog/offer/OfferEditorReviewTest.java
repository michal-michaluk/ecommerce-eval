package shop.catalog.offer;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import shop.catalog.offer.Offer.State;
import shop.catalog.offer.OfferEvent.OfferReviewClosed;

import static shop.catalog.offer.OfferAssert.assertRefused;
import static shop.catalog.offer.OfferAssert.assertThat;
import static shop.catalog.offer.OfferFixture.OFFER_ID;
import static shop.catalog.offer.OfferFixture.REVIEWER;
import static shop.catalog.offer.OfferFixture.REVIEW_ID;
import static shop.catalog.offer.OfferFixture.draftOfferWithMedia;
import static shop.catalog.offer.OfferFixture.freeIdentifiers;
import static shop.catalog.offer.OfferFixture.now;
import static shop.catalog.offer.OfferFixture.offerPendingReview;
import static shop.catalog.offer.OfferFixture.publishedOffer;
import static shop.catalog.offer.OfferFixture.removedOffer;

/**
 * The two ends of one review attempt (D51): {@code requestReview} opens it and {@code closeReview} ends it whenever the attempt ends
 * without a version. A draft returns to {@code draft} (04 RULE-5), a published offer stays published and revertable again (D37), a
 * removed one stays removed (RULE-6) — the decision is never vetoed by a state the offer reached on its own — and the closure is one
 * fact, never two. An acceptance needs no closure: the freeze clears the flag itself.
 */
class OfferEditorReviewTest {

    @Test
    void aRejectedReviewReturnsTheDraftToDraft() {
        OfferEditor offer = offerPendingReview();

        offer.closeReview(REVIEW_ID);

        assertThat(offer).hasState(State.DRAFT).hasVersion(0);
        Assertions.assertThat(offer.toSurface().reviewOpen()).isFalse();
    }

    @Test
    void aRejectedReviewOfAPublishedOfferKeepsItPublished() {
        OfferEditor offer = publishedOffer();
        offer.requestReview();

        offer.closeReview(REVIEW_ID);

        assertThat(offer).hasState(State.PUBLISHED).hasVersion(1);
        Assertions.assertThat(offer.toSurface().reviewOpen()).isFalse();
    }

    @Test
    void aClosedReviewIsRecordedOnceWithTheAttemptItEnded() {
        OfferEditor offer = offerPendingReview();
        offer.events.clear();

        offer.closeReview(REVIEW_ID);
        offer.closeReview(REVIEW_ID);

        Assertions.assertThat(offer.events).containsExactly(new OfferReviewClosed(OFFER_ID, REVIEW_ID));
    }

    @Test
    void closingAReviewNobodyOpenedRecordsNothing() {
        OfferEditor offer = draftOfferWithMedia();
        offer.events.clear();

        offer.closeReview(REVIEW_ID);

        assertThat(offer).hasState(State.DRAFT);
        Assertions.assertThat(offer.events).isEmpty();
    }

    @Test
    void anAcceptanceClearsTheReviewOnTheFreezeSoNoClosureIsNeeded() {
        OfferEditor offer = offerPendingReview();
        OfferCandidate accepted = offer.toCandidate();
        offer.events.clear();

        offer.publishCandidate(accepted, now(), REVIEWER, freeIdentifiers());

        Assertions.assertThat(offer.toSurface().reviewOpen()).isFalse();
        Assertions.assertThat(offer.events).containsExactly(OfferFixture.versionFrozen(1, accepted.description()));
    }

    @Test
    void theReviewOfARemovedOfferStillClosesAndLeavesItRemoved() {
        OfferEditor offer = publishedOffer();
        offer.requestReview();

        offer.remove();
        offer.closeReview(REVIEW_ID);

        assertThat(offer).hasState(State.REMOVED).hasVersion(1);
        Assertions.assertThat(offer.toSurface().reviewOpen()).isFalse();
    }

    @Test
    void closingAReviewARemovedOfferNeverHadRecordsNothing() {
        OfferEditor offer = removedOffer();
        offer.events.clear();

        offer.closeReview(REVIEW_ID);

        assertThat(offer).hasState(State.REMOVED).hasVersion(1);
        Assertions.assertThat(offer.events).isEmpty();
    }

    @Test
    void closingAReviewWithoutAnAttemptIsRefused() {
        OfferEditor offer = offerPendingReview();

        assertRefused(OfferException.Error.COMMAND_VALUE_MISSING, () -> offer.closeReview(null));
    }

    @Test
    void theOfferIsRevertableAgainOnceTheRejectedReviewClosed() {
        OfferEditor offer = publishedOffer();
        offer.requestReview();
        offer.closeReview(REVIEW_ID);

        offer.revert(1, now(), REVIEWER, freeIdentifiers());

        assertThat(offer).hasVersion(2);
        Assertions.assertThat(offer.version(2).copiedFrom()).isEqualTo(1);
    }
}
