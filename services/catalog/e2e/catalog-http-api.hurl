# catalog — HTTP API (element 12), executable contract
#
# Four resource shapes, one edit endpoint per family:
#   /offers/{offerId}            — the offer itself: state (removal), publish history
#   /offers/{offerId}/draft      — description draft + media + availability
#   /offers/{offerId}/pricing    — prices and discounts
#   /offers/{offerId}/review     — review process
# POST = start / create · PATCH = edit · GET = read
#
# Self-contained: every fixture offer is created in-protocol, every variable this file reads is either captured here or handed
# in by the harness — the four tokens (author, reviewer, photographer, sales) and the run-relative dates. No request names a
# fixed date (12 D35), so the suite asserts the same things on any day it runs.
#
# Usage, from a booted service: hurl --test e2e/catalog-http-api.hurl \
#   --variable BASE_URL=http://localhost:18090 \
#   --variable author_token=<jwt> --variable reviewer_token=<jwt> \
#   --variable photographer_token=<jwt> --variable sales_token=<jwt> \
#   --variable availability_future=2026-06-01 --variable availability_past=2026-04-01 \
#   --variable price_from_future=2026-06-01T00:00:00Z --variable price_until_future=2026-07-01T00:00:00Z
# The harness that supplies all nine variables and the realm they come from: src/test/java/shop/catalog/e2e
# (`./gradlew e2eHurl`). Scopes: catalog-author, catalog-reviewer, catalog-photographer, catalog-sales.

# ══ Authentication ═════════════════════════════════════════════════════════════════════════════════════════════════════════════

# No token is 401, and the body is empty
GET {{BASE_URL}}/offers?page=0&size=20
HTTP 401
[Asserts]
body == ""

# No token on a write is 401 too, before the command is even read
POST {{BASE_URL}}/offers
Content-Type: application/json
{ "title": "x" }
HTTP 401

# A token this issuer never signed is 401
GET {{BASE_URL}}/offers?page=0&size=20
Authorization: Bearer invalid-token
HTTP 401

# ══ Offer ══════════════════════════════════════════════════════════════════════════════════════════════════════════════════════

# ContentAuthor creates the offer (title + identifiers, empty draft)
POST {{BASE_URL}}/offers
Authorization: Bearer {{author_token}}
Content-Type: application/json
{
  "title": "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423",
  "productNumber": "75423",
  "eanGtin": "5702017817705",
  "categoryPath": ["Dziecko", "Zabawki", "Klocki", "LEGO", "Zestawy", "Star Wars"]
}
HTTP 201
[Captures]
offer_id: jsonpath "$.offerId"
[Asserts]
jsonpath "$.offerId" matches "^OFR-[A-Z0-9]{8}$"
jsonpath "$.offerState" == "draft"
jsonpath "$.offerVersion" == null
header "Location" == "/offers/{{offer_id}}"

# The list is paged and carries the offer (D16, D31)
GET {{BASE_URL}}/offers?page=0&size=20
Authorization: Bearer {{author_token}}
HTTP 200
[Asserts]
jsonpath "$.items[0].offerId" == "{{offer_id}}"
jsonpath "$.page.number" == 0
jsonpath "$.page.size" == 20
jsonpath "$.page.totalElements" == 1

# The offer resource: state and current version, no version before the first publication
GET {{BASE_URL}}/offers/{{offer_id}}
Authorization: Bearer {{author_token}}
HTTP 200
[Asserts]
jsonpath "$.offerState" == "draft"
jsonpath "$.offerVersion" == null

# ══ Draft — description + media + availability ═════════════════════════════════════════════════════════════════════════════════

# Photographer adds media through the same draft PATCH — D13a grants the photographer the media component only
PATCH {{BASE_URL}}/offers/{{offer_id}}/draft
Authorization: Bearer {{photographer_token}}
Content-Type: application/json
{
  "media": {
    "add": [
      { "kind": "image", "format": "jpg", "position": 1, "primary": true,  "url": "https://cdn.example/M-01.jpg" },
      { "kind": "image", "format": "jpg", "position": 2, "primary": false, "url": "https://cdn.example/M-02.jpg" },
      { "kind": "video", "format": "mp4", "position": 3, "primary": false, "url": "https://cdn.example/M-03.mp4" }
    ]
  }
}
HTTP 200
[Asserts]
jsonpath "$.media[0].mediaId" == "M-01"
jsonpath "$.media[0].primary" == true
jsonpath "$.media[2].kind" == "video"

