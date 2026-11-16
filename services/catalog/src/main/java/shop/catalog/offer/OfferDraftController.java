package shop.catalog.offer;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import shop.catalog.Scopes;
import shop.catalog.reads.DraftFindings;
import shop.catalog.reads.QualityFinding;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * 12's draft resource (D13): one `PATCH` for description, media and availability, one `GET` for the screen of 14 —
 * description, media, availability and the findings 08 reports right now.
 *
 * <p>Two writers share the endpoint, so D13a is decided per component: the caller's scopes are handed the components the body
 * actually carried, and anything outside them is `403 FIELD_NOT_IN_SCOPE` before the offer is even loaded — a wrong scope is never
 * a `404` ({@link Scopes#requireDraftWrite}).
 *
 * <p>It injects two collaborators because the two halves come from two owners: {@link OfferService} is the primary port that
 * performs the edit, and {@link DraftFindings} is the read-side port 14 declares for the quality panel. The findings are asked of
 * 08 on every read and never stored, so the panel cannot drift from the rules (D20, {@code http.no-write-model-in-read}).
 */
@RestController
@RequiredArgsConstructor
class OfferDraftController {

    private final OfferService offers;
    private final DraftFindings findings;

    @GetMapping(path = "/offers/{offerId}/draft", produces = APPLICATION_JSON_VALUE)
    DraftView draft(@PathVariable String offerId) {
        Offer offer = offers.getOffer(offerId).orElseThrow(OfferDraftController::offerUnknown);
        return DraftView.of(offer, findings.findingsOf(offer.description(), offer.media()));
    }

    @PatchMapping(path = "/offers/{offerId}/draft", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
    DraftView updateDraft(@PathVariable String offerId, @RequestBody @Valid UpdateDraft update) {
        refuseUnknown(update.unknownKeys());
        Scopes.requireDraftWrite(update.components());
        Offer offer = offers.updateDraft(offerId, update.change(), update.addedMedia(), update.availabilityStart())
                .orElseThrow(OfferDraftController::offerUnknown);
        return DraftView.of(offer, findings.findingsOf(offer.description(), offer.media()));
    }

    private static ResponseStatusException offerUnknown() {
        return new ResponseStatusException(NOT_FOUND);
    }

    /** The members a record collected as "not mine": a body that carried none leaves the trap empty, or absent. */
    private static List<String> declared(Map<String, Object> unknown) {
        return unknown == null ? List.of() : List.copyOf(unknown.keySet());
    }

    /**
     * D18 at every depth: the draft has exactly three editable components and each of them declares its own, so a key nobody
     * declares — a typo, or one of the media edits 02 has no command for yet — is a `400` instead of an edit silently dropped.
     */
    private static void refuseUnknown(Collection<String> unknown) {
        if (!unknown.isEmpty()) {
            throw unknownComponent(unknown.iterator().next());
        }
    }

    /**
     * 12 names the offending field beside the `400`, and the one advice reads that name off a Jackson
     * {@link UnrecognizedPropertyException}. The process-wide mapper ignores unknown members on purpose (a published payload stays
     * readable across versions), so the command collects them and the adapter states the key the refusal names.
     */
    private static HttpMessageNotReadableException unknownComponent(String component) {
        return new HttpMessageNotReadableException("unknown component " + component,
                new UnrecognizedPropertyException(null, component, null, Object.class, component, List.of()), null);
    }

    /** A command a client could write but this resource cannot read: 12 answers it as a `400`, never as a `500`. */
    private static HttpMessageNotReadableException unreadable(String component, String expected) {
        return new HttpMessageNotReadableException("%s must carry %s".formatted(component, expected),
                new UnrecognizedPropertyException(null, component, null, Object.class, component, List.of()), null);
    }

    /**
     * 12's draft edit: the three components of the draft resource, each optional — an absent component is absent, never "unchanged
     * on the client's behalf" (D18) — and an unknown one is a `400`, so a typo cannot silently drop an edit.
     *
     * <p>The command owns the "which components were sent" decision, because it is the only place that knows: {@link #components()}
     * is what D13a is decided on, and the present components travel to the port together, so one `PATCH` is one transaction.
     */
    record UpdateDraft(@Valid DescriptionView description, @Valid MediaChange media, LocalDate availabilityStart,
                       @JsonAnySetter Map<String, Object> unknown) {

        /** D18 below the top level too: every nested record declares its own keys, and the command counts all of them. */
        List<String> unknownKeys() {
            return Stream.of(declared(unknown),
                            description == null ? List.<String>of() : description.unknownKeys(),
                            media == null ? List.<String>of() : media.unknownKeys())
                    .flatMap(Collection::stream)
                    .toList();
        }

        List<String> components() {
            List<String> sent = new ArrayList<>();
            if (description != null) {
                sent.add(Scopes.DESCRIPTION);
            }
            if (media != null) {
                sent.add(Scopes.MEDIA);
            }
            if (availabilityStart != null) {
                sent.add(Scopes.AVAILABILITY_START);
            }
            return sent;
        }

        ProductDescription.Draft change() {
            return description == null ? null : description.toDraft();
        }

        List<Media> addedMedia() {
            return media == null ? null : media.addedMedia();
        }
    }

    /** What the draft screen renders for one offer, findings included: the answer of every draft write and of its read (D14). */
    record DraftView(String offerId, String offerState, LocalDate availabilityStart, DescriptionView description,
                     List<MediaItem> media, Quality quality) {

        static DraftView of(Offer offer, List<QualityFinding> findings) {
            return new DraftView(offer.offerId(), OfferController.OfferView.stateName(offer.state()),
                    offer.availabilityStart(), DescriptionView.of(offer.description()),
                    offer.media().stream().map(MediaItem::of).toList(), new Quality(findings));
        }

        /** 08's report as the panel of 14 groups it; the rule label is what a reader traces to the spec. */
        record Quality(List<QualityFinding> findings) {

            Quality {
                findings = List.copyOf(findings);
            }
        }
    }

    /**
     * The description in builder form on the wire: every component of 01's value, nullable, because a draft tolerates what a frozen
     * description refuses (RULE-8). `parameters` and `blocks` carry the flat shape 12's prototype sends — one `valueType` per row and
     * the carrier's own fields beside it — so the wire never names a Java type.
     */
    record DescriptionView(String title, ProductDescription.Identifiers identifiers, ProductDescription.Brand brand,
                           ProductDescription.Condition condition, List<String> categoryPath,
                           List<ParameterView> parameters, List<BlockView> blocks,
                           @JsonAnySetter Map<String, Object> unknown) {

        List<String> unknownKeys() {
            return Stream.of(declared(unknown),
                            parameters == null ? List.<String>of() : parameters.stream().flatMap(row -> row.unknownKeys().stream()).toList(),
                            blocks == null ? List.<String>of() : blocks.stream().flatMap(block -> block.unknownKeys().stream()).toList())
                    .flatMap(Collection::stream)
                    .toList();
        }

        static DescriptionView of(ProductDescription.Draft draft) {
            return new DescriptionView(draft.title(), draft.identifiers(), draft.brand(), draft.condition(),
                    draft.categoryPath(),
                    draft.parameters() == null ? List.of() : draft.parameters().stream().map(ParameterView::of).toList(),
                    draft.blocks() == null ? List.of() : draft.blocks().stream().map(BlockView::of).toList(),
                    Map.of());
        }

        ProductDescription.Draft toDraft() {
            return ProductDescription.Draft.builder()
                    .title(title)
                    .identifiers(identifiers)
                    .brand(brand)
                    .condition(condition)
                    .categoryPath(categoryPath)
                    .parameters(parameters == null ? null : parameters.stream().map(ParameterView::toDomain).toList())
                    .blocks(blocks == null ? null : blocks.stream().map(BlockView::toDomain).toList())
                    .build();
        }
    }

    /** One attribute row: the code and label of 01, the declared `valueType` and the carrier's value beside it. */
    record ParameterView(String code, String label, ValueType valueType, Object value, BigDecimal from, String unit,
                         @JsonAnySetter Map<String, Object> unknown) {

        List<String> unknownKeys() {
            return declared(unknown);
        }

        static ParameterView of(ProductDescription.Parameter parameter) {
            return switch (parameter.value()) {
                case ProductDescription.Parameter.Value.Text text -> plain(parameter, ValueType.TEXT, text.text());
                case ProductDescription.Parameter.Value.Whole whole -> plain(parameter, ValueType.INTEGER, whole.value());
                case ProductDescription.Parameter.Value.Decimal decimal -> plain(parameter, ValueType.DECIMAL, decimal.value());
                case ProductDescription.Parameter.Value.RangeFrom range -> new ParameterView(parameter.code(),
                        parameter.label(), ValueType.RANGE_FROM, null, range.from(), range.unit(), Map.of());
                case ProductDescription.Parameter.Value.Flag flag -> plain(parameter, ValueType.BOOLEAN, flag.value());
                case ProductDescription.Parameter.Value.DateValue date -> plain(parameter, ValueType.DATE, date.date());
                case ProductDescription.Parameter.Value.Choices choices -> plain(parameter, ValueType.ENUM_SET, choices.values());
                case ProductDescription.Parameter.Value.Items items -> plain(parameter, ValueType.LIST, items.values());
            };
        }

        ProductDescription.Parameter toDomain() {
            if (valueType == null) {
                throw unreadable("valueType", "one of the eight value types of 01");
            }
            return new ProductDescription.Parameter(code, label, valueType.toValue(value, from, unit));
        }

        private static ParameterView plain(ProductDescription.Parameter parameter, ValueType valueType, Object value) {
            return new ParameterView(parameter.code(), parameter.label(), valueType, value, null, null, Map.of());
        }

        /** The eight value types of 01, under the `valueType` spelling the prototype sends (`rangeFrom`, `enumSet`). */
        enum ValueType {
            TEXT("text"),
            INTEGER("integer"),
            DECIMAL("decimal"),
            RANGE_FROM("rangeFrom"),
            BOOLEAN("boolean"),
            DATE("date"),
            ENUM_SET("enumSet"),
            LIST("list");

            private final String wire;

            ValueType(String wire) {
                this.wire = wire;
            }

            @JsonValue
            String wire() {
                return wire;
            }

            @JsonCreator
            static ValueType of(String wire) {
                return Arrays.stream(values())
                        .filter(type -> type.wire.equals(wire))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("unknown valueType '%s'".formatted(wire)));
            }

            /** A `value` that does not fit its own `valueType` is a broken request, so every conversion answers a `400`. */
            ProductDescription.Parameter.Value toValue(Object value, BigDecimal from, String unit) {
                if (this != RANGE_FROM && value == null) {
                    throw unreadable("value", "the value its valueType declares");
                }
                return switch (this) {
                    case TEXT -> new ProductDescription.Parameter.Value.Text(String.valueOf(value));
                    case INTEGER -> new ProductDescription.Parameter.Value.Whole((int) number(value).longValue());
                    case DECIMAL -> new ProductDescription.Parameter.Value.Decimal(number(value));
                    case RANGE_FROM -> new ProductDescription.Parameter.Value.RangeFrom(required(from), unit);
                    case BOOLEAN -> new ProductDescription.Parameter.Value.Flag(flag(value));
                    case DATE -> new ProductDescription.Parameter.Value.DateValue(date(value));
                    case ENUM_SET -> new ProductDescription.Parameter.Value.Choices(strings(value));
                    case LIST -> new ProductDescription.Parameter.Value.Items(strings(value));
                };
            }

            private static BigDecimal number(Object value) {
                try {
                    return new BigDecimal(String.valueOf(value));
                } catch (NumberFormatException notANumber) {
                    throw unreadable("value", "a number");
                }
            }

            private static BigDecimal required(BigDecimal from) {
                if (from == null) {
                    throw unreadable("from", "the threshold a rangeFrom starts at");
                }
                return from;
            }

            private static boolean flag(Object value) {
                if (!(value instanceof Boolean flag)) {
                    throw unreadable("value", "a boolean");
                }
                return flag;
            }

            private static LocalDate date(Object value) {
                try {
                    return LocalDate.parse(String.valueOf(value));
                } catch (DateTimeParseException notADate) {
                    throw unreadable("value", "an ISO date");
                }
            }

            private static List<String> strings(Object value) {
                if (!(value instanceof List<?> values)) {
                    throw unreadable("value", "a list of strings");
                }
                return values.stream().map(String::valueOf).toList();
            }
        }
    }

    /** One block of 01's ordered content: the type discriminates, and only that type's own field is set (A3). */
    record BlockView(BlockType type, Integer level, String text, List<String> items, String mediaId,
                     @JsonAnySetter Map<String, Object> unknown) {

        List<String> unknownKeys() {
            return declared(unknown);
        }

        static BlockView of(ProductDescription.DescriptionBlock block) {
            return switch (block) {
                case ProductDescription.DescriptionBlock.Heading heading ->
                        new BlockView(BlockType.HEADING, heading.level(), heading.text(), null, null, Map.of());
                case ProductDescription.DescriptionBlock.Paragraph paragraph ->
                        new BlockView(BlockType.PARAGRAPH, null, paragraph.text(), null, null, Map.of());
                case ProductDescription.DescriptionBlock.BulletList list ->
                        new BlockView(BlockType.BULLET_LIST, null, null, list.items(), null, Map.of());
                case ProductDescription.DescriptionBlock.Image image ->
                        new BlockView(BlockType.IMAGE, null, null, null, image.mediaId(), Map.of());
            };
        }

        /** A block whose own fields are missing is a broken request, not a failed command (01 A3). */
        ProductDescription.DescriptionBlock toDomain() {
            return switch (type == null ? BlockType.PARAGRAPH : type) {
                case HEADING -> new ProductDescription.DescriptionBlock.Heading(
                        Objects.requireNonNullElse(level, 1), required(text, "text"));
                case PARAGRAPH -> new ProductDescription.DescriptionBlock.Paragraph(required(text, "text"));
                case BULLET_LIST -> new ProductDescription.DescriptionBlock.BulletList(required(items, "items"));
                case IMAGE -> new ProductDescription.DescriptionBlock.Image(required(mediaId, "mediaId"));
            };
        }

        private static <T> T required(T value, String component) {
            if (value == null) {
                throw unreadable(component, "a value of its own block type");
            }
            return value;
        }

        /** 01's four block shapes under the `type` spelling the prototype sends (`bulletList`). */
        enum BlockType {
            HEADING("heading"),
            PARAGRAPH("paragraph"),
            BULLET_LIST("bulletList"),
            IMAGE("image");

            private final String wire;

            BlockType(String wire) {
                this.wire = wire;
            }

            @JsonValue
            String wire() {
                return wire;
            }

            @JsonCreator
            static BlockType of(String wire) {
                return Arrays.stream(values())
                        .filter(type -> type.wire.equals(wire))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("unknown block type '%s'".formatted(wire)));
            }
        }
    }

    /**
     * One picture or film as 12 references it (D17): the location is the client's, `mediaId` is optional because
     * {@link Media#ofLocation} names the file from the location itself, `durationSec` is accepted as the prototype sends it (Q12
     * leaves it unvalidated and 02 does not store it), and the position and primary flag carry the order RULE-2 checks at the freeze.
     */
    record MediaItem(Kind kind, String format, Integer position, Boolean primary, String url, String mediaId,
                     Integer durationSec, @JsonAnySetter Map<String, Object> unknown) {

        List<String> unknownKeys() {
            return declared(unknown);
        }

        static MediaItem of(Media media) {
            return new MediaItem(Kind.of(media.kind()), media.format(), media.position(), media.primary(), media.url(),
                    media.mediaId(), null, Map.of());
        }

        /** A file with no kind or no location is a broken request, answered as a `400` rather than a domain `409`. */
        Media toMedia() {
            if (kind == null) {
                throw unreadable("kind", "image or video");
            }
            if (url == null || url.isBlank()) {
                throw unreadable("url", "the location of the file");
            }
            String id = mediaId == null || mediaId.isBlank() ? Media.idOf(url) : mediaId;
            return new Media(id, kind.toDomain(), Objects.requireNonNullElse(position, 0),
                    Objects.requireNonNullElse(primary, false), format, url);
        }

        enum Kind {
            IMAGE("image"),
            VIDEO("video");

            private final String wire;

            Kind(String wire) {
                this.wire = wire;
            }

            @JsonValue
            String wire() {
                return wire;
            }

            @JsonCreator
            static Kind of(String wire) {
                return Arrays.stream(values())
                        .filter(kind -> kind.wire.equals(wire))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("unknown media kind '%s'".formatted(wire)));
            }

            static Kind of(Media.Kind kind) {
                return valueOf(kind.name());
            }

            Media.Kind toDomain() {
                return Media.Kind.valueOf(name());
            }
        }
    }

    /**
     * The `media` component of 12's draft edit. Only `add` has a command in 02 — remove, reorder and primary are not modelled yet, so
     * asking for one is a `400` (D18) instead of a silently dropped edit.
     */
    record MediaChange(@Valid List<MediaItem> add, @JsonAnySetter Map<String, Object> unknown) {

        List<String> unknownKeys() {
            return Stream.of(declared(unknown),
                            add == null ? List.<String>of() : add.stream().flatMap(item -> item.unknownKeys().stream()).toList())
                    .flatMap(Collection::stream)
                    .toList();
        }

        List<Media> addedMedia() {
            return add == null ? List.of() : add.stream().map(MediaItem::toMedia).toList();
        }
    }
}
