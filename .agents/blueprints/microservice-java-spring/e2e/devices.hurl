# ============================================================================
# Devices E2E — executable documentation
#
# Runs against {{BASE_URL}} (set via --variable, default in-cluster).
# Gets JWT from {{KEYCLOAK_URL}} (Keycloak service inside cluster).
#
# Usage:
#   devices-cli test e2e/devices.hurl
#   hurl --test --variable BASE_URL=http://localhost:8080 --variable KEYCLOAK_URL=http://localhost:8080 e2e/devices.hurl
# ============================================================================

# ── 1. Public health endpoint ──────────────────────────────────────────────
GET {{BASE_URL}}/actuator/health
HTTP 200
[Asserts]
jsonpath "$.status" == "UP"

# ── 2. Protected endpoints require auth ────────────────────────────────────
GET {{BASE_URL}}/devices/unknown-id
HTTP 401

PATCH {{BASE_URL}}/devices/e2e-noauth
Content-Type: application/json
{}
HTTP 401

GET {{BASE_URL}}/configs/interval-rules
HTTP 401

# ── 3. Get JWT from Keycloak ──────────────────────────────────────────────
POST {{KEYCLOAK_URL}}/realms/iot/protocol/openid-connect/token
[FormParams]
scope: openid
username: john
password: john
grant_type: password
client_id: iot-service
client_secret: secret
HTTP 200
[Captures]
access_token: jsonpath "$.access_token"
[Asserts]
jsonpath "$.access_token" exists

# ── 4. Public protocol endpoints (no auth needed) ──────────────────────────

# IoT16 boot notification
POST {{BASE_URL}}/protocols/iot16/bootnotification/e2e-iot16-device
Content-Type: application/json
{
  "chargePointVendor": "E2E-Vendor",
  "chargePointModel": "E2E-Model-16",
  "chargePointSerialNumber": "SN-E2E-16-001",
  "chargeBoxSerialNumber": "CBN-E2E-16-001",
  "firmwareVersion": "1.0.0"
}
HTTP 200
[Asserts]
jsonpath "$.status" exists
jsonpath "$.interval" exists

# IoT20 boot notification
POST {{BASE_URL}}/protocols/iot20/bootnotification/e2e-iot20-device
Content-Type: application/json
{
  "device": {
    "serialNumber": "SN-E2E-20-001",
    "model": "E2E-Model-20",
    "vendorName": "E2E-Vendor",
    "firmwareVersion": "2.0.0",
    "modem": {
      "iccid": "89345678901234567890",
      "imsi": "262011234567890"
    }
  },
  "reason": "PowerUp"
}
HTTP 200
[Asserts]
jsonpath "$.status" exists
jsonpath "$.interval" exists

# ── 5. Device CRUD ────────────────────────────────────────────────────────

# 5a. Create device via PATCH
PATCH {{BASE_URL}}/devices/e2e-crud-device
Authorization: Bearer {{access_token}}
Content-Type: application/json
{
  "ownership": {
    "operator": "E2E-Operator",
    "provider": "E2E-Provider"
  },
  "location": {
    "street": "Testowa",
    "houseNumber": "42",
    "city": "Warszawa",
    "postalCode": "00-001",
    "country": "POL",
    "coordinates": {
      "longitude": 21.0122,
      "latitude": 52.2297
    }
  },
  "openingHours": {
    "alwaysOpen": true
  },
  "settings": {
    "autoStart": true,
    "remoteControl": false,
    "showOnMap": true,
    "publicAccess": true
  }
}
HTTP 200
[Asserts]
jsonpath "$.deviceId" == "e2e-crud-device"
jsonpath "$.ownership.operator" == "E2E-Operator"
jsonpath "$.ownership.provider" == "E2E-Provider"
jsonpath "$.openingHours.alwaysOpen" == true
jsonpath "$.settings.autoStart" == true
jsonpath "$.settings.showOnMap" == true
jsonpath "$.visibility.roamingEnabled" == true
jsonpath "$.violations.operatorNotAssigned" == false
jsonpath "$.violations.providerNotAssigned" == false
jsonpath "$.violations.locationMissing" == false

