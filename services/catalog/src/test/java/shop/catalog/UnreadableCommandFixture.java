package shop.catalog;

import org.springframework.http.converter.HttpMessageNotReadableException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A real unreadable-command failure for {@code shop.catalog.ErrorMappingTest}. The mapper the application wires ignores unknown
 * components (Jackson 3's default, re-stated in {@code tools.JsonConfiguration}), so a strictly configured read is the only way to
 * obtain the refusal 12 wants for a typo in a `PATCH` body (D18).
 */
public final class UnreadableCommandFixture {

    private UnreadableCommandFixture() {
    }

    public static HttpMessageNotReadableException unknownComponent() {
        var strict = JsonMapper.builder().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true).build();
        var unknown = assertThrows(UnrecognizedPropertyException.class,
                () -> strict.readValue("{\"offerState\": \"removed\"}", Removal.class));

        return new HttpMessageNotReadableException("unknown PATCH component", unknown, null);
    }

    public record Removal(String state) {
    }
}
