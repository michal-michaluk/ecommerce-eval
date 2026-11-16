package shop.catalog.pricing;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import shop.catalog.ApiAdviceFixture;
import shop.catalog.CallerFixture;
import shop.catalog.JsonAssert;
import shop.catalog.Scopes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static shop.catalog.pricing.PricingFixture.JULY_1;
import static shop.catalog.pricing.PricingFixture.JUNE_1;
import static shop.catalog.pricing.PricingFixture.JUNE_20;
import static shop.catalog.pricing.PricingFixture.OFFER_ID;
import static shop.catalog.pricing.PricingFixture.clockAt;
import static shop.catalog.pricing.PricingFixture.knowing;
import static shop.catalog.pricing.PricingFixture.openPriceChange;
import static shop.catalog.pricing.PricingFixture.percentageChange;
import static shop.catalog.pricing.PricingFixture.pln;
import static shop.catalog.pricing.PricingFixture.pricingModelOfInstanceA;

/**
 * 12's `prices and discounts` resource over the real {@link PricingService} and its in-memory ports: the wire shape of the read, the one
 * edit of D18 that carries every component at once (a replacement included), the refusal a started entry answers with, and the `404` of
 * an offer this context has never heard of.
 *
 * <p>Both verbs are `catalog-sales`; {@link shop.catalog.ScopeGuardTest} proves that against the filter chain, so this test is about the
 * bodies and the statuses behind the route.
 */
class PricingControllerTest {

    static final String PRICING = "/offers/" + OFFER_ID + "/pricing";

    static final String UNKNOWN = "/offers/OFR-UNKNOWN/pricing";

    final Map<String, PricingModelEditor> models = new HashMap<>();

    final PricingController controller = new PricingController(
            PricingFixture.serviceAt(new InMemoryPricingRepository(), clockAt(JUNE_20), knowing(OFFER_ID)), clockAt(JUNE_20));

    MockMvc mvc;

    @BeforeEach
    void opensTheResource() {
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(ApiAdviceFixture.apiErrorAdvice()).build();
        CallerFixture.actsAs("sales.nowak", Scopes.SALES);
    }

    @AfterEach
    void clearsTheCaller() {
        CallerFixture.forgets();
    }

