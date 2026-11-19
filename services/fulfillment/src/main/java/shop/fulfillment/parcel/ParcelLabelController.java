package shop.fulfillment.parcel;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_PDF_VALUE;

/**
 * The consignment note of element 06 on the wire: {@code GET …/documents/label} answers the carrier's document as
 * {@code application/pdf}, byte for byte what the carrier printed.
 *
 * <p>Nothing is rendered here. The bytes come from {@link ParcelLabelProjection}, which stores them on the first print and
 * serves the stored document afterwards, so a reprint never registers a parcel and never consumes a number (element 06
 * RULE-9). A parcel that does not exist yet answers a refusal through the one advice rather than an empty document.
 */
@RestController
@RequiredArgsConstructor
class ParcelLabelController {

    private final ParcelLabelProjection labels;

    @GetMapping(path = "/processing/{processingId}/documents/label", produces = APPLICATION_PDF_VALUE)
    byte[] label(@PathVariable String processingId) {
        return labels.labelOf(processingId).bytes();
    }
}
