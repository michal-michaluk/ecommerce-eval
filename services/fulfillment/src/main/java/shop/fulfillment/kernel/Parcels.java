package shop.fulfillment.kernel;

import java.util.Optional;

/**
 * Consumer-side contract: the parcel of one order as the processing lane needs it — registered before anything physical
 * exists, answering which parcel carries an order, and asked to collect once the order is ready.
 *
 * <p>The port is written in the processing lane's language and lives in the shared kernel, the one package both slices
 * may read: the parcel slice implements it, the processing slice holds it and never sees a parcel type. Its signature is
 * value objects only — our reference and the carrier's identity are two values, never one merged type.
 *
 * <p>Nothing here registers twice: {@link #register} is keyed by the processing, which owns exactly one parcel
 * (register D10), so a repeated call answers the parcel that already exists and consumes no second number.
 */
public interface Parcels {

    /**
     * The parcel registered for one processing, or empty while the first scan has not registered one.
     *
     * @param processingId the processing asking about its parcel
     * @return the parcel as the wire renders it, or empty
     */
    Optional<ParcelState> registeredFor(OrderProcessingId processingId);

    /**
     * Registers the one parcel of an order at the very beginning, and answers the identity we hold for it.
     *
     * @param processingId the processing the parcel carries
     * @param delivery     where the parcel goes and which service was bought
     * @return our identity of the registered parcel
     */
    ParcelId register(OrderProcessingId processingId, Delivery delivery);

    /**
     * Asks the carrier to collect the parcel. At most one collection is open per parcel, so a parcel that already has one
     * books nothing.
     *
     * @param parcelId the parcel to collect
     */
    void requestCollection(ParcelId parcelId);
}
