package shop.fulfillment.parcel;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import shop.fulfillment.kernel.CarrierPort;
import shop.fulfillment.kernel.CollectionRequest;
import shop.fulfillment.kernel.Delivery;
import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.LegState;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ParcelId;
import shop.fulfillment.kernel.ParcelReference;
import shop.fulfillment.kernel.ParcelState;
import shop.fulfillment.kernel.Parcels;
import shop.fulfillment.kernel.RegistrationRequest;

/**
 * The parcel side of the lane: one parcel is registered at the very beginning — on the first scan of its order (register
 * D11) — and collected when the order is ready, and nothing physical is recorded before the carrier answered an identity
 * (element 03 RULE-1).
 *
 * <p>It holds no rule. Whether a leg may move, that one processing owns one parcel and that a carrier identity is only ever
 * what a response assigned are the aggregate's and the port's; this class mints our reference, asks the carrier once and
 * stores the result. A registration addressed at a processing that already has a parcel answers that parcel and consumes no
 * second number, so a repeated scan cannot register twice.
 *
 * <p>A deployment with no carrier still boots and is refused loudly: no reference is invented, no placeholder identity is
 * stored, and no parcel record exists until a carrier answered (element 14 RULE-1, spec D14).
 */
@Service
@Transactional
class ParcelService implements Parcels {

    private static final int FIRST_PARCEL = 1;

    private final ParcelRepository parcels;
    private final Optional<CarrierPort> carrier;
    private final CarrierRegistration registration;
    private final Clock clock;

    ParcelService(ParcelRepository parcels, Optional<CarrierPort> carrier, CarrierRegistration registration,
                  Clock clock) {
        this.parcels = parcels;
        this.carrier = carrier;
        this.registration = registration;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ParcelState> registeredFor(OrderProcessingId processingId) {
        return parcels.findByProcessing(processingId.value()).map(ParcelEditor::toParcelState);
    }

    @Override
    public ParcelId register(OrderProcessingId processingId, Delivery delivery) {
        return parcels.findByProcessing(processingId.value())
                .map(parcel -> parcel.toParcelState().parcelId())
                .orElseGet(() -> registerFirstParcel(processingId, delivery));
    }

    @Override
    public void requestCollection(ParcelId parcelId) {
        parcels.get(parcelId).ifPresent(this::collect);
    }

    /** Asks the carrier to collect a parcel that has no open collection yet; a parcel already booked books nothing. */
    private void collect(ParcelEditor parcel) {
        ParcelState parcelState = parcel.toParcelState();
        if (parcelState.legState() != LegState.PARCEL_REGISTERED) {
            return;
        }
        parcel.markReady(carrier().requestCollection(
                new CollectionRequest(parcelState.reference(), parcelState.carrierIdentity())));
        parcels.save(parcel);
    }

    private ParcelId registerFirstParcel(OrderProcessingId processingId, Delivery delivery) {
        RegistrationRequest request = registration.requestFor(referenceOf(processingId), delivery);
        ParcelEditor parcel = ParcelEditor.registerFor(parcels.nextIdentity(), processingId, request,
                carrier().register(request), Instant.now(clock));
        parcels.save(parcel);
        return parcel.toParcelState().parcelId();
    }

    private static ParcelReference referenceOf(OrderProcessingId processingId) {
        return new ParcelReference(processingId.value() + "-" + FIRST_PARCEL);
    }

    private CarrierPort carrier() {
        return carrier.orElseThrow(() -> new FulfillmentException(
                FulfillmentException.Error.CARRIER_NOT_WIRED, "registering a parcel"));
    }
}
