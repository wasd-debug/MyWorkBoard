package com.salarytracker;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.salarytracker")
class ArchitectureBoundaryTest {
    @ArchTest
    static final ArchRule identity_does_not_depend_on_worktime = noClasses()
            .that().resideInAnyPackage("com.salarytracker.identity..")
            .should().dependOnClassesThat().resideInAnyPackage("com.salarytracker.worktime..");

    @ArchTest
    static final ArchRule worktime_does_not_depend_on_controllers = noClasses()
            .that().resideInAnyPackage("com.salarytracker.worktime..")
            .should().dependOnClassesThat().resideInAnyPackage("com.salarytracker.controller..");

    @ArchTest
    static final ArchRule ledger_does_not_depend_on_worktime = noClasses()
            .that().resideInAnyPackage("com.salarytracker.ledger..")
            .should().dependOnClassesThat().resideInAnyPackage("com.salarytracker.worktime..");

    @ArchTest
    static final ArchRule worktime_does_not_depend_on_ledger = noClasses()
            .that().resideInAnyPackage("com.salarytracker.worktime..")
            .should().dependOnClassesThat().resideInAnyPackage("com.salarytracker.ledger..");
}