# Author edits the description draft — one PATCH for every content component (D13, D18); a complete draft reports nothing
PATCH {{BASE_URL}}/offers/{{offer_id}}/draft
Authorization: Bearer {{author_token}}
Content-Type: application/json
{
  "description": {
    "title": "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423",
    "identifiers": { "productNumber": "75423", "eanGtin": "5702017817705", "setName": "X-Wing Czerwona Piątka" },
    "brand": { "name": "LEGO", "verified": true },
    "condition": "NEW",
    "categoryPath": ["Dziecko", "Zabawki", "Klocki", "LEGO", "Zestawy", "Star Wars"],
    "parameters": [
      { "code": "condition",    "label": "Stan",                          "valueType": "text",      "value": "Nowy" },
      { "code": "childAge",     "label": "Wiek dziecka",                  "valueType": "rangeFrom", "from": 6, "unit": "lat" },
      { "code": "pieceCount",   "label": "Liczba elementów",              "valueType": "integer",   "value": 581 },
      { "code": "gender",       "label": "Płeć",                          "valueType": "enumSet",   "value": ["boys", "girls", "unisex"] },
      { "code": "certificates", "label": "Certyfikaty, opinie, atesty",    "valueType": "list",      "value": ["CE", "EN 71"] },
      { "code": "safetyInfo",   "label": "Informacje dot. bezpieczeństwa", "valueType": "text",      "value": "Nie nadaje się dla dzieci w wieku poniżej 36 miesięcy" }
    ],
    "blocks": [
      { "type": "paragraph", "text": "Odkryj nową galaktykę LEGO Star Wars dzięki interaktywnej zabawce konstrukcyjnej X-Wing Czerwona Piątka Luke'a. Ten zestaw Wszystko w jednym, będący częścią serii zestawów obsługiwanych przez system LEGO SMART Play, zawiera wszystkie elementy SMART Play: SMART klocek i ładowarkę, dwie SMART minifigurki LEGO oraz pięć SMART Tagów, które umożliwiają responsywną zabawę, dynamiczne efekty dźwiękowe i świetlne oraz wiele innych funkcji. Model myśliwca X-wing został odwzorowany z dbałością o każdy szczegół i zachowuje charakterystyczną sylwetkę maszyny znanej z filmów, dzięki czemu po złożeniu wygląda doskonale na półce każdego fana sagi." },
      { "type": "paragraph", "text": "Posłuchaj ryku silnika podczas lotu myśliwca X-wing. Aktywuj dźwięki strzałów laserowych X-winga i wieżyczki imperialnej. Monitoruj komunikację z centrum dowodzenia. Ożyw SMART Luke'a Skywalkera i SMART Księżniczkę Leię. Zatankuj i napraw X-winga za pomocą transportera. Zestaw zachęca do odgrywania scen z ulubionych filmów, a jednocześnie wspiera rozwój wyobraźni przestrzennej i zdolności manualnych dziecka. Wszystkie elementy są wykonane z trwałych, bezpiecznych materiałów, a instrukcja prowadzi krok po kroku przez proces budowy modelu." },
      { "type": "bulletList", "items": [
        "Interaktywna zabawka konstrukcyjna ożywia X-winga poprzez zabawę aktywowaną ruchem",
        "SMART klocek umożliwia korzystanie z systemu SMART Play ze światłami i dźwiękami",
        "Model emituje dźwięki i światła w reakcji na ruchy i zabawę dzieci"
      ] }
    ]
  }
}
HTTP 200
[Asserts]
jsonpath "$.description.title" == "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423"
jsonpath "$.description.brand.name" == "LEGO"
jsonpath "$.description.parameters[0].code" == "condition"
jsonpath "$.quality.findings" isEmpty

# Author sets the availability start (D35: a date the harness computes, never one fixed in this file)
PATCH {{BASE_URL}}/offers/{{offer_id}}/draft
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "availabilityStart": "{{availability_future}}" }
HTTP 200
[Asserts]
jsonpath "$.availabilityStart" == "{{availability_future}}"

# Read the draft — description, media, availability and the findings 08 reports right now (D14)
GET {{BASE_URL}}/offers/{{offer_id}}/draft
Authorization: Bearer {{author_token}}
HTTP 200
[Asserts]
jsonpath "$.description.parameters[*].code" contains "safetyInfo"
jsonpath "$.media[0].primary" == true
jsonpath "$.media[*].position" contains 3
jsonpath "$.availabilityStart" == "{{availability_future}}"
jsonpath "$.quality.findings" isEmpty

# ══ Pricing ════════════════════════════════════════════════════════════════════════════════════════════════════════════════════

# Sales sets the current price and a scheduled discount in one PATCH (D18)
PATCH {{BASE_URL}}/offers/{{offer_id}}/pricing
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{
  "prices": [ { "currency": "PLN", "amountMinor": 30989 } ],
  "discounts": [ { "kind": "percentage", "basisPoints": 1000, "validFrom": "{{price_from_future}}", "validUntil": "{{price_until_future}}" } ]
}
HTTP 200
[Captures]
started_price_id: jsonpath "$.current.priceId"
[Asserts]
jsonpath "$.current.amountMinor" == 30989
jsonpath "$.current.currency" == "PLN"
jsonpath "$.current.validUntil" == null
jsonpath "$.scheduled" isEmpty
jsonpath "$.discounts.scheduled[0].basisPoints" == 1000

