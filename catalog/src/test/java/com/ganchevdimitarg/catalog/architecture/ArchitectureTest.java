package com.ganchevdimitarg.catalog.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTag;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Tag;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@Tag("unit")
@ArchTag("unit") // JUnit's @Tag is invisible to the ArchUnit engine; ArchTag makes -Dgroups=unit select these rules.
@AnalyzeClasses(packages = "com.ganchevdimitarg.catalog",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllers_do_not_use_repositories =
            noClasses().that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule repositories_do_not_use_services_or_controllers =
            noClasses().that().resideInAPackage("..repository..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..service..", "..controller..");

    @ArchTest
    static final ArchRule services_do_not_use_controllers =
            noClasses().that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule domain_is_independent =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..controller..", "..service..", "..repository..",
                            "..dto..", "..config..");

    @ArchTest
    static final ArchRule mapper_does_not_use_services_or_repositories =
            noClasses().that().resideInAPackage("..mapper..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..service..", "..repository..", "..controller..");

    @ArchTest
    static final ArchRule idempotency_does_not_use_services_or_controllers =
            noClasses().that().resideInAPackage("..idempotency..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..service..", "..controller..");

    @ArchTest
    static final ArchRule packages_are_free_of_cycles =
            slices().matching("com.ganchevdimitarg.catalog.(*)..").should().beFreeOfCycles();
}
