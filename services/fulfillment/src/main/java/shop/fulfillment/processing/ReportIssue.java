package shop.fulfillment.processing;

import jakarta.validation.constraints.NotBlank;

/**
 * One warehouse reality gap as an employee reports it — {@code POST /processing/{processingId}/issues}.
 *
 * <p>The body carries the two facts the record needs and nothing else: the kind, which is a free-form code stored verbatim
 * (element 04 RULE-4 — the four named kinds never close the set), and the product the pick list tripped over. Who reported
 * it and when are not the caller's to choose: the reporter is the token's subject and the moment is the clock of the
 * handover into the record (element 04 RULE-2, RULE-5).
 */
record ReportIssue(@NotBlank String kind, @NotBlank String productNumber) {
}
