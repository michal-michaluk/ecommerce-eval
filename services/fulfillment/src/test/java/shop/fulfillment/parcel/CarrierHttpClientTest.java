package shop.fulfillment.parcel;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import shop.fulfillment.kernel.CarrierFixture;
import shop.fulfillment.kernel.CarrierIdentity;
import shop.fulfillment.kernel.CollectionHandle;
import shop.fulfillment.kernel.LabelDocument;
import shop.fulfillment.kernel.Leg;
import shop.fulfillment.kernel.LegObservation;
import shop.fulfillment.kernel.ParcelSequence;
import shop.fulfillment.kernel.Registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The carrier adapter against recorded payloads: our reference and the carrier's identity never merge, the label is the
 * document the carrier returns, a collection answers the carrier's handle, and a status we cannot map is kept with its raw
 * payload instead of being invented into a leg.
 */
class CarrierHttpClientTest {

    static final String URL = "http://carrier.test";
    static final String TOKEN = "t0ken";
    static final Instant OCCURRED_AT = Instant.parse("2026-11-19T10:00:00Z");

    final RestClient.Builder builder = RestClient.builder();
    final MockRestServiceServer carrier = MockRestServiceServer.bindTo(builder).build();
    final CarrierHttpClient client = new CarrierHttpClient(builder.build(), settings());

    @Test
    void registersOneParcelAndTakesTheIdentityFromTheAnswer() {
        carrier.expect(requestTo(URL + "/parcels"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(content().json("""
                        {"reference": "PCL-REF-0001",
                         "sender": {"street": "ul. Mineralna 5A", "city": "Kraków", "postalCode": "30-001",
                                    "country": "PL"},
                         "receiver": null, "pickupPointId": "KRA01M", "weightGrams": 1200, "ofTotal": 1,
                         "serviceCode": "DPD_CLASSIC", "cashOnDelivery": null, "declaredValue": null}""", true))
                .andRespond(withSuccess("""
                        {"waybill": "0234567890123", "sequence": 1, "ofTotal": 1}""", json()));

        Registration registration = client.register(CarrierFixture.aRegistrationRequest());

        assertThat(registration.reference()).isEqualTo(CarrierFixture.REFERENCE);
        assertThat(registration.carrierIdentity()).isEqualTo(CarrierFixture.IDENTITY);
        assertThat(registration.sequence()).isEqualTo(new ParcelSequence(1, 1));
    }

    @Test
    void asksForTheLabelOfTheWaybillItAlreadyHolds() {
        carrier.expect(requestTo(URL + "/parcels/0234567890123/label?format=PDF&layout=A4"))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(new byte[]{'%', 'P', 'D', 'F'}, pdf()));

        LabelDocument label = client.label(CarrierFixture.aLabelRequest());

        assertThat(label.bytes()).isEqualTo(new byte[]{'%', 'P', 'D', 'F'});
        assertThat(label.format()).isEqualTo("PDF");
    }

    @Test
    void booksOneCollectionAndTakesTheCarrierHandle() {
        carrier.expect(requestTo(URL + "/collections"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"reference": "PCL-REF-0001", "waybill": "0234567890123"}""", true))
                .andRespond(withSuccess("""
                        {"handle": "COL-0001"}""", json()));

        CollectionHandle handle = client.requestCollection(CarrierFixture.aCollectionRequest());

        assertThat(handle).isEqualTo(new CollectionHandle("COL-0001"));
    }

    @Test
    void mapsTheCarriersStatusesOntoOurLegs() {
        statuses("""
                [{"status": "ASSIGNED_FOR_PICKUP", "occurredAt": "2026-11-19T10:00:00Z"},
                 {"status": "DELIVERED_TO_CUSTOMER", "occurredAt": "2026-11-19T18:00:00Z"}]""");

        List<LegObservation> observations = client.status(CarrierFixture.IDENTITY);

        assertThat(observations).extracting(LegObservation::leg).containsExactly(Leg.L3, Leg.L6);
        assertThat(observations.get(0).carrierTimestamp()).isEqualTo(OCCURRED_AT);
        assertThat(observations.get(0).rawPayload()).contains("ASSIGNED_FOR_PICKUP");
    }

    @Test
    void keepsAStatusItCannotMapWithoutInventingALeg() {
        statuses("""
                [{"status": "RETURNED_TO_SENDER", "occurredAt": "2026-11-19T10:00:00Z"}]""");

        List<LegObservation> observations = client.status(CarrierFixture.IDENTITY);

        assertThat(observations).singleElement()
                .satisfies(observation -> {
                    assertThat(observation.leg()).isNull();
                    assertThat(observation.carrierStatus()).isEqualTo("RETURNED_TO_SENDER");
                    assertThat(observation.rawPayload()).contains("RETURNED_TO_SENDER");
                });
    }

    @Test
    void translatesTheWholeStatusVocabularyItRecognises() {
        statuses("""
                [{"status": "REGISTERED", "occurredAt": "2026-11-19T10:00:00Z"},
                 {"status": "COLLECTED", "occurredAt": "2026-11-19T11:00:00Z"},
                 {"status": "PICKED_UP", "occurredAt": "2026-11-19T12:00:00Z"},
                 {"status": "IN_SORTING_FACILITY", "occurredAt": "2026-11-19T13:00:00Z"}]""");

        assertThat(client.status(CarrierFixture.IDENTITY)).extracting(LegObservation::leg)
                .containsExactly(Leg.L1, Leg.L2, Leg.L4, Leg.L5);
    }

    @Test
    void readsItsSettingsFromTheDeploymentsConfiguration() {
        MockEnvironment configuration = new MockEnvironment()
                .withProperty("fulfillment.carrier.url", URL)
                .withProperty("fulfillment.carrier.token", TOKEN);

        CarrierHttpClient.Settings settings = CarrierHttpClient.Settings.from(configuration);

        assertThat(settings.url()).isEqualTo(URL);
        assertThat(settings.authorization()).isEqualTo("Bearer " + TOKEN);
    }

    private void statuses(String body) {
        carrier.expect(requestTo(URL + "/parcels/0234567890123/status"))
                .andExpect(header("Authorization", "Bearer " + TOKEN))
                .andRespond(withSuccess(body, json()));
    }

    private static CarrierHttpClient.Settings settings() {
        return new CarrierHttpClient.Settings(URL, TOKEN);
    }

    private static org.springframework.http.MediaType json() {
        return org.springframework.http.MediaType.APPLICATION_JSON;
    }

    private static org.springframework.http.MediaType pdf() {
        return org.springframework.http.MediaType.APPLICATION_PDF;
    }
}
