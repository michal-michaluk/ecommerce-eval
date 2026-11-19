package shop.fulfillment.kernel;

import java.time.LocalDate;
import java.util.List;

/**
 * The confirmation that goods left the warehouse for one reservation: its document number, its date and the lines that
 * were actually taken.
 *
 * <p>It is the only warehouse operation that changes what physically exists, and it is called once per reservation.
 */
public record IssueNote(String documentNumber, LocalDate date, List<PickedItem> lines) {

    public IssueNote {
        if (documentNumber == null || documentNumber.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "documentNumber");
        }
        if (lines == null || lines.isEmpty()) {
            throw new FulfillmentException(FulfillmentException.Error.COMMAND_VALUE_MISSING, "lines");
        }
        lines = List.copyOf(lines);
    }
}
