package io.github.krosstack.kscope.analyzer.kotlin.worker

import io.github.krosstack.kscope.model.CanonicalFacts
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.spi.worker.ScriptOutline
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Compares output with the checked-in golden file of the same name. Run with the environment variable
 * `KSCOPE_UPDATE_GOLDEN=1` to rewrite the golden files after an intended change, then review the diff.
 */
object Golden {
    private val directory: Path = Path.of("src", "test", "resources", "golden")
    private val updating = System.getenv("KSCOPE_UPDATE_GOLDEN") == "1"

    fun facts(batches: List<FactBatch>): String = CanonicalFacts.sorted(batches).joinToString("") { batch ->
        val header = KscopeJson.encodeToString(batch.copy(facts = emptyList()))
        (listOf(header) + batch.facts.map { KscopeJson.encodeToString<Fact>(it) }).joinToString("\n", postfix = "\n")
    }

    /** Only the relationships and the diagnostics about references, which is what the resolution pass adds. */
    fun relationships(batches: List<FactBatch>): String = facts(
        batches.map { batch ->
            batch.copy(facts = batch.facts.filter { it is Fact.OfRelationship || isReferenceDiagnostic(it) })
        },
    )

    private fun isReferenceDiagnostic(fact: Fact): Boolean =
        fact is Fact.OfDiagnostic && fact.value.code.value == "kotlin.reference-resolution-failed"

    fun outline(outline: ScriptOutline): String =
        outline.calls.joinToString("\n", postfix = "\n") { KscopeJson.encodeToString(it) }

    fun assertMatches(name: String, actual: String) {
        val file = directory.resolve(name)
        if (updating) {
            Files.createDirectories(file.parent)
            Files.writeString(file, actual)
            return
        }
        if (!Files.exists(file)) fail("Missing golden file $file; run with KSCOPE_UPDATE_GOLDEN=1 to create it")
        assertEquals(Files.readString(file).replace("\r\n", "\n"), actual, "Output differs from $file")
    }
}
