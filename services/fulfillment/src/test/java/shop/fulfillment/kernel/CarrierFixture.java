package shop.fulfillment.kernel;

import java.time.Instant;

/**
 * The carrier values the tests work with: the identity a carrier assigns, the label it renders and the legs it reports.
 */
public class CarrierFixture {

    public static final ParcelReference REFERENCE = new ParcelReference("PCL-REF-0001");
    public static final CarrierIdentity IDENTITY = new CarrierIdentity("0234567890123");
    public static final Instant REACHED_AT = Instant.parse("2026-11-19T10:00:00Z");

    private CarrierFixture() {
    }

    public static RegistrationRequest aRegistrationRequest() {
        return RegistrationRequest.builder()
                .reference(REFERENCE)
                .sender(OrderFixture.anAddress())
                .pickupPointId("KRA01M")
                .weightGrams(1200)
                .ofTotal(1)
                .serviceCode("DPD_CLASSIC")
                .build();
    }

    public static Registration aRegistration() {
        return aRegistrationOf(REFERENCE, IDENTITY, new ParcelSequence(1, 1));
    }

    public static Registration aRegistrationOf(ParcelReference reference, CarrierIdentity identity,
                                               ParcelSequence sequence) {
        return new Registration(reference, identity, sequence);
    }

    public static LabelRequest aLabelRequest() {
        return aLabelRequestOf(REFERENCE, IDENTITY, "PDF", "A4");
    }

    public static LabelRequest aLabelRequestOf(ParcelReference reference, CarrierIdentity identity,
                                              String format, String layout) {
        return new LabelRequest(reference, identity, format, layout);
    }

    public static LabelDocument aLabelDocument() {
        return new LabelDocument(new byte[]{'%', 'P', 'D', 'F'}, "PDF");
    }

    public static CollectionRequest aCollectionRequest() {
        return new CollectionRequest(REFERENCE, IDENTITY);
    }

    public static LegObservation aLegObservation(Leg leg) {
        return aLegObservationOf(leg, REACHED_AT, leg.name(), "{\"leg\":\"" + leg.name() + "\"}");
    }

    public static LegObservation aLegObservationOf(Leg leg, Instant timestamp, String status, String payload) {
        return new LegObservation(leg, timestamp, status, payload);
    }
}
