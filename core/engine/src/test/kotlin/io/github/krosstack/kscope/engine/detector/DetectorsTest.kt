package io.github.krosstack.kscope.engine.detector

import io.github.krosstack.kscope.engine.CoreCapabilities
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.spi.ArtifactCandidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetectorsTest {
    private val detectors = CoreCapabilities.detectors().sortedBy { it.id }

    private fun classify(path: String): String? {
        val candidate = ArtifactCandidate(RepoPath(path), sizeBytes = 1) { ByteArray(0) }
        return detectors.firstNotNullOfOrNull { it.detect(candidate) }?.id
    }

    @Test
    fun `source files are classified by language`() {
        assertEquals("kotlin.source", classify("src/main/kotlin/A.kt"))
        assertEquals("java.source", classify("src/main/java/A.java"))
        assertEquals("kotlin.script", classify("scripts/tool.kts"))
    }

    @Test
    fun `gradle files are told apart from plain kotlin scripts`() {
        assertEquals("gradle.build-script", classify("app/build.gradle.kts"))
        assertEquals("gradle.build-script", classify("app/build.gradle"))
        assertEquals("gradle.settings", classify("settings.gradle.kts"))
        assertEquals("gradle.settings", classify("settings.gradle"))
        assertEquals("kotlin.script", classify("build-logic/src/main/kotlin/convention.gradle.kts"))
    }

    @Test
    fun `version catalogs and gradle properties are recognised`() {
        assertEquals("gradle.version-catalog", classify("gradle/libs.versions.toml"))
        assertEquals("gradle.version-catalog", classify("gradle/tools.versions.toml"))
        assertEquals("gradle.properties", classify("gradle.properties"))
        assertEquals("gradle.properties", classify("app/gradle.properties"))
    }

    @Test
    fun `markdown documents are documentation`() {
        assertEquals("markdown", classify("README.md"))
        assertEquals("markdown", classify("docs/guide.markdown"))
        val type = detectors.firstNotNullOf { it.detect(ArtifactCandidate(RepoPath("a.md"), 1) { ByteArray(0) }) }
        assertEquals(ArtifactCategory.DOCUMENTATION, type.category)
    }

    @Test
    fun `names are matched regardless of case`() {
        assertEquals("kotlin.source", classify("src/Main.KT"))
        assertEquals("gradle.build-script", classify("app/Build.Gradle.Kts"))
    }

    @Test
    fun `other files are not detected`() {
        assertNull(classify("notes.txt"))
        assertNull(classify("app/src/main/AndroidManifest.xml"))
        assertNull(classify("build.gradle.kts.bak"))
    }
}
