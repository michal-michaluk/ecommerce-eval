package shop.catalog.tools;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

/**
 * The mapper every stored and published payload is written and read with — Hibernate's {@code FormatMapper} through
 * {@link Jackson3JsonFormatMapper}, and the repositories that rebuild it for their own documents. It ignores unknown members on
 * purpose: a payload written before a field was removed stays readable (12 D49, 13 R1).
 *
 * <p>Request bodies are not read with it. The servlet stack uses the {@code JsonMapper} Spring Boot auto-configures, and
 * {@code spring.jackson.deserialization.fail-on-unknown-properties} makes that one strict, so an unknown component is 12's `400`
 * (D18) without a document ever becoming unreadable.
 */
@Configuration
public class JsonConfiguration {

    public static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .changeDefaultVisibility(v -> v
                    .withVisibility(PropertyAccessor.CREATOR, ANY)
                    .withVisibility(PropertyAccessor.FIELD, ANY))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS, false)
            .configure(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN, true)
            .build();

    @Bean
    ObjectMapper objectMapper() {
        return OBJECT_MAPPER;
    }
}
