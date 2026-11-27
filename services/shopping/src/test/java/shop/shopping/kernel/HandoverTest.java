package shop.shopping.kernel;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.shopping.kernel.HandoverFixture.BASKET_ID;
import static shop.shopping.kernel.HandoverFixture.CUSTOMER_ID;
import static shop.shopping.kernel.HandoverFixture.FROM;
import static shop.shopping.kernel.HandoverFixture.TO;
import static shop.shopping.kernel.HandoverFixture.aDiscountedLine;
import static shop.shopping.kernel.HandoverFixture.aHandover;
import static shop.shopping.kernel.HandoverFixture.aHandoverAnsweredWith;
import static shop.shopping.kernel.HandoverFixture.aLine;
import static shop.shopping.kernel.HandoverFixture.aLineOf;
import static shop.shopping.kernel.HandoverFixture.aSecondLine;
import static shop.shopping.kernel.HandoverFixture.anEstimatedRange;
import static shop.shopping.kernel.HandoverFixture.theTotalsOfOneItem;
import static shop.shopping.kernel.HandoverFixture.totalsOf;

/**
 * D41/D51/D64: the content digest is the identity of what a client sees — the rows and the totals — so a moved row, discount,
 * customer or delivery rate answers another digest, while the values derived from the identity itself (revision, instant) and
 * the clock-derived delivery range stay out of it, or a repeat could never be recognised.
 */
class HandoverTest {

    @Test
    void answersTheSameDigestForTheSamePayload() {
        assertThat(contentDigestOf(List.of(aLine()), theTotalsOfOneItem()))
                .isEqualTo(contentDigestOf(List.of(aLine()), theTotalsOfOneItem()));
    }

    @Test
    void answersAnotherDigestWhenTheTotalsMoved() {
        assertThat(contentDigestOf(List.of(aLine()), totalsOf(HandoverFixture.PRODUCT_VALUE, 0, 12_340)))
                .isNotEqualTo(contentDigestOf(List.of(aLine()), theTotalsOfOneItem()));
    }

    @Test
    void answersTheSameDigestWhenOnlyTheDeliveryRangeMoved() {
        LocalDate theNextDay = FROM.plusDays(1);
        Handover beforeMidnight = aHandover(3);
        Handover afterMidnight = aHandoverAnsweredWith(
                Estimate.estimatedBetween(theNextDay, TO.plusDays(1), Estimate.Source.configured), 3);

        assertThat(afterMidnight.deliveryEstimate()).isNotEqualTo(beforeMidnight.deliveryEstimate());
        assertThat(afterMidnight.contentDigest()).isEqualTo(beforeMidnight.contentDigest());
    }

    @Test
    void answersAnotherDigestWhenARowMoved() {
        assertThat(contentDigestOf(List.of(aLineOf(HandoverFixture.OFFER_ID, 2)), theTotalsOfOneItem()))
                .isNotEqualTo(contentDigestOf(List.of(aLine()), theTotalsOfOneItem()));
    }

    @Test
    void answersAnotherDigestWhenTheDiscountMoved() {
        assertThat(contentDigestOf(List.of(aDiscountedLine()), theTotalsOfOneItem()))
                .isNotEqualTo(contentDigestOf(List.of(aLine()), theTotalsOfOneItem()));
    }

    @Test
    void answersAnotherDigestForAnotherCustomer() {
        assertThat(Handover.contentDigestOf(BASKET_ID, "C-another", List.of(aLine()), theTotalsOfOneItem()))
                .isNotEqualTo(contentDigestOf(List.of(aLine()), theTotalsOfOneItem()));
    }

    @Test
    void answersTheSameDigestWhateverOrderTheRowsArriveIn() {
        List<Handover.Line> asTheBasketHoldsThem = List.of(aLine(), aSecondLine());

        assertThat(contentDigestOf(List.of(aSecondLine(), aLine()), theTotalsOfOneItem()))
                .isEqualTo(contentDigestOf(asTheBasketHoldsThem, theTotalsOfOneItem()));
    }

    @Test
    void refusesADigestThatIsNotTheDigestOfItsOwnContent() {
        assertThatThrownBy(() -> HandoverFixture.aHandoverWith(BASKET_ID, 3, "sha256:not-the-content"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String contentDigestOf(List<Handover.Line> selection, Handover.Totals totals) {
        return Handover.contentDigestOf(BASKET_ID, CUSTOMER_ID, selection, totals);
    }
}
