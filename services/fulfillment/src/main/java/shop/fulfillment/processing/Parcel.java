package shop.fulfillment.processing;

import lombok.Builder;

import java.util.List;

/**
 * One consignment of one order as the read side serves it: our reference, the carrier's identity, the sequence and
 * service printed on the label, the chain of legs in leg order, and the last carrier payload we could not map. The two
 * identities are never merged (element 03 RULE-2).
 *
 * <p>No carrier name appears here: a carrier is identified by the identity it assigns, never by its own name
 * (element 14, spec D3).
 */
@Builder
record Parcel(
        ParcelId parcelId,
        OrderProcessingId processingId,
        ParcelReference reference,
        CarrierIdentity carrierIdentity,
        ParcelSequence sequence,
        String serviceCode,
        int weightGrams,
        List<LegReached> legs,
        LegState legState,
        CollectionHandle collectionHandle,
        String rawPayload) {
}