# Sales schedules a future price through the same PATCH
PATCH {{BASE_URL}}/offers/{{offer_id}}/pricing
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "prices": [ { "currency": "PLN", "amountMinor": 29900, "validFrom": "{{price_from_future}}" } ] }
HTTP 200
[Captures]
scheduled_price_id: jsonpath "$.scheduled[0].priceId"
[Asserts]
jsonpath "$.scheduled[0].amountMinor" == 29900
jsonpath "$.scheduled[0].validFrom" == "{{price_from_future}}"

# Read pricing — what is in force, what starts later, what ended, and the discounts beside them
GET {{BASE_URL}}/offers/{{offer_id}}/pricing
Authorization: Bearer {{sales_token}}
HTTP 200
[Asserts]
jsonpath "$.current.amountMinor" == 30989
jsonpath "$.scheduled[*].amountMinor" contains 29900
jsonpath "$.history" isEmpty
jsonpath "$.discounts.active" isEmpty
jsonpath "$.discounts.scheduled[0].kind" == "percentage"

# Remove a scheduled entry through the same PATCH
PATCH {{BASE_URL}}/offers/{{offer_id}}/pricing
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "removePriceIds": ["{{scheduled_price_id}}"] }
HTTP 200
[Asserts]
jsonpath "$.scheduled" isEmpty
jsonpath "$.current.amountMinor" == 30989

# 409 — a price that already started can only be closed by a new entry, never removed (03 RULE-8)
PATCH {{BASE_URL}}/offers/{{offer_id}}/pricing
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "removePriceIds": ["{{started_price_id}}"] }
HTTP 409
[Asserts]
jsonpath "$.reason" == "PRICE_ALREADY_STARTED"

# Sales adds a discount that starts now, so it is active immediately
PATCH {{BASE_URL}}/offers/{{offer_id}}/pricing
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "discounts": [ { "kind": "percentage", "basisPoints": 500 } ] }
HTTP 200
[Captures]
started_discount_id: jsonpath "$.discounts.active[0].discountId"
[Asserts]
jsonpath "$.discounts.active[0].basisPoints" == 500

# 409 — a discount that already started cannot be removed either (03 RULE-8)
PATCH {{BASE_URL}}/offers/{{offer_id}}/pricing
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "removeDiscountIds": ["{{started_discount_id}}"] }
HTTP 409
[Asserts]
jsonpath "$.reason" == "DISCOUNT_ALREADY_STARTED"

# 409 — a discount bound to a currency this offer does not price (03 RULE-5)
PATCH {{BASE_URL}}/offers/{{offer_id}}/pricing
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{
  "discounts": [ { "kind": "fixed", "amountMinor": 2000, "currency": "USD", "validFrom": "{{price_from_future}}" } ]
}
HTTP 409
[Asserts]
jsonpath "$.reason" == "DISCOUNT_CURRENCY_NOT_PRICED"

# ══ Review ═════════════════════════════════════════════════════════════════════════════════════════════════════════════════════

# 409 — the offer never asked for a review, so there is none to decide
PATCH {{BASE_URL}}/offers/{{offer_id}}/review
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "decision": "accepted" }
HTTP 409
[Asserts]
jsonpath "$.reason" == "REVIEW_NOT_OPEN"

# Author starts the review (POST = start, freezes the candidate)
POST {{BASE_URL}}/offers/{{offer_id}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{}
HTTP 201
[Asserts]
jsonpath "$.reviewId" matches "^RV-[A-Z0-9]{8}$"
jsonpath "$.state" == "requested"
jsonpath "$.requestedBy" matches "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
jsonpath "$.findings" isEmpty
header "Location" == "/offers/{{offer_id}}/review"

# Reviewer reads the pending list of 15, served from the projection only
GET {{BASE_URL}}/reviews?page=0&size=20
Authorization: Bearer {{reviewer_token}}
HTTP 200
[Asserts]
jsonpath "$.items[0].offerId" == "{{offer_id}}"
jsonpath "$.items[0].findingsCount" == 0
jsonpath "$.items[0].waitingFor" matches "^PT[0-9]+[HMS]"
jsonpath "$.items[0].requestedBy" matches "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
jsonpath "$.items[1]" not exists
jsonpath "$.page.totalElements" == 1

# Reviewer reads the review — the frozen candidate, not the live draft (04 RULE-3)
GET {{BASE_URL}}/offers/{{offer_id}}/review
Authorization: Bearer {{reviewer_token}}
HTTP 200
[Asserts]
jsonpath "$.state" == "requested"
jsonpath "$.candidate.description.title" == "LEGO Star Wars SMART Play X-Wing Czerwona Piątka Luke'a 75423"
jsonpath "$.candidate.media[0].primary" == true
jsonpath "$.candidate.availabilityStart" == "{{availability_future}}"
jsonpath "$.override" == null

# Reviewer edits the review to accepted — the edit publishes (D15)
PATCH {{BASE_URL}}/offers/{{offer_id}}/review
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "decision": "accepted" }
HTTP 200
[Asserts]
jsonpath "$.state" == "accepted"
jsonpath "$.publishedOfferVersion" == 1
jsonpath "$.reviewedBy" matches "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
jsonpath "$.reason" == null

