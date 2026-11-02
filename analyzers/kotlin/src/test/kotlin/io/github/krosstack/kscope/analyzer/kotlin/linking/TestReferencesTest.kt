package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals

internal class TestReferencesTest {
    private val draft = GraphDraft()
    private val main = draft.sourceSet(":app", "main")
    private val test = draft.sourceSet(":app", "test", SetShape(SourceSetRole.UNIT_TEST))

    @Test
    @Timeout(10)
    fun `a test and a declaration that are their own containers are each read once`() {
        val subject = draft.type(main, "app.Subject")
        val check = draft.type(test, "app.SubjectTest", setOf(SymbolFlag.TEST))
        draft.relate(check, RelationKind.CALLS, subject)
        val plain = draft.graph()
        val looped = DraftGraph(
            plain.structure(),
            plain.symbols(SymbolQuery(limit = Int.MAX_VALUE)).map { it.copy(container = it.id) },
            plain.relationships(RelationshipQuery(limit = Int.MAX_VALUE)),
            plain.artifacts(),
        )

        val reached = TestReferences(looped).reachedBy(checkNotNull(looped.symbol(check.id)))

        assertEquals(listOf(listOf(subject.id)), reached.map { use -> use.chain.map { it.id } })
    }
}
