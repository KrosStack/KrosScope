package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.diagnostics
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Edges
import io.github.krosstack.kscope.analyzer.kotlin.worker.symbols
import kotlin.test.Test
import kotlin.test.assertEquals

/** Private top-level functions and properties belong to their file: two files may declare the same name. */
class FilePrivateIdentityTest {
    private val module = SampleWorkspace.singleModule("priv", "relations/private/main")
    private val batches by lazy { analyzeAll(module) }
    private val edges by lazy { Edges("priv@main ").of(batches) }

    @Test
    fun `equally named private declarations of two files of one package are both recorded`() {
        val collisions = batches.diagnostics().filter { it.code.value == "identity.collision" }
        assertEquals(emptyList(), collisions.map { it.message })
        val names = batches.symbols().mapNotNull { it.qualifiedName }.filter { it.startsWith("rel.priv.") }
        assertEquals(
            listOf("LIMIT", "LIMIT", "alphaTitle", "betaTitle", "label", "label"),
            names.map { it.removePrefix("rel.priv.") }.sorted(),
        )
    }

    @Test
    fun `a reference to a private declaration leads to the declaration of its own file`() {
        Edges.assertHas(
            edges,
            "rel/priv/alphaTitle(). calls -> rel/priv/AlphaKt#label(Int).",
            "rel/priv/alphaTitle(). references/read -> rel/priv/AlphaKt#LIMIT.",
            "rel/priv/betaTitle(). calls -> rel/priv/BetaKt#label(Int).",
            "rel/priv/betaTitle(). references/read -> rel/priv/BetaKt#LIMIT.",
        )
        Edges.assertLacks(
            edges,
            "rel/priv/alphaTitle(). calls -> rel/priv/BetaKt#label(Int).",
            "rel/priv/betaTitle(). calls -> rel/priv/AlphaKt#label(Int).",
        )
    }

    @Test
    fun `public top-level declarations keep their identity without a file`() {
        val ids = batches.symbols().map { Edges.text(it.id).removePrefix("priv@main ") }
        assertEquals(true, "rel/priv/alphaTitle()." in ids && "rel/priv/betaTitle()." in ids)
    }
}
