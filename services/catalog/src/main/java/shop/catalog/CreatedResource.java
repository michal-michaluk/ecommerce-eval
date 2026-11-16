package shop.catalog;

/**
 * A `201` body that states where the resource it created now lives (D19a): the handler declares its status with
 * {@code @ResponseStatus(CREATED)} and {@link CreatedResponseAdvice} copies this path into the {@code Location} header, so no handler
 * builds a response by hand ({@code http.no-response-entity}).
 *
 * <p>The resource names its own path instead of the advice deriving one from the request: only the response record knows which
 * identity the create produced, and a guess would be a second route rule outside the handler.
 */
public interface CreatedResource {

    String location();
}
