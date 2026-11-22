package io.github.krosstack.kscope.service.lookup

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.resolveNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SymbolNarrowingTest {
    private fun KrosScope.inFirst(query: String): NarrowedSymbol {
        val resolved = resolveNode(query).candidates.single()
        return symbolWithin(query, resolved, narrowingFor("first"))
    }

    private fun NarrowedSymbol.names(): List<String> = result.symbols.map { it.symbol.name }

    @Test
    fun `an identity that lies outside the narrowing is shown and its repository is named`() {
        TaskRepositoriesScope.inTwoQualified { scope ->
            listOf("sync.TaskSync", scope.resolveNode("sync.TaskSync").candidates.single()).forEach { identity ->
                val answer = scope.inFirst(identity)

                assertEquals("TaskSync", answer.names().first(), identity)
                assertEquals("first", answer.origin.narrowing?.name, identity)
                assertEquals("second", answer.origin.outside?.name, identity)
                assertNull(answer.origin.widenedFrom, identity)
                assertTrue(answer.names().none { it == "TaskClock" }, identity)
            }
        }
    }

    @Test
    fun `a simple name with a declaration inside the narrowing is a narrowed search`() {
        TaskRepositoriesScope.inTwoQualified { scope ->
            val answer = scope.inFirst("TaskStore")

            assertEquals("first", answer.origin.narrowing?.name)
            assertNull(answer.origin.outside)
            assertNull(answer.origin.widenedFrom)
            assertEquals("TaskStore", answer.names().first())
            assertTrue(answer.names().none { it == "TaskSync" || it == "TaskClock" })
        }
    }

    @Test
    fun `a simple name that only a declaration outside the narrowing bears is widened to the workspace`() {
        TaskRepositoriesScope.inTwoQualified { scope ->
            val answer = scope.inFirst("TaskSync")

            assertNull(answer.origin.narrowing)
            assertNull(answer.origin.outside)
            assertEquals("first", answer.origin.widenedFrom?.name)
            assertEquals("TaskSync", answer.names().first())
            assertTrue("TaskStore" in answer.names())
        }
    }

    @Test
    fun `a search with a match inside the narrowing stays inside it`() {
        TaskRepositoriesScope.inTwoQualified { scope ->
            val answer = scope.inFirst("reminders")

            assertEquals("first", answer.origin.narrowing?.name)
            assertNull(answer.origin.widenedFrom)
            assertEquals(listOf("TaskReminders"), answer.names().filter { it == "TaskReminders" })
            assertTrue(answer.names().none { it == "TaskSync" || it == "TaskClock" })
        }
    }

    @Test
    fun `a search that matches nothing inside the narrowing but something outside it is widened`() {
        TaskRepositoriesScope.inTwoQualified { scope ->
            val answer = scope.inFirst("clock")

            assertNull(answer.origin.narrowing)
            assertEquals("first", answer.origin.widenedFrom?.name)
            assertTrue("TaskClock" in answer.names())
        }
    }

    @Test
    fun `a search that matches nothing anywhere stays narrowed and empty`() {
        TaskRepositoriesScope.inTwoQualified { scope ->
            val answer = scope.inFirst("unmatched")

            assertEquals("first", answer.origin.narrowing?.name)
            assertNull(answer.origin.widenedFrom)
            assertTrue(answer.result.symbols.isEmpty())
        }
    }

    @Test
    fun `a capped search with nothing inside the narrowing is not widened and says it may be incomplete`() {
        TaskRepositoriesScope.inTwoQualified { scope ->
            val narrowing = checkNotNull(scope.narrowingFor("first"))
            val inside = scope.symbol("clock", narrowing.repository).copy(searchCapped = true)
            var searchedEverywhere = false

            val answer = searchedInside(inside, narrowing) {
                searchedEverywhere = true
                scope.symbol("clock")
            }

            assertFalse(searchedEverywhere)
            assertEquals("first", answer.origin.narrowing?.name)
            assertNull(answer.origin.widenedFrom)
            assertTrue(answer.origin.incomplete)
            assertTrue(answer.result.symbols.isEmpty())
        }
    }

    @Test
    fun `a complete search with nothing inside the narrowing is widened`() {
        TaskRepositoriesScope.inTwoQualified { scope ->
            val narrowing = checkNotNull(scope.narrowingFor("first"))
            val inside = scope.symbol("clock", narrowing.repository)

            val answer = searchedInside(inside, narrowing) { scope.symbol("clock") }

            assertFalse(inside.searchCapped)
            assertNull(answer.origin.narrowing)
            assertEquals("first", answer.origin.widenedFrom?.name)
            assertFalse(answer.origin.incomplete)
            assertTrue("TaskClock" in answer.names())
        }
    }

    @Test
    fun `without a narrowing the answer has no origin`() {
        TaskRepositoriesScope.inTwoQualified { scope ->
            val answer = scope.symbolWithin("TaskSync", scope.resolveNode("TaskSync").candidates.single(), null)

            assertEquals(SearchOrigin(), answer.origin)
            assertNotNull(answer.names().firstOrNull { it == "TaskSync" })
        }
    }
}