# The offer is live, version 1, with the availability start the draft carried
GET {{BASE_URL}}/offers/{{offer_id}}
Authorization: Bearer {{author_token}}
HTTP 200
[Asserts]
jsonpath "$.offerState" == "published"
jsonpath "$.offerVersion" == 1
jsonpath "$.availabilityStart" == "{{availability_future}}"

# Publish history — one frozen version, published by the reviewer (D31)
GET {{BASE_URL}}/offers/{{offer_id}}/versions?page=0&size=20
Authorization: Bearer {{author_token}}
HTTP 200
[Asserts]
jsonpath "$.items[0].offerVersion" == 1
jsonpath "$.items[0].publishedBy" matches "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
jsonpath "$.items[0].copiedFrom" == null
jsonpath "$.items[0].availabilityStart" == "{{availability_future}}"
jsonpath "$.page.totalElements" == 1

# Replaying the same decision is a no-op, not a second version (D15, 09 R6)
PATCH {{BASE_URL}}/offers/{{offer_id}}/review
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "decision": "accepted" }
HTTP 200
[Asserts]
jsonpath "$.publishedOfferVersion" == 1

# The author moves the availability start into the past — the live offer keeps serving version 1 (D37)
PATCH {{BASE_URL}}/offers/{{offer_id}}/draft
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "availabilityStart": "{{availability_past}}" }
HTTP 200
[Asserts]
jsonpath "$.availabilityStart" == "{{availability_past}}"
jsonpath "$.offerState" == "published"

# A second clean review publishes version 2 with the past availability start
POST {{BASE_URL}}/offers/{{offer_id}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{}
HTTP 201
[Asserts]
jsonpath "$.state" == "requested"

# Acceptance freezes the candidate the request captured
PATCH {{BASE_URL}}/offers/{{offer_id}}/review
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "decision": "accepted" }
HTTP 200
[Asserts]
jsonpath "$.publishedOfferVersion" == 2

# Both availability starts reached a version, so no assertion here depends on the run date
GET {{BASE_URL}}/offers/{{offer_id}}/versions?page=0&size=20
Authorization: Bearer {{reviewer_token}}
HTTP 200
[Asserts]
jsonpath "$.items[0].availabilityStart" == "{{availability_future}}"
jsonpath "$.items[1].availabilityStart" == "{{availability_past}}"
jsonpath "$.page.totalElements" == 2

# Revert creates a new published version from an older one (D19, POST = create)
POST {{BASE_URL}}/offers/{{offer_id}}/versions
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "copyFrom": 1 }
HTTP 201
[Asserts]
jsonpath "$.offerId" == "{{offer_id}}"
jsonpath "$.offerVersion" == 3
jsonpath "$.copiedFrom" == 1
header "Location" == "/offers/{{offer_id}}/versions/3"

# The reverted version carries the content it copied, availability start included
GET {{BASE_URL}}/offers/{{offer_id}}/versions?page=0&size=20
Authorization: Bearer {{author_token}}
HTTP 200
[Asserts]
jsonpath "$.items[2].offerVersion" == 3
jsonpath "$.items[2].copiedFrom" == 1
jsonpath "$.items[2].availabilityStart" == "{{availability_future}}"
jsonpath "$.page.totalElements" == 3

# A source version the offer does not have is a resource that does not exist
POST {{BASE_URL}}/offers/{{offer_id}}/versions
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "copyFrom": 9 }
HTTP 404

# ══ Removal ════════════════════════════════════════════════════════════════════════════════════════════════════════════════════

# Removal is an edit of the offer state, not of the draft (D13b)
PATCH {{BASE_URL}}/offers/{{offer_id}}
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "offerState": "removed" }
HTTP 200
[Asserts]
jsonpath "$.offerState" == "removed"
jsonpath "$.offerVersion" == 3

# Removal is terminal: nothing can be edited afterwards (Q11)
PATCH {{BASE_URL}}/offers/{{offer_id}}/draft
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "availabilityStart": "{{availability_past}}" }
HTTP 409
[Asserts]
jsonpath "$.reason" == "OFFER_REMOVED"

# ══ Fixtures and the state refusals they produce ═══════════════════════════════════════════════════════════════════════════════

# A second offer, never published, whose draft carries no media at all
POST {{BASE_URL}}/offers
Authorization: Bearer {{author_token}}
Content-Type: application/json
{
  "title": "LEGO Technic McLaren P1 42172",
  "productNumber": "75424",
  "eanGtin": "5702017817712",
  "categoryPath": ["Dziecko", "Zabawki", "Klocki", "LEGO", "Technic"]
}
HTTP 201
[Captures]
offer_without_media: jsonpath "$.offerId"

