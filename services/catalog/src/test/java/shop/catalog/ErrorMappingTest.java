package shop.catalog;

import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import shop.catalog.offer.OfferRefusalFixture;
import shop.catalog.pricing.PricingRefusalFixture;
import shop.catalog.quality.QualityRefusalFixture;
import shop.catalog.review.ReviewRefusalFixture;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * 12's status ownership, one failure class at a time, driven through a real exception instance of each context: `409 {reason,
 * findings?}` for a refused command, `404` with no body for an unknown offer, `403 {reason}` for a scope decision, `400 {errors[]}` for
 * an invalid or unreadable command, `409 {retry}` for a lost optimistic lock, and a `500` the advice leaves to the framework.
 *
 * <p>No assertion reads a message: `reason` is the contract ({@code http.rejection-status}), every body is asserted whole, and
 * {@code include-message: never} keeps the text out of every response.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = ErrorMappingTest.Web.class)
class ErrorMappingTest {

    static final String OFFER_ID = "OFR-1A2B3C4D";

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void buildsMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void answersARefusedOfferCommandWith409AndItsReason() throws Exception {
        var response = responseTo(HttpMethod.GET, "/refusals/offer");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getContentAsString()).isEqualTo("{\"reason\":\"AVAILABILITY_START_MISSING\"}");
    }

    @Test
    void answersARefusedPricingCommandWith409AndItsReason() throws Exception {
        var response = responseTo(HttpMethod.GET, "/refusals/pricing");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getContentAsString()).isEqualTo("{\"reason\":\"DISCOUNT_CURRENCY_NOT_PRICED\"}");
    }

    @Test
    void answersARefusedReviewCommandWith409AndItsReason() throws Exception {
        var response = responseTo(HttpMethod.GET, "/refusals/review");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getContentAsString()).isEqualTo("{\"reason\":\"REVIEW_NOT_OPEN\"}");
    }

    @Test
    void answersARefusalThatCarriesFindingsWith409AndThem() throws Exception {
        var response = responseTo(HttpMethod.GET, "/refusals/findings");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getContentAsString())
                .isEqualTo("{\"reason\":\"QUALITY_FINDINGS\",\"findings\":[\"QP-6\"]}");
    }

    @Test
    void answersAnUnknownOfferWith404AndNoBody() throws Exception {
        var response = responseTo(HttpMethod.GET, "/refusals/unknown-offer");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void answersALostOptimisticLockWith409AndARetryHint() throws Exception {
        var response = responseTo(HttpMethod.GET, "/refusals/lost-race");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getContentAsString()).isEqualTo("{\"retry\":true}");
    }

    @Test
    void answersADraftComponentOutsideTheCallerWith403AndItsReason() throws Exception {
        var response = responseTo(HttpMethod.GET, "/refusals/draft-scope");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString()).isEqualTo("{\"reason\":\"FIELD_NOT_IN_SCOPE\"}");
    }

    @Test
    void answersAnInvalidCommandWith400AndItsField() throws Exception {
        var response = mvc.perform(request(HttpMethod.POST, "/refusals/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eanGtin\":\"x\"}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"eanGtin\"}]}");
    }

    @Test
    void answersAnUnknownPatchComponentWith400AndItsField() throws Exception {
        var response = responseTo(HttpMethod.GET, "/refusals/unknown-component");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getContentAsString()).isEqualTo("{\"errors\":[{\"field\":\"offerState\"}]}");
    }

    @Test
    void answersAnUnparsableCommandWith400AndNoField() throws Exception {
        var response = mvc.perform(request(HttpMethod.POST, "/refusals/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getContentAsString()).isEqualTo("{\"errors\":[]}");
    }

    @Test
    void leavesAFailureThatIsNoCommandRefusalToTheFramework() {
        var failure = catchThrowable(() -> responseTo(HttpMethod.GET, "/refusals/quality"));

        assertThat(failure).hasRootCauseInstanceOf(QualityRefusalFixture.minProseCharsNotPositive().getClass());
    }

    @Test
    void answers201WithTheBodyAndTheLocationOfTheCreatedResource() throws Exception {
        var response = responseTo(HttpMethod.GET, "/refusals/created");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("/offers/" + OFFER_ID);
        assertThat(response.getContentAsString()).isEqualTo("{\"offerId\":\"" + OFFER_ID + "\"}");
    }

    @Test
    void leavesTheNotFoundStatusAndTheEmptyBodyToTheHandler() throws Exception {
        var response = responseTo(HttpMethod.GET, "/refusals/unknown");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(response.getContentAsString()).isEmpty();
    }

    MockHttpServletResponse responseTo(HttpMethod method, String path) throws Exception {
        return mvc.perform(request(method, path)).andReturn().getResponse();
    }

    @Configuration
    @EnableWebMvc
    static class Web {

        @Bean
        ApiErrorAdvice apiErrorAdvice() {
            return new ApiErrorAdvice();
        }

        @Bean
        CreatedResponseAdvice createdResponseAdvice() {
            return new CreatedResponseAdvice();
        }

        @Bean
        Refusals refusals() {
            return new Refusals();
        }
    }

    @RestController
    static class Refusals {

        @GetMapping(path = "/refusals/offer", produces = MediaType.APPLICATION_JSON_VALUE)
        Object offer() {
            throw OfferRefusalFixture.availabilityStartMissing();
        }

        @GetMapping(path = "/refusals/pricing", produces = MediaType.APPLICATION_JSON_VALUE)
        Object pricing() {
            throw PricingRefusalFixture.discountCurrencyNotPriced();
        }

        @GetMapping(path = "/refusals/review", produces = MediaType.APPLICATION_JSON_VALUE)
        Object review() {
            throw ReviewRefusalFixture.reviewNotOpen();
        }

        @GetMapping(path = "/refusals/findings", produces = MediaType.APPLICATION_JSON_VALUE)
        Object findings() {
            throw new FindingsRefusal("QUALITY_FINDINGS", List.of("QP-6"));
        }

        @GetMapping(path = "/refusals/unknown-offer", produces = MediaType.APPLICATION_JSON_VALUE)
        Object unknownOffer() {
            throw OfferRefusalFixture.offerUnknown();
        }

        @GetMapping(path = "/refusals/quality", produces = MediaType.APPLICATION_JSON_VALUE)
        Object quality() {
            throw QualityRefusalFixture.minProseCharsNotPositive();
        }

        @GetMapping(path = "/refusals/lost-race", produces = MediaType.APPLICATION_JSON_VALUE)
        Object lostRace() {
            throw new org.springframework.dao.OptimisticLockingFailureException(
                    "offer " + OFFER_ID + " was changed by someone else");
        }

        @GetMapping(path = "/refusals/draft-scope", produces = MediaType.APPLICATION_JSON_VALUE)
        Object draftScope() {
            Scopes.requireDraftWrite(Set.of(Scopes.AUTHOR), Set.of(Scopes.MEDIA));
            return Map.of();
        }

        @GetMapping(path = "/refusals/unknown-component", produces = MediaType.APPLICATION_JSON_VALUE)
        Object unknownComponent() {
            throw UnreadableCommandFixture.unknownComponent();
        }

        @PostMapping(path = "/refusals/command",
                consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        Object command(@RequestBody @jakarta.validation.Valid NewOffer command) {
            return command;
        }

        @GetMapping(path = "/refusals/created", produces = MediaType.APPLICATION_JSON_VALUE)
        @ResponseStatus(HttpStatus.CREATED)
        Created created() {
            return new Created(OFFER_ID);
        }

        @GetMapping(path = "/refusals/unknown", produces = MediaType.APPLICATION_JSON_VALUE)
        Object unknown() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    record NewOffer(@Pattern(regexp = "\\d{13}") String eanGtin) {
    }

    static final class FindingsRefusal extends RuntimeException implements DomainRefusal {

        private final String reason;
        private final List<?> findings;

        FindingsRefusal(String reason, List<?> findings) {
            super(reason);
            this.reason = reason;
            this.findings = findings;
        }

        @Override
        public String reason() {
            return reason;
        }

        @Override
        public List<?> findings() {
            return findings;
        }
    }

    /** The created body names its own path; the path is no wire component, so the snapshot stays the only body 12 describes. */
    record Created(String offerId) implements CreatedResource {

        @Override
        public String location() {
            return "/offers/" + offerId;
        }
    }
}
