package shop.fulfillment.kernel;

import java.util.List;

/**
 * Who the buyer is, for the documents the context prints: the name, the tax id when the buyer is a company, and the
 * invoicing address.
 *
 * <p>{@code nip} has a named absent state — a consumer order carries none — so it is the one optional component.
 */
public record Invoicing(String buyerName, String nip, Address address) {

    public Invoicing {
        if (buyerName == null || buyerName.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.ORDER_INCOMPLETE, List.of("invoicing.buyerName"));
        }
        if (nip != null && nip.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.ORDER_INCOMPLETE, List.of("invoicing.nip"));
        }
        if (address == null) {
            throw new FulfillmentException(FulfillmentException.Error.ORDER_INCOMPLETE, List.of("invoicing.address"));
        }
    }
}
