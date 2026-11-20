package shop.fulfillment.kernel;

/**
 * A warehouse location code — an opaque string used verbatim. This context never normalises it, never stores a
 * topology and never invents a zone, aisle or shelf model; the only thing it does with a code is compare it, because
 * the walk is ordered lexicographically by the only ordering key the warehouse gives us.
 */
public record LocationCode(String value) implements Comparable<LocationCode> {

    public LocationCode {
        if (value == null || value.isBlank()) {
            throw new FulfillmentException(FulfillmentException.Error.LOCATION_CODE_MALFORMED, value);
        }
    }

    @Override
    public int compareTo(LocationCode other) {
        return value.compareTo(other.value);
    }
}
