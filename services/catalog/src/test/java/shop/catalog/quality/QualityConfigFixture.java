package shop.catalog.quality;

import shop.catalog.offer.ProductDescription.Parameter.ValueType;
import shop.catalog.quality.QualityConfig.CategoryParameters;
import shop.catalog.quality.QualityConfig.RequiredParameter;
import shop.catalog.quality.QualityConfig.SeverityOverride;
import shop.catalog.quality.QualityFindings.Rule;
import shop.catalog.quality.QualityFindings.Severity;

import java.util.ArrayList;
import java.util.List;

import static shop.catalog.quality.QualityFixture.LEGO_CATEGORY;
import static shop.catalog.quality.QualityFixture.LEGO_STAR_WARS_CATEGORY;

/**
 * The configuration of 08 as the scenarios use it: the LEGO category of the binding shape (D33), the {@code strict-media}
 * variant, and the two prefixes that prove the longest-prefix match. Every factory returns a fresh value, so no test shares a
 * configuration with another.
 */
public class QualityConfigFixture {

    public static final String STAR_WARS_CHARACTER = "starWarsCharacter";

    private QualityConfigFixture() {
    }

    public static QualityPolicy legoPolicy() {
        return QualityPolicy.withConfig(legoConfig());
    }

    public static QualityPolicy strictMediaPolicy() {
        return QualityPolicy.withConfig(strictMediaConfig());
    }

    public static QualityPolicy legoAndStarWarsPolicy() {
        return QualityPolicy.withConfig(new QualityConfig(
                QualityConfig.DEFAULT_MIN_PROSE_CHARS,
                List.of(legoCategory(), starWarsCategory()),
                List.of()));
    }

    /** The same two categories declared the other way round: the deeper prefix wins whatever the declaration order is. */
    public static QualityPolicy starWarsThenLegoPolicy() {
        return QualityPolicy.withConfig(new QualityConfig(
                QualityConfig.DEFAULT_MIN_PROSE_CHARS,
                List.of(starWarsCategory(), legoCategory()),
                List.of()));
    }

    /** The same prefix declared twice; the second declaration demands a parameter the first does not, so the first can be told. */
    public static QualityPolicy repeatedLegoPrefixPolicy() {
        return QualityPolicy.withConfig(new QualityConfig(
                QualityConfig.DEFAULT_MIN_PROSE_CHARS,
                List.of(legoCategory(), legoCategoryWithExtraParameter()),
                List.of()));
    }

    /** A configuration whose {@code pieceCount} may be absent; QP-10 reports a missing required parameter only. */
    public static QualityPolicy policyWithOptionalPieceCount() {
        List<RequiredParameter> parameters = new ArrayList<>(legoCategory().parameters());
        parameters.replaceAll(parameter -> parameter.code().equals("pieceCount")
                ? optionalParameter("pieceCount", ValueType.INTEGER)
                : parameter);
        return QualityPolicy.withConfig(new QualityConfig(QualityConfig.DEFAULT_MIN_PROSE_CHARS,
                List.of(new CategoryParameters(LEGO_CATEGORY, parameters)), List.of()));
    }

    public static QualityConfig legoConfig() {
        return new QualityConfig(QualityConfig.DEFAULT_MIN_PROSE_CHARS, List.of(legoCategory()), List.of());
    }

    public static QualityConfig strictMediaConfig() {
        return new QualityConfig(QualityConfig.DEFAULT_MIN_PROSE_CHARS, List.of(legoCategory()),
                List.of(new SeverityOverride(Rule.QP_8, Severity.BLOCKING)));
    }

    public static CategoryParameters legoCategory() {
        return new CategoryParameters(LEGO_CATEGORY, List.of(
                requiredParameter("condition", ValueType.TEXT),
                requiredParameter("childAge", ValueType.RANGE_FROM),
                requiredParameter("pieceCount", ValueType.INTEGER),
                requiredParameter("gender", ValueType.ENUM_SET),
                requiredParameter("certificates", ValueType.LIST),
                requiredParameter("safetyInfo", ValueType.TEXT)));
    }

    /**
     * A deeper category adds one parameter to its ancestor's set, so the longest-prefix match is observable in the findings.
     *
     * <p>The repetition of the ancestor parameters is the binder's composition, not the policy's: 08 says the longest matching
     * prefix wins and never says a deeper prefix inherits its ancestors, so a configuration that names only the extra parameter
     * would drop every ancestor requirement — a specification gap, not a policy decision.
     */
    public static CategoryParameters starWarsCategory() {
        List<RequiredParameter> parameters = new ArrayList<>(legoCategory().parameters());
        parameters.add(requiredParameter(STAR_WARS_CHARACTER, ValueType.TEXT));
        return new CategoryParameters(LEGO_STAR_WARS_CATEGORY, parameters);
    }

    public static CategoryParameters legoCategoryWithExtraParameter() {
        List<RequiredParameter> parameters = new ArrayList<>(legoCategory().parameters());
        parameters.add(requiredParameter(STAR_WARS_CHARACTER, ValueType.TEXT));
        return new CategoryParameters(LEGO_CATEGORY, parameters);
    }

    public static RequiredParameter requiredParameter(String code, ValueType valueType) {
        return new RequiredParameter(code, valueType, true);
    }

    public static RequiredParameter optionalParameter(String code, ValueType valueType) {
        return new RequiredParameter(code, valueType, false);
    }

    public static QualityConfig configWithMinProseChars(int minProseChars) {
        return new QualityConfig(minProseChars, List.of(), List.of());
    }

    public static QualityConfig configWithoutPrefix() {
        return new QualityConfig(QualityConfig.DEFAULT_MIN_PROSE_CHARS,
                List.of(new CategoryParameters(List.of(), List.of())), List.of());
    }

    public static QualityConfig configWithBlankParameterCode() {
        return new QualityConfig(QualityConfig.DEFAULT_MIN_PROSE_CHARS,
                List.of(new CategoryParameters(LEGO_CATEGORY, List.of(requiredParameter("  ", ValueType.TEXT)))),
                List.of());
    }

    public static QualityConfig configWithRepeatedSeverityOverride() {
        return new QualityConfig(QualityConfig.DEFAULT_MIN_PROSE_CHARS, List.of(legoCategory()), List.of(
                new SeverityOverride(Rule.QP_8, Severity.WARNING),
                new SeverityOverride(Rule.QP_8, Severity.BLOCKING)));
    }
}
