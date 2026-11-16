package shop.catalog.offer;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import shop.catalog.ArchitectureDescription;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;

/**
 * PACKAGE is the narrow pattern (no {@code ..}): widen it to {@code shop.catalog.offer..} in the commit that adds this
 * context's first {@code protocols/<version>} sub-package (arch-unit.md, archunit.package-pattern-explicit).
 */
@AnalyzeClasses(
        packages = "shop.catalog",
        importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureOfOfferTest {
    public static final String PACKAGE = "shop.catalog.offer";

    public static final DescribedPredicate<JavaClass> sharedKernelExposed =
            belongToAnyOf(ProductDescription.class, Offer.class, Media.class, OfferCandidate.class, OfferDraft.class);
    public static final DescribedPredicate<JavaClass> sharedKernelUsed = belongToAnyOf();

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
