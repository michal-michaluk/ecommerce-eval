package shop.catalog.offer;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import shop.catalog.offer.Offer.State;

import java.time.LocalDate;

public class OfferAssert {

    private final Offer offer;

    private OfferAssert(Offer offer) {
        this.offer = offer;
    }

    public static OfferAssert assertThat(OfferEditor actual) {
        Assertions.assertThat(actual).isNotNull();
        return new OfferAssert(actual.toOffer());
    }

    public static OfferAssert assertThat(Offer actual) {
        Assertions.assertThat(actual).isNotNull();
        return new OfferAssert(actual);
    }

    public static void assertRefused(OfferException.Error expected, ThrowingCallable call) {
        Assertions.assertThatThrownBy(call)
                .isInstanceOfSatisfying(OfferException.class,
                        refused -> Assertions.assertThat(refused.error()).isEqualTo(expected));
    }

    public OfferAssert hasOfferId(String expected) {
        Assertions.assertThat(offer.offerId()).isEqualTo(expected);
        return this;
    }

    public OfferAssert hasState(State expected) {
        Assertions.assertThat(offer.state()).isEqualTo(expected);
        return this;
    }

    public OfferAssert hasVersion(int expected) {
        Assertions.assertThat(offer.version()).isEqualTo(expected);
        return this;
    }

    public OfferAssert hasAvailabilityStart(LocalDate expected) {
        Assertions.assertThat(offer.availabilityStart()).isEqualTo(expected);
        return this;
    }

    public OfferAssert hasNoAvailabilityStart() {
        Assertions.assertThat(offer.availabilityStart()).isNull();
        return this;
    }
}
