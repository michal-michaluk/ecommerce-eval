package shop.fulfillment;

/**
 * The handovers element 12 delivers in the route tests, each one a payload the contract has an answer for: the finished
 * order, and the four ways an order arrives and cannot be read.
 */
public final class HandoverJson {

    private static final String HEAD = """
            {"orderHash":"sha256:1f3c9d0a","placedAt":"2026-11-19T09:31:12Z","lines":""";
    private static final String TAIL = """
            ,"delivery":{"methodCode":"PARCEL_LOCKER","location":{"kind":"PICKUP_POINT","pointId":"KRA01M"}},
             "payment":{"methodCode":"PAY_DEFERRED"},
             "invoicing":{"buyerName":"Jan Kowalski","nip":null,
                          "address":{"street":"ul. Mineralna 5A","city":"Kraków","postalCode":"30-001","country":"PL"}}}""";

    private HandoverJson() {
    }

    public static String complete() {
        return """
                {
                  "orderHash": "sha256:1f3c9d0a",
                  "placedAt": "2026-11-19T09:31:12Z",
                  "lines": [{ "productNumber": "75423", "name": "LEGO X-Wing 75423", "quantity": 1 }],
                  "delivery": { "methodCode": "PARCEL_LOCKER",
                                "location": { "kind": "PICKUP_POINT", "pointId": "KRA01M" } },
                  "payment": { "methodCode": "PAY_DEFERRED" },
                  "invoicing": { "buyerName": "Jan Kowalski", "nip": null,
                                 "address": { "street": "ul. Mineralna 5A", "city": "Kraków",
                                              "postalCode": "30-001", "country": "PL" } }
                }""";
    }

    public static String incomplete() {
        return """
                {"orderHash":"sha256:bad","placedAt":"2026-11-19T09:31:12Z","lines":[]}""";
    }

    public static String pickupPointWithoutPointId() {
        return """
                {"orderHash":"sha256:no-point","placedAt":"2026-11-19T09:31:12Z",
                 "lines":[{"productNumber":"75423","name":"LEGO X-Wing 75423","quantity":1}],
                 "delivery":{"methodCode":"PARCEL_LOCKER","location":{"kind":"PICKUP_POINT","pointId":null}},
                 "payment":{"methodCode":"PAY_DEFERRED"},
                 "invoicing":{"buyerName":"Jan Kowalski","nip":null,
                              "address":{"street":"ul. Mineralna 5A","city":"Kraków","postalCode":"30-001","country":"PL"}}}""";
    }

    public static String lineWithoutQuantity() {
        return HEAD + """
                [{"productNumber":"75423","name":"LEGO X-Wing 75423","quantity":0}]""" + TAIL;
    }

    public static String lineWithoutProductNumber() {
        return HEAD + """
                [{"productNumber":" ","name":"LEGO X-Wing 75423","quantity":1}]""" + TAIL;
    }

    public static String duplicatedLine() {
        return HEAD + """
                [{"productNumber":"75423","name":"LEGO X-Wing 75423","quantity":1},
                  {"productNumber":"75423","name":"LEGO X-Wing 75423","quantity":2}]""" + TAIL;
    }

    public static String sameHashOtherQuantity() {
        return HEAD + """
                [{"productNumber":"75423","name":"LEGO X-Wing 75423","quantity":2}]""" + TAIL;
    }
}
