package shop.catalog;

/**
 * 12's one {@code @RestControllerAdvice} as an object a slice test outside this package can register: {@link ApiErrorAdvice} is
 * package-private, and a controller in a bounded context answers the same statuses whoever builds the {@code MockMvc}.
 */
public final class ApiAdviceFixture {

    private ApiAdviceFixture() {
    }

    public static Object apiErrorAdvice() {
        return new ApiErrorAdvice();
    }
}
