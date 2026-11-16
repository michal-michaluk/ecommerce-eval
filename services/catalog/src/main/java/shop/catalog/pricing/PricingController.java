package shop.catalog.pricing;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * 12's `prices and discounts` resource: one read and one edit per offer, over the pricing context's primary port.
 *
 * <p>The edit is the whole of D18 — {@code prices}, {@code discounts}, {@code removePriceIds} and {@code removeDiscountIds} in one
 * command, one transaction — and its answer is the model the edit left behind, exactly as the read answers it (D14).
 *
 * <p>The clock is injected for one reason: {@code current}, {@code scheduled}, {@code history} and the three discount lists are
 * statements about now, and the model states the ranges while only the prices arrive already split by the aggregate. This adapter
 * therefore orders and renames; it decides no pricing rule.
 *
 * <p>12's read describes one currency ({@code current} is a single entry) while 03 D2 states the currency per entry: the aggregate keeps
 * one open entry per currency, so {@code current} names the first of them and every entry carries its own currency. A per-currency
 * read is 16's screen, not this route.
 */
@RestController
@RequiredArgsConstructor
class PricingController {

    private final PricingService pricing;

    private final Clock clock;

    @GetMapping(path = "/offers/{offerId}/pricing", produces = APPLICATION_JSON_VALUE)
    Pricing pricing(@PathVariable String offerId) {
        return pricing.modelOf(offerId)
                .map(model -> Pricing.of(model, Instant.now(clock)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PatchMapping(path = "/offers/{offerId}/pricing", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    Pricing editPricing(@PathVariable String offerId, @RequestBody @Valid PricingEdits edits) {
        return Pricing.of(pricing.edit(offerId, edits), Instant.now(clock));
    }

    /** The pricing resource as 12 answers it: what is in force, what starts later, what ended, and the discounts beside them. */
    record Pricing(PriceItem current, List<PriceItem> scheduled, List<PriceItem> history, Discounts discounts) {

        static Pricing of(PricingModel model, Instant now) {
            return new Pricing(openEntryOf(model),
                    ordered(model.scheduled(), Comparator.comparing(PriceItem::validFrom)),
                    ordered(model.history(), Comparator.comparing(PriceItem::validFrom).reversed()),
                    Discounts.of(model.discounts(), now));
        }

        private static PriceItem openEntryOf(PricingModel model) {
            return model.current().stream()
                    .min(Comparator.comparing(entry -> entry.price().currency()))
                    .map(PriceItem::of)
                    .orElse(null);
        }

        private static List<PriceItem> ordered(List<PriceEntry> entries, Comparator<PriceItem> order) {
            return entries.stream().map(PriceItem::of).sorted(order).toList();
        }

        /** One price entry: the identity a sales person quotes, the amount in minor units (D1) and the half-open range. */
        record PriceItem(String priceId, int amountMinor, String currency, Instant validFrom, Instant validUntil) {

            static PriceItem of(PriceEntry entry) {
                return new PriceItem(entry.priceId().value(), entry.price().amountMinor(), entry.price().currency(),
                        entry.validity().validFrom(), entry.validity().validUntil());
            }
        }

        /** The three discount lists of 16 — in force, upcoming and expired — split by the clock, never stored. */
        record Discounts(List<DiscountItem> active, List<DiscountItem> scheduled, List<DiscountItem> expired) {

            static Discounts of(List<Discount> stored, Instant now) {
                return new Discounts(bucket(stored, discount -> discount.validity().isActiveAt(now)),
                        bucket(stored, discount -> discount.validity().isFutureAt(now)),
                        bucket(stored, discount -> discount.validity().hasEndedBy(now)));
            }

            private static List<DiscountItem> bucket(List<Discount> stored, Predicate<Discount> inBucket) {
                return stored.stream().filter(inBucket).map(DiscountItem::of).toList();
            }

            /** One promotion: its family, the number it uses and the range it applies over (03 RULE-5). */
            record DiscountItem(String discountId, String kind, int basisPoints, int amountMinor, String currency,
                                Instant validFrom, Instant validUntil) {

                static DiscountItem of(Discount discount) {
                    Discount.Kind kind = discount.kind();
                    return new DiscountItem(discount.discountId().value(), wire(kind), kind.basisPoints(),
                            kind.fixedMinor(), currencyOf(kind),
                            discount.validity().validFrom(), discount.validity().validUntil());
                }

                private static String wire(Discount.Kind kind) {
                    return kind instanceof Discount.Kind.Fixed ? "fixed" : "percentage";
                }

                private static String currencyOf(Discount.Kind kind) {
                    return kind instanceof Discount.Kind.Fixed fixed ? fixed.amount().currency() : null;
                }
            }
        }
    }
}
