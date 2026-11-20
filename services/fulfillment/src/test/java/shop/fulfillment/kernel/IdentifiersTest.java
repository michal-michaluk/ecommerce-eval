package shop.fulfillment.kernel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The identities of this context and the one reference a carrier never gives back: every one of them states its own format
 * and refuses anything else at the boundary.
 */
class IdentifiersTest {

    @Test
    void acceptsAProcessingIdOfTheDocumentedShape() {
        assertThat(new OrderProcessingId("OP-2026-0001").value()).isEqualTo("OP-2026-0001");
    }

    @Test
    void refusesAProcessingIdOfAnotherShape() {
        assertThatThrownBy(() -> new OrderProcessingId("OP-1"))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.PROCESSING_ID_MALFORMED));
    }

    @Test
    void refusesAMissingProcessingId() {
        assertThatThrownBy(() -> new OrderProcessingId(null)).isInstanceOf(FulfillmentException.class);
    }

    @Test
    void acceptsAnIssueIdOfTheDocumentedShape() {
        assertThat(new IssueId("ISS-2026-0031").value()).isEqualTo("ISS-2026-0031");
    }

    @Test
    void refusesAnIssueIdOfAnotherShape() {
        assertThatThrownBy(() -> new IssueId("ISS-31"))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.ISSUE_ID_MALFORMED));
    }

    @Test
    void acceptsAParcelIdOfTheDocumentedShape() {
        assertThat(new ParcelId("PCL-2026-0001").value()).isEqualTo("PCL-2026-0001");
    }

    @Test
    void refusesAParcelIdOfAnotherShape() {
        assertThatThrownBy(() -> new ParcelId("PCL-1"))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.PARCEL_ID_MALFORMED));
    }

    @Test
    void acceptsAParcelReferenceOfAtMostFiftyCharacters() {
        assertThat(new ParcelReference("PCL-REF-0001").value()).isEqualTo("PCL-REF-0001");
    }

    @Test
    void refusesAParcelReferenceLongerThanFiftyCharacters() {
        assertThatThrownBy(() -> new ParcelReference("R".repeat(51)))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.PARCEL_REFERENCE_MALFORMED));
    }

    @Test
    void refusesAnEmptyParcelReference() {
        assertThatThrownBy(() -> new ParcelReference(" ")).isInstanceOf(FulfillmentException.class);
    }

    @Test
    void acceptsTheFirstParcelOfOne() {
        assertThat(new ParcelSequence(1, 1)).isEqualTo(new ParcelSequence(1, 1));
    }

    @Test
    void refusesASequencePositionOutsideTheSequence() {
        assertThatThrownBy(() -> new ParcelSequence(2, 1))
                .isInstanceOfSatisfying(FulfillmentException.class, refused -> assertThat(refused.error())
                        .isEqualTo(FulfillmentException.Error.PARCEL_SEQUENCE_IMPOSSIBLE));
    }
}