# 409 — a review needs one primary image before it can even be requested (02 RULE-1, 12 D49)
POST {{BASE_URL}}/offers/{{offer_without_media}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{}
HTTP 409
[Asserts]
jsonpath "$.reason" == "MEDIA_IMAGE_MISSING"

# 409 — removal needs a published offer (02 RULE-6)
PATCH {{BASE_URL}}/offers/{{offer_without_media}}
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "offerState": "removed" }
HTTP 409
[Asserts]
jsonpath "$.reason" == "REMOVAL_NEEDS_PUBLICATION"

# Two images, both primary
POST {{BASE_URL}}/offers
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "title": "LEGO Icons Zamek 10305", "productNumber": "75425", "eanGtin": "5702017817729", "categoryPath": ["Dziecko", "Klocki", "LEGO"] }
HTTP 201
[Captures]
offer_primary_ambiguous: jsonpath "$.offerId"

PATCH {{BASE_URL}}/offers/{{offer_primary_ambiguous}}/draft
Authorization: Bearer {{photographer_token}}
Content-Type: application/json
{
  "media": { "add": [
    { "kind": "image", "format": "jpg", "position": 1, "primary": true, "url": "https://cdn.example/M-11.jpg" },
    { "kind": "image", "format": "jpg", "position": 2, "primary": true, "url": "https://cdn.example/M-12.jpg" }
  ] }
}
HTTP 200

# 409 — exactly one primary image is the price of entering a review (02 RULE-2)
POST {{BASE_URL}}/offers/{{offer_primary_ambiguous}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{}
HTTP 409
[Asserts]
jsonpath "$.reason" == "MEDIA_PRIMARY_AMBIGUOUS"

# Media positions that do not start at 1
POST {{BASE_URL}}/offers
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "title": "LEGO City Port 60391", "productNumber": "75426", "eanGtin": "5702017817736", "categoryPath": ["Dziecko", "Klocki", "LEGO"] }
HTTP 201
[Captures]
offer_position_gap: jsonpath "$.offerId"

PATCH {{BASE_URL}}/offers/{{offer_position_gap}}/draft
Authorization: Bearer {{photographer_token}}
Content-Type: application/json
{
  "media": { "add": [
    { "kind": "image", "format": "jpg", "position": 2, "primary": true, "url": "https://cdn.example/M-21.jpg" }
  ] }
}
HTTP 200

# 409 — positions must be contiguous from 1 (02 RULE-2)
POST {{BASE_URL}}/offers/{{offer_position_gap}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{}
HTTP 409
[Asserts]
jsonpath "$.reason" == "MEDIA_POSITIONS_NOT_CONTIGUOUS"

# Two videos on one offer
POST {{BASE_URL}}/offers
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "title": "LEGO Friends Dom na plaży 41709", "productNumber": "75427", "eanGtin": "5702017817743", "categoryPath": ["Dziecko", "Klocki", "LEGO"] }
HTTP 201
[Captures]
offer_two_videos: jsonpath "$.offerId"

PATCH {{BASE_URL}}/offers/{{offer_two_videos}}/draft
Authorization: Bearer {{photographer_token}}
Content-Type: application/json
{
  "media": { "add": [
    { "kind": "image", "format": "jpg", "position": 1, "primary": true,  "url": "https://cdn.example/M-31.jpg" },
    { "kind": "video", "format": "mp4", "position": 2, "primary": false, "url": "https://cdn.example/M-32.mp4" },
    { "kind": "video", "format": "mp4", "position": 3, "primary": false, "url": "https://cdn.example/M-33.mp4" }
  ] }
}
HTTP 200

# 409 — one video at most (02 RULE-2)
POST {{BASE_URL}}/offers/{{offer_two_videos}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{}
HTTP 409
[Asserts]
jsonpath "$.reason" == "MEDIA_VIDEO_COUNT"

# An offer whose media can be published and whose availability start is never scheduled
POST {{BASE_URL}}/offers
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "title": "LEGO Creator Bukiet 10280", "productNumber": "75428", "eanGtin": "5702017817750", "categoryPath": ["Dziecko", "Klocki", "LEGO"] }
HTTP 201
[Captures]
offer_no_availability: jsonpath "$.offerId"

PATCH {{BASE_URL}}/offers/{{offer_no_availability}}/draft
Authorization: Bearer {{photographer_token}}
Content-Type: application/json
{
  "media": { "add": [
    { "kind": "image", "format": "jpg", "position": 1, "primary": true, "url": "https://cdn.example/M-41.jpg" }
  ] }
}
HTTP 200

