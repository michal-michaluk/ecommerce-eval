package shop.shopping.basket;

import org.junit.jupiter.api.Test;

import shop.shopping.JsonAssert;

import static shop.shopping.basket.BasketFixture.aBasketWith;
import static shop.shopping.basket.BasketFixture.aFirstItem;
import static shop.shopping.basket.BasketFixture.aSecondItem;
import static shop.shopping.basket.BasketFixture.aSnapshotOf;
import static shop.shopping.basket.BasketFixture.aThirdItemOfAnotherSeller;
import static shop.shopping.basket.BasketFixture.anEmptyBasket;
import static shop.shopping.basket.BasketFixture.anOutOfStockItem;
import static shop.shopping.basket.BasketFixture.anUnavailableItem;

/**
 * The payload of element 07 as {@code prototypes/basket-api.prove.md} fixes it: the four amounts of element 04, the two shipment
 * rules, the estimate element 05 answers, and the rows an unavailable or a deselected item is not part of.
 *
 * <p>Steps 2 and 3 are asserted literally — every key, every amount — and the later ones by the fields they decide, because the
 * contract ignores {@code items[*].title} and {@code items[*].picture} and no golden prints them. The bodies are steps 2, 3, 12,
 * 13, 18 and 20 of the contract; the one difference is that the fixture prices every row of the capture's seller at
 * {@code 99,89 zł}, so a two-row shipment sums two of them.
 */
class BasketContentTest {

    /** Step 12 of the contract: a deselected row keeps its line total and leaves the shipment and the totals. */
    private static final String STEP_TWELVE = """
            {
              "items": [
                { "offerId": "OF-1001", "selected": true, "lineTotal": { "amount": 99890 } },
                { "offerId": "OF-1002", "selected": false, "lineTotal": { "amount": 99890 } }
              ],
              "shipments": [ { "seller": "UGREEN_POLSKA", "items": ["OF-1001"] } ],
              "totals": {
                "productValue": { "amount": 99890 },
                "delivery": { "amount": 9990 },
                "totalWithDelivery": { "amount": 109880 }
              },
              "deliveryEstimate": { "from": "2026-11-28", "to": "2026-11-30" }
            }
            """;

    /** Step 20 of the contract: an unavailable row is rendered, then left out of every sum (D18, D33). */
    private static final String STEP_TWENTY = """
            {
              "items": [
                { "offerId": "OF-1001", "selected": true,
                  "availability": { "status": "available", "stale": false } },
                { "offerId": "OF-1002", "selected": false,
                  "availability": { "status": "outOfStock", "stale": false } },
                { "offerId": "OF-2007", "seller": "ANKER_STORE", "selected": true,
                  "availability": { "status": "unavailable", "stale": false } }
              ],
              "shipments": [ { "seller": "UGREEN_POLSKA", "items": ["OF-1001"] } ],
              "totals": {
                "productValue": { "amount": 99890 },
                "delivery": { "amount": 9990 },
                "totalWithDelivery": { "amount": 109880 }
              },
              "deliveryEstimate": { "from": "2026-11-28", "to": "2026-11-30" }
            }
            """;

    /** Step 2 of the contract: an emptied basket answers zero on all four amounts and no estimate at all. */
    private static final String STEP_TWO = """

                {
                  "basketId": "B-7f3a",
                  "state": "active",
                  "currency": "PLN",
                  "items": [],
                  "shipments": [],
                  "totals": {
                    "productValue": { "amount": 0, "currency": "PLN", "kind": "charge" },
                    "discountValue": { "amount": 0, "currency": "PLN", "kind": "discount" },
                    "delivery": { "amount": 0, "currency": "PLN", "kind": "charge" },
                    "totalWithDelivery": { "amount": 0, "currency": "PLN", "kind": "charge" }
                  },
                  "deliveryEstimate": null
                }
                """;

