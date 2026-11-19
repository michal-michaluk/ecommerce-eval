package shop.fulfillment.processing;

import lombok.Builder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import shop.fulfillment.kernel.FulfillmentException;
import shop.fulfillment.kernel.IssueNote;
import shop.fulfillment.kernel.LocationCode;
import shop.fulfillment.kernel.PickedItem;
import shop.fulfillment.kernel.ProductId;
import shop.fulfillment.kernel.ReservationId;
import shop.fulfillment.kernel.StockAtLocation;
import shop.fulfillment.kernel.StockChange;
import shop.fulfillment.kernel.StockChangePage;
import shop.fulfillment.kernel.StockCursor;
import shop.fulfillment.kernel.WarehouseStock;

import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * The one adapter behind {@link WarehouseStock}: InvenTree's HTTP surface translated into this context's vocabulary, and
 * nothing else (element 13 RULE-1, {@code docs/api/warehouse-api.md}:48-52).
 *
 * <p>It translates, it never decides: no retry, no backoff, no fallback quantity, no cached stock served as fresh, and a
 * warehouse failure surfaces as the failure it is (RULE-7). Every quantity the warehouse reports is a whole number of units
 * — a fractional one is refused instead of being rounded, because no rounding exists anywhere in this context.
 *
 * <p>Three facts about the vendor shape drive the translation, and each is a decision recorded in
 * {@code docs/phase-1-decisions.md}:
 *
 * <ul>
 *   <li>the port speaks a product number, the vendor addresses a part by its numeric primary key, and no documented
 *       endpoint searches parts by our number — so the number is the part key, and a non-numeric one is refused;
 *   <li>a reservation is an allocation against a transfer order, and the allocation rows are not returned by the allocate
 *       call — so {@link WarehouseReservationRepository} keeps the correlation this boundary needs (state S1);
 *   <li>free units are the vendor's {@code quantity} minus its {@code allocated}: two fields the warehouse reports
 *       independently, never one derived from the other (RULE-2).
 * </ul>
 *
 * <p>The goods issue note is numbered here, because the warehouse answers no document for a removal and the port returns
 * one: the note carries this boundary's reservation id (element 13 Q-13-2, default "optimistic note, remote confirmation
 * pending"). The removal is one HTTP call per picked item, and the warehouse is not in our transaction: a call that fails
 * after an earlier one succeeded leaves that earlier removal made, and no compensation is invented for it (RULE-7).
 */
class InvenTreeWarehouseClient implements WarehouseStock {

    private static final String STOCK = "/api/stock/";
    private static final String LOCATIONS = "/api/stock/location/";
    private static final String REMOVE_STOCK = "/api/stock/remove/";
    private static final String TRANSFER_ORDERS = "/api/order/transfer-order/";
    private static final String TRANSFER_ORDER_LINES = "/api/order/transfer-order-line/";
    private static final String TRANSFER_ORDER_ALLOCATIONS = "/api/order/transfer-order-allocation/";
    private static final String RESERVATION_DESCRIPTION = "fulfillment picking reservation";

    private final RestClient http;
    private final Settings settings;
    private final WarehouseReservationRepository reservations;
    private final Clock clock;

    InvenTreeWarehouseClient(RestClient http, Settings settings, WarehouseReservationRepository reservations,
                             Clock clock) {
        this.http = http;
        this.settings = settings;
        this.reservations = reservations;
        this.clock = clock;
    }

