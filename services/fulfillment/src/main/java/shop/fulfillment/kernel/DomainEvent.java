package shop.fulfillment.kernel;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

/**
 * Every fact this context emits, one record per fact, named in the past tense after what happened.
 *
 * <p>A payload is identity plus the changed value object — never the aggregate, never a persistence type. Events are
 * appended by the aggregate inside the change they record and published by the persistence adapter; nothing else
 * touches the buffer.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DomainEvent.OrderProcessingStarted.class, name = "OrderProcessingStarted_v1"),
        @JsonSubTypes.Type(value = DomainEvent.EmployeeAssigned.class, name = "EmployeeAssigned_v1"),
        @JsonSubTypes.Type(value = DomainEvent.PositionPicked.class, name = "PositionPicked_v1"),
        @JsonSubTypes.Type(value = DomainEvent.PositionPacked.class, name = "PositionPacked_v1"),
        @JsonSubTypes.Type(value = DomainEvent.ReadyForDeliveryConfirmed.class,
                name = "ReadyForDeliveryConfirmed_v1"),
        @JsonSubTypes.Type(value = DomainEvent.OrderDelivered.class, name = "OrderDelivered_v1"),
        @JsonSubTypes.Type(value = DomainEvent.IssueReported.class, name = "IssueReported_v1"),
        @JsonSubTypes.Type(value = DomainEvent.HoldPlaced.class, name = "HoldPlaced_v1"),
        @JsonSubTypes.Type(value = DomainEvent.OrderResumed.class, name = "OrderResumed_v1"),
        @JsonSubTypes.Type(value = DomainEvent.StockChanged.class, name = "StockChanged_v1"),
        @JsonSubTypes.Type(value = DomainEvent.DeliveryNumberGenerated.class,
                name = "DeliveryNumberGenerated_v1"),
        @JsonSubTypes.Type(value = DomainEvent.CollectionRequested.class, name = "CollectionRequested_v1"),
        @JsonSubTypes.Type(value = DomainEvent.DeliveryStateNotified.class, name = "DeliveryStateNotified_v1"),
        @JsonSubTypes.Type(value = DomainEvent.DeliveryStatusUnrecognised.class,
                name = "DeliveryStatusUnrecognised_v1")
})
public sealed interface DomainEvent {

    /** A finished order was taken into processing; the {@code orderHash} is the idempotency key of the handover. */
    record OrderProcessingStarted(OrderProcessingId processingId, Order order) implements DomainEvent {
    }

    /** An employee claimed the order — one claim, one event. */
    record EmployeeAssigned(OrderProcessingId processingId, String assignee) implements DomainEvent {
    }

    /** A scan confirmed one position. */
    record PositionPicked(OrderProcessingId processingId, String productNumber) implements DomainEvent {
    }

    /** One position went into the box. */
    record PositionPacked(OrderProcessingId processingId, String productNumber) implements DomainEvent {
    }

    /** The order is done locally and the carrier was asked to collect it — one event for the two effects. */
    record ReadyForDeliveryConfirmed(OrderProcessingId processingId, ParcelId parcelId) implements DomainEvent {
    }

    /** The customer picked the parcel up: the last leg closed the order. */
    record OrderDelivered(OrderProcessingId processingId) implements DomainEvent {
    }

    /**
     * What one location holds for one product changed, as we read it at the warehouse — the warehouse cannot notify us,
     * so {@code stock changed} is a fact we raise (element 13, RULE-6). The same observation read twice is a duplicate to
     * collapse, not a second change.
     */
    @Builder
    record StockChanged(ProductId productId, LocationCode location, int available, int reserved, Instant observedAt)
            implements DomainEvent {
    }

    /** An employee reported a warehouse reality gap. */
    record IssueReported(IssueId issueId, OrderProcessingId processingId, String kind) implements DomainEvent {
    }

    /** The whole order is held because an issue was reported. */
    record HoldPlaced(OrderProcessingId processingId, Instant since) implements DomainEvent {
    }

    /** The hold was lifted and the order returned to the state it was held from. */
    record OrderResumed(OrderProcessingId processingId, ProcessingState state) implements DomainEvent {
    }

    /** The carrier assigned an identity to the parcel — the identity is only ever assigned, never invented. */
    record DeliveryNumberGenerated(ParcelId parcelId, CarrierIdentity carrierIdentity) implements DomainEvent {
    }

    /** The carrier was asked to collect one parcel, and booked it. */
    record CollectionRequested(ParcelId parcelId, CollectionHandle collectionHandle) implements DomainEvent {
    }

    /** A carrier reported one of our legs. */
    record DeliveryStateNotified(ParcelId parcelId, Leg leg, String carrierStatus) implements DomainEvent {
    }
    /** A carrier status we cannot map to one of our legs; the payload is kept so the mapping can be corrected. */
    record DeliveryStatusUnrecognised(ParcelId parcelId, String carrierStatus, String rawPayload)
            implements DomainEvent {
    }
}