# 5b. Read device details
GET {{BASE_URL}}/devices/e2e-crud-device
Authorization: Bearer {{access_token}}
HTTP 200
[Asserts]
jsonpath "$.deviceId" == "e2e-crud-device"
jsonpath "$.ownership.operator" == "E2E-Operator"
jsonpath "$.ownership.provider" == "E2E-Provider"

# 5c. Partial update (settings only)
PATCH {{BASE_URL}}/devices/e2e-crud-device
Authorization: Bearer {{access_token}}
Content-Type: application/json
{
  "settings": {
    "autoStart": false,
    "remoteControl": true
  }
}
HTTP 200
[Asserts]
jsonpath "$.settings.autoStart" == false
jsonpath "$.settings.remoteControl" == true
jsonpath "$.settings.showOnMap" == true
jsonpath "$.settings.publicAccess" == true

# 5d. Read pin list
GET {{BASE_URL}}/devices?page=0&size=50
Authorization: Bearer {{access_token}}
Accept: application/vnd.device.pin+json
HTTP 200
[Asserts]
jsonpath "$[*].deviceId" contains "e2e-crud-device"

# 5e. Read summary list
GET {{BASE_URL}}/devices?page=0&size=50
Authorization: Bearer {{access_token}}
Accept: application/vnd.device.summary+json
HTTP 200
[Asserts]
jsonpath "$.totalElements" >= 1
jsonpath "$.content[*].deviceId" contains "e2e-crud-device"

# ── 6. Boot info visible in device details ────────────────────────────────
GET {{BASE_URL}}/devices/e2e-iot20-device
Authorization: Bearer {{access_token}}
HTTP 200
[Asserts]
jsonpath "$.deviceId" == "e2e-iot20-device"
jsonpath "$.boot" exists
jsonpath "$.boot.protocol" == "IoT20"
jsonpath "$.boot.vendor" == "E2E-Vendor"
jsonpath "$.boot.model" == "E2E-Model-20"
jsonpath "$.boot.serial" == "SN-E2E-20-001"
jsonpath "$.boot.firmware" == "2.0.0"

GET {{BASE_URL}}/devices/e2e-iot16-device
Authorization: Bearer {{access_token}}
HTTP 200
[Asserts]
jsonpath "$.deviceId" == "e2e-iot16-device"
jsonpath "$.boot" exists
jsonpath "$.boot.protocol" == "IoT16"
jsonpath "$.boot.vendor" == "E2E-Vendor"
jsonpath "$.boot.model" == "E2E-Model-16"

# ── 7. Interval rules ─────────────────────────────────────────────────────

# 7a. GET default rules
GET {{BASE_URL}}/configs/interval-rules
Authorization: Bearer {{access_token}}
HTTP 200
[Asserts]
jsonpath "$.defSeconds" exists
jsonpath "$.byIds" exists
jsonpath "$.byModel" exists

# 7b. PUT updated rules
PUT {{BASE_URL}}/configs/interval-rules
Authorization: Bearer {{access_token}}
Content-Type: application/json
{
  "defSeconds": 3600,
  "byIds": [],
  "byModel": [
    {
      "seconds": 7200,
      "vendor": "E2E-Vendor",
      "model": "E2E-Model.*",
      "firmware": null
    }
  ]
}
HTTP 200

# 7c. Verify update
GET {{BASE_URL}}/configs/interval-rules
Authorization: Bearer {{access_token}}
HTTP 200
[Asserts]
jsonpath "$.defSeconds" == 3600
jsonpath "$.byModel[0].seconds" == 7200
jsonpath "$.byModel[0].vendor" == "E2E-Vendor"

# 7d. Restore defaults
PUT {{BASE_URL}}/configs/interval-rules
Authorization: Bearer {{access_token}}
Content-Type: application/json
{
  "defSeconds": 1800,
  "byIds": [],
  "byModel": []
}
HTTP 200
