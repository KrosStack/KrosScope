package io.github.krosstack.kscope.benchmark.gold

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceGoldTest {
    private val fixture = Path.of(System.getProperty("kscope.workspaceFixture"))
    private val directory = Path.of(System.getProperty("kscope.workspaceGold"))
    private val json = Json { ignoreUnknownKeys = true }
    private val gold = json.decodeFromString<GoldRelationships>(directory.resolve("gold/relationships.json").readText())
    private val outputs = json.decodeFromString<Outputs>(directory.resolve("outputs.json").readText())
    private val addresses = gold.relationships.flatMap { listOf(it.id to it.from, it.id to it.to) }

    @Test
    fun `every relationship has its own id and a defined class, and the counts add up`() {
        assertEquals(gold.relationships.size, gold.relationships.map { it.id }.toSet().size)
        gold.relationships.forEach { assertTrue(it.relationshipClass in gold.meta.classDefinitions, it.id) }
        assertEquals(gold.meta.counts, gold.relationships.groupingBy { it.relationshipClass }.eachCount())
        gold.relationships.forEach { assertTrue(it.expectedEvidence in gold.meta.evidenceClasses, it.id) }
    }

    @Test
    fun `every address of the sources names a line of the fixture that holds it`() {
        addresses.filterNot { it.second.output }.forEach { (id, address) ->
            val file = fixture.resolve(checkNotNull(address.file) { "$id has an address without a file" })
            assertTrue(Files.isRegularFile(file), "$id: ${address.file} is not in the fixture")
            val lines = Files.readAllLines(file)
            (address.lines + listOfNotNull(address.line)).forEach { assertTrue(it in 1..lines.size, "$id: line $it") }
            val token = address.fqName?.substringAfterLast('.') ?: address.name?.substringAfterLast(' ')
            if (address.line != null && token != null) {
                val text = lines[address.line - 1]
                assertTrue(token in text, "$id: line ${address.line} of ${address.file} lacks $token")
            }
        }
    }

    @Test
    fun `every path starts with the directory of a repository of the fixture`() {
        val repositories = Files.list(fixture).use { paths -> paths.filter(Files::isDirectory).toList() }
            .map { it.fileName.toString() }.toSet()
        assertEquals(6, repositories.size)
        (addresses.mapNotNull { it.second.file } + outputs.outputs.map { it.path }).forEach { path ->
            assertTrue(path.substringBefore('/') in repositories, path)
        }
    }

    @Test
    fun `a build output in gold is one that the outputs list writes and the fixture lacks`() {
        val written = outputs.outputs.map { it.path }.toSet()
        val required = gold.relationships.filter { it.requiresOutput }
        assertTrue(required.isNotEmpty())
        addresses.filter { it.second.output }.forEach { (id, address) ->
            assertTrue(address.file in written, "$id: ${address.file} is not in the outputs list")
            assertTrue(required.any { it.id == id }, "$id names an output but does not require one")
        }
        written.forEach { assertFalse(Files.exists(fixture.resolve(it)), "$it is a build output inside the fixture") }
    }

    @Serializable
    private data class Outputs(val outputs: List<Output>)

    @Serializable
    private data class Output(val path: String, val kind: String)
}