    @Test
    void readsThePricesAndTheDiscountsOfTheOffer() throws Exception {
        models.put(OFFER_ID, pricingModelOfInstanceA());

        MockHttpServletResponse read = mvc.perform(get(PRICING)).andReturn().getResponse();

        assertThat(read.getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonAssert.assertThat(read.getContentAsString()).isExactlyLike("""
                { "current": { "priceId": "P-1", "amountMinor": 30989, "currency": "PLN",
                               "validFrom": "2026-05-01T00:00:00Z", "validUntil": "2026-07-01T00:00:00Z" },
                  "scheduled": [ { "priceId": "P-2", "amountMinor": 29900, "currency": "PLN",
                                   "validFrom": "2026-07-01T00:00:00Z", "validUntil": null } ],
                  "history": [],
                  "discounts": { "active": [ { "discountId": "D-1", "kind": "percentage", "basisPoints": 1000,
                                               "amountMinor": 0, "currency": null,
                                               "validFrom": "2026-06-20T00:00:00Z", "validUntil": "2026-07-01T00:00:00Z" } ],
                                 "scheduled": [ { "discountId": "D-2", "kind": "fixed", "basisPoints": 0,
                                                  "amountMinor": 2000, "currency": "PLN",
                                                  "validFrom": "2026-07-01T00:00:00Z", "validUntil": "2026-07-08T00:00:00Z" } ],
                                 "expired": [] } }
                """);
    }

    @Test
    void answersAnOfferThisContextNeverHeardOfWith404AndNoBody() throws Exception {
        MockHttpServletResponse read = mvc.perform(get(UNKNOWN)).andReturn().getResponse();
        MockHttpServletResponse edited = mvc.perform(patch(UNKNOWN).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "prices": [ { "currency": "PLN", "amountMinor": 30989 } ] }
                        """)).andReturn().getResponse();

        assertThat(read.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(read.getContentAsString()).isEmpty();
        assertThat(edited.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(edited.getContentAsString()).isEmpty();
        assertThat(models).isEmpty();
    }

    @Test
    void answersAKnownOfferThatHasNoPriceYetWith404() throws Exception {
        MockHttpServletResponse read = mvc.perform(get(PRICING)).andReturn().getResponse();

        assertThat(read.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(read.getContentAsString()).isEmpty();
    }

    @Test
    void appliesAPriceAndADiscountInOneEdit() throws Exception {
        MockHttpServletResponse edited = mvc.perform(writes(PRICING, """
                { "prices": [ { "currency": "PLN", "amountMinor": 30989 } ],
                  "discounts": [ { "kind": "percentage", "basisPoints": 1000,
                                   "validFrom": "%s", "validUntil": "2026-06-30T23:59:59Z" } ] }
                """.formatted(JUNE_20))).andReturn().getResponse();

        assertThat(edited.getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonAssert.assertThat(edited.getContentAsString()).hasFieldsLike("""
                { "current": { "amountMinor": 30989, "currency": "PLN", "validUntil": null },
                  "discounts": { "active": [ { "basisPoints": 1000 } ] } }
                """);
    }

    @Test
    void editsNewEntriesARemovalOfEachFamilyAndAReplacementInTheSameRequest() throws Exception {
        String scheduledPriceId = priceIdIn(writes(PRICING, """
                { "prices": [ { "currency": "PLN", "amountMinor": 29900, "validFrom": "%s" } ],
                  "discounts": [ { "kind": "percentage", "basisPoints": 500,
                                   "validFrom": "%s", "validUntil": "2026-07-01T00:00:00Z" } ] }
                """.formatted(JULY_1, JUNE_20)), "$.scheduled[0].priceId");
        String expiredDiscountId = priceIdIn(writes(PRICING, """
                { "discounts": [ { "kind": "percentage", "basisPoints": 300,
                                   "validFrom": "2026-06-25T00:00:00Z", "validUntil": "2026-06-30T00:00:00Z" } ] }
                """), "$.discounts.scheduled[0].discountId");

        MockHttpServletResponse edited = mvc.perform(writes(PRICING, """
                { "prices": [ { "currency": "PLN", "amountMinor": 29500, "validFrom": "%s" } ],
                  "discounts": [ { "kind": "fixed", "amountMinor": 2000, "currency": "PLN",
                                   "validFrom": "2026-07-01T00:00:00Z", "validUntil": "2026-07-08T00:00:00Z" } ],
                  "removePriceIds": [ "%s" ], "removeDiscountIds": [ "%s" ] }
                """.formatted(JULY_1, scheduledPriceId, expiredDiscountId))).andReturn().getResponse();

        assertThat(edited.getStatus()).isEqualTo(HttpStatus.OK.value());
        JsonAssert.assertThat(edited.getContentAsString()).hasFieldsLike("""
                { "scheduled": [ { "amountMinor": 29500 } ],
                  "discounts": { "active": [ { "kind": "percentage", "basisPoints": 500 } ],
                                 "scheduled": [ { "kind": "fixed", "amountMinor": 2000 } ] } }
                """);
    }

    @Test
    void refusesToRemoveAnEntryThatAlreadyStarted() throws Exception {
        String startedPriceId = startedPriceId();

        MockHttpServletResponse refused = mvc.perform(writes(PRICING, """
                { "removePriceIds": [ "%s" ] }
                """.formatted(startedPriceId))).andReturn().getResponse();

        assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(refused.getContentAsString()).isEqualTo("{\"reason\":\"PRICE_ALREADY_STARTED\"}");
    }

    @Test
    void refusesAPriceWithoutACurrencyOrAnAmountAs400() throws Exception {
        MockHttpServletResponse noCurrency = mvc.perform(writes(PRICING, """
                { "prices": [ { "amountMinor": 30989 } ] }
                """)).andReturn().getResponse();
        MockHttpServletResponse noAmount = mvc.perform(writes(PRICING, """
                { "prices": [ { "currency": "PLN" } ] }
                """)).andReturn().getResponse();
        MockHttpServletResponse badBasisPoints = mvc.perform(writes(PRICING, """
                { "discounts": [ { "kind": "percentage", "basisPoints": 20000,
                                   "validFrom": "%s" } ] }
                """.formatted(JUNE_20))).andReturn().getResponse();

        assertThat(noCurrency.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        JsonAssert.assertThat(noCurrency.getContentAsString()).hasFieldsLike("""
                { "errors": [ { "field": "prices[0].currency" } ] }
                """);
        assertThat(noAmount.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        JsonAssert.assertThat(noAmount.getContentAsString()).hasFieldsLike("""
                { "errors": [ { "field": "prices[0].amountMinor" } ] }
                """);
        assertThat(badBasisPoints.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        JsonAssert.assertThat(badBasisPoints.getContentAsString()).hasFieldsLike("""
                { "errors": [ { "field": "discounts[0].complete" } ] }
                """);
        assertThat(models).isEmpty();
    }

    private MockHttpServletRequestBuilder writes(String path, String body) {
        return patch(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private String priceIdIn(MockHttpServletRequestBuilder request, String path) throws Exception {
        MockHttpServletResponse response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        return JsonPath.read(response.getContentAsString(), path);
    }

    private String startedPriceId() throws Exception {
        models.put(OFFER_ID, startedModel());
        MockHttpServletResponse read = mvc.perform(get(PRICING)).andReturn().getResponse();
        assertThat(read.getStatus()).isEqualTo(HttpStatus.OK.value());
        return JsonPath.read(read.getContentAsString(), "$.current.priceId");
    }

    private static PricingModelEditor startedModel() {
        PricingModelEditor model = PricingModelEditor.newPricingModelFor(OFFER_ID);
        model.schedulePrice(openPriceChange("P-1", pln(30989), JUNE_1), JUNE_1);
        model.scheduleDiscount(percentageChange("D-1", 1000, JUNE_1, JULY_1), JUNE_1);
        return model;
    }

    class InMemoryPricingRepository implements PricingRepository {

        @Override
        public Optional<PricingModelEditor> get(String offerId) {
            return Optional.ofNullable(models.get(offerId));
        }

        @Override
        public void save(PricingModelEditor pricing) {
            models.put(pricing.offerId, pricing);
        }
    }
}