    /** Step 3 of the contract: one selected row, its shipment, the four totals and the range element 05 fixes. */
    private static final String STEP_THREE = """

                {
                  "basketId": "B-7f3a",
                  "state": "active",
                  "currency": "PLN",
                  "items": [
                    {
                      "offerId": "OF-1001",
                      "seller": "UGREEN_POLSKA",
                      "title": "UGREEN Nexode Szybka Ładowarka Sieciowa USB 2x USB Typ C Zasilacz QC PD 65W",
                      "picture": "img-1001",
                      "quantity": 1,
                      "selected": true,
                      "availability": { "status": "available", "stale": false },
                      "currentPrice": { "amount": 99890, "currency": "PLN", "kind": "charge" },
                      "discount": { "amount": 0, "currency": "PLN", "kind": "discount" },
                      "lineTotal": { "amount": 99890, "currency": "PLN", "kind": "charge" }
                    }
                  ],
                  "shipments": [
                    {
                      "seller": "UGREEN_POLSKA",
                      "items": ["OF-1001"],
                      "productValue": { "amount": 99890, "currency": "PLN", "kind": "charge" },
                      "delivery": { "amount": 9990, "currency": "PLN", "kind": "charge" }
                    }
                  ],
                  "totals": {
                    "productValue": { "amount": 99890, "currency": "PLN", "kind": "charge" },
                    "discountValue": { "amount": 0, "currency": "PLN", "kind": "discount" },
                    "delivery": { "amount": 9990, "currency": "PLN", "kind": "charge" },
                    "totalWithDelivery": { "amount": 109880, "currency": "PLN", "kind": "charge" }
                  },
                  "deliveryEstimate": { "from": "2026-11-28", "to": "2026-11-30" }
                }
                """;

    @Test
    void rendersAnEmptyBasketAsStepTwoOfTheContractStatesIt() {
        JsonAssert.assertThat(contentOf(aSnapshotOf(anEmptyBasket()))).isExactlyLike(STEP_TWO);
    }

    @Test
    void rendersOneSelectedRowWithItsShipmentAndItsTotalsAsStepThreeDoes() {
        JsonAssert.assertThat(contentOf(aSnapshotOf(aBasketWith(aFirstItem())))).isExactlyLike(STEP_THREE);
    }

    @Test
    void keepsTheLineTotalOfADeselectedRowAndDropsItFromTheShipmentAsStepTwelveDoes() {
        JsonAssert.assertThat(contentOf(aSnapshotOf(
                aBasketWith(aFirstItem(), aSecondItem().withSelection(false))))).hasFieldsLike(STEP_TWELVE);
    }

    @Test
    void leavesAnUnavailableRowOutOfTheShipmentsAndOutOfTheTotalsAsStepTwentyDoes() {
        JsonAssert.assertThat(contentOf(aSnapshotOf(aBasketWith(aFirstItem(), anOutOfStockItem().withSelection(false),
                anUnavailableItem())))).hasFieldsLike(STEP_TWENTY);
    }

    @Test
    void answersNoEstimateWhenNothingIsSelected() {
        JsonAssert.assertThat(contentOf(aSnapshotOf(aBasketWith(aFirstItem().withSelection(false))))).hasFieldsLike("""
                { "items": [ { "offerId": "OF-1001", "selected": false } ], "shipments": [], "deliveryEstimate": null }
                """);
    }

    @Test
    void groupsTwoSellersIntoTwoShipmentsAndChargesEachOfThem() {
        JsonAssert.assertThat(contentOf(aSnapshotOf(
                aBasketWith(aFirstItem(), aSecondItem(), aThirdItemOfAnotherSeller())))).hasFieldsLike("""
                {
                  "shipments": [
                    { "seller": "UGREEN_POLSKA", "items": ["OF-1001", "OF-1002"],
                      "productValue": { "amount": 199780 }, "delivery": { "amount": 9990 } },
                    { "seller": "ANKER_STORE", "items": ["OF-2007"],
                      "productValue": { "amount": 99890 }, "delivery": { "amount": 9990 } }
                  ],
                  "totals": { "productValue": { "amount": 299670 }, "delivery": { "amount": 19980 },
                              "totalWithDelivery": { "amount": 319650 } }
                }
                """);
    }

    /** The payload of one snapshot, priced and dated by the two policies the service holds — no HTTP, no container. */
    private static BasketContent contentOf(Basket basket) {
        return BasketContent.of(basket,
                BasketPriceCalculation.defaultCalculation().calculate(basket, Channel.standard),
                ShipmentEstimates.answeredBy(BasketDeliveryEstimate.defaultEstimate())
                        .latestTo(basket.items(), Channel.standard, BasketFixture.NOW),
                DeliveryRates.defaultRates().rateFor(Channel.standard, BasketFixture.PLN));
    }
}
