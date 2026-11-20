package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class SemanticFirstScanTest {
    @TempDir
    lateinit var root: Path

    private val ignore get() = root.resolve(".kscope/.gitignore")

    @Test
    fun `a first scan leaves the ignore file of the index directory`() {
        SemanticTestWorkspace.create(root)
        KrosScope.open(root, OpenOptions(), { null }).use { it.scan() }
        assertEquals(EXPECTED, Files.readString(ignore))
    }

    @Test
    fun `a first rebuild leaves the ignore file of the index directory`() {
        SemanticTestWorkspace.create(root)
        KrosScope.open(root, OpenOptions(), { null }).use { it.scan(rebuild = true) }
        assertEquals(EXPECTED, Files.readString(ignore))
    }

    @Test
    fun `a first foreground scan leaves the ignore file too`() {
        SemanticTestWorkspace.create(root)
        val fake = FakeSemanticComponents(root.resolveSibling("${root.fileName}-scratch"))
        SemanticTestWorkspace.open(root, fake, SemanticIndexingMode.FOREGROUND).use { it.scan() }
        assertEquals(EXPECTED, Files.readString(ignore))
    }

    private companion object {
        const val EXPECTED = "# The kscope index is derived data and is rebuilt by a scan.\n*\n"
    }
}
