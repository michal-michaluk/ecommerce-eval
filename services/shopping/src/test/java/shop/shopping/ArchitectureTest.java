package shop.shopping;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The kernel/slice boundary of the shopping context, executable.
 *
 * <p>The context is one root package split in two directions: {@code kernel} is the jointly owned, immutable and public model
 * every slice reads — money, availability, the handover payload and its estimate, the two handover contracts and the service's
 * one exception — and {@code basket} and {@code checkout} are the two vertical slices (D5). The six shared rule factories are
 * instantiated once per package.
 *
 * <p>A slice's {@code sharedKernelUsed} is the kernel package, the one foreign package it may depend on. Its
 * {@code sharedKernelExposed} is the kernel package as well, which subtracts nothing: every type the slice itself owns stays
 * unexposed, so {@code modelIsolation} forbids any class outside the slice from touching it. That one predicate is what
 * forbids a kernel type reaching back into the slice, which {@code kernelDependsOnNoSlice} states directly — and the two slices
 * are closed against each other the same way, in both directions (DoD25, {@code archunit.kernel-isolation-access}).
 *
 * <p>The kernel exposes all of itself by design, so its own {@code modelIsolation} has nothing left to check; it stays
 * declared — with {@code allowEmptyShould} — because deleting it would drop the kernel from this test.
 */
@AnalyzeClasses(packages = "shop.shopping", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    public static final String KERNEL = "shop.shopping.kernel";
    public static final String BASKET = "shop.shopping.basket";
    public static final String CHECKOUT = "shop.shopping.checkout";
    public static final String CONTEXT = "shop.shopping..";

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
    public static final ArchRule checkoutAdaptersDependencies =
            ArchitectureDescription.adaptersDependencies(CHECKOUT, sharedKernelUsed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule checkoutServicesDependencies =
            ArchitectureDescription.servicesDependencies(CHECKOUT, sharedKernelUsed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule checkoutModelDependencies =
            ArchitectureDescription.modelDependencies(CHECKOUT, sharedKernelUsed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule checkoutAdaptersIsolation =
            ArchitectureDescription.adaptersIsolation(CHECKOUT).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule checkoutServicesIsolation =
            ArchitectureDescription.servicesIsolation(CHECKOUT, ArchitectureDescription.mediators).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule checkoutModelIsolation =
            ArchitectureDescription.modelIsolation(CHECKOUT, sharedKernelExposed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule kernelModelIsolation =
            ArchitectureDescription.modelIsolation(KERNEL, sharedKernelExposed).allowEmptyShould(true);

    @ArchTest
    public static final ArchRule kernelDependsOnNoSlice =
            noClasses().that().resideInAPackage(KERNEL + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(BASKET + "..", CHECKOUT + "..");

    /**
     * DoD25: the two slices of this context meet through the kernel and nowhere else, so neither may name the other's type.
     * The compiler already refuses most of it — both slices are package-private — and this rule is the assertion that the
     * contract they meet through is the kernel one, which is what a widened visibility would break first.
     *
     * <p>It is not a vacuous rule: it was exercised once with a {@code public} checkout record named from a basket type, and it
     * failed as it should. That experiment cannot be kept as a test — the reference it needs is exactly the code the rule
     * forbids — so this note is the record of it, the way {@code refusesAPortThatReservesStock} records the other one.
     */
    @ArchTest
    public static final ArchRule basketDependsOnNoCheckoutType =
            noClasses().that().resideInAPackage(BASKET + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(CHECKOUT + "..");

    @ArchTest
    public static final ArchRule checkoutDependsOnNoBasketType =
            noClasses().that().resideInAPackage(CHECKOUT + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(BASKET + "..");

    @ArchTest
    public static final ArchRule noReservationOperationIsDeclared =
            ArchitectureDescription.noReservationOperations(CONTEXT);

    /**
     * The gate above must not be vacuous: a port shaped the way fulfilment's is — one operation that holds stock — is refused
     * by it, so a future {@code reserve…} on the basket's path turns the rule red instead of quietly passing (DoD17).
     */
    @Test
    void refusesAPortThatReservesStock() {
        JavaClasses theForbiddenShape = new ClassFileImporter().importClasses(StockHoldingPort.class);

        assertThatThrownBy(() -> ArchitectureDescription.noReservationOperations(CONTEXT).check(theForbiddenShape))
                .hasMessageContaining("reserveProductQuantity");
    }

    /** Fulfilment's own port, which the basket may never reach for (R6, element 08 §Port). */
    interface StockHoldingPort {

        int reserveProductQuantity(String productId, int quantity);
    }
}
