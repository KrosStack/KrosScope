package io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.javarefs

import io.github.krosstack.kscope.analyzer.kotlin.worker.SampleWorkspace
import io.github.krosstack.kscope.analyzer.kotlin.worker.analyzeAll
import io.github.krosstack.kscope.analyzer.kotlin.worker.relationships.Edges
import kotlin.test.Test

/** The methods of an anonymous class are not symbols, so their overrides belong to the symbol around the class. */
class JavaAnonymousOverrideRelationshipsTest {
    private val edges by lazy {
        Edges("anonymous@main ").of(analyzeAll(SampleWorkspace.singleModule("anonymous", "relations/anonymous/main")))
    }

    @Test
    fun `a method of an anonymous class overrides the interface method for the enclosing method`() {
        Edges.assertHas(
            edges,
            "rel/anon/Factory#create(). implements/anonymous -> rel/anon/Task#",
            "rel/anon/Factory#create(). overrides/anonymous -> rel/anon/Task#run().",
        )
    }
}
