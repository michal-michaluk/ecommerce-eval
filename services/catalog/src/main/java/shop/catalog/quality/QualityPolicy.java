package shop.catalog.quality;

import shop.catalog.offer.Media;
import shop.catalog.offer.ProductDescription;
import shop.catalog.offer.ProductDescription.DescriptionBlock;
import shop.catalog.offer.ProductDescription.DescriptionBlock.BulletList;
import shop.catalog.offer.ProductDescription.DescriptionBlock.Heading;
import shop.catalog.offer.ProductDescription.DescriptionBlock.Image;
import shop.catalog.offer.ProductDescription.DescriptionBlock.Paragraph;
import shop.catalog.offer.ProductDescription.Identifiers;
import shop.catalog.offer.ProductDescription.Parameter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Element 08 — the quality of one description draft: {@code (draft, media, categoryPath) -> findings}.
 *
 * <pre>
 * Findings(draft, media, categoryPath) =
 *       universal(draft, media)                       # QP-1..QP-8, ordered
 *     + categoryRules(draft, categoryPath)            # QP-10..QP-12, only for a configured category
 *
 * prose(draft) = sum(length(block.text) for paragraph blocks)
 *              + sum(length(item)     for bulletList items)     # headings excluded
 * </pre>
 *
 * <p>It runs continuously on the draft, is frozen into the review candidate at request (04) and is re-enforced by
 * {@code Draft.toDefinition()} (02). It is an instance policy: the configuration arrives as a value through the constructor and
 * is never read from a configuration utility, a repository or a clock, so the call is a pure function of its three inputs and
 * never mutates them.
 *
 * <p>It answers, it does not fail: an incomplete draft is a legal draft — that is the state this policy exists to report — so
 * nothing here throws on a missing title, an absent identifier list, an empty parameter or block list, and both {@code media}
 * and {@code categoryPath} may be {@code null}. Only the draft itself is required, because a call without a draft judges
 * nothing and has no answer to give. QP-6 is a total, never a per-block limit: prose is summed over the whole draft and needs
 * at least one paragraph block, because 1 000 sits above the longest paragraph of the sample (449), so no single block can
 * satisfy it. QP-9 (grammar and correctness validation) is out of scope: it needs a new external technology and is reserved as
 * the unimplemented {@code grammar-enabled} variant.
 *
 * <p>The category path is an input of its own rather than a component read off the draft, as 08 types the call: the argument
 * decides which category the draft is judged in, and {@code Draft.categoryPath()} is never read, so a path that differs from the
 * draft's own component judges the draft in the category it is about to be listed in.
 *
 * <p>{@code media} is the offer's own media value: the review and the offer both hold {@link Media}, so 08 judges the items a
 * candidate froze instead of a copy of them (D45).
 *
 * <p>The policy and its answer are public for exactly one reason: the mediator of the {@code review → quality} edge names them
 * (D42, 17 RULE-1). No other type of this context widens; {@link QualityConfig} stays internal, because the mediator injects the
 * policy bean and never the configuration behind it.
 */
public record QualityPolicy(QualityConfig config) {

    private static final int TITLE_MAX_LENGTH = 200;
    private static final int ONE_ITEM = 1;
    private static final Comparator<QualityFindings.Finding> BY_RULE_NUMBER =
            Comparator.comparingInt(finding -> finding.rule().number());

    public QualityPolicy {
        Objects.requireNonNull(config, "config");
    }

    /**
     * The configuration arrives as a value — no fallback is declared here, because 08's default variant is "no configured prefix
     * matched", not "no configuration at all": a category set is data the binder supplies, so code cannot default it.
     */
    static QualityPolicy withConfig(QualityConfig config) {
        return new QualityPolicy(config);
    }

    /**
     * The findings of one call, ordered by rule number and — inside one rule — by the configuration's declaration order.
     *
     * <p>A category path that matches no configured prefix produces no QP-10…QP-12 finding, while the universal rules always
     * apply; the longest matching prefix wins and the first match is final, so the same inputs always report the same list.
     */
    public QualityFindings calculateFindings(
            ProductDescription.Draft draft, List<Media> media, List<String> categoryPath) {
        Objects.requireNonNull(draft, "draft");
        List<Media> items = media == null ? List.of() : List.copyOf(media);
        QualityConfig.CategoryParameters category = matchedCategory(categoryPath);
        List<QualityFindings.Finding> findings = new ArrayList<>(universalFindings(draft, items));
        if (category != null) {
            findings.addAll(categoryFindings(draft, category));
        }
        findings.sort(BY_RULE_NUMBER);
        return new QualityFindings(configuredSeverities(findings), proseOf(blocksOf(draft)), prefixOf(category));
    }

