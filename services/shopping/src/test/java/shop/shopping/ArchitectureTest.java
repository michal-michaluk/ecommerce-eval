package shop.shopping;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The kernel/slice boundary of the shopping context, executable.
 *
 * <p>The context is one root package split into two: {@code kernel} is the jointly owned, immutable and public model every
 * slice reads — money and the service's one exception — and {@code basket} is the slice that owns the basket aggregate. The
 * six shared rule factories are instantiated once per package.
 *
 * <p>A slice's {@code sharedKernelUsed} is the kernel package, the one foreign package it may depend on. Its
 * {@code sharedKernelExposed} is the kernel package as well, which subtracts nothing: every type the slice itself owns stays
 * unexposed, so {@code modelIsolation} forbids any class outside the slice from touching it. That one predicate is what
 * forbids a kernel type reaching back into the slice, which {@code kernelDependsOnNoSlice} states directly.
 *
 * <p>The kernel exposes all of itself by design, so its own {@code modelIsolation} has nothing left to check; it stays
 * declared — with {@code allowEmptyShould} — because deleting it would drop the kernel from this test.
 */
@AnalyzeClasses(packages = "shop.shopping", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    public static final String KERNEL = "shop.shopping.kernel";
    public static final String BASKET = "shop.shopping.basket";

    public static final DescribedPredicate<JavaClass> sharedKernelExposed = resideInAPackage(KERNEL + "..");
    public static final DescribedPredicate<JavaClass> sharedKernelUsed = resideInAPackage(KERNEL + "..");

    @ArchTest
    public static final ArchRule basketAdaptersDependencies =
            ArchitectureDescription.adaptersDependencies(BASKET, sharedKernelUsed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule basketServicesDependencies =
            ArchitectureDescription.servicesDependencies(BASKET, sharedKernelUsed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule basketModelDependencies =
            ArchitectureDescription.modelDependencies(BASKET, sharedKernelUsed);

    @ArchTest
    public static final ArchRule basketAdaptersIsolation =
            ArchitectureDescription.adaptersIsolation(BASKET).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule basketServicesIsolation =
            ArchitectureDescription.servicesIsolation(BASKET, ArchitectureDescription.mediators).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule basketModelIsolation =
            ArchitectureDescription.modelIsolation(BASKET, sharedKernelExposed);

    @ArchTest
    public static final ArchRule kernelModelIsolation =
            ArchitectureDescription.modelIsolation(KERNEL, sharedKernelExposed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule kernelDependsOnNoSlice =
            noClasses().that().resideInAPackage(KERNEL + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(BASKET + "..");
}
