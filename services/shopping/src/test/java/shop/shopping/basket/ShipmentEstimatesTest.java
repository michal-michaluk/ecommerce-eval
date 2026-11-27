package shop.shopping.basket;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import shop.shopping.kernel.Estimate;

import static org.assertj.core.api.Assertions.assertThat;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.FROM;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.NOW;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.TO;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.TODAY;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.anEstimatedRange;
import static shop.shopping.basket.BasketDeliveryEstimateFixture.anUnknownEstimate;
import static shop.shopping.basket.BasketFixture.ANKER;
import static shop.shopping.basket.BasketFixture.aFirstItem;
import static shop.shopping.basket.BasketFixture.aThirdItemOfAnotherSeller;

/**
 * Element 05 §Granularity through the aggregation that owns it: one {@link Estimate} per seller shipment, the basket publishing
 * the latest {@code to} of the shipments that answered a range, and an {@code unknown} shipment contributing no date (Q22).
 *
 * <p>Today the policy answers the same days for every shipment, because the range counts from the clock and the zone only, so
 * these tests give the aggregation two shipments that answer <em>differently</em> — the only way to tell "the latest
 * {@code to}" from "the first answer" before the deferred algorithm (issue #71) can make real ranges differ.
 */
class ShipmentEstimatesTest {

    @Test
    void publishesTheLatestToAcrossTheShipments() {
        LocalDate aLaterDay = TODAY.plusDays(10);
        ShipmentEstimates twoShipments = new ShipmentEstimates(shipment -> isOfTheSecondSeller(shipment)
                ? anEstimatedRange(TODAY.plusDays(1), aLaterDay)
                : anEstimatedRange(FROM, TO));

        Estimate published = twoShipments.latestTo(List.of(aFirstItem(), aThirdItemOfAnotherSeller()),
                Channel.standard, NOW);

        assertThat(published).isEqualTo(anEstimatedRange(TODAY.plusDays(1), aLaterDay));
    }

    @Test
    void ignoresAShipmentThatAnswersNoRange() {
        ShipmentEstimates oneAnswerAndOneNonAnswer = new ShipmentEstimates(shipment -> isOfTheSecondSeller(shipment)
                ? anUnknownEstimate()
                : anEstimatedRange(FROM, TO));

        Estimate published = oneAnswerAndOneNonAnswer.latestTo(List.of(aFirstItem(), aThirdItemOfAnotherSeller()),
                Channel.standard, NOW);

        assertThat(published).isEqualTo(anEstimatedRange(FROM, TO));
    }

    @Test
    void answersUnknownWhenNoShipmentHoldsARange() {
        ShipmentEstimates noAnswers = new ShipmentEstimates(shipment -> anUnknownEstimate());

        Estimate published = noAnswers.latestTo(List.of(aFirstItem(), aThirdItemOfAnotherSeller()),
                Channel.standard, NOW);

        assertThat(published).isEqualTo(anUnknownEstimate());
    }

    private static boolean isOfTheSecondSeller(BasketDeliveryEstimate.Request shipment) {
        return shipment.items().stream().anyMatch(item -> item.seller().equals(ANKER));
    }
}
