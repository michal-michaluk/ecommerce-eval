package shop.fulfillment.processing;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * The bulk packing step — {@code POST /processing/{processingId}/packing}: the picked positions that go into the box.
 *
 * <p>An empty request is refused by the framework as the invalid request it is, and a position that is not picked is
 * refused by the aggregate with {@code POSITION_NOT_PICKED} (element 02, element 11).
 */
record PackPositions(@NotEmpty List<String> productNumbers) {

    PackPositions {
        productNumbers = productNumbers == null ? null : List.copyOf(productNumbers);
    }
}
