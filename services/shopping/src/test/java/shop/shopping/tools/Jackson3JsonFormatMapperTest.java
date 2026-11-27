package shop.shopping.tools;

import org.hibernate.type.descriptor.java.StringJavaType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The one adapter Hibernate is told to use for a JSON column (`hibernate-types.properties`): the format mapper between a
 * JSONB document and the record an attribute holds.
 *
 * <p>A {@code CharSequence} is not a {@code String} — Hibernate hands over whatever buffer the driver produced — so both
 * spellings are read, and a document it cannot parse is a failure of the mapping rather than a silent {@code null} attribute.
 */
class Jackson3JsonFormatMapperTest {

    private final Jackson3JsonFormatMapper mapper = new Jackson3JsonFormatMapper();
    private final StringJavaType strings = new StringJavaType();

    @Test
    void readsADocumentTheDriverPassedAsAString() {
        assertThat(mapper.fromString("\"a-json-value\"", strings, null)).isEqualTo("a-json-value");
    }

    @Test
    void readsADocumentTheDriverPassedAsAnotherBuffer() {
        assertThat(mapper.fromString(new StringBuilder("\"a-json-value\""), strings, null)).isEqualTo("a-json-value");
    }

    @Test
    void writesTheValueBackAsADocument() {
        assertThat(mapper.toString("a-json-value", strings, null)).isEqualTo("\"a-json-value\"");
    }

    @Test
    void refusesADocumentThatIsNotJson() {
        assertThatThrownBy(() -> mapper.fromString("not-json", strings, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to deserialize JSON");
    }
}
