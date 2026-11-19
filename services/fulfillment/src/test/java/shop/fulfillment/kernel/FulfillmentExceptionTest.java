package shop.fulfillment.kernel;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The one failure type of the context: it carries the error constant, the values the message was built from and — when it
 * wraps one — the cause, so no caller matches on a message.
 */
class FulfillmentExceptionTest {

    @Test
    void carriesTheErrorConstantAndTheValuesItNames() {
        FulfillmentException refused = new FulfillmentException(
                FulfillmentException.Error.QUANTITY_NOT_POSITIVE, 0);

        assertThat(refused.error()).isEqualTo(FulfillmentException.Error.QUANTITY_NOT_POSITIVE);
        assertThat(refused.details()).containsExactly(0);
        assertThat(refused.getMessage()).isEqualTo("quantity must be at least 1, got 0");
    }

    @Test
    void carriesTheCauseOfAnIntegrationFailure() {
        IllegalStateException cause = new IllegalStateException("warehouse down");

        FulfillmentException refused = ExceptionFixture.refusedWithCause(
                FulfillmentException.Error.STOCK_OBSERVATION_MISSING, cause, "75423");

        assertThat(refused.getCause()).isSameAs(cause);
        assertThat(refused.error()).isEqualTo(FulfillmentException.Error.STOCK_OBSERVATION_MISSING);
    }

    @Test
    void holdsItsDetailsUnmodifiable() {
        FulfillmentException refused = new FulfillmentException(
                FulfillmentException.Error.ORDER_INCOMPLETE, List.of("lines"));

        assertThatThrownBy(() -> refused.details().add("delivery"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void namesTheProcessingAnUnknownLookupAddressed() {
        FulfillmentException refused = new FulfillmentException(
                FulfillmentException.Error.PROCESSING_NOT_FOUND, "OP-DOES-NOT-EXIST");

        assertThat(refused.error().name()).isEqualTo("PROCESSING_NOT_FOUND");
        assertThat(refused.getMessage()).contains("OP-DOES-NOT-EXIST");
    }
}
