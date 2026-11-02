package io.github.krosstack.kscope.analyzer.schema

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.fail

/** Checked-in sample files of the tests. */
internal object Resources {
    fun text(name: String): String {
        val resource = checkNotNull(Resources::class.java.getResource("/samples/$name")) { "No sample $name" }
        return Files.readString(Path.of(resource.toURI())).replace("\r\n", "\n")
    }
}

/**
 * Compares output with the checked-in golden file of the same name. Run with the environment variable
 * `KSCOPE_UPDATE_GOLDEN=1` to rewrite the golden files after an intended change, then review the diff.
 */
internal object Golden {
    private val directory: Path = Path.of("src", "test", "resources", "golden")
    private val updating = System.getenv("KSCOPE_UPDATE_GOLDEN") == "1"

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
