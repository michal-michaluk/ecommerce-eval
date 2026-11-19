package shop.fulfillment;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The kernel/slice boundary of the fulfillment context, executable.
 *
 * <p>The context is one root package split into four: {@code kernel} is the jointly owned, immutable and public model
 * every slice reads, {@code processing} and {@code parcel} are the slices that own a mutable aggregate each and never see one
 * another, and {@code orders} is the read slice that sees neither of them — it is fed by the domain events and answers the
 * orders list of element 15. The six shared rule factories are instantiated once per package.
 *
 * <p>A slice's {@code sharedKernelUsed} is the kernel package — that is the one foreign package it may depend on. A
 * slice's {@code sharedKernelExposed} is the kernel package as well, which subtracts nothing: every type the slice
 * itself owns stays unexposed, so {@code modelIsolation} forbids any class outside the slice from touching it. That
 * one predicate is what forbids an inter-slice edge in both directions and a kernel type reaching back into a slice.
 *
 * <p>The kernel exposes all of itself by design, so its own {@code modelIsolation} has nothing left to check; it stays
 * declared — with {@code allowEmptyShould} — because the kernel-to-slice direction is only visible through the two
 * slices' rules, and deleting it would drop the kernel from this test.
 */
@AnalyzeClasses(packages = "shop.fulfillment", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    public static final String KERNEL = "shop.fulfillment.kernel";
    public static final String PROCESSING = "shop.fulfillment.processing";
    public static final String PARCEL = "shop.fulfillment.parcel";
    public static final String ORDERS = "shop.fulfillment.orders";

    public static final DescribedPredicate<JavaClass> sharedKernelExposed = resideInAPackage(KERNEL + "..");
    public static final DescribedPredicate<JavaClass> sharedKernelUsed = resideInAPackage(KERNEL + "..");

    @ArchTest
    public static final ArchRule processingAdaptersDependencies =
            ArchitectureDescription.adaptersDependencies(PROCESSING, sharedKernelUsed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule processingServicesDependencies =
            ArchitectureDescription.servicesDependencies(PROCESSING, sharedKernelUsed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule processingModelDependencies =
            ArchitectureDescription.modelDependencies(PROCESSING, sharedKernelUsed);

    @ArchTest
    public static final ArchRule processingAdaptersIsolation =
            ArchitectureDescription.adaptersIsolation(PROCESSING).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule processingServicesIsolation =
            ArchitectureDescription.servicesIsolation(PROCESSING, ArchitectureDescription.mediators).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule processingModelIsolation =
            ArchitectureDescription.modelIsolation(PROCESSING, sharedKernelExposed);

    @ArchTest
    public static final ArchRule parcelAdaptersDependencies =
            ArchitectureDescription.adaptersDependencies(PARCEL, sharedKernelUsed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule parcelServicesDependencies =
            ArchitectureDescription.servicesDependencies(PARCEL, sharedKernelUsed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule parcelModelDependencies =
            ArchitectureDescription.modelDependencies(PARCEL, sharedKernelUsed);

    @ArchTest
    public static final ArchRule parcelAdaptersIsolation =
            ArchitectureDescription.adaptersIsolation(PARCEL).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule parcelServicesIsolation =
            ArchitectureDescription.servicesIsolation(PARCEL, ArchitectureDescription.mediators).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule parcelModelIsolation =
            ArchitectureDescription.modelIsolation(PARCEL, sharedKernelExposed);

    @ArchTest
    public static final ArchRule kernelModelIsolation =
            ArchitectureDescription.modelIsolation(KERNEL, sharedKernelExposed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule ordersAdaptersDependencies =
            ArchitectureDescription.adaptersDependencies(ORDERS, sharedKernelUsed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule ordersServicesDependencies =
            ArchitectureDescription.servicesDependencies(ORDERS, sharedKernelUsed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule ordersModelDependencies =
            ArchitectureDescription.modelDependencies(ORDERS, sharedKernelUsed);

    @ArchTest
    public static final ArchRule ordersAdaptersIsolation =
            ArchitectureDescription.adaptersIsolation(ORDERS).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule ordersServicesIsolation =
            ArchitectureDescription.servicesIsolation(ORDERS, ArchitectureDescription.mediators).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule ordersModelIsolation =
            ArchitectureDescription.modelIsolation(ORDERS, sharedKernelExposed);

    @ArchTest
    public static final ArchRule ordersDependsOnNoWriteSlice =
            noClasses().that().resideInAPackage(ORDERS + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(PROCESSING + "..", PARCEL + "..");

    @ArchTest
    public static final ArchRule writeSlicesDependOnNoReadSlice =
            noClasses().that().resideInAnyPackage(PROCESSING + "..", PARCEL + "..")
                    .should().dependOnClassesThat().resideInAPackage(ORDERS + "..");

    @ArchTest
    public static final ArchRule processingDependsOnNoSlice =
            noClasses().that().resideInAPackage(PROCESSING + "..")
                    .should().dependOnClassesThat().resideInAPackage(PARCEL + "..");

    @ArchTest
    public static final ArchRule parcelDependsOnNoSlice =
            noClasses().that().resideInAPackage(PARCEL + "..")
                    .should().dependOnClassesThat().resideInAPackage(PROCESSING + "..");

    @ArchTest
    public static final ArchRule kernelDependsOnNoSlice =
            noClasses().that().resideInAPackage(KERNEL + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(PROCESSING + "..", PARCEL + "..");
}
