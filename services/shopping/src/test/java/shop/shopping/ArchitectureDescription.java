package shop.shopping;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.syntax.elements.ClassesShouldConjunction;
import com.tngtech.archunit.lang.ArchRule;

import java.util.Arrays;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.base.DescribedPredicate.or;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.*;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

public class ArchitectureDescription {

    public static final DescribedPredicate<JavaClass> adapters = or(
            simpleNameEndingWith("Controller"),
            simpleNameEndingWith("Repository"),
            simpleNameEndingWith("Projection"),
            simpleNameEndingWith("Entity"),
            simpleNameEndingWith("Integration"),
            simpleNameEndingWith("Listener"),
            simpleNameEndingWith("Client")
    );

    public static final DescribedPredicate<JavaClass> services = simpleNameEndingWith("Service");

    public static final DescribedPredicate<JavaClass> model = not(services.or(adapters));

    public static final DescribedPredicate<JavaClass> mediators = resideInAPackage(
            "..mediators.."
    );

    public static ClassesShouldConjunction adaptersDependencies(String aPackage, DescribedPredicate<JavaClass> sharedKernelUsed) {
        return classes()
                .that(adapters).and().resideInAPackage(aPackage)
                .should()
                .onlyDependOnClassesThat(or(
                        sharedKernelUsed,
                        resideInAnyPackage(aPackage),
                        resideInAnyPackage("shop.shopping.tools.."),
                        resideOutsideOfPackage(parentOf(aPackage) + "..")
                ));
    }

    public static ClassesShouldConjunction servicesDependencies(String aPackage, DescribedPredicate<JavaClass> sharedKernelUsed) {
        return classes()
                .that(services).and().resideInAPackage(aPackage)
                .should()
                .onlyDependOnClassesThat(or(
                        sharedKernelUsed,
                        resideInAnyPackage(aPackage, "java..", "lombok.."),
                        resideInAnyPackage("org.springframework.stereotype",
                                "org.springframework.transaction.annotation",
                                "org.springframework.context..")
                ));
    }

    public static ClassesShouldConjunction modelDependencies(String aPackage, DescribedPredicate<JavaClass> sharedKernelUsed) {
        return classes()
                .that(model).and().resideInAPackage(aPackage)
                .and().areTopLevelClasses()
                .should()
                .onlyDependOnClassesThat(or(
                        model,
                        sharedKernelUsed,
                        resideInAnyPackage("java..", "lombok.."),
                        resideInAnyPackage("jakarta.validation.."),
                        resideInAnyPackage("com.fasterxml.jackson..")
                ));
    }

    public static ClassesShouldConjunction adaptersIsolation(String aPackage) {
        return classes()
                .that(adapters).and().resideInAPackage(aPackage)
                .should()
                .onlyBeAccessed().byClassesThat()
                .resideInAPackage(aPackage);
    }

    public static ClassesShouldConjunction servicesIsolation(String aPackage, DescribedPredicate<JavaClass> isAllowedUsers) {
        return classes()
                .that(services).and().resideInAPackage(aPackage)
                .should()
                .onlyBeAccessed().byClassesThat(
                        resideInAnyPackage(aPackage, aPackage + "..").or(mediators).or(isAllowedUsers)
                );
    }

    /**
     * Element 08 R6, DoD17: a reservation operation on the basket's path is the one leak this context is specified against,
     * so none may be declared anywhere in it — not on a port, not on an adapter, not as a helper nobody notices.
     *
     * <p>{@code reserveProductQuantity} belongs to fulfilment ({@code kernel/WarehouseStock.java:29}), where it is explicitly
     * not idempotent: a repeated call reserves twice. The rule is name-shaped because the intent is that the operation does
     * not exist in this context at all, and the vocabulary of an operation is its name. Match nothing is the rule holding, so
     * an empty result is allowed rather than reported as a rule that checked nothing.
     *
     * @param aPackage the context package as a {@code ..}-suffixed pattern, so every slice of it is covered
     * @return the rule that no such method is declared there
     */
    public static ArchRule noReservationOperations(String aPackage) {
        return noMethods()
                .that().haveNameMatching(RESERVATION_SHAPED)
                .should().beDeclaredInClassesThat().resideInAPackage(aPackage)
                .allowEmptyShould(true);
    }

    public static ClassesShouldConjunction modelIsolation(String aPackage, DescribedPredicate<JavaClass> sharedKernelExposed) {
        return classes()
                .that(model).and().resideInAPackage(aPackage)
                .and(not(sharedKernelExposed))
                .should()
                .onlyBeAccessed().byClassesThat(
                        resideInAnyPackage(aPackage, aPackage + "..")
                );
    }

    private static final String RESERVATION_SHAPED = "(?i)reserve.*";

    private static String parentOf(String aPackage) {
        String[] packages = aPackage.split("\\.");
        return String.join(".", Arrays.copyOf(packages, packages.length - 1));
    }
}
