package io.github.krosstack.kscope.analyzer.compose

import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Compares output with the checked-in golden file of the same name. Run with the environment variable
 * `KSCOPE_UPDATE_GOLDEN=1` to rewrite the golden files after an intended change, then review the diff.
 */
internal object Golden {
    private val directory: Path = Path.of("src", "test", "resources", "golden")
    private val updating = System.getenv("KSCOPE_UPDATE_GOLDEN") == "1"

    fun facts(facts: List<Fact>): String = facts.joinToString("") { KscopeJson.encodeToString<Fact>(it) + "\n" }

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
