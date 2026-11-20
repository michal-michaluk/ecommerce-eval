package shop.fulfillment.parcel;

import lombok.Builder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import shop.fulfillment.kernel.Address;
import shop.fulfillment.kernel.CarrierIdentity;
import shop.fulfillment.kernel.CarrierPort;
import shop.fulfillment.kernel.CollectionHandle;
import shop.fulfillment.kernel.CollectionRequest;
import shop.fulfillment.kernel.LabelDocument;
import shop.fulfillment.kernel.LabelRequest;
import shop.fulfillment.kernel.Leg;
import shop.fulfillment.kernel.LegObservation;
import shop.fulfillment.kernel.ParcelSequence;
import shop.fulfillment.kernel.Registration;
import shop.fulfillment.kernel.RegistrationRequest;
import shop.fulfillment.tools.JsonConfiguration;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PDF;

/**
 * The one adapter behind {@link CarrierPort}: one carrier's HTTP surface translated into this context's vocabulary, and
 * nothing else.
 *
 * <p>It translates, it never decides. Our reference travels in and the carrier's identity comes back, and the two are never
 * merged; a label request asks for a document and returns the bytes the carrier printed, never bytes we compose; a status the
 * adapter cannot map comes back as {@link LegObservation#unmapped} with its raw payload kept, so the mapping can be corrected
 * later without having lost the fact ({@code 14-carrier-port.md} RULE-6, RULE-7).
 *
 * <p>The carrier's endpoints, its payload shapes and its status vocabulary are adapter detail — no type of the carrier's
 * leaves this class (spec D2, D3). Every credential comes from configuration and appears in no port signature, event or log
 * line (RULE-9).
 */
class CarrierHttpClient implements CarrierPort {

    private static final String PARCELS = "/parcels";
    private static final String PARCEL_LABEL = "/parcels/{waybill}/label";
    private static final String COLLECTIONS = "/collections";
    private static final String PARCEL_STATUS = "/parcels/{waybill}/status";
    private static final String WAYBILL = "waybill";

    private final RestClient http;
    private final Settings settings;

    CarrierHttpClient(RestClient http, Settings settings) {
        this.http = http;
        this.settings = settings;
    }

    @Override
    public Registration register(RegistrationRequest request) {
        RegistrationAnswer answer = post(uri(PARCELS), RegistrationBody.of(request), RegistrationAnswer.class);
        return new Registration(request.reference(), new CarrierIdentity(answer.waybill()),
                new ParcelSequence(answer.sequence(), answer.ofTotal()));
    }

    @Override
    public LabelDocument label(LabelRequest request) {
        byte[] document = http.get()
                .uri(labelUri(request))
                .header(HttpHeaders.AUTHORIZATION, settings.authorization())
                .accept(APPLICATION_PDF)
                .retrieve()
                .body(byte[].class);
        return new LabelDocument(document, request.format());
    }

    @Override
    public CollectionHandle requestCollection(CollectionRequest request) {
        CollectionAnswer answer = post(uri(COLLECTIONS),
                new CollectionBody(request.reference().value(), request.carrierIdentity().value()),
                CollectionAnswer.class);
        return new CollectionHandle(answer.handle());
    }

    @Override
    public List<LegObservation> status(CarrierIdentity identity) {
        StatusRow[] rows = http.get()
                .uri(uri(PARCEL_STATUS, identity.value()))
                .header(HttpHeaders.AUTHORIZATION, settings.authorization())
                .retrieve()
                .body(StatusRow[].class);
        return Arrays.stream(rows).map(CarrierHttpClient::observationOf).toList();
    }

    /**
     * One carrier status as an observation: a status we recognise becomes one of our legs, a status we do not becomes an
     * observation that moves nothing and keeps the payload — an unknown status is a value here, never an error.
     */
    private static LegObservation observationOf(StatusRow row) {
        String raw = JsonConfiguration.OBJECT_MAPPER.writeValueAsString(row);
        return legOf(row.status())
                .map(leg -> new LegObservation(leg, row.occurredAt(), row.status(), raw))
                .orElseGet(() -> LegObservation.unmapped(row.occurredAt(), row.status(), raw));
    }

