package io.github.krosstack.kscope.service.components

import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ComponentManifestTest {
    private val file = ComponentFile("data", "any", listOf("https://example.test/data"), 1, "0".repeat(64))
    private val entry = ComponentEntry("sample", "1", "Apache-2.0", "LICENSE", "Sample", listOf(file))

    @Test
    fun `duplicate component names are rejected`() {
        val json = KscopeJson.encodeToString(ComponentManifest(1, listOf(entry, entry.copy(version = "2"))))
        assertFailsWith<IllegalArgumentException> { ComponentManifest.read(json) }
    }

    @Test
    fun `duplicate file paths are rejected`() {
        assertFailsWith<IllegalArgumentException> { ComponentManifest.validate(entry.copy(files = listOf(file, file))) }
    }

    @Test
    fun `unknown platform is rejected`() {
        rejects(file.copy(platform = "unknown"))
    }

    @Test
    fun `short sha256 is rejected`() {
        rejects(file.copy(sha256 = "0".repeat(63)))
    }

    @Test
    fun `non hexadecimal sha256 is rejected`() {
        rejects(file.copy(sha256 = "g".repeat(64)))
    }

    @Test
    fun `zero size is rejected`() {
        rejects(file.copy(size = 0))
    }

    @Test
    fun `negative size is rejected`() {
        rejects(file.copy(size = -1))
    }

    @Test
    fun `empty file list is rejected`() {
        assertFailsWith<IllegalArgumentException> { ComponentManifest.validate(entry.copy(files = emptyList())) }
    }

    @Test
    fun `unknown manifest key is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            ComponentManifest.read("""{"schemaVersion":1,"components":[],"extra":true}""")
        }
    }

    private fun rejects(file: ComponentFile) {
        assertFailsWith<IllegalArgumentException> { ComponentManifest.validate(entry.copy(files = listOf(file))) }
    }

    @TestFactory
    fun `reserved component metadata is rejected`() = listOf(
        entry.copy(version = "1.installing-release"),
        entry.copy(version = "1.replacing-release"),
        entry.copy(name = "sample.installing-release"),
        entry.copy(name = "sample.replacing-release"),
        entry.copy(files = listOf(file.copy(path = "KSCOPE-STAGING.JSON"))),
    ).map { candidate ->
        dynamicTest("rejects ${candidate.name} ${candidate.version} ${candidate.files.single().path}") {
            assertFailsWith<IllegalArgumentException> { ComponentManifest.validate(candidate) }
        }
    }
}
