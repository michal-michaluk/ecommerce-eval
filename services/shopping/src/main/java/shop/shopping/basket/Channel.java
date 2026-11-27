package shop.shopping.basket;

/**
 * How the customer arrives, as far as delivery is priced — the selector of the delivery rate (element 04 §Parameters and
 * value-object origins, {@code Channel}).
 *
 * <p>The capture names one channel and one consequence: {@code dostawa od 9,99 zł lub za 0,00 zł ze SMART}. Two outcomes are
 * all the rate needs, so two constants are all this enum holds. The claim that fills the value is the token's (D13 boundary);
 * reading it is not this slice's business.
 */
enum Channel {

    /** The default arrival of the capture: delivery is charged at the configured rate. */
    standard,

    /** SMART: delivery is free, {@code 0,00 zł}, while {@code basket.delivery.freeWhenSmart} holds. */
    smart
}
