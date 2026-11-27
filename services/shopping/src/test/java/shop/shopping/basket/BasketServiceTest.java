package shop.shopping.basket;

import org.junit.jupiter.api.Test;

import shop.shopping.kernel.ShoppingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static shop.shopping.basket.BasketFixture.BASKET_ID;
import static shop.shopping.basket.BasketFixture.CUSTOMER_ID;
import static shop.shopping.basket.BasketFixture.FIRST_OFFER;
import static shop.shopping.basket.BasketFixture.NOW;
import static shop.shopping.basket.BasketFixture.PLN;
import static shop.shopping.basket.BasketFixture.aBasketFor;
import static shop.shopping.basket.BasketFixture.aFirstItem;
import static shop.shopping.basket.BasketFixture.aServiceWith;
import static shop.shopping.basket.BasketFixture.anEmptyBasket;

/**
 * The primary port behind the basket commands: a customer always has one active basket (RULE-1, D42), a command stores what
 * the aggregate decided, and a handover answers the payload checkout receives (element 09).
 */
class BasketServiceTest {

    final FakeBasketRepository baskets = new FakeBasketRepository();
    final BasketService service = aServiceWith(baskets, new FakeCheckoutHandovers());

    @Test
    void createsTheEmptyBasketOfACustomerWhoHasNone() {
        Basket basket = service.basketOf(CUSTOMER_ID);

        assertThat(basket.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(basket.currency()).isEqualTo(PLN);
        assertThat(basket.state()).isEqualTo(BasketState.active);
        assertThat(basket.items()).isEmpty();
        assertThat(baskets.saved).containsExactly(basket.basketId().value());
    }

    @Test
    void answersTheBasketTheCustomerAlreadyHas() {
        BasketId created = service.basketOf(CUSTOMER_ID).basketId();

        BasketId again = service.basketOf(CUSTOMER_ID).basketId();

        assertThat(again).isEqualTo(created);
        assertThat(baskets.saved).hasSize(1);
    }

    @Test
    void answersTheBasketAnotherRequestCreatedFirst() {
        BasketId createdByAnotherRequest = BasketId.minted();
        baskets.loseTheCreateTo(aBasketFor(createdByAnotherRequest, CUSTOMER_ID));

        Basket basket = service.basketOf(CUSTOMER_ID);

        assertThat(basket.basketId()).isEqualTo(createdByAnotherRequest);
    }

    @Test
    void storesWhatACommandChangedAndPublishesTheFact() {
        BasketId basketId = service.basketOf(CUSTOMER_ID).basketId();

        Basket basket = service.addToBasket(basketId, aFirstItem());

        assertThat(basket.items()).singleElement()
                .satisfies(item -> assertThat(item.offerId()).isEqualTo(FIRST_OFFER));
        assertThat(baskets.emitted).singleElement().isInstanceOf(DomainEvent.AddedToBasket.class);
    }

    @Test
    void refusesToReadABasketThatDoesNotExist() {
        assertThatThrownBy(() -> service.basket(BASKET_ID))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.BASKET_NOT_FOUND));
    }

    @Test
    void refusesACommandOnABasketThatDoesNotExist() {
        assertThatThrownBy(() -> service.changeQuantity(BASKET_ID, FIRST_OFFER, 2))
                .isInstanceOfSatisfying(ShoppingException.class, refused -> assertThat(refused.error())
                        .isEqualTo(ShoppingException.Error.BASKET_NOT_FOUND));
    }

    @Test
    void readsTheBasketBackByItsIdentity() {
        baskets.save(anEmptyBasket());

        assertThat(service.basket(BASKET_ID).basketId()).isEqualTo(BASKET_ID);
    }
}