    private List<QualityFindings.Finding> configuredSeverities(List<QualityFindings.Finding> findings) {
        return findings.stream().map(finding -> finding.withSeverity(config.severityOf(finding.rule()))).toList();
    }

    private List<QualityFindings.Finding> universalFindings(
            ProductDescription.Draft draft, List<Media> media) {
        return Stream.of(
                        titleFindings(draft),
                        identifierFindings(draft),
                        blockFindings(draft, media),
                        mediaFindings(media))
                .flatMap(List::stream)
                .toList();
    }

    private static List<QualityFindings.Finding> titleFindings(ProductDescription.Draft draft) {
        String trimmed = textOf(draft.title()).strip();
        return trimmed.isEmpty() || trimmed.length() > TITLE_MAX_LENGTH
                ? List.of(QualityFindings.Finding.titleNotAccepted(textOf(draft.title()), TITLE_MAX_LENGTH))
                : List.of();
    }

    private static List<QualityFindings.Finding> identifierFindings(ProductDescription.Draft draft) {
        Identifiers identifiers = draft.identifiers();
        List<QualityFindings.Finding> findings = new ArrayList<>();
        if (identifiers == null || !identifiers.hasValidEanGtin()) {
            findings.add(QualityFindings.Finding.eanGtinNotAccepted(identifiers == null ? null : identifiers.eanGtin()));
        }
        if (identifiers == null || !identifiers.hasProductNumber()) {
            findings.add(QualityFindings.Finding.productNumberMissing());
        }
        return findings;
    }

    private List<QualityFindings.Finding> blockFindings(
            ProductDescription.Draft draft, List<Media> media) {
        List<DescriptionBlock> blocks = blocksOf(draft);
        return Stream.of(proseFindings(blocks), unknownMediaFindings(blocks, media))
                .flatMap(List::stream)
                .toList();
    }

    private List<QualityFindings.Finding> proseFindings(List<DescriptionBlock> blocks) {
        int prose = proseOf(blocks);
        List<QualityFindings.Finding> findings = new ArrayList<>();
        if (prose < config.minProseChars()) {
            findings.add(QualityFindings.Finding.proseTooShort(prose, config.minProseChars()));
        }
        if (blocks.stream().noneMatch(Paragraph.class::isInstance)) {
            findings.add(QualityFindings.Finding.paragraphMissing());
        }
        return findings;
    }

