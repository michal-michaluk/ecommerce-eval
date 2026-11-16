package shop.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * 12's auth matrix against the real {@link AppConfiguration#filterChain}: every route shape accepts exactly the scopes the table lists
 * and answers `403` — never `404` — for every other one, an anonymous request is `401`, a route nobody decided is denied, and the two
 * decisions a route rule cannot carry are enforced where 12 puts them: D13a per draft component and the review decision per
 * withdrawal.
 *
 * <p>The handlers are route markers, not controllers: this node adds no endpoint, so the chain is proven on the shape alone.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ScopeGuardTest.Web.class)
class ScopeGuardTest {

    static final String OFFER = "/offers/OFR-1A2B3C4D";

    static final String WITHDRAWN = "{\"decision\":\"withdrawn\"}";

    static final List<String> ALL_SCOPES = List.of(Scopes.AUTHOR, Scopes.REVIEWER, Scopes.PHOTOGRAPHER, Scopes.SALES);

    static final List<String> DECLARED_DRAFT_COMPONENTS =
            List.of(Scopes.DESCRIPTION, Scopes.MEDIA, Scopes.AVAILABILITY_START);

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    static Stream<Arguments> matrix() {
        return Stream.of(
                        routeWith(HttpMethod.POST, "/offers", "", Scopes.AUTHOR),
                        routeWith(HttpMethod.GET, "/offers", "", Scopes.AUTHOR, Scopes.REVIEWER),
                        routeWith(HttpMethod.GET, OFFER, "", Scopes.AUTHOR, Scopes.REVIEWER),
                        routeWith(HttpMethod.PATCH, OFFER, "", Scopes.AUTHOR, Scopes.REVIEWER),
                        routeWith(HttpMethod.GET, OFFER + "/versions", "", Scopes.AUTHOR, Scopes.REVIEWER),
                        routeWith(HttpMethod.POST, OFFER + "/versions", "", Scopes.REVIEWER),
                        routeWith(HttpMethod.GET, OFFER + "/draft", "", Scopes.AUTHOR, Scopes.REVIEWER, Scopes.PHOTOGRAPHER),
                        routeWith(HttpMethod.PATCH, OFFER + "/draft", "", Scopes.AUTHOR, Scopes.PHOTOGRAPHER),
                        routeWith(HttpMethod.GET, OFFER + "/pricing", "", Scopes.SALES),
                        routeWith(HttpMethod.PATCH, OFFER + "/pricing", "", Scopes.SALES),
                        routeWith(HttpMethod.POST, OFFER + "/review", "", Scopes.AUTHOR),
                        routeWith(HttpMethod.GET, OFFER + "/review", "", Scopes.AUTHOR, Scopes.REVIEWER),
                        routeWith(HttpMethod.PATCH, OFFER + "/review", WITHDRAWN, Scopes.AUTHOR, Scopes.REVIEWER),
                        routeWith(HttpMethod.GET, "/reviews", "", Scopes.REVIEWER))
                .flatMap(identity());
    }

    static Stream<Arguments> routeWith(HttpMethod method, String path, String body, String... scopes) {
        Set<String> allowed = Set.of(scopes);
        Stream<Arguments> listed = allowed.stream().map(scope -> Arguments.of(method, path, body, scope, 200));
        Stream<Arguments> refused = ALL_SCOPES.stream()
                .filter(scope -> !allowed.contains(scope))
                .map(scope -> Arguments.of(method, path, body, scope, 403));

        return Stream.concat(listed, refused);
    }

    static Stream<Arguments> draftComponents() {
        return Stream.of(
                Arguments.of(Scopes.AUTHOR, Scopes.DESCRIPTION, 200),
                Arguments.of(Scopes.AUTHOR, Scopes.AVAILABILITY_START, 200),
                Arguments.of(Scopes.AUTHOR, Scopes.MEDIA, 403),
                Arguments.of(Scopes.PHOTOGRAPHER, Scopes.MEDIA, 200),
                Arguments.of(Scopes.PHOTOGRAPHER, Scopes.DESCRIPTION, 403),
                Arguments.of(Scopes.PHOTOGRAPHER, Scopes.AVAILABILITY_START, 403));
    }

