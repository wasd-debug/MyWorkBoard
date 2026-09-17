package com.salarytracker;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AnalyzeClasses(packages = "com.salarytracker")
class ArchitectureBoundaryTest {
    private static final Path BACKEND_ROOT = backendRoot();

    private static Path backendRoot() {
        String configured = System.getProperty("maven.multiModuleProjectDirectory");
        if (configured != null && !configured.isBlank()) return Path.of(configured);
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        return "app".equals(String.valueOf(workingDirectory.getFileName()))
                ? workingDirectory.getParent() : workingDirectory;
    }

    @Test
    void businessSourcesAreOwnedByTheirPhysicalMavenModules() {
        assertModuleOwns("platform");
        assertModuleOwns("identity");
        assertModuleOwns("worktime");
        assertModuleOwns("ledger");
        Path legacySource = BACKEND_ROOT.resolve("src/main/java/com/salarytracker");
        for (String module : new String[]{"platform", "identity", "worktime", "ledger"}) {
            assertFalse(Files.exists(legacySource.resolve(module)),
                    () -> "central source root still owns " + module);
        }
    }

    private void assertModuleOwns(String module) {
        Path source = BACKEND_ROOT.resolve("modules").resolve(module)
                .resolve("src/main/java/com/salarytracker").resolve(module);
        assertTrue(Files.isDirectory(source), () -> module + " module source root is missing: " + source);
        try (var files = Files.list(source)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().endsWith(".java")),
                    () -> module + " module contains no Java sources");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("cannot inspect module sources", exception);
        }
    }

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
