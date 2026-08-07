package com.gabojago.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** HTTP 계층이 영속성 계층을 우회하지 않도록 최소 의존 방향을 고정한다. */
@AnalyzeClasses(packages = "com.gabojago")
class LayerArchitectureTest {

    @ArchTest
    static final ArchRule controllersMustNotDependOnRepositories = noClasses()
            .that().resideInAnyPackage("..controller..")
            .should().dependOnClassesThat().resideInAnyPackage("..repository..");
}
