package shop.catalog.review;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import shop.catalog.ArchitectureDescription;
import shop.catalog.CreatedResource;
import shop.catalog.Scopes;
import shop.catalog.offer.Media;
import shop.catalog.offer.Offer;
import shop.catalog.offer.ProductDescription;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;

/**
 * PACKAGE is the narrow pattern (no {@code ..}): widen it to {@code shop.catalog.review..} in the commit that adds this
 * context's first {@code protocols/<version>} sub-package (arch-unit.md, archunit.package-pattern-explicit).
 *
 * <p>Two root-package types are registered as used, both because 12 puts them on the transport and nowhere else: {@link Scopes}, the one
 * authorization rule a route rule cannot carry (the decision each caller owns inside the PATCH), and {@link CreatedResource}, the body a
 * `201` answers with so {@code CreatedResponseAdvice} can name the new resource (D19a). Every other route rule stays in the filter chain.
 */
@AnalyzeClasses(
        packages = "shop.catalog",
        importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureOfReviewTest {
    public static final String PACKAGE = "shop.catalog.review";

    public static final DescribedPredicate<JavaClass> sharedKernelExposed = belongToAnyOf(
            OfferDraftSnapshot.class, OfferPublication.class, OfferReview.class, QualityFindings.class,
            OfferSnapshot.class, OfferContent.class, ReviewCandidate.class,
            ReviewFinding.class, ReviewOverride.class, Review.class);
    public static final DescribedPredicate<JavaClass> sharedKernelUsed =
            belongToAnyOf(ProductDescription.class, Media.class, Offer.class, Offer.State.class,
                    Scopes.class, CreatedResource.class);

    @ArchTest
    public static final ArchRule adaptersDependencies = ArchitectureDescription
            .adaptersDependencies(PACKAGE, sharedKernelUsed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule servicesDependencies = ArchitectureDescription
            .servicesDependencies(PACKAGE, sharedKernelUsed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule modelDependencies = ArchitectureDescription
            .modelDependencies(PACKAGE, sharedKernelUsed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule adaptersIsolation = ArchitectureDescription
            .adaptersIsolation(PACKAGE).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule servicesIsolation = ArchitectureDescription
            .servicesIsolation(PACKAGE, ArchitectureDescription.mediators).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule modelIsolation = ArchitectureDescription
            .modelIsolation(PACKAGE, sharedKernelExposed).allowEmptyShould(true);
}