# 409 — a candidate carries an availability start, so one has to be scheduled (02 RULE-3)
POST {{BASE_URL}}/offers/{{offer_no_availability}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{}
HTTP 409
[Asserts]
jsonpath "$.reason" == "AVAILABILITY_START_MISSING"

# A draft with one blocking finding (QP-6, prose) and a publishable media set
POST {{BASE_URL}}/offers
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "title": "LEGO Ninjago Smoczy Mech 71794", "productNumber": "75429", "eanGtin": "5702017817767", "categoryPath": ["Dziecko"] }
HTTP 201
[Captures]
offer_with_findings: jsonpath "$.offerId"

PATCH {{BASE_URL}}/offers/{{offer_with_findings}}/draft
Authorization: Bearer {{photographer_token}}
Content-Type: application/json
{
  "media": { "add": [
    { "kind": "image", "format": "jpg", "position": 1, "primary": true, "url": "https://cdn.example/M-51.jpg" }
  ] }
}
HTTP 200

PATCH {{BASE_URL}}/offers/{{offer_with_findings}}/draft
Authorization: Bearer {{author_token}}
Content-Type: application/json
{
  "description": {
    "title": "LEGO Ninjago Smoczy Mech 71794",
    "identifiers": { "productNumber": "75429", "eanGtin": "5702017817767" },
    "brand": { "name": "LEGO", "verified": true },
    "condition": "NEW",
    "categoryPath": ["Dziecko"],
    "blocks": [ { "type": "paragraph", "text": "Krótki opis, który nie osiąga progu prozy z 08." } ]
  },
  "availabilityStart": "{{availability_future}}"
}
HTTP 200
[Asserts]
jsonpath "$.quality.findings[0].rule" == "QP-6"
jsonpath "$.quality.findings[0].severity" == "BLOCKING"

# 409 — blocking findings cannot be reviewed without the author's override (04 RULE-8, D27)
POST {{BASE_URL}}/offers/{{offer_with_findings}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{}
HTTP 409
[Asserts]
jsonpath "$.reason" == "FINDINGS_NEED_OVERRIDE"

# 201 — the author pushes the review past the gate with an explicit override (04 RULE-8)
POST {{BASE_URL}}/offers/{{offer_with_findings}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "override": { "findings": ["QP-6"] } }
HTTP 201
[Asserts]
jsonpath "$.state" == "requested"
jsonpath "$.override.findings[0]" == "QP-6"
jsonpath "$.override.actor" matches "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"

# 409 — at most one review is open per offer (04 RULE-1)
POST {{BASE_URL}}/offers/{{offer_with_findings}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{}
HTTP 409
[Asserts]
jsonpath "$.reason" == "REVIEW_ALREADY_OPEN"

# 409 — only the requester withdraws, and a reviewer is not the requester (04 RULE-6, D50)
PATCH {{BASE_URL}}/offers/{{offer_with_findings}}/review
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "decision": "withdrawn" }
HTTP 409
[Asserts]
jsonpath "$.reason" == "WITHDRAWAL_NEEDS_REQUESTER"

# The author takes the request back
PATCH {{BASE_URL}}/offers/{{offer_with_findings}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "decision": "withdrawn" }
HTTP 200
[Asserts]
jsonpath "$.state" == "withdrawn"

# A fresh attempt is a new review, with the override again
POST {{BASE_URL}}/offers/{{offer_with_findings}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "override": { "findings": ["QP-6"] } }
HTTP 201
[Asserts]
jsonpath "$.state" == "requested"

# 409 — acceptance is refused while 09 sees a blocking finding, override or not (09 R2, D27)
PATCH {{BASE_URL}}/offers/{{offer_with_findings}}/review
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "decision": "accepted" }
HTTP 409
[Asserts]
jsonpath "$.reason" == "QUALITY_FINDINGS"
jsonpath "$.findings[0].rule" == "QP-6"

# 409 — the review of that offer is decided, so no further decision is left
PATCH {{BASE_URL}}/offers/{{offer_with_findings}}/review
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "decision": "accepted" }
HTTP 409
[Asserts]
jsonpath "$.reason" == "REVIEW_NOT_OPEN"

# ══ Invalid commands — 400 ══════════════════════════════════════════════════════════════════════════════════════════════════════

# 400 — a GTIN-13 with a wrong check digit is a broken command, not a refused one
POST {{BASE_URL}}/offers
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "title": "LEGO", "productNumber": "1", "eanGtin": "5702017817706", "categoryPath": ["Dziecko"] }
HTTP 400
[Asserts]
jsonpath "$.errors[0].field" == "eanGtin"

# 400 — an unknown draft component is named, never silently dropped (D18)
PATCH {{BASE_URL}}/offers/{{offer_without_media}}/draft
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "titlee": "typo" }
HTTP 400
[Asserts]
jsonpath "$.errors[0].field" == "titlee"

# 400 — an unknown offer component is named too
PATCH {{BASE_URL}}/offers/{{offer_without_media}}
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "offerState": "removed", "publishNow": true }
HTTP 400
[Asserts]
jsonpath "$.errors[0].field" == "publishNow"

