package shop.fulfillment;

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

import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;
import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

@Configuration
@EnableScheduling
@EnableRetry
@EnableJpaRepositories(considerNestedRepositories = true)
class AppConfiguration {

    /** The four scopes of element 11 as Spring Security spells a token's {@code scope} claim. */
    private static final String EMPLOYEE = "SCOPE_fulfillment-employee";
    private static final String SHOPPING = "SCOPE_shopping-service";

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Element 11's auth matrix, one rule per route shape, and nothing public but the actuator. A route nobody decided is
     * denied, so a later endpoint cannot become "any authenticated caller" by omission ({@code security.md}).
     *
     * <p>CSRF is off and the session is stateless because every caller authenticates with a bearer token and no cookie is
     * issued.
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(auth -> scopesOfElement11(auth).anyRequest().denyAll())
                .exceptionHandling(rejections -> rejections
                        .authenticationEntryPoint(unauthenticated())
                        .accessDeniedHandler(denied()))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    private static AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry scopesOfElement11(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        return auth
                .requestMatchers(pathPattern("/actuator/health/**")).permitAll()
                .requestMatchers(pathPattern("/actuator/info")).permitAll()
                .requestMatchers(pathPattern("/error")).permitAll()
                .requestMatchers(pathPattern("/actuator/**")).authenticated()
                .requestMatchers(pathPattern(POST, "/orders")).hasAuthority(SHOPPING)
                .requestMatchers(pathPattern("/orders")).hasAuthority(EMPLOYEE)
                .requestMatchers(pathPattern(POST, "/processing/{processingId}/assignment"))
                .hasAuthority(EMPLOYEE);
    }

    /** A missing token is {@code 401} with the status alone: element 11's {@code 401} body is empty. */
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
