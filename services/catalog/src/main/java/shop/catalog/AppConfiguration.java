package shop.catalog;

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
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;
import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;
import static shop.catalog.Scopes.AUTHOR;
import static shop.catalog.Scopes.PHOTOGRAPHER;
import static shop.catalog.Scopes.REVIEWER;
import static shop.catalog.Scopes.SALES;

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
     * 12's auth matrix, one rule per route shape of 12, and nothing public but the actuator. A route nobody decided is denied, so a
     * later endpoint cannot become "any authenticated caller" by omission ({@code security.md}, "deny everything else by default").
     *
     * <p>CSRF is off and the session is stateless because every caller authenticates with a bearer token and no cookie is issued: the
     * default CSRF matcher would only be able to reject requests it cannot tell apart from a browser's.
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(auth -> scopesOf12(auth).anyRequest().denyAll())
                .exceptionHandling(rejections -> rejections
                        .authenticationEntryPoint(unauthenticated())
                        .accessDeniedHandler(denied()))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    private static AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry scopesOf12(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        return auth
                .requestMatchers(pathPattern("/actuator/health/**")).permitAll()
                .requestMatchers(pathPattern("/actuator/info")).permitAll()
                .requestMatchers(pathPattern("/error")).permitAll()
                .requestMatchers(pathPattern("/actuator/**")).authenticated()
                .requestMatchers(pathPattern(POST, "/offers")).hasAuthority(AUTHOR)
                .requestMatchers(pathPattern(GET, "/offers"), pathPattern(GET, "/offers/{offerId}"))
                .hasAnyAuthority(AUTHOR, REVIEWER)
                .requestMatchers(pathPattern(PATCH, "/offers/{offerId}")).hasAnyAuthority(AUTHOR, REVIEWER)
                .requestMatchers(pathPattern(GET, "/offers/{offerId}/versions")).hasAnyAuthority(AUTHOR, REVIEWER)
                .requestMatchers(pathPattern(POST, "/offers/{offerId}/versions")).hasAuthority(REVIEWER)
                .requestMatchers(pathPattern(GET, "/offers/{offerId}/draft"))
                .hasAnyAuthority(AUTHOR, REVIEWER, PHOTOGRAPHER)
                .requestMatchers(pathPattern(PATCH, "/offers/{offerId}/draft")).hasAnyAuthority(AUTHOR, PHOTOGRAPHER)
                .requestMatchers(pathPattern("/offers/{offerId}/pricing")).hasAuthority(SALES)
                .requestMatchers(pathPattern(POST, "/offers/{offerId}/review")).hasAuthority(AUTHOR)
                .requestMatchers(pathPattern(GET, "/offers/{offerId}/review")).hasAnyAuthority(AUTHOR, REVIEWER)
                .requestMatchers(pathPattern(PATCH, "/offers/{offerId}/review")).hasAnyAuthority(AUTHOR, REVIEWER)
                .requestMatchers(pathPattern(GET, "/reviews")).hasAuthority(REVIEWER);
    }

    /** A missing token is `401` with the status alone: 12's `401` body is empty and no message reaches the client. */
    private static AuthenticationEntryPoint unauthenticated() {
        return (request, response, failure) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        };
    }

    /** A missing scope is `403` with an empty body; only the draft component and the review decision carry a `reason`. */
    private static AccessDeniedHandler denied() {
        return (request, response, failure) -> response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
}
