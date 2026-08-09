package com.wego.architecture

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

@AnalyzeClasses(
    packages = ["com.wego"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class DomainIsolationTest {
    companion object {
        @ArchTest
        @JvmField
        val domainMustNotDependOnFrameworkOrInfrastructure: ArchRule =
            noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    "org.springframework..",
                    "org.jooq..",
                    "jakarta.servlet..",
                    "..infrastructure..",
                )
    }
}
