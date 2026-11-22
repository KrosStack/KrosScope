package io.github.krosstack.kscope.service.lookup

import io.github.krosstack.kscope.retrieval.MlMode
import io.github.krosstack.kscope.service.context.ContextRequest
import io.github.krosstack.kscope.service.contextResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryNarrowingTest {
    @Test
    fun `a folder of one repository of several is narrowed to that repository`() {
        TaskRepositoriesScope.inTwo { scope ->
            val narrowing = assertNotNull(scope.narrowingFor("first/lib/src/main/kotlin"))

            assertEquals("first", narrowing.name)
            assertEquals(narrowing, scope.narrowingFor("first"))
            assertEquals("second", scope.narrowingFor("second/lib")?.name)
            assertEquals(scope.repositoryHolding("first"), narrowing.repository)
        }
    }

    @Test
    fun `no folder and a folder of no repository are not narrowed`() {
        TaskRepositoriesScope.inTwo { scope ->
            assertNull(scope.narrowingFor(null))
            assertNull(scope.narrowingFor("notes"))
        }
    }

    @Test
    fun `an index of one repository is not narrowed`() {
        TaskRepositoriesScope.inOne { scope -> assertNull(scope.narrowingFor("only/lib")) }
    }

    @Test
    fun `analyze within a repository ranks only the files of that repository`() {
        TaskRepositoriesScope.inTwo { scope ->
            val first = assertNotNull(scope.narrowingFor("first")).repository
            val task = TaskRepositoriesScope.TASK
            val everywhere = scope.analyze(task, includeContextEstimates = false, ml = MlMode.OFF)
            val inside = scope.analyze(task, includeContextEstimates = false, ml = MlMode.OFF, within = first)

            assertTrue(everywhere.files.any { it.artifact.repository != first })
            assertTrue(inside.files.isNotEmpty())
            assertTrue(inside.files.all { it.artifact.repository == first })
            assertTrue(inside.symbols.all { it.symbol.declaration?.artifact?.repository == first })
        }
    }

    @Test
    fun `symbol within a repository drops the matches of the others`() {
        TaskRepositoriesScope.inTwo { scope ->
            val first = assertNotNull(scope.narrowingFor("first")).repository
            val everywhere = scope.symbol("TaskStore")
            val inside = scope.symbol("TaskStore", first)

            assertTrue(everywhere.symbols.any { it.symbol.name == "TaskSync" })
            assertTrue(inside.symbols.any { it.symbol.name == "TaskReminders" })
            assertTrue(inside.symbols.none { it.symbol.name == "TaskSync" })
        }
    }

    @Test
    fun `context within a repository reads only that repository`() {
        TaskRepositoriesScope.inTwo { scope ->
            val first = assertNotNull(scope.narrowingFor("first")).repository
            val request = ContextRequest(TaskRepositoriesScope.TASK, ml = MlMode.OFF)
            val everywhere = scope.contextResult(request).context.markdown
            val inside = scope.contextResult(request.copy(within = first)).context.markdown

            assertTrue(everywhere.contains("TaskSync"))
            assertTrue(inside.contains("TaskStore"))
            assertFalse(inside.contains("TaskSync"))
        }
    }
}