    /** The carrier's codes, in the adapter and nowhere else: the port speaks L1–L6 with our names (RULE-7). */
    private static Optional<Leg> legOf(String carrierStatus) {
        return switch (carrierStatus) {
            case "REGISTERED" -> Optional.of(Leg.L1);
            case "COLLECTED" -> Optional.of(Leg.L2);
            case "ASSIGNED_FOR_PICKUP" -> Optional.of(Leg.L3);
            case "PICKED_UP" -> Optional.of(Leg.L4);
            case "IN_SORTING_FACILITY" -> Optional.of(Leg.L5);
            case "DELIVERED_TO_CUSTOMER" -> Optional.of(Leg.L6);
            default -> Optional.empty();
        };
    }

    private URI uri(String path) {
        return UriComponentsBuilder.fromUriString(settings.url()).path(path).build().toUri();
    }

    private URI uri(String path, String variable) {
        return UriComponentsBuilder.fromUriString(settings.url()).path(path).buildAndExpand(variable).toUri();
    }

    private URI labelUri(LabelRequest request) {
        return UriComponentsBuilder.fromUriString(settings.url()).path(PARCEL_LABEL)
                .queryParam("format", request.format())
                .queryParam("layout", request.layout())
                .buildAndExpand(request.carrierIdentity().value()).toUri();
    }

    private <T> T post(URI uri, Object body, Class<T> type) {
        return http.post()
                .uri(uri)
                .contentType(APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, settings.authorization())
                .body(body)
                .retrieve()
                .body(type);
    }

    /**
     * What the deployment configures: the carrier's base URL and the credential this boundary authenticates with. Both come
     * from configuration and neither is in this repository (RULE-9).
     *
     * @param url   the carrier's base URL
     * @param token the credential, supplied by the deployment's secret
     */
    record Settings(String url, String token) {

        static final String PREFIX = "fulfillment.carrier";

        static Settings from(Environment configuration) {
            return new Settings(
                    configuration.getRequiredProperty(PREFIX + ".url"),
                    configuration.getRequiredProperty(PREFIX + ".token"));
        }

        String authorization() {
            return "Bearer " + token;
        }
    }

    /** What we send to register one parcel: our reference, both addresses, the parcel's facts, the opaque money. */
    @Builder
    record RegistrationBody(
            String reference,
            AddressBody sender,
            AddressBody receiver,
            String pickupPointId,
            int weightGrams,
            int ofTotal,
            String serviceCode,
            String cashOnDelivery,
            String declaredValue) {

        static RegistrationBody of(RegistrationRequest request) {
            return RegistrationBody.builder()
                    .reference(request.reference().value())
                    .sender(AddressBody.of(request.sender()))
                    .receiver(AddressBody.ofNullable(request.receiver()))
                    .pickupPointId(request.pickupPointId())
                    .weightGrams(request.weightGrams())
                    .ofTotal(request.ofTotal())
                    .serviceCode(request.serviceCode())
                    .cashOnDelivery(request.cashOnDelivery())
                    .declaredValue(request.declaredValue())
                    .build();
        }
    }

    /** One postal address on the carrier's wire. */
    record AddressBody(String street, String city, String postalCode, String country) {

        static AddressBody of(Address address) {
            return new AddressBody(address.street(), address.city(), address.postalCode(), address.country());
        }

        static AddressBody ofNullable(Address address) {
            return address == null ? null : of(address);
        }
    }

    /** The identity the carrier assigned and the sequence it will print. */
    record RegistrationAnswer(String waybill, int sequence, int ofTotal) {
    }

    /** The one parcel a collection is booked for, named by our reference and the carrier's identity. */
    record CollectionBody(String reference, String waybill) {
    }

    /** The carrier's opaque booking handle. */
    record CollectionAnswer(String handle) {
    }

    /** One status the carrier reports for one parcel. */
    record StatusRow(String status, Instant occurredAt) {
    }
}
