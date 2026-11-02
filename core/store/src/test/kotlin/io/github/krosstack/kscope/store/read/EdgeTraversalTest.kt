package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.store.Builders
import io.github.krosstack.kscope.store.read.EdgeFixtures.extendsEdge
import io.github.krosstack.kscope.store.read.EdgeFixtures.probableCall
import io.github.krosstack.kscope.store.read.EdgeFixtures.resolvedCall
import io.github.krosstack.kscope.store.read.EdgeFixtures.unanalysedImport
import io.github.krosstack.kscope.store.read.EdgeFixtures.unresolvedCall
import io.github.krosstack.kscope.store.read.EdgeFixtures.withEdges
import kotlin.test.Test
import kotlin.test.assertEquals

class EdgeTraversalTest {
    private val a = Builders.symbolRef("a")

    @Test
    fun `outgoing returns every edge leaving a node including unresolved and not analysed ones`() = withEdges { store ->
        assertEquals(
            setOf(resolvedCall, probableCall, unresolvedCall, unanalysedImport),
            store.outgoing(a).toSet(),
        )
        assertEquals(4, store.outgoing(a).size)
    }

    @Test
    fun `outgoing can be limited to some kinds`() = withEdges { store ->
        assertEquals(listOf(unanalysedImport), store.outgoing(a, setOf(RelationKind.IMPORTS)))
        assertEquals(
            setOf(resolvedCall, probableCall, unresolvedCall),
            store.outgoing(a, setOf(RelationKind.CALLS)).toSet(),
        )
        assertEquals(4, store.outgoing(a, setOf(RelationKind.CALLS, RelationKind.IMPORTS)).size)
    }

    @Test
    fun `incoming returns the resolved edges that target a node`() = withEdges { store ->
        assertEquals(listOf(resolvedCall), store.incoming(Builders.symbolRef("b")))
        assertEquals(setOf(probableCall, extendsEdge), store.incoming(Builders.symbolRef("c")).toSet())
        assertEquals(listOf(extendsEdge), store.incoming(Builders.symbolRef("c"), setOf(RelationKind.EXTENDS)))
    }

    @Test
    fun `unresolved edges have no incoming side`() = withEdges { store ->
        assertEquals(emptyList(), store.incoming(Builders.symbolRef("ghost")))
        assertEquals(emptyList(), store.incoming(Builders.symbolRef("jni")))
    }

    @Test
    fun `a node of another kind with the same key is not confused with a symbol`() = withEdges { store ->
        val module = NodeRef.OfModule(ModuleId("a"))
        assertEquals(emptyList(), store.outgoing(module))
        assertEquals(emptyList(), store.incoming(module))
    }
}