    @Override
    public List<StockAtLocation> stockAt(ProductId productId) {
        int partPk = partPkOf(productId);
        Map<Integer, String> locations = locationNames();
        Map<String, List<StockItemRow>> byLocation = new LinkedHashMap<>();
        stockItems(partPk, null)
                .forEach(item -> byLocation.computeIfAbsent(locationName(locations, item.location()),
                        code -> new ArrayList<>()).add(item));
        return byLocation.entrySet().stream()
                .map(entry -> atLocation(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(StockAtLocation::code))
                .toList();
    }

    @Override
    public ReservationId reserve(ProductId productId, LocationCode location, int quantity) {
        int partPk = partPkOf(productId);
        List<StockItemRow> items = stockItems(partPk, locationPkOf(location));
        ReservationId reservationId = new ReservationId("RSV-" + UUID.randomUUID());
        int orderPk = createTransferOrder(reservationId);
        int linePk = addTransferOrderLine(orderPk, partPk, quantity, reservationId);
        allocate(orderPk, allocationItems(linePk, items, quantity));
        reservations.record(new WarehouseReservation(
                reservationId, orderPk, allocationPks(orderPk), null, null));
        return reservationId;
    }

    @Override
    public void release(ReservationId reservationId) {
        reservations.find(reservationId).ifPresent(this::releaseAllocations);
    }

    @Override
    public IssueNote issue(ReservationId reservationId, List<PickedItem> picked) {
        WarehouseReservation reservation = reservations.find(reservationId)
                .orElseThrow(() -> new FulfillmentException(
                        FulfillmentException.Error.WAREHOUSE_RESERVATION_UNKNOWN, reservationId.value()));
        if (reservation.issued()) {
            return new IssueNote(reservation.issueDocument(), reservation.issueDate(), picked);
        }
        removeFromStock(picked);
        IssueNote note = new IssueNote(reservationId.value(), LocalDate.now(clock), picked);
        reservations.record(reservation.issuedAs(note.documentNumber(), note.date()));
        return note;
    }

    @Override
    public StockChangePage stockChanges(StockCursor cursor) {
        List<StockItemRow> changed = rowsChangedAfter(cursor);
        Map<Integer, String> locations = locationNames();
        String watermark = changed.isEmpty() ? cursor.value() : changed.get(changed.size() - 1).updated();
        return new StockChangePage(new StockCursor(watermark),
                changed.stream().map(item -> changeOf(item, locations)).toList());
    }

    /**
     * Pages back from the newest row until the watermark is reached, so a burst larger than one page is read whole: the
     * watermark only ever names a row that was actually returned, and nothing between the two reads is skipped.
     */
    private List<StockItemRow> rowsChangedAfter(StockCursor cursor) {
        List<StockItemRow> changed = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<StockItemRow> page = stockItemsUpdatedLast(offset);
            if (!collectUntilWatermark(page, cursor, changed) || page.size() < settings.pageSize()) {
                return changed;
            }
            offset = offset + page.size();
        }
    }

    /** Takes the rows of one page that are newer than the watermark; answers whether the whole page was taken. */
    private static boolean collectUntilWatermark(List<StockItemRow> page, StockCursor cursor,
                                                 List<StockItemRow> changed) {
        for (StockItemRow item : page) {
            if (!changedAfter(item, cursor)) {
                return false;
            }
            changed.add(item);
        }
        return true;
    }

    /**
     * Gives the units back by deleting the allocation rows that hold them. A reservation whose goods already left has no
     * allocation left to release, so a release after an issue is refused instead of attempted — the one thing this port
     * may never do (element 13, "release after an issue").
     */
    private void releaseAllocations(WarehouseReservation reservation) {
        if (reservation.issued()) {
            throw new FulfillmentException(
                    FulfillmentException.Error.WAREHOUSE_ALLOCATION_CONSUMED, reservation.reservationId().value());
        }
        reservation.allocationPks().forEach(this::deleteAllocation);
        reservations.forget(reservation.reservationId());
    }

    private void deleteAllocation(int allocationPk) {
        http.delete()
                .uri(uri(TRANSFER_ORDER_ALLOCATIONS + allocationPk + "/", Map.of()))
                .header(HttpHeaders.AUTHORIZATION, settings.authorization())
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Books every picked item out in the one removal request the vendor offers: one call is the closest thing to an
     * all-or-nothing removal this boundary can ask for, so a failure cannot leave half the goods issued with no note
     * (RULE-5). The units come off what the location physically holds, never off its free units — the allocation of this
     * very reservation is already taken out of those.
     */
    private void removeFromStock(List<PickedItem> picked) {
        List<StockAdjustment> adjustments = new ArrayList<>();
        picked.forEach(item -> adjustments.addAll(adjustmentsOf(
                stockItems(partPkOf(item.productNumber()), locationPkOf(item.location())), item.quantity())));
        post(uri(REMOVE_STOCK, Map.of()), new StockRemoveRequest(adjustments), Void.class);
    }

    private int createTransferOrder(ReservationId reservationId) {
        return post(uri(TRANSFER_ORDERS, Map.of()),
                new TransferOrderRequest(reservationId.value(), RESERVATION_DESCRIPTION),
                TransferOrderRow.class).pk();
    }

    private int addTransferOrderLine(int orderPk, int partPk, int quantity, ReservationId reservationId) {
        return post(uri(TRANSFER_ORDER_LINES, Map.of()),
                new TransferOrderLineRequest(orderPk, partPk, quantity(quantity), reservationId.value()),
                TransferOrderLineRow.class).pk();
    }

    private void allocate(int orderPk, List<AllocationItem> items) {
        post(uri(TRANSFER_ORDERS + orderPk + "/allocate/", Map.of()), new AllocationRequest(items), Void.class);
    }

    private List<Integer> allocationPks(int orderPk) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("limit", settings.pageSize());
        query.put("order", orderPk);
        return get(uri(TRANSFER_ORDER_ALLOCATIONS, query),
                new ParameterizedTypeReference<Page<AllocationRow>>() {
                }).results().stream()
                .map(AllocationRow::pk)
                .toList();
    }