# 400 — removal is the only state a client may name
PATCH {{BASE_URL}}/offers/{{offer_without_media}}
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "offerState": "deleted" }
HTTP 400
[Asserts]
jsonpath "$.errors[0].field" == "offerState"

# 400 — a discount whose basisPoints are outside the 1..10000 the family states (12's 400 row)
PATCH {{BASE_URL}}/offers/{{offer_id}}/pricing
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "discounts": [ { "kind": "percentage", "basisPoints": 20000 } ] }
HTTP 400
[Asserts]
jsonpath "$.errors[0].field" == "discounts[0].complete"

# 400 — a decision without the decision is an invalid command record
PATCH {{BASE_URL}}/offers/{{offer_with_findings}}/review
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "reason": "brak decyzji" }
HTTP 400
[Asserts]
jsonpath "$.errors[0].field" == "decision"

# 400 — a rejection without a reason is an invalid command record (04 RULE-5)
PATCH {{BASE_URL}}/offers/{{offer_with_findings}}/review
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "decision": "rejected" }
HTTP 400
[Asserts]
jsonpath "$.errors[0].field" == "reasonGiven"

# 400 — unreadable JSON never reaches a handler; it names no field, because no component could be read
POST {{BASE_URL}}/offers
Authorization: Bearer {{author_token}}
Content-Type: application/json
`{ "title": "LEGO",`
HTTP 400
[Asserts]
jsonpath "$.errors" isEmpty

# 400 — paging is required, both parameters (D16)
GET {{BASE_URL}}/reviews
Authorization: Bearer {{reviewer_token}}
HTTP 400

# 400 — the offer list needs them too
GET {{BASE_URL}}/offers
Authorization: Bearer {{author_token}}
HTTP 400

# 400 — so does the publish history
GET {{BASE_URL}}/offers/{{offer_without_media}}/versions
Authorization: Bearer {{author_token}}
HTTP 400

# 400 — a page below zero is outside the bound D16 states
GET {{BASE_URL}}/offers?page=-1&size=20
Authorization: Bearer {{author_token}}
HTTP 400

# 400 — and so is a size above the maximum, so no client can ask for the whole table
GET {{BASE_URL}}/reviews?page=0&size=101
Authorization: Bearer {{reviewer_token}}
HTTP 400

# ══ Authorization — 403 on the route matrix of 12 ══════════════════════════════════════════════════════════════════════════════

# 403 — only the author creates an offer
POST {{BASE_URL}}/offers
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "title": "x", "productNumber": "75430" }
HTTP 403

# 403 — the photographer reads neither the list nor the offer; a missing scope carries no body
GET {{BASE_URL}}/offers?page=0&size=20
Authorization: Bearer {{photographer_token}}
HTTP 403
[Asserts]
body == ""

# 403 — sales does not read the offer
GET {{BASE_URL}}/offers/{{offer_without_media}}
Authorization: Bearer {{sales_token}}
HTTP 403

# 403 — removal is not the photographer's component
PATCH {{BASE_URL}}/offers/{{offer_without_media}}
Authorization: Bearer {{photographer_token}}
Content-Type: application/json
{ "offerState": "removed" }
HTTP 403

# 403 — sales does not read the draft
GET {{BASE_URL}}/offers/{{offer_without_media}}/draft
Authorization: Bearer {{sales_token}}
HTTP 403

# 403 — sales does not write the draft
PATCH {{BASE_URL}}/offers/{{offer_without_media}}/draft
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "availabilityStart": "{{availability_future}}" }
HTTP 403

# 403 — field-level scope: the photographer may not edit the description (D13a)
PATCH {{BASE_URL}}/offers/{{offer_without_media}}/draft
Authorization: Bearer {{photographer_token}}
Content-Type: application/json
{ "description": { "title": "x" } }
HTTP 403
[Asserts]
jsonpath "$.reason" == "FIELD_NOT_IN_SCOPE"

# 403 — field-level scope: the author may not touch media, and gets the same reason
PATCH {{BASE_URL}}/offers/{{offer_without_media}}/draft
Authorization: Bearer {{author_token}}
Content-Type: application/json
{
  "media": { "add": [ { "kind": "image", "format": "jpg", "position": 1, "primary": true, "url": "https://cdn.example/M-61.jpg" } ] }
}
HTTP 403
[Asserts]
jsonpath "$.reason" == "FIELD_NOT_IN_SCOPE"

# 403 — field-level scope is decided before the offer is loaded, so an unknown offer is still 403 and not 404
PATCH {{BASE_URL}}/offers/OFR-00000000/draft
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "media": { "add": [] } }
HTTP 403
[Asserts]
jsonpath "$.reason" == "FIELD_NOT_IN_SCOPE"