    private static List<QualityFindings.Finding> unknownMediaFindings(
            List<DescriptionBlock> blocks, List<Media> media) {
        Set<String> mediaIds = media.stream().map(Media::mediaId).collect(Collectors.toSet());
        List<QualityFindings.Finding> findings = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index++) {
            if (blocks.get(index) instanceof Image image && !mediaIds.contains(image.mediaId())) {
                findings.add(QualityFindings.Finding.mediaReferenceUnknown(index + ONE_ITEM, image.mediaId()));
            }
        }
        return findings;
    }

    private static List<QualityFindings.Finding> mediaFindings(List<Media> media) {
        List<QualityFindings.Finding> findings = new ArrayList<>();
        if (media.stream().noneMatch(QualityPolicy::isImage)) {
            findings.add(QualityFindings.Finding.imageMediaMissing());
        }
        long primaryItems = media.stream().filter(Media::primary).count();
        if (primaryItems != ONE_ITEM) {
            findings.add(QualityFindings.Finding.primaryMediaAmbiguous(primaryItems));
        }
        return Stream.of(findings, sequenceFindings(media)).flatMap(List::stream).toList();
    }

    private static List<QualityFindings.Finding> sequenceFindings(List<Media> media) {
        List<Integer> positions = media.stream().map(Media::position).sorted().toList();
        List<QualityFindings.Finding> findings = new ArrayList<>();
        if (!isContiguousFromOne(positions)) {
            findings.add(QualityFindings.Finding.mediaPositionsNotContiguous(positions));
        }
        long videos = media.stream().filter(QualityPolicy::isVideo).count();
        if (videos > ONE_ITEM) {
            findings.add(QualityFindings.Finding.tooManyVideos(videos));
        }
        return findings;
    }

    private static boolean isImage(Media media) {
        return media.kind() == Media.Kind.IMAGE;
    }

    private static boolean isVideo(Media media) {
        return media.kind() == Media.Kind.VIDEO;
    }

    private static boolean isContiguousFromOne(List<Integer> positions) {
        return IntStream.range(0, positions.size())
                .allMatch(index -> positions.get(index) == index + ONE_ITEM);
    }

    private static List<QualityFindings.Finding> categoryFindings(
            ProductDescription.Draft draft, QualityConfig.CategoryParameters category) {
        List<Parameter> parameters = parametersOf(draft);
        return Stream.of(
                        missingParameterFindings(parameters, category),
                        valueTypeFindings(parameters, category),
                        unconfiguredParameterFindings(parameters, category))
                .flatMap(List::stream)
                .toList();
    }

    private static List<QualityFindings.Finding> missingParameterFindings(
            List<Parameter> parameters, QualityConfig.CategoryParameters category) {
        return category.parameters().stream()
                .filter(QualityConfig.RequiredParameter::required)
                .filter(required -> parameterOf(parameters, required.code()).isEmpty())
                .map(required -> QualityFindings.Finding.requiredParameterMissing(required.code(), category.name()))
                .toList();
    }

    private static List<QualityFindings.Finding> valueTypeFindings(
            List<Parameter> parameters, QualityConfig.CategoryParameters category) {
        return category.parameters().stream()
                .flatMap(required -> parameterOf(parameters, required.code())
                        .filter(parameter -> parameter.valueType() != required.valueType())
                        .map(parameter -> QualityFindings.Finding.parameterValueTypeMismatch(
                                required.code(), required.valueType(), parameter.valueType()))
                        .stream())
                .toList();
    }

    private static List<QualityFindings.Finding> unconfiguredParameterFindings(
            List<Parameter> parameters, QualityConfig.CategoryParameters category) {
        return parameters.stream()
                .filter(parameter -> category.parameter(parameter.code()).isEmpty())
                .map(parameter -> QualityFindings.Finding.parameterNotConfigured(parameter.code(), category.name()))
                .toList();
    }

    /** The longest configured prefix the path starts with; the first longest one is final, so the match is deterministic. */
    private QualityConfig.CategoryParameters matchedCategory(List<String> categoryPath) {
        List<String> path = categoryPath == null ? List.of() : categoryPath;
        QualityConfig.CategoryParameters matched = null;
        for (QualityConfig.CategoryParameters candidate : config.categories()) {
            if (candidate.matches(path) && isDeeperThan(candidate, matched)) {
                matched = candidate;
            }
        }
        return matched;
    }

    private static boolean isDeeperThan(
            QualityConfig.CategoryParameters candidate, QualityConfig.CategoryParameters matched) {
        return matched == null || candidate.prefix().size() > matched.prefix().size();
    }

    private static List<String> prefixOf(QualityConfig.CategoryParameters category) {
        return category == null ? List.of() : category.prefix();
    }

    private static int proseOf(List<DescriptionBlock> blocks) {
        return blocks.stream().mapToInt(QualityPolicy::proseOf).sum();
    }

    private static int proseOf(DescriptionBlock block) {
        return switch (block) {
            case Paragraph paragraph -> paragraph.text().length();
            case BulletList bullets -> bullets.items().stream().mapToInt(String::length).sum();
            case Heading ignorable -> 0;
            case Image ignorable -> 0;
        };
    }

    private static List<DescriptionBlock> blocksOf(ProductDescription.Draft draft) {
        return draft.blocks() == null ? List.of() : draft.blocks();
    }

    private static List<Parameter> parametersOf(ProductDescription.Draft draft) {
        return draft.parameters() == null ? List.of() : draft.parameters();
    }

    private static Optional<Parameter> parameterOf(List<Parameter> parameters, String code) {
        return parameters.stream().filter(parameter -> parameter.code().equals(code)).findFirst();
    }

    private static String textOf(String value) {
        return value == null ? "" : value;
    }
}
