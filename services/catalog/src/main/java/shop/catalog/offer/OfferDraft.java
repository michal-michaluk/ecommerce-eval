package shop.catalog.offer;

import java.util.Objects;

/**
 * The offer as another context reads it while a review is being prepared: the lifecycle state and the content a version would freeze
 * (02 Instance A, 04 RULE-3, D38).
 *
 * <p>Both halves are needed at different moments and they travel together because they come from one load: the state is what 09 asks
 * about when it decides, and the candidate is what the reviewer reviews. The description is already a value — an incomplete draft is
 * refused when this is built, with this context's own failure, because a candidate never carries a draft (01 RULE-4).
 */
public record OfferDraft(Offer.State state, OfferCandidate candidate) {

    public OfferDraft {
        state = Objects.requireNonNull(state, "state");
        candidate = Objects.requireNonNull(candidate, "candidate");
    }
}
