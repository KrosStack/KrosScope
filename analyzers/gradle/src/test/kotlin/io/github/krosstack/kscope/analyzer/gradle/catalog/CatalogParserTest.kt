package io.github.krosstack.kscope.analyzer.gradle.catalog

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogParserTest {
    private val text = """
        [versions]
        kotlin = "2.4.20"
        coroutines = { strictly = "1.11.0" }
        room = { require = "2.8.5" }

        [libraries]
        kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
        kotlinx_coroutines_core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version.ref = "coroutines" }
        junit = "junit:junit:4.13.2"
        androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
        room.runtime = { module = "androidx.room:room-runtime", version = "2.8.5" }

        [bundles]
        testing = [
            "kotlin-test",
            "junit",
        ]

        [plugins]
        kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
        ksp = "com.google.devtools.ksp:2.3.12"
    """.trimIndent() + "\n"

    private val file = ArtifactRef(RepositoryId("r"), RepoPath("gradle/libs.versions.toml"))
    private val catalog = CatalogParser("libs", file, text).parse().catalog

    private fun entry(section: CatalogSection, accessor: String) = assertNotNull(catalog.entry(section, accessor))

    private fun library(accessor: String) = entry(CatalogSection.LIBRARIES, accessor)

    @Test
    fun `aliases are normalized to accessor segments`() {
        assertEquals("kotlin.test", library("kotlin.test").accessor)
        assertEquals("kotlinx_coroutines_core", library("kotlinx.coroutines.core").alias)
        assertEquals("androidx.compose.material3", library("androidx.compose.material3").accessor)
        assertEquals("room.runtime", library("room.runtime").accessor)
    }

    @Test
    fun `version references and version tables are resolved`() {
        assertEquals("2.4.20", library("kotlin.test").version)
        assertEquals("1.11.0", library("kotlinx.coroutines.core").version)
        assertEquals("2.8.5", entry(CatalogSection.VERSIONS, "room").value)
        assertEquals("2.8.5", library("room.runtime").version)
        assertEquals(null, library("androidx.compose.material3").version)
    }

    @Test
    fun `libraries keep their module in every notation`() {
        assertEquals("org.jetbrains.kotlin:kotlin-test", library("kotlin.test").value)
        assertEquals("org.jetbrains.kotlinx:kotlinx-coroutines-core", library("kotlinx.coroutines.core").value)
        assertEquals("junit:junit", library("junit").value)
        assertEquals("4.13.2", library("junit").version)
    }

    @Test
    fun `plugins keep their id and version`() {
        assertEquals("org.jetbrains.kotlin.jvm", entry(CatalogSection.PLUGINS, "kotlin.jvm").value)
        assertEquals("2.4.20", entry(CatalogSection.PLUGINS, "kotlin.jvm").version)
        assertEquals("com.google.devtools.ksp", entry(CatalogSection.PLUGINS, "ksp").value)
        assertEquals("2.3.12", entry(CatalogSection.PLUGINS, "ksp").version)
    }

    @Test
    fun `bundles list their members as accessors`() {
        assertEquals(listOf("kotlin.test", "junit"), entry(CatalogSection.BUNDLES, "testing").members)
    }

    @Test
    fun `declarations span the key and the whole value`() {
        assertEquals(TextRange(2, 1, 2, 18), entry(CatalogSection.VERSIONS, "kotlin").declaration)
        assertEquals(TextRange(2, 1, 2, 7), entry(CatalogSection.VERSIONS, "kotlin").nameRange)
        assertEquals(TextRange(7, 1, 7, 86), library("kotlin.test").declaration)
        assertEquals(TextRange(7, 1, 7, 12), library("kotlin.test").nameRange)
        assertEquals(TextRange(14, 1, 17, 2), entry(CatalogSection.BUNDLES, "testing").declaration)
    }

    @Test
    fun `a catalog with a syntax error keeps what could be read and reports the error`() {
        val broken = CatalogParser("libs", file, "[versions]\nkotlin = \n").parse()
        assertTrue(broken.problems.isNotEmpty())
        assertEquals("gradle.static.catalog-unparsable", broken.problems.first().code.value)
    }

    @Test
    fun `a section that is not a table is reported and the other sections are still read`() {
        val text = """
            versions = "backup"

            [libraries]
            junit = "junit:junit:4.13.2"
        """.trimIndent() + "\n"
        val parsed = CatalogParser("backup", file, text).parse()
        assertEquals("junit:junit", parsed.catalog.entry(CatalogSection.LIBRARIES, "junit")?.value)
        val problem = parsed.problems.single()
        assertEquals("gradle.static.catalog-section-invalid", problem.code.value)
        assertTrue("versions" in problem.message)
        assertEquals(1, problem.location?.range?.startLine)
    }
}
