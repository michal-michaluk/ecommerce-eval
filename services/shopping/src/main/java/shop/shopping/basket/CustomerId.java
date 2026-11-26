package shop.shopping.basket;

import java.util.Objects;

/**
 * The customer a basket belongs to — always the identity the token carries, never a value the client sends (element 07
 * §Contract decisions).
 *
 * @param value the customer's identity as the identity provider states it
 */
record CustomerId(String value) {

    CustomerId {
        Objects.requireNonNull(value, "value");
    }
}
