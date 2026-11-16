# External warehouse API (open-source, InvenTree)

The warehouse is an off-the-shelf system. This eval uses **InvenTree** (open-source inventory management, MIT) as the reference API;
its OpenAPI 3.0.3 document is vendored at `spec/business/inventree-openapi.json` (293 paths). You do **not** run it — you integrate
through a port and prove the fake.

## Endpoints this eval cares about (verbatim from the schema)

| Operation | Endpoint | Request / response |
|---|---|---|
| list stock items | `GET /api/stock/` | `limit` is **required**; filters include `part`, `location`, `available`, `in_stock`, `external`; response `PaginatedStockItemList` |
| list locations | `GET /api/stock/location/` | filters `parent`, `structural`, `external`, `depth`; response `PaginatedLocationList` |
| one stock item | `GET /api/stock/{id}/` | `StockItem` |
| move stock | `POST /api/stock/transfer/` | body `StockTransfer` |
| remove stock (issue) | `POST /api/stock/remove/` | body `StockRemove` |
| stock count | `POST /api/stock/count/` | body `StockCount` |
| issue a transfer order | `POST /api/order/transfer-order/{id}/issue/` | — |
| allocate a transfer order | `POST /api/order/transfer-order/{id}/allocate/` | — |

## The port the fulfillment context declares

The warehouse is a **provider outside every context**, so the consumer (`fulfillment`) declares the port in its own language and the
adapter translates. Design it in the vocabulary of the business material, not in InvenTree's:

```java
/** Consumer-side contract: the warehouse as the picking process needs it. */
public interface WarehouseStock {

    /** Where the goods are and how many are free, per warehouse location. */
    List<StockAtLocation> stockAt(ProductId productId);

    /** Short-term reservation of a quantity at a location, before the picker starts walking. */
    ReservationId reserve(ProductId productId, LocationCode location, int quantity);

    void release(ReservationId reservationId);

    /** Goods issue note: the goods physically leave the warehouse. */
    IssueNote issue(ReservationId reservationId, List<PickedItem> picked);
}
```

- `StockAtLocation(LocationCode code, int available, int reserved)`
- `ReservationId`, `IssueNote` — value objects of the consumer; `IssueNote` mirrors the reason the business material ships a
  `goods-issue-note.json` sample (document number, date, lines).
- Reservation ids are not an InvenTree concept: the HTTP adapter keeps the mapping in its own table/state, or uses
  `POST /api/stock/transfer/` with a hold location. Document your choice in the adapter's code comment, not in the port.

## Adapter rules

- One adapter class per protocol, package-private, `@Repository`-like role, behind the port.
- Translate only: no business rule, no retry policy invented in the adapter, no InvenTree type in any signature.
- The fake adapter lives with the tests and satisfies the same port; the service test proves the use case against it.
