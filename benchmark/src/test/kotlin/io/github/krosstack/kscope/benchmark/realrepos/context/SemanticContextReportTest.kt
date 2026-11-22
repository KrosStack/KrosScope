package io.github.krosstack.kscope.benchmark.realrepos.context

import io.github.krosstack.kscope.benchmark.context.semantic.SemanticContextGate
import io.github.krosstack.kscope.benchmark.realrepos.context.SemanticContextRunTest.Companion.hash
import io.github.krosstack.kscope.benchmark.realrepos.context.SemanticContextRunTest.Fixture
import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SemanticContextReportTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `report replacement preserves previously linked report files`() = Fixture(root).use { f ->
        val output = f.options.output
        val expected = f.options.tasks(f.manifest)
        SemanticContextReport.write(emptyList(), output, expected)
        val names = listOf("semantic-context-report.json", "semantic-context-report.md")
        val previous = names.associateWith { Files.readString(output.resolve(it)) }
        names.forEach { Files.createLink(root.resolve(it), output.resolve(it)) }
        val record = SemanticContextRecord(expected.first(), hash("run"), failure = "Unavailable generation")
        SemanticContextReport.write(listOf(record), output, expected)
        for (name in names) {
            assertEquals(previous.getValue(name), Files.readString(root.resolve(name)))
            assertNotEquals(previous.getValue(name), Files.readString(output.resolve(name)))
        }
    }

    @Test
    fun `report publication excludes competing runs and matches the retried records`() = Fixture(root).use { f ->
        val failed = f.runner { _, _ -> error("Unavailable generation") }.run(f.manifest) { records ->
            SemanticContextReport.write(records, f.options.output, f.options.tasks(f.manifest))
            records
        }
        assertTrue(failed.single().failure != null)
        val completed = f.runner().run(f.manifest) { records ->
            val competitors = listOf(
                f.flags + ("--scratch" to root.resolve("other-scratch").toString()),
                f.flags + ("--out" to root.resolve("other-output").toString()),
            )
            for (flags in competitors) {
                assertFailsWith<IllegalStateException> {
                    SemanticContextRun(SemanticContextOptions(flags), recipe = { hash("recipe") }).run(f.manifest)
                }
            }
            SemanticContextReport.write(records, f.options.output, f.options.tasks(f.manifest))
            records
        }
        assertEquals(2, completed.size)
        assertTrue(completed.all { it.complete })
        val checkpoint = SemanticContextCheckpoint(f.options.output)
        assertEquals(completed, completed.map { checkpoint.load(it.task, it.identity) })
        assertReport(completed, f)
        assertEquals(completed, f.runner().run(f.manifest))
    }

    private fun assertReport(records: List<SemanticContextRecord>, fixture: Fixture) {
        val output = fixture.options.output
        val json = KscopeJson.parseToJsonElement(
            Files.readString(output.resolve("semantic-context-report.json")),
        ).jsonObject
        assertEquals("2", json.getValue("expected").jsonPrimitive.content)
        assertEquals("2", json.getValue("complete").jsonPrimitive.content)
        val verdict = SemanticContextGate.evaluate(records, fixture.options.tasks(fixture.manifest))
        assertEquals(verdict.regressionPassed.toString(), json.getValue("regressionPassed").jsonPrimitive.content)
        assertEquals(KscopeJson.encodeToJsonElement(verdict), json.getValue("verdict"))
        assertEquals(
            KscopeJson.encodeToJsonElement(
                records.mapNotNull { it.pipeline }.distinct().sortedBy { it.fingerprint().value },
            ),
            json.getValue("pipelines"),
        )
        assertEquals(
            SemanticContextGate.markdown(verdict),
            Files.readString(output.resolve("semantic-context-report.md")),
        )
        assertTrue(Files.list(output).use { paths -> paths.noneMatch { it.toString().endsWith(".pending") } })
    }
}
