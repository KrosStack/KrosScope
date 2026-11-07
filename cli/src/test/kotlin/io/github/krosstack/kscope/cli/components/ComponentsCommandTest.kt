package io.github.krosstack.kscope.cli.components

import com.github.ajalt.clikt.testing.test
import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.components.ComponentEntry
import io.github.krosstack.kscope.service.components.ComponentFile
import io.github.krosstack.kscope.service.components.ComponentManifest
import io.github.krosstack.kscope.service.components.InstalledComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentsCommandTest {
    @TempDir
    lateinit var root: Path
    private val manifest = ComponentManifest(
        1,
        listOf(
            ComponentEntry(
                "sample",
                "1",
                "Apache-2.0",
                "LICENSE",
                "Sample component",
                listOf(ComponentFile("data", "any", listOf("https://example.test/data"), 7, "0".repeat(64))),
            ),
        ),
    )

    @Test
    fun `install without yes refuses outside a terminal`() {
        val result = command().test(listOf("install", "sample"))
        assertEquals(2, result.statusCode, result.output)
    }

    @Test
    fun `list json prints component state`() {
        val result = command().test(listOf("list", "--format", "json", "--workspace", root.toString()))
        val row = Json.parseToJsonElement(result.stdout).jsonArray.single().jsonObject
        assertEquals("not installed", row.getValue("state").jsonPrimitive.content)
    }

    @Test
    fun `status includes installed components missing from manifest`() {
        val target = Files.createDirectories(root.resolve("home/other/2"))
        InstalledComponent.write(InstalledComponent("other", "2", target, emptyList()))
        val result = command().test(listOf("status", "--format", "json"))
        val row = Json.parseToJsonElement(result.stdout).jsonArray.single().jsonObject
        assertEquals(
            listOf("false", target.toString()),
            listOf("matchesManifest", "directory").map { row.getValue(it).jsonPrimitive.content },
        )
    }

    @Test
    fun `install success prints pinned download and installed result`() {
        val source = Files.createDirectory(root.resolve("offline"))
        val bytes = "payload".toByteArray()
        Files.write(source.resolve("data"), bytes)
        val file = manifest.components.single().files.single().copy(
            sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
        )
        val fixture = manifest.copy(components = listOf(manifest.components.single().copy(files = listOf(file))))
        val result = ComponentsCommand(fixture, ComponentDirectory(root.resolve("home")))
            .test(listOf("install", "sample", "--yes", "--from", source.toString()))
        assertEquals(
            "sample 1  Apache-2.0  7 bytes\n  data: https://example.test/data\nInstalled sample 1.\n",
            result.stdout,
        )
    }

    @Test
    fun `install checksum failure prints pinned download and mismatch`() {
        val source = Files.createDirectory(root.resolve("offline"))
        Files.writeString(source.resolve("data"), "payload")
        val result = command().test(listOf("install", "sample", "--yes", "--from", source.toString()))
        assertEquals(
            "sample 1  Apache-2.0  7 bytes\n  data: https://example.test/data\nChecksum mismatch: data\n",
            result.stdout,
        )
    }

    private fun command() = ComponentsCommand(manifest, ComponentDirectory(root.resolve("home")))
}
