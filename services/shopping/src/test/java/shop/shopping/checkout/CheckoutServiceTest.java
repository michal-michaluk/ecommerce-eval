package shop.shopping.checkout;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import shop.shopping.kernel.CheckoutHandover;
import shop.shopping.kernel.Handover;
import shop.shopping.kernel.ShoppingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.shopping.kernel.HandoverFixture.BASKET_ID;
import static shop.shopping.kernel.HandoverFixture.aHandover;
import static shop.shopping.kernel.HandoverFixture.aHandoverOf;
import static shop.shopping.kernel.HandoverFixture.aHandoverOfOtherContent;

/**
 * The receiving end of the handover: two keys, two questions. The content digest says whether this is the payload already
 * accepted (D41), the revision whether it is an older one that must not be applied (D35). What it must never do is answer the
 * old receipt for new content — the case a lost basket write produces — and it never invents anything beyond the receipt
 * (HAND-3).
 */
class CheckoutServiceTest {

    final CheckoutService checkout = new CheckoutService();

    @Test
    void acceptsTheHandoverAndAnswersWhatCheckoutIsCalled() {
        Handover handover = aHandover(3);

        CheckoutHandover accepted = checkout.accept(handover);

        assertThat(accepted.handoverId()).isEqualTo(handover.handoverId());
        assertThat(accepted.revision()).isEqualTo(3);
        assertThat(accepted.checkoutId()).startsWith("CO-");
    }

    @Test
    void answersTheSameCheckoutForAPayloadItAlreadyAccepted() {
        CheckoutHandover first = checkout.accept(aHandover(3));

        assertThat(checkout.accept(aHandover(3))).isEqualTo(first);
    }

    @Test
    void acceptsAnotherPayloadAtTheSameRevisionRatherThanAnsweringTheOldReceipt() {
        CheckoutHandover theEarlierContent = checkout.accept(aHandover(3));

        CheckoutHandover theChangedContent = checkout.accept(aHandoverOfOtherContent(3));

        assertThat(theChangedContent.revision()).isEqualTo(3);
        assertThat(theChangedContent.checkoutId()).isNotEqualTo(theEarlierContent.checkoutId());
    }

    @Test
    void refusesARevisionOlderThanTheOneItHolds() {
        checkout.accept(aHandover(3));

        assertThatThrownBy(() -> checkout.accept(aHandoverOfOtherContent(2)))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> {
                    assertThat(refused.error()).isEqualTo(ShoppingException.Error.HANDOVER_STALE);
                    assertThat(refused.details()).containsExactly(BASKET_ID, 2L, 3L);
                });
    }

    @Test
    void keepsTheRevisionItRefusedTheOlderHandoverAgainst() {
        CheckoutHandover newer = checkout.accept(aHandover(5));

        assertThatThrownBy(() -> checkout.accept(aHandover(4)))
                .isInstanceOf(ShoppingException.class);

        assertThat(checkout.accept(aHandover(5))).isEqualTo(newer);
    }

    @Test
    void holdsOneAcceptedHandoverPerBasketNotOneForTheWholeSlice() {
        checkout.accept(aHandoverOf(BASKET_ID, 7));

        CheckoutHandover otherBasket = checkout.accept(aHandoverOf("B-another", 1));

        assertThat(otherBasket.revision()).isEqualTo(1);
    }

    @Test
    void answersAReceiptAndNothingElse() {
        assertThat(Arrays.stream(CheckoutHandover.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("handoverId", "revision", "checkoutId");
    }
}
