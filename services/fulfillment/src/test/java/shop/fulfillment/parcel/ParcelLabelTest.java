package shop.fulfillment.parcel;

import org.junit.jupiter.api.Test;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.LabelDocument;
import shop.fulfillment.kernel.OrderFixture;
import shop.fulfillment.kernel.ParcelId;
import shop.fulfillment.kernel.ProcessingFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Element 06's document as this context serves it: the first print asks the carrier for the waybill we already hold and stores
 * what comes back, and every reprint is the stored document — no registration, no second number, no template of ours.
 */
class ParcelLabelTest {

    static final ParcelId PARCEL_ID = new ParcelId("PCL-2026-0001");

    final FakeParcelRepository parcels = new FakeParcelRepository();
    final FakeCarrierPort carrier = new FakeCarrierPort();
    final ParcelLabelProjection labels = ParcelFixture.labels(parcels, carrier);

    @Test
    void asksTheCarrierForTheWaybillWeHoldAndStoresTheDocument() {
        givenAParcel();

        LabelDocument label = labels.labelOf(ProcessingFixture.PROCESSING_ID.value());

        assertThat(label.bytes()).isEqualTo(FakeCarrierPort.DOCUMENT);
        assertThat(label.format()).isEqualTo(ParcelLabelProjection.FORMAT);
        assertThat(carrier.callsStartingWith("label")).hasSize(1);
        assertThat(parcels.labels).containsKey(PARCEL_ID.value());
    }

    @Test
    void servesTheStoredDocumentOnEveryReprint() {
        givenAParcel();
        labels.labelOf(ProcessingFixture.PROCESSING_ID.value());

        LabelDocument reprint = labels.labelOf(ProcessingFixture.PROCESSING_ID.value());

        assertThat(reprint.bytes()).isEqualTo(FakeCarrierPort.DOCUMENT);
        assertThat(carrier.callsStartingWith("label")).hasSize(1);
        assertThat(carrier.callsStartingWith("register")).hasSize(1);
    }

    @Test
    void refusesALabelForAProcessingWithoutAParcel() {
        assertThatThrownBy(() -> labels.labelOf(ProcessingFixture.PROCESSING_ID.value()))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.PROCESSING_NOT_FOUND));
    }

    @Test
    void refusesALabelWithoutACarrierRatherThanServingAnEmptyDocument() {
        givenAParcel();
        ParcelLabelProjection unwired = ParcelFixture.labels(parcels, null);

        assertThatThrownBy(() -> unwired.labelOf(ProcessingFixture.PROCESSING_ID.value()))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.CARRIER_NOT_WIRED));
        assertThat(parcels.labels).isEmpty();
    }

    private void givenAParcel() {
        ParcelFixture.service(parcels, carrier)
                .register(ProcessingFixture.PROCESSING_ID, OrderFixture.aDelivery());
    }
}
