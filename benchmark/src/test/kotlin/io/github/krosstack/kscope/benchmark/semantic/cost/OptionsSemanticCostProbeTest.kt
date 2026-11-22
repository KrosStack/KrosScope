package io.github.krosstack.kscope.benchmark.semantic.cost

import io.github.krosstack.kscope.benchmark.context.ContextAnalysisIdentityTest.Fixture
import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OptionsSemanticCostProbeTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `a missing reference machine or workload stays not run`() = Fixture(root).use { fixture ->
        val options = SemanticCostOptions(
            fixture.root,
            fixture.root.resolve(".kscope"),
            root.resolve("out"),
            fixture.pipeline,
            machine = mapOf("reference" to "windows"),
            workload = SemanticCostOptions.Workload(Fixture.hash("queries"), listOf("Change anchor text")),
        )
        for (missing in listOf(options.copy(machine = null), options.copy(workload = null))) {
            var opened = false
            val result = SemanticCostProbe(
                missing,
                SemanticCostProbe.Dependencies(open = { _, _ ->
                    opened = true
                    error("Missing measurements must not open a service")
                }),
            ).run()
            assertTrue(result.notRun.any { it.contains(if (missing.machine == null) "machine" else "workload") })
            assertTrue(result.samples.isEmpty())
            assertFalse(opened)
        }
    }

    @Test
    fun `strict options reject duplicates tuning and overlapping output`() = Fixture(root).use { f ->
        val identity = root.resolve("identity.json")
        Files.writeString(identity, KscopeJson.encodeToJsonElement(f.pipeline).toString())
        val base = listOf(
            "semantic-cost", "--corpus", f.root.toString(), "--index",
            f.root.resolve(
                ".kscope",
            ).toString(),
            "--out", root.resolve("out").toString(), "--identity", identity.toString(),
        )
        val parsed = SemanticCostOptions.parse(base)
        assertEquals(f.pipeline, parsed.pipeline)
        assertEquals(null, parsed.machine)
        assertEquals(null, parsed.workload)
        for (extra in listOf(listOf("--model", "other"), listOf("--out", "other"), listOf("--workload"))) {
            assertFailsWith<IllegalArgumentException> { SemanticCostOptions.parse(base + extra) }
        }
        assertFailsWith<IllegalArgumentException> {
            SemanticCostOptions.parse(base.map { if (it == root.resolve("out").toString()) f.root.toString() else it })
        }
        assertEquals(1, runSemanticCostCommand(base, {}))
        assertTrue(Files.readString(root.resolve("out/semantic-cost.json")).contains("notRun"))
    }

    @Test
    fun `publishing replaces a destination link without changing its target`() = Fixture(root).use { fixture ->
        val identity = root.resolve("identity.json")
        Files.writeString(identity, KscopeJson.encodeToJsonElement(fixture.pipeline).toString())
        val output = Files.createDirectories(root.resolve("linked-out"))
        val destination = output.resolve("semantic-cost.json")
        val protected = fixture.root.resolve("lib/docs/anchor.md")
        val original = Files.readAllBytes(protected)
        try {
            Files.createSymbolicLink(destination, protected)
        } catch (failure: FileSystemException) {
            assumeTrue(false, "Symbolic link creation refused: ${failure.reason}")
        } catch (failure: UnsupportedOperationException) {
            assumeTrue(false, "Symbolic links unsupported: ${failure.message}")
        }
        val args = listOf(
            "semantic-cost", "--corpus", fixture.root.toString(),
            "--index", fixture.root.resolve(".kscope").toString(),
            "--out", output.toString(), "--identity", identity.toString(),
        )
        assertEquals(1, runSemanticCostCommand(args, {}))
        assertContentEquals(original, Files.readAllBytes(protected))
        assertFalse(Files.isSymbolicLink(destination))
        assertTrue(Files.readString(destination).contains("notRun"))
        assertEquals(listOf(destination), Files.list(output).use { it.toList() })
    }

    @Test
    fun `explicit missing machine and workload files are invalid inputs`() = Fixture(root).use { fixture ->
        val identity = root.resolve("identity.json")
        Files.writeString(identity, KscopeJson.encodeToJsonElement(fixture.pipeline).toString())
        val base = listOf(
            "semantic-cost", "--corpus", fixture.root.toString(),
            "--index", fixture.root.resolve(".kscope").toString(),
            "--out", root.resolve("missing-out").toString(), "--identity", identity.toString(),
        )
        for (flag in listOf("--machine", "--workload")) {
            val args = base + listOf(flag, root.resolve("absent.json").toString())
            assertFailsWith<IllegalArgumentException> { SemanticCostOptions.parse(args) }
            val messages = mutableListOf<String>()
            assertEquals(1, runSemanticCostCommand(args, messages::add))
            assertTrue(messages.single().startsWith("Invalid semantic cost probe:"))
            assertFalse(Files.exists(root.resolve("missing-out")))
        }
    }
}
