package shop.catalog;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * D19a's other half: a handler declares `201` with {@code @ResponseStatus(CREATED)} and returns a {@link CreatedResource}, and this
 * advice copies the resource's own path into the {@code Location} header while MVC writes the body — so no handler builds a status, a
 * header or a response ({@code http.no-response-entity}).
 *
 * <p>It hooks the response body rather than implementing {@code ResponseBodyAdvice}, the obvious Spring seam: that interface forces a
 * six-parameter method, which the project's parameter limit forbids, and a return-value handler is registered after MVC's own body
 * writer, so it never sees a {@code @RestController} answer. A converter's {@code write} takes three.
 */
@Configuration
class CreatedResponseAdvice implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.add(0, new CreatedResourceConverter(converters));
    }

    static final class CreatedResourceConverter implements HttpMessageConverter<CreatedResource> {

        private final List<HttpMessageConverter<?>> converters;

        CreatedResourceConverter(List<HttpMessageConverter<?>> converters) {
            this.converters = converters;
        }

        @Override
        public boolean canRead(Class<?> clazz, MediaType mediaType) {
            return false;
        }

        @Override
        public boolean canWrite(Class<?> clazz, MediaType mediaType) {
            return CreatedResource.class.isAssignableFrom(clazz);
        }

        @Override
        public List<MediaType> getSupportedMediaTypes() {
            return List.of(MediaType.APPLICATION_JSON);
        }

        @Override
        public CreatedResource read(Class<? extends CreatedResource> clazz, HttpInputMessage inputMessage) {
            throw new HttpMessageNotReadableException("a created resource is never read back", inputMessage);
        }

        @Override
        public void write(CreatedResource created, MediaType contentType, HttpOutputMessage outputMessage)
                throws IOException, HttpMessageNotWritableException {
            outputMessage.getHeaders().setLocation(URI.create(created.location()));
            bodyWriter(created, contentType).write(created, contentType, outputMessage);
        }

        @SuppressWarnings("unchecked")
        private HttpMessageConverter<Object> bodyWriter(CreatedResource created, MediaType contentType) {
            return (HttpMessageConverter<Object>) converters.stream()
                    .filter(converter -> converter != this && converter.canWrite(created.getClass(), contentType))
                    .findFirst()
                    .orElseThrow(() -> new HttpMessageNotWritableException(
                            "no converter writes " + created.getClass().getName()));
        }
    }
}
