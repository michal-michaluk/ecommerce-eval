package shop.shopping;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpHeaders;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.time.Clock;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;
import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

@Configuration
@EnableScheduling
@EnableRetry
@EnableJpaRepositories(considerNestedRepositories = true)
class AppConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Element 07's auth matrix: one rule per route shape, nothing public but the actuator, and a route nobody decided denied.
     *
     * <p>{@code SCOPE_customer} is the whole authority of the basket (D27) — a customer owns their own baskets and no route here
     * is anybody else's, so there is no second scope and no role. The service's own realm provisions the token that carries it
     * and a realm-{@code shopping} service token carries no {@code customer} scope at all, which is what DoD16's {@code 403}
     * stands on. {@link Scopes} is the only place the name is spelled, and this class the only place it is demanded.
     *
     * <p>CSRF is off and the session is stateless because every caller authenticates with a bearer token and no cookie is
     * issued.
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(auth -> customersOfTheBasket(auth).anyRequest().denyAll())
                .exceptionHandling(rejections -> rejections
                        .authenticationEntryPoint(unauthenticated())
                        .accessDeniedHandler(denied()))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    /** The six routes of element 07, each demanding the customer's scope on its own method and path. */
    private static AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry customersOfTheBasket(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        return auth
                .requestMatchers(pathPattern("/actuator/health/**")).permitAll()
                .requestMatchers(pathPattern("/actuator/info")).permitAll()
                .requestMatchers(pathPattern("/error")).permitAll()
                .requestMatchers(pathPattern("/actuator/**")).authenticated()
                .requestMatchers(pathPattern(GET, "/baskets")).hasAuthority(Scopes.CUSTOMER)
                .requestMatchers(pathPattern(GET, "/baskets/{basketId}")).hasAuthority(Scopes.CUSTOMER)
                .requestMatchers(pathPattern(PATCH, "/baskets/{basketId}/items")).hasAuthority(Scopes.CUSTOMER)
                .requestMatchers(pathPattern(PATCH, "/baskets/{basketId}/items/{offerId}")).hasAuthority(Scopes.CUSTOMER)
                .requestMatchers(pathPattern(PATCH, "/baskets/{basketId}/selection")).hasAuthority(Scopes.CUSTOMER)
                .requestMatchers(pathPattern(PATCH, "/baskets/{basketId}/checkout")).hasAuthority(Scopes.CUSTOMER);
    }

    /** A missing token is {@code 401} with the status alone: element 07's {@code 401} body is empty. */
    private static AuthenticationEntryPoint unauthenticated() {
        return (request, response, failure) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        };
    }

    /** A missing scope is {@code 403} with an empty body; no field or reason reaches a caller who may not ask. */
    private static AccessDeniedHandler denied() {
        return (request, response, failure) -> response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
}
