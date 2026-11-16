package devices.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
        + "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration,"
        + "org.springframework.boot.security.oauth2.server.resource.autoconfigure."
        + "OAuth2ResourceServerAutoConfiguration",
        "spring.datasource.url=jdbc:postgresql://localhost:1/devices",
        "spring.datasource.hikari.initialization-fail-timeout=-1",
        "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false"})
class OpenApiDocumentTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path DOCUMENT = Path.of("build", "openapi", "openapi.json");

    @Autowired
    WebApplicationContext context;

    @Test
    void writesOpenApiDocument() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        var response = mockMvc.perform(get("/v3/api-docs").with(jwt())).andReturn().getResponse();

        assertThat(response.getStatus())
                .as("status of GET /v3/api-docs")
                .isEqualTo(200);

        JsonNode document = JSON.readTree(response.getContentAsByteArray());
        assertThat(document.path("paths").isObject())
                .as("generated document has a paths object")
                .isTrue();

        Files.createDirectories(DOCUMENT.getParent());
        Files.writeString(DOCUMENT, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(document));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubbedAuthentication {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> new Jwt(token, Instant.now(), Instant.now().plusSeconds(60),
                    Map.of("alg", "none"), Map.of("sub", "openapi-document"));
        }
    }
}