    private List<StockItemRow> stockItems(int partPk, Integer locationPk) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("limit", settings.pageSize());
        query.put("part", partPk);
        Optional.ofNullable(locationPk).ifPresent(pk -> query.put("location", pk));
        return get(uri(STOCK, query), new ParameterizedTypeReference<Page<StockItemRow>>() {
        }).results();
    }

    private List<StockItemRow> stockItemsUpdatedLast(int offset) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("limit", settings.pageSize());
        query.put("ordering", "-updated");
        if (offset > 0) {
            query.put("offset", offset);
        }
        return get(uri(STOCK, query), new ParameterizedTypeReference<Page<StockItemRow>>() {
        }).results();
    }

    private Map<Integer, String> locationNames() {
        return get(uri(LOCATIONS, Map.of("limit", settings.pageSize())),
                new ParameterizedTypeReference<Page<LocationRow>>() {
                }).results().stream()
                .filter(location -> location.name() != null)
                .collect(LinkedHashMap::new, (names, location) -> names.put(location.pk(), location.name()),
                        Map::putAll);
    }

    private int locationPkOf(LocationCode location) {
        Optional<Integer> pk = locationNames().entrySet().stream()
                .filter(entry -> entry.getValue().equals(location.value()))
                .map(Map.Entry::getKey)
                .findFirst();
        return pk.orElseThrow(() -> new FulfillmentException(
                FulfillmentException.Error.LOCATION_CODE_MALFORMED, location.value()));
    }

    private static String locationName(Map<Integer, String> locations, Integer locationPk) {
        return Optional.ofNullable(locations.get(locationPk))
                .orElseThrow(() -> new FulfillmentException(
                        FulfillmentException.Error.LOCATION_CODE_MALFORMED, locationPk));
    }

    private static List<AllocationItem> allocationItems(int linePk, List<StockItemRow> items, int quantity) {
        return unitsTakenFrom(items, quantity, Units.FREE, "allocate").entrySet().stream()
                .map(taken -> new AllocationItem(linePk, taken.getKey(), quantity(taken.getValue())))
                .toList();
    }

    private static List<StockAdjustment> adjustmentsOf(List<StockItemRow> items, int quantity) {
        return unitsTakenFrom(items, quantity, Units.ON_HAND, "issue").entrySet().stream()
                .map(taken -> new StockAdjustment(taken.getKey(), quantity(taken.getValue())))
                .toList();
    }

    /**
     * How many units come off each stock item, in the order the warehouse listed them: the allocation and the removal walk
     * the same shelves the same way, so a reservation and its goods issue address the same stock items.
     *
     * <p>The two measures differ on purpose: an allocation may only take what is <em>free</em>, while a goods issue takes
     * what the shelf physically <em>holds</em> — the allocation of this very reservation is already subtracted from the
     * free units, and the goods leaving are exactly what it holds.
     */
    private static Map<Integer, Integer> unitsTakenFrom(List<StockItemRow> items, int quantity, Units units,
                                                       String operation) {
        Map<Integer, Integer> taken = new LinkedHashMap<>();
        int left = quantity;
        for (StockItemRow item : items) {
            int take = left == 0 ? 0 : Math.min(left, units.of(item));
            if (take > 0) {
                taken.put(item.pk(), take);
            }
            left = left - take;
        }
        ensureShelvesCover(left, quantity, operation);
        return taken;
    }

    /** Which number of a stock item a translation asks about. */
    private enum Units {

        /** What another consumer could still take: the shelf's quantity minus every allocation on it. */
        FREE,
        /** What the shelf physically holds — the units a goods issue books out. */
        ON_HAND;

        int of(StockItemRow item) {
            return this == FREE ? freeUnits(item) : units(item.quantity());
        }
    }

    private static void ensureShelvesCover(int left, int quantity, String operation) {
        if (left > 0) {
            throw new FulfillmentException(FulfillmentException.Error.WAREHOUSE_STOCK_SHORT, quantity, operation);
        }
    }

    private static StockAtLocation atLocation(String code, List<StockItemRow> items) {
        int quantity = units(items.stream().mapToDouble(StockItemRow::quantity).sum());
        int allocated = units(items.stream().mapToDouble(InvenTreeWarehouseClient::allocatedOf).sum());
        return new StockAtLocation(new LocationCode(code), quantity - allocated, allocated);
    }

    private static StockChange changeOf(StockItemRow item, Map<Integer, String> locations) {
        StockAtLocation stock = atLocation(locationName(locations, item.location()), List.of(item));
        return StockChange.builder()
                .productNumber(new ProductId(String.valueOf(item.part())))
                .location(stock.code())
                .available(stock.available())
                .reserved(stock.reserved())
                .observedAt(Instant.parse(item.updated()))
                .build();
    }

    private static boolean changedAfter(StockItemRow item, StockCursor cursor) {
        return item.updated() != null
                && Instant.parse(item.updated()).isAfter(Instant.parse(cursor.value()));
    }

    private static int freeUnits(StockItemRow item) {
        return units(item.quantity()) - units(allocatedOf(item));
    }

    private static double allocatedOf(StockItemRow item) {
        return Optional.ofNullable(item.allocated()).orElse(0d);
    }

    private static int units(double reported) {
        try {
            return BigDecimal.valueOf(reported).intValueExact();
        } catch (ArithmeticException fractional) {
            throw new FulfillmentException(
                    FulfillmentException.Error.WAREHOUSE_QUANTITY_FRACTIONAL, reported);
        }
    }

    private static int partPkOf(ProductId productId) {
        try {
            return Integer.parseInt(productId.value());
        } catch (NumberFormatException notANumber) {
            throw new FulfillmentException(
                    FulfillmentException.Error.PRODUCT_NUMBER_MALFORMED, productId.value());
        }
    }

    private static String quantity(int units) {
        return BigDecimal.valueOf(units).toPlainString();
    }

    private URI uri(String path, Map<String, ?> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(settings.url()).path(path);
        query.forEach((name, value) -> builder.queryParam(name, value));
        return builder.build().toUri();
    }

    private <T> T get(URI uri, ParameterizedTypeReference<T> type) {
        return http.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, settings.authorization())
                .retrieve()
                .body(type);
    }

    private <T> T post(URI uri, Object body, Class<T> type) {
        return http.post()
                .uri(uri)
                .contentType(APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, settings.authorization())
                .body(body)
                .retrieve()
                .body(type);
    }

    /**
     * What the deployment configures. The token comes from configuration and never from code, and the page size is the one
     * bound the vendor makes mandatory on every list endpoint.
     *
     * @param url      the InvenTree base URL, without the {@code /api} suffix
     * @param token    the API token this boundary authenticates with
     * @param pageSize how many rows one read asks for
     */
    record Settings(String url, String token, int pageSize) {

        static final String PREFIX = "fulfillment.warehouse";

        static Settings from(Environment configuration) {
            return new Settings(
                    configuration.getRequiredProperty(PREFIX + ".url"),
                    configuration.getRequiredProperty(PREFIX + ".token"),
                    configuration.getProperty(PREFIX + ".page-size", Integer.class, 100));
        }

        String authorization() {
            return "Token " + token;
        }
    }

    /** One page of the vendor's pagination envelope; only the rows are translated. */
    record Page<T>(int count, List<T> results) {
    }

    /** One stock item as the vendor reports it. */
    @Builder
    record StockItemRow(Integer pk, Integer part, double quantity, Double allocated, Integer location, String updated) {
    }

    /** One stock location as the vendor reports it: our code is its name. */
    record LocationRow(Integer pk, String name) {
    }

    /** The order a reservation is allocated against. */
    record TransferOrderRow(Integer pk, String reference) {
    }

    /** The line of that order which carries the reserved quantity. */
    record TransferOrderLineRow(Integer pk) {
    }

    /** One allocation row: the units the warehouse actually holds for us. */
    record AllocationRow(Integer pk, Integer order, Integer item, Double quantity) {
    }

    record TransferOrderRequest(String reference, String description) {
    }

    record TransferOrderLineRequest(Integer order, Integer part, String quantity, String reference) {
    }

    record AllocationRequest(List<AllocationItem> items) {
    }

    record AllocationItem(Integer lineItem, Integer stockItem, String quantity) {
    }

    record StockRemoveRequest(List<StockAdjustment> items) {
    }

    record StockAdjustment(Integer pk, String quantity) {
    }
}
