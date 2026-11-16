package shop.catalog.review;

import org.assertj.core.api.Assertions;

/** The assertions of 09: which outcome the decision took, and for a rejection the reason it carries. */
public class PublishDecisionAssert {

    private final PublishDecision decision;

    private PublishDecisionAssert(PublishDecision decision) {
        this.decision = decision;
    }

    public static PublishDecisionAssert assertThat(PublishDecision actual) {
        Assertions.assertThat(actual).isNotNull();
        return new PublishDecisionAssert(actual);
    }

    public PublishDecisionAssert publishes() {
        Assertions.assertThat(decision.outcome()).isEqualTo(PublishDecision.Outcome.PUBLISH);
        Assertions.assertThat(decision.isPublishable()).isTrue();
        Assertions.assertThat(decision.reason()).isNull();
        return this;
    }

    public PublishDecisionAssert doesNothing() {
        Assertions.assertThat(decision.outcome()).isEqualTo(PublishDecision.Outcome.NO_OP);
        Assertions.assertThat(decision.isPublishable()).isFalse();
        Assertions.assertThat(decision.reason()).isNull();
        return this;
    }

    public PublishDecisionAssert isBlockedBy(PublishDecision.BlockReason expected) {
        Assertions.assertThat(decision.outcome()).isEqualTo(PublishDecision.Outcome.BLOCK);
        Assertions.assertThat(decision.isPublishable()).isFalse();
        Assertions.assertThat(decision.reason()).isEqualTo(expected);
        return this;
    }
}
