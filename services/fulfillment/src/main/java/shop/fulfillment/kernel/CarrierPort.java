package shop.fulfillment.kernel;

import java.util.List;

/**
 * Consumer-side contract: what the delivery of one parcel needs from any carrier — register it before it exists,
 * render its label, ask for its collection, and report its legs.
 *
 * <p>The port is carrier-agnostic on purpose: no carrier name appears in a type, a field, an event or a method, and the
 * first adapter is just the first implementation. Four asymmetries belong to the contract: our reference is the only
 * correlation key, a label is a render and never a registration, at most one collection is open per parcel, and status
 * is polled — no callback is promised, and only transitions are published.
 *
 * <p>Cash-on-delivery and declared value pass through as opaque values; nothing here computes, validates or rounds
 * money, and no credential appears in any signature.
 */
public interface CarrierPort {

    /**
     * Registers one parcel with the carrier. A carrier identity comes into existence, which is why nothing physical is
     * recorded before this answers.
     *
     * @param request our reference and everything the carrier prints
     * @return the identity the carrier assigned and the sequence it will print
     */
    Registration register(RegistrationRequest request);

    /**
     * Renders the label of one parcel from data the carrier already holds.
     *
     * @param request the parcel and the format and layout wanted
     * @return the printable document
     */
    LabelDocument label(LabelRequest request);

    /**
     * Asks the carrier to collect one parcel. Not idempotent: a repeat books a second pickup, so the caller keeps at
     * most one open collection per parcel.
     *
     * @param request the parcel to collect
     * @return the carrier's booking handle
     */
    CollectionHandle requestCollection(CollectionRequest request);

    /**
     * Reads the legs the carrier reports for one parcel. Idempotent: re-polling the same leg is a no-op for the caller.
     *
     * @param identity the carrier's identity of the parcel
     * @return every leg the carrier currently reports, mapped onto our own names where it can
     */
    List<LegObservation> status(CarrierIdentity identity);
}
