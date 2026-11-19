package shop.fulfillment.parcel;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import shop.fulfillment.kernel.CarrierPort;

/**
 * The carrier boundary's collaborators, built where a test is allowed to build a wide constructor: the registration block, the
 * service that registers and collects, the status poll and the label projection.
 */
public class ParcelFixture {

    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneOffset.UTC);
    public static final int WEIGHT_GRAMS = 1000;

    private ParcelFixture() {
    }

    public static CarrierRegistration aRegistration() {
        return CarrierRegistration.builder()
                .senderStreet("ul. Mineralna 5")
                .senderCity("Warszawa")
                .senderPostalCode("02-274")
                .senderCountry("PL")
                .weightGrams(WEIGHT_GRAMS)
                .build();
    }

    public static CarrierRegistration anUnconfiguredRegistration() {
        return CarrierRegistration.builder()
                .senderStreet("")
                .senderCity("")
                .senderPostalCode("")
                .senderCountry("")
                .weightGrams(WEIGHT_GRAMS)
                .build();
    }

    public static ParcelService service(ParcelRepository parcels, CarrierPort carrier) {
        return new ParcelService(parcels, Optional.ofNullable(carrier), aRegistration(), CLOCK);
    }

    public static CarrierPollingIntegration poller(ParcelRepository parcels, CarrierPort carrier) {
        return new CarrierPollingIntegration(Optional.ofNullable(carrier), parcels);
    }

    public static ParcelLabelProjection labels(ParcelRepository parcels, CarrierPort carrier) {
        return new ParcelLabelProjection(parcels, Optional.ofNullable(carrier));
    }
}
