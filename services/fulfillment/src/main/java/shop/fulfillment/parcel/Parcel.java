package shop.fulfillment.parcel;

import lombok.Builder;

import java.util.List;

import shop.fulfillment.kernel.CarrierIdentity;
import shop.fulfillment.kernel.CollectionHandle;
import shop.fulfillment.kernel.LegReached;
import shop.fulfillment.kernel.LegState;
import shop.fulfillment.kernel.OrderProcessingId;
import shop.fulfillment.kernel.ParcelId;
import shop.fulfillment.kernel.ParcelReference;
import shop.fulfillment.kernel.ParcelSequence;

/**
 * One consignment of one order as the read side serves it: our reference, the carrier's identity, the sequence and
 * service printed on the label, the chain of legs in leg order, and every carrier payload we could not map — kept so the
 * mapping can be corrected later (element 14 RULE-6). The two identities are never merged (element 03 RULE-2).
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
        List<String> unmappedPayloads) {
}