    static Stream<Arguments> reviewDecisions() {
        return Stream.of(
                Arguments.of(Scopes.AUTHOR, Scopes.ACCEPTED, 403),
                Arguments.of(Scopes.AUTHOR, Scopes.REJECTED, 403),
                Arguments.of(Scopes.AUTHOR, Scopes.WITHDRAWN, 200),
                Arguments.of(Scopes.REVIEWER, Scopes.ACCEPTED, 200),
                Arguments.of(Scopes.REVIEWER, Scopes.REJECTED, 200));
    }

    @ParameterizedTest
    @MethodSource("matrix")
    void enforcesTheScopeOfEachRouteShape(HttpMethod method, String path, String body, String scope, int status) throws Exception {
        assertThat(as(scope, request(method, path).contentType(MediaType.APPLICATION_JSON).content(body)).getStatus())
                .isEqualTo(status);
    }

    @ParameterizedTest
    @MethodSource("draftComponents")
    void enforcesTheDraftComponentsScopePerWriter(String scope, String component, int status) throws Exception {
        assertThat(as(scope, writesDraftComponent(component)).getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @MethodSource("reviewDecisions")
    void allowsOnlyTheReviewerToDecide(String scope, String decision, int status) throws Exception {
        assertThat(as(scope, decides(decision)).getStatus()).isEqualTo(status);
    }

    @Test
    void admitsACallerHoldingBothDraftScopes() throws Exception {
        var request = writesDraftComponent(Scopes.MEDIA)
                .with(jwt().authorities(new SimpleGrantedAuthority(Scopes.AUTHOR),
                        new SimpleGrantedAuthority(Scopes.PHOTOGRAPHER)));

        assertThat(mvc.perform(request).andReturn().getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void answersAnAnonymousRequestWith401AndNoBody() throws Exception {
        var response = mvc.perform(request(HttpMethod.GET, "/offers")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void answersAMissingScopeWith403AndNoBody() throws Exception {
        var response = as(Scopes.REVIEWER, request(HttpMethod.POST, "/offers"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void deniesARouteNobodyDecided() throws Exception {
        assertThat(as(Scopes.AUTHOR, request(HttpMethod.GET, OFFER + "/undecided")).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void namesTheDraftComponentRefusalWith403AndItsReason() throws Exception {
        var response = as(Scopes.AUTHOR, writesDraftComponent(Scopes.MEDIA));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString()).isEqualTo("{\"reason\":\"FIELD_NOT_IN_SCOPE\"}");
    }

    @Test
    void namesTheReviewDecisionRefusalWith403AndItsReason() throws Exception {
        var response = as(Scopes.AUTHOR, decides(Scopes.ACCEPTED));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString()).isEqualTo("{\"reason\":\"FIELD_NOT_IN_SCOPE\"}");
    }

    /**
     * A component no writer owns is refused by the decision itself; on the wire it cannot arrive, because the draft command declares
     * its three components and an unknown JSON member is D18's `400` — which the application's mapper does not raise yet.
     */
    @Test
    void refusesAComponentNobodyOwns() {
        assertThatThrownBy(() -> Scopes.requireDraftWrite(Set.of(Scopes.AUTHOR), Set.of(Scopes.SALES)))
                .isInstanceOf(Scopes.OutOfScopeException.class);
    }

    @Test
    void namesTheFourScopesAndTheRefusalOf12() {
        assertThat(ALL_SCOPES).containsExactly(
                "SCOPE_catalog-author", "SCOPE_catalog-reviewer", "SCOPE_catalog-photographer", "SCOPE_catalog-sales");
        assertThat(Scopes.FIELD_NOT_IN_SCOPE).isEqualTo("FIELD_NOT_IN_SCOPE");
    }

    MockHttpServletResponse as(String scope, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(jwt().authorities(new SimpleGrantedAuthority(scope)))).andReturn().getResponse();
    }

    static MockHttpServletRequestBuilder writesDraftComponent(String component) {
        return request(HttpMethod.PATCH, OFFER + "/draft")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"" + component + "\": {}}");
    }

    static MockHttpServletRequestBuilder decides(String decision) {
        return request(HttpMethod.PATCH, OFFER + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"" + decision + "\"}");
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class Web {

        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return new AppConfiguration().filterChain(http);
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new JwtException("this test authenticates with a mock JWT");
            };
        }

        @Bean
        ApiErrorAdvice apiErrorAdvice() {
            return new ApiErrorAdvice();
        }

        @Bean
        Routes routes() {
            return new Routes();
        }
    }

    @RestController
    static class Routes {

        @GetMapping(path = "/offers", produces = MediaType.APPLICATION_JSON_VALUE)
        Object offers() {
            return Map.of();
        }

        @PostMapping(path = "/offers", produces = MediaType.APPLICATION_JSON_VALUE)
        Object create() {
            return Map.of();
        }

        @GetMapping(path = "/offers/{offerId}", produces = MediaType.APPLICATION_JSON_VALUE)
        Object offer(@PathVariable String offerId) {
            return Map.of();
        }

        @PatchMapping(path = "/offers/{offerId}", produces = MediaType.APPLICATION_JSON_VALUE)
        Object remove(@PathVariable String offerId) {
            return Map.of();
        }

        @GetMapping(path = "/offers/{offerId}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
        Object versions(@PathVariable String offerId) {
            return Map.of();
        }

        @PostMapping(path = "/offers/{offerId}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
        Object revert(@PathVariable String offerId) {
            return Map.of();
        }

        @GetMapping(path = "/offers/{offerId}/draft", produces = MediaType.APPLICATION_JSON_VALUE)
        Object draft(@PathVariable String offerId) {
            return Map.of();
        }

        @PatchMapping(path = "/offers/{offerId}/draft",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        Object updateDraft(@PathVariable String offerId,
                           @RequestBody(required = false) Map<String, Object> sent) {
            Map<String, Object> body = Objects.requireNonNullElse(sent, Map.of());
            Scopes.requireDraftWrite(DECLARED_DRAFT_COMPONENTS.stream().filter(body::containsKey).toList());
            return Map.of();
        }

        @GetMapping(path = "/offers/{offerId}/pricing", produces = MediaType.APPLICATION_JSON_VALUE)
        Object pricing(@PathVariable String offerId) {
            return Map.of();
        }

        @PatchMapping(path = "/offers/{offerId}/pricing", produces = MediaType.APPLICATION_JSON_VALUE)
        Object updatePricing(@PathVariable String offerId) {
            return Map.of();
        }

        @PostMapping(path = "/offers/{offerId}/review", produces = MediaType.APPLICATION_JSON_VALUE)
        Object requestReview(@PathVariable String offerId) {
            return Map.of();
        }

        @GetMapping(path = "/offers/{offerId}/review", produces = MediaType.APPLICATION_JSON_VALUE)
        Object review(@PathVariable String offerId) {
            return Map.of();
        }

        @PatchMapping(path = "/offers/{offerId}/review",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        Object decideReview(@PathVariable String offerId,
                            @RequestBody(required = false) Map<String, Object> decision) {
            Scopes.requireReviewDecision(String.valueOf(
                    Objects.requireNonNullElse(decision, Map.of()).get("decision")));
            return Map.of();
        }

        @GetMapping(path = "/reviews", produces = MediaType.APPLICATION_JSON_VALUE)
        Object pendingReviews() {
            return Map.of();
        }
    }
}