# 403 — the photographer does not read the publish history
GET {{BASE_URL}}/offers/{{offer_without_media}}/versions?page=0&size=20
Authorization: Bearer {{photographer_token}}
HTTP 403

# 403 — only the reviewer reverts
POST {{BASE_URL}}/offers/{{offer_without_media}}/versions
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "copyFrom": 1 }
HTTP 403

# 403 — only sales reads pricing
GET {{BASE_URL}}/offers/{{offer_without_media}}/pricing
Authorization: Bearer {{author_token}}
HTTP 403

# 403 — and only sales edits it
PATCH {{BASE_URL}}/offers/{{offer_without_media}}/pricing
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "prices": [ { "currency": "PLN", "amountMinor": 100 } ] }
HTTP 403

# 403 — only the author starts a review
POST {{BASE_URL}}/offers/{{offer_without_media}}/review
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{}
HTTP 403

# 403 — the review is read by the two content roles, not by sales
GET {{BASE_URL}}/offers/{{offer_without_media}}/review
Authorization: Bearer {{sales_token}}
HTTP 403

# 403 — sales does not decide a review either
PATCH {{BASE_URL}}/offers/{{offer_without_media}}/review
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "decision": "accepted" }
HTTP 403

# 403 — an author may read a review but never accept one (12's review rule)
PATCH {{BASE_URL}}/offers/{{offer_without_media}}/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "decision": "accepted" }
HTTP 403

# 403 — the pending list is the reviewer's screen
GET {{BASE_URL}}/reviews?page=0&size=20
Authorization: Bearer {{author_token}}
HTTP 403

# 403 — and no other role reads it
GET {{BASE_URL}}/reviews?page=0&size=20
Authorization: Bearer {{photographer_token}}
HTTP 403

# ══ Unknown resources — 404 ═════════════════════════════════════════════════════════════════════════════════════════════════════

# 404 — an unknown offer, with no body at all
GET {{BASE_URL}}/offers/OFR-00000000
Authorization: Bearer {{author_token}}
HTTP 404
[Asserts]
body == ""

# 404 — an unknown draft
GET {{BASE_URL}}/offers/OFR-00000000/draft
Authorization: Bearer {{author_token}}
HTTP 404

# 404 — an unknown pricing model
GET {{BASE_URL}}/offers/OFR-00000000/pricing
Authorization: Bearer {{sales_token}}
HTTP 404

# 404 — an unknown review
GET {{BASE_URL}}/offers/OFR-00000000/review
Authorization: Bearer {{reviewer_token}}
HTTP 404

# 404 — a pricing edit on an unknown offer opens no model
PATCH {{BASE_URL}}/offers/OFR-00000000/pricing
Authorization: Bearer {{sales_token}}
Content-Type: application/json
{ "prices": [ { "currency": "PLN", "amountMinor": 100 } ] }
HTTP 404

# 404 — a draft edit on an unknown offer
PATCH {{BASE_URL}}/offers/OFR-00000000/draft
Authorization: Bearer {{author_token}}
Content-Type: application/json
{ "availabilityStart": "{{availability_future}}" }
HTTP 404

# 404 — a decision on an unknown offer
PATCH {{BASE_URL}}/offers/OFR-00000000/review
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "decision": "accepted" }
HTTP 404

# 404 — a removal of an unknown offer
PATCH {{BASE_URL}}/offers/OFR-00000000
Authorization: Bearer {{reviewer_token}}
Content-Type: application/json
{ "offerState": "removed" }
HTTP 404

# 404 — a review request over an unknown offer
POST {{BASE_URL}}/offers/OFR-00000000/review
Authorization: Bearer {{author_token}}
Content-Type: application/json
{}
HTTP 404

# ══ The roles the matrix allows, outside the flow above ═════════════════════════════════════════════════════════════════════════

# 200 — the reviewer reads the offer resource too, not only its history
GET {{BASE_URL}}/offers/{{offer_id}}
Authorization: Bearer {{reviewer_token}}
HTTP 200
[Asserts]
jsonpath "$.offerState" == "removed"

# 200 — the reviewer reads the draft (12: author, reviewer, photographer)
GET {{BASE_URL}}/offers/{{offer_without_media}}/draft
Authorization: Bearer {{reviewer_token}}
HTTP 200
[Asserts]
jsonpath "$.offerId" == "{{offer_without_media}}"
jsonpath "$.quality.findings[*].rule" contains "QP-4"

# 200 — and so does the photographer
GET {{BASE_URL}}/offers/{{offer_without_media}}/draft
Authorization: Bearer {{photographer_token}}
HTTP 200
[Asserts]
jsonpath "$.media" isEmpty

# 200 — the author reads the review of the offer it asked for
GET {{BASE_URL}}/offers/{{offer_id}}/review
Authorization: Bearer {{author_token}}
HTTP 200
[Asserts]
jsonpath "$.state" == "accepted"
jsonpath "$.publishedOfferVersion" == 2
