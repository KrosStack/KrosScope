package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import kotlin.test.Test

/** An unresolved type written with its package keeps the whole name as its hint, as an import does. */
class QualifiedTypeHintRelationshipsTest {
    private val module =
        RelationSamples.withClasspath(SampleWorkspace.singleModule("qualified", "relations/qualified/main"))
    private val edges by lazy { Edges("qualified@main ").of(analyzeAll(module)) }
    private val unresolved = "kotlin.unresolved-reference"

    @Test
    fun `a Kotlin type written with its package is unresolved under its qualified name`() {
        Edges.assertHas(edges, "rel/qualified/Holder#value. type-uses -> ?lib.api.Greeter ($unresolved)")
    }

    @Test
    fun `a Java type written with its package is unresolved under its qualified name`() {
        val javaHolder = "rel/qualified/JavaHolder#"
        Edges.assertHas(
            edges,
            "${javaHolder}value. type-uses -> ?lib.api.Greeter ($unresolved)",
            "${javaHolder}made. calls -> ?lib.api.Maker ($unresolved)",
        )
    }

    @Test
    fun `a type written without a package keeps its simple name as the hint`() {
        Edges.assertHas(
            edges,
            "rel/qualified/Holder#plain. type-uses -> ?Greeter ($unresolved)",
            "rel/qualified/JavaHolder#plain. type-uses -> ?Greeter ($unresolved)",
        )
    }
}
