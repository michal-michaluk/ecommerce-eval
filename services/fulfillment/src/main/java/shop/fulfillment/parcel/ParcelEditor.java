package shop.fulfillment.parcel;

import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import shop.fulfillment.kernel.CarrierIdentity;
import shop.fulfillment.kernel.CollectionHandle;
import shop.fulfillment.kernel.DomainEvent;
import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.Leg;
import shop.fulfillment.kernel.LegObservation;
import shop.fulfillment.kernel.LegReached;
import shop.fulfillment.kernel.LegState;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ParcelId;
import shop.fulfillment.kernel.ParcelReference;
import shop.fulfillment.kernel.ParcelSequence;
import shop.fulfillment.kernel.Registration;
import shop.fulfillment.kernel.RegistrationRequest;

/**
 * The carrier-side shipment of one processing, created before anything physical exists: the parcel is registered at
 * the very beginning, and the waybill it returns is the only carrier identity this record will ever hold
 * (element 03).
 *
 * <p>Our {@code reference} is the correlation key and is never derived from the waybill; legs move forward only, an
 * unknown carrier status leaves the leg state untouched and stores the raw payload, and re-applying a leg that is
 * already recorded changes nothing. Instances are created through
 * {@link #registerFor(ParcelId, OrderProcessingId, RegistrationRequest, Registration, Instant)} — there is no parcel
 * without a registration.
 */
@AllArgsConstructor
class ParcelEditor {

    final ParcelId parcelId;

    final List<DomainEvent> events;

    private final OrderProcessingId processingId;
    private final ParcelReference reference;
    private final int weightGrams;
    private final String serviceCode;
    private final ParcelSequence sequence;
    private final CarrierIdentity carrierIdentity;
    private final List<LegReached> legs;
    private LegState legState;
    private CollectionHandle collectionHandle;
    private String rawPayload;

    /**
     * Opens the parcel record from the carrier's answer: the waybill exists, so the parcel may exist, and the first leg
     * is recorded as reached.
     *
     * @param parcelId     the identity minted for this parcel
     * @param processingId the processing the parcel carries
     * @param request      the registration our side asked for
     * @param registration the carrier's answer, holding the identity and the sequence it will print
     * @param registeredAt the moment the carrier identity was assigned
     * @return a registered parcel that has announced its delivery number
     */
    static ParcelEditor registerFor(ParcelId parcelId, OrderProcessingId processingId,
                                    RegistrationRequest request, Registration registration, Instant registeredAt) {
        ensureSingleParcel(processingId, registration.sequence());
        ensureReferencesMatch(request.reference(), registration.reference());
        List<LegReached> legs = new ArrayList<>();
        legs.add(new LegReached(Leg.L1, registeredAt));
        ParcelEditor parcel = new ParcelEditor(parcelId, new ArrayList<>(), processingId, request.reference(),
                request.weightGrams(), request.serviceCode(), registration.sequence(),
                registration.carrierIdentity(), legs, LegState.PARCEL_REGISTERED, null, null);
        parcel.events.add(new DomainEvent.DeliveryNumberGenerated(parcelId, registration.carrierIdentity()));
        return parcel;
    }

    /**
     * Asks the carrier to collect, as the second effect of "ready for delivery". At most one collection is open per
     * parcel, so a parcel that already has one does not book a second.
     *
     * @param handle the carrier's opaque booking handle, stored so the booking can be changed or cancelled later
     */
    void markReady(CollectionHandle handle) {
        Objects.requireNonNull(handle, "handle");
        if (legState != LegState.PARCEL_REGISTERED) {
            return;
        }
        collectionHandle = handle;
        legState = LegState.PARCEL_READY;
        events.add(new DomainEvent.CollectionRequested(parcelId, handle));
    }

    /**
     * Applies one carrier observation. A leg the parcel has not reached yet moves it forward and is published; a leg
     * already recorded is a no-op; a status we cannot map moves nothing and is stored with its payload.
     *
     * @param observation the carrier's answer for one leg, or an unmapped status
     */
    void applyNotification(LegObservation observation) {
        Objects.requireNonNull(observation, "observation");
        if (observation.leg() == null) {
            rememberUnmappedStatus(observation);
            return;
        }
        applyLeg(observation);
    }

    /**
     * Reads the parcel as an immutable snapshot.
     *
     * @return the state as the read side serves it, its legs in leg order
     */
    Parcel toSnapshot() {
        return new Parcel(parcelId, processingId, reference, carrierIdentity, sequence, serviceCode, weightGrams,
                orderedLegs(), legState, collectionHandle, rawPayload);
    }

    private List<LegReached> orderedLegs() {
        return legs.stream()
                .sorted(Comparator.comparingInt(reached -> reached.leg().ordinal()))
                .toList();
    }

    private static void ensureSingleParcel(OrderProcessingId processingId, ParcelSequence sequence) {
        if (sequence.ofTotal() != 1) {
            throw new FulfillmentException(FulfillmentException.Error.PARCEL_SPLIT_UNSUPPORTED,
                    processingId, sequence.ofTotal());
        }
    }

    private static void ensureReferencesMatch(ParcelReference asked, ParcelReference answered) {
        if (!Objects.equals(asked, answered)) {
            throw new FulfillmentException(
                    FulfillmentException.Error.PARCEL_REFERENCE_MISMATCH, answered, asked);
        }
    }

    private void rememberUnmappedStatus(LegObservation observation) {
        if (!Objects.equals(rawPayload, observation.rawPayload())) {
            rawPayload = observation.rawPayload();
            events.add(new DomainEvent.DeliveryStatusUnrecognised(
                    parcelId, observation.carrierStatus(), observation.rawPayload()));
        }
    }

    private void applyLeg(LegObservation observation) {
        LegState reached = observation.leg().legState();
        boolean recorded = legs.stream().anyMatch(leg -> leg.leg() == observation.leg());
        if (!recorded && reached.ordinal() >= legState.ordinal()) {
            legs.add(new LegReached(observation.leg(), observation.carrierTimestamp()));
            legState = reached;
            events.add(new DomainEvent.DeliveryStateNotified(
                    parcelId, observation.leg(), observation.carrierStatus()));
        }
    }
}
