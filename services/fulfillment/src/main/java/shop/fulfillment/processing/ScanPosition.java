package shop.fulfillment.processing;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * One scan of one position — {@code POST /processing/{processingId}/positions/{productNumber}}.
 *
 * <p>The body names what the scan confirms, and the only confirmation the contract has is {@code picked} (one scan = one
 * pick, the bulk {@code packing} command is the second step; element 16 Q-16-1 default R-A). The product is the path, never
 * the body ({@code protocol.identity-from-path}), so a scan cannot confirm a position it was not routed to.
 */
record ScanPosition(@NotNull @Pattern(regexp = "picked") String confirmation) {
}
