package io.github.krosstack.kscope.analyzer.gradle.statics.structure

import io.github.krosstack.kscope.analyzer.gradle.statics.StaticWorkspace
import io.github.krosstack.kscope.analyzer.gradle.statics.problems
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.RelationKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogReferencesStaticTest {
    private val tree = StaticWorkspace("catalog-kotlin")
    private val provided = tree.provided()
    private val facts = CatalogFacts(provided)

    @AfterTest
    fun cleanUp() {
        tree.close()
    }

    @Test
    fun `scripts reference the catalog entries they use with the call site`() {
        val ids = facts.targetsOf("app/build.gradle.kts").toMap()
        assertEquals(2, ids.getValue(facts.symbol("plugin", "kotlin.jvm").id))
        assertEquals(3, ids.getValue(facts.symbol("plugin", "ksp").id))
        assertEquals(7, ids.getValue(facts.symbol("library", "kotlinx.coroutines.core").id))
        assertEquals(8, ids.getValue(facts.symbol("bundle", "testing").id))
        assertEquals(9, ids.getValue(facts.symbol("library", "junit").id))
        assertEquals(10, ids.getValue(facts.symbol("library", "room.compiler").id))
    }

    @Test
    fun `a build that imports the catalog references the same symbols`() {
        assertEquals(
            listOf(facts.symbol("plugin", "kotlin.jvm").id),
            facts.targetsOf("build.gradle.kts").map { it.first },
        )
        assertEquals(
            listOf(facts.symbol("library", "kotlin.gradle.plugin").id),
            facts.targetsOf("build-logic/build.gradle.kts").map { it.first },
        )
    }

    @Test
    fun `references are build model relationships of the references kind`() {
        assertEquals(setOf(RelationKind.REFERENCES), facts.references.map { it.kind }.toSet())
        assertEquals(setOf(EvidenceClass.BUILD_MODEL), facts.references.map { it.evidenceClass }.toSet())
        assertEquals(
            setOf("catalog.library", "catalog.plugin", "catalog.bundle"),
            facts.references.mapNotNull { it.subKind }.toSet(),
        )
    }

    @Test
    fun `an alias that the catalog does not have is a warning and not a reference`() {
        val warning = provided.problems("gradle.static.catalog-alias-unresolved").single()
        assertEquals(DiagnosticSeverity.WARNING, warning.severity)
        assertEquals(11, warning.location?.range?.startLine)
        assertEquals(6, facts.targetsOf("app/build.gradle.kts").size)
    }
}
