package io.github.krosstack.kscope.benchmark.workspace.tasks

import io.github.krosstack.kscope.benchmark.gold.GoldRelationship
import io.github.krosstack.kscope.benchmark.workspace.WorkspaceGoldLoader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceTaskGoldTest {
    private val goldDirectory = Path.of(checkNotNull(System.getProperty("kscope.workspaceGold")))
    private val fixture = Path.of(checkNotNull(System.getProperty("kscope.workspaceFixture")))
    private val tasks = WorkspaceTaskLoader.load(goldDirectory)
    private val relationships = WorkspaceGoldLoader.load(goldDirectory).relationships.associateBy { it.id }

    @Test
    fun `there are six tasks of level 4 and each has a named flag`() {
        assertEquals(listOf("W01", "W02", "W03", "W04", "W05", "W06"), tasks.map { it.id })
        assertTrue(tasks.all { it.level == LEVEL })
    }

    @Test
    fun `exactly three tasks are named`() {
        assertEquals(listOf("W01", "W03", "W05"), tasks.filter { it.named }.map { it.id })
    }

    @Test
    fun `the files to edit lie in exactly two repositories`() {
        tasks.forEach { assertEquals(2, it.editedRepositories.size, "${it.id}: ${it.mustEdit}") }
    }

    @Test
    fun `every path of a task exists in the fixture`() {
        tasks.forEach { task ->
            val paths = task.mustEdit + task.mustRead + task.symbols.map { it.file }
            paths.forEach { assertTrue(Files.isRegularFile(fixture.resolve(it)), "${task.id}: $it") }
        }
    }

    @Test
    fun `every symbol line holds the declaration that the symbol names`() {
        tasks.forEach { task ->
            task.symbols.forEach { symbol ->
                val line = checkNotNull(symbol.line) { "${task.id}: ${symbol.file} has no line" }
                val text = Files.readAllLines(fixture.resolve(symbol.file))[line - 1]
                val declaration = simpleName(symbol.fqName, symbol.name)
                assertTrue(declaration in text, "${task.id}: ${symbol.file}:$line is `$text`")
            }
        }
    }

    @Test
    fun `every relationship of a task is in the gold and joins two repositories of the task`() {
        tasks.forEach { task ->
            assertTrue(task.relationships.isNotEmpty(), task.id)
            task.relationships.forEach { id ->
                val relationship = relationships[id]
                assertTrue(relationship != null, "${task.id}: $id is not in the gold")
                val repositories = repositoriesOf(relationship)
                assertTrue(task.editedRepositories.containsAll(repositories), "${task.id}: $id joins $repositories")
            }
        }
    }

    @Test
    fun `a task that is not named names no file, class, function or path`() {
        tasks.filterNot { it.named }.forEach { task ->
            val files = (task.mustEdit + task.mustRead).map { it.substringAfterLast('/').substringBeforeLast('.') }
            val names = files + task.symbols.mapNotNull { simpleNameOrNull(it.fqName, it.name) }
            names.filter { it.length > MIN_NAME }.forEach {
                assertTrue(!task.statement.contains(it, ignoreCase = true), "${task.id} names $it")
            }
        }
    }

    private fun repositoriesOf(relationship: GoldRelationship): Set<String> =
        listOfNotNull(relationship.from.file, relationship.to.file).map { it.substringBefore('/') }.toSet()

    private fun simpleName(fqName: String?, name: String?): String =
        checkNotNull(simpleNameOrNull(fqName, name)) { "A symbol needs a name." }

    private fun simpleNameOrNull(fqName: String?, name: String?): String? = fqName?.substringAfterLast('.') ?: name

    private companion object {
        const val LEVEL = 4
        const val MIN_NAME = 5
    }
}
