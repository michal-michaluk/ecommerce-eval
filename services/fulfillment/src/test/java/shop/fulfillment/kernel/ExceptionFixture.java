package shop.fulfillment.kernel;

/**
 * The refusals the tests build: one factory so a case that has to carry a cause states what failed and why, without a test
 * spelling out the exception's constructor.
 */
public class ExceptionFixture {

    private ExceptionFixture() {
    }

    public static FulfillmentException refusedWithCause(FulfillmentException.Error error, Throwable cause,
                                                        Object... details) {
        return new FulfillmentException(error, cause, details);
    }
}
