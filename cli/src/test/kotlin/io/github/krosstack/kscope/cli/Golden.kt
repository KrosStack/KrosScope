package io.github.krosstack.kscope.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

/**
 * Compares text with the golden files under `src/test/resources/golden`. A text that differs is written to
 * `build/golden-actual`, so that an intended change can be reviewed and copied over.
 */
internal object Golden {
    private val actualDirectory: Path = Path.of("build", "golden-actual")

    fun assertMatches(name: String, actual: String) = assertAllMatch(mapOf(name to actual))

    /** Checks every text before failing, so that one run shows all that differ. */
    fun assertAllMatch(texts: Map<String, String>) {
        val differing = texts.filter { (name, actual) -> !matches(name, normalize(actual)) }
        assertEquals(emptyList(), differing.keys.toList(), "these differ from golden; see build/golden-actual")
    }

    private fun normalize(text: String): String = text.replace("\r\n", "\n").trimEnd() + "\n"

    private fun matches(name: String, normalized: String): Boolean {
        val resource = Golden::class.java.getResource("/golden/$name.txt")
        val expected = resource?.readText(Charsets.UTF_8)?.replace("\r\n", "\n")
        if (expected == normalized) return true
        Files.createDirectories(actualDirectory)
        Files.writeString(actualDirectory.resolve("$name.txt"), normalized, Charsets.UTF_8)
        return false
    }
}

/** A workspace of two modules, one depending on the other, with a test; small enough to read its results by eye. */
internal fun <T> inGreeterWorkspace(block: (Path) -> T): T {
    val root = Files.createTempDirectory("kscope-golden")
    try {
        writeFile(root, "settings.gradle.kts", "include(\":app\", \":lib\")\n")
        writeFile(root, "lib/build.gradle.kts", "")
        writeFile(
            root,
            "lib/src/main/kotlin/Greeter.kt",
            "package greet\n\nclass Greeter {\n    fun greet(name: String): String = \"Hello, \$name\"\n}\n",
        )
        writeFile(
            root,
            "app/build.gradle.kts",
            "dependencies {\n    implementation(project(\":lib\"))\n}\n",
        )
        writeFile(
            root,
            "app/src/main/kotlin/Main.kt",
            "package app\n\nimport greet.Greeter\n\nclass Main {\n    private val greeter = Greeter()\n\n" +
                "    fun run(): String = greeter.greet(\"world\")\n}\n",
        )
        writeFile(
            root,
            "lib/src/test/kotlin/GreeterTest.kt",
            "package greet\n\nclass GreeterTest {\n    fun greetsByName() = Greeter().greet(\"x\")\n}\n",
        )
        writeFile(root, "README.md", "# Greeter\n\nSays hello.\n")
        return block(root)
    } finally {
        root.toFile().deleteRecursively()
    }
}
