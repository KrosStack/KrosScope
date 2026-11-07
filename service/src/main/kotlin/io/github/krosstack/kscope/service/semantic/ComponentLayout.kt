package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.readText

internal class ComponentLayout(
    private val directory: Path,
    private val model: Path,
    private val jdkHome: Path = Path.of(System.getProperty("java.home")),
) {
    val identity: ModelIdentity = readIdentity()

    fun command(): List<String> {
        require(Files.isDirectory(directory.resolve("lib"), NOFOLLOW_LINKS)) { "Missing worker payload: lib" }
        return listOf(
            jdkHome.resolve("bin/java").toString(),
            "-Donnxruntime.native.path=${directory.resolve("native").resolve(platform())}",
            "-cp",
            "${directory.resolve("lib")}/*",
            "io.github.krosstack.kscope.semantic.worker.Main",
            model.toString(),
        )
    }

    private fun readIdentity(): ModelIdentity = try {
        val json = KscopeJson.parseToJsonElement(model.resolve("model.json").readText())
        require(json is JsonObject) { "Expected a model descriptor object" }
        ModelIdentity(
            json.string("name"),
            json.string("revision"),
            json.positiveInt("dimensions"),
            json.string("quantization"),
            if ("chunkBuilderVersion" in json) json.positiveInt("chunkBuilderVersion") else 1,
        )
    } catch (failure: IOException) {
        throw IOException("Missing or unreadable model.json", failure)
    } catch (failure: IllegalArgumentException) {
        throw IllegalArgumentException("${failure.message} (model.json)", failure)
    }

    private fun JsonObject.string(field: String): String = requireNotNull(
        (get(field) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() },
    ) { "Missing or malformed model field: $field" }

    private fun JsonObject.positiveInt(field: String): Int = requireNotNull(
        (get(field) as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull?.takeIf { it > 0 },
    ) { "Missing or malformed model field: $field" }

    private fun platform(): String {
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        val architecture = System.getProperty("os.arch").lowercase(Locale.ROOT)
        val arm = architecture in setOf("aarch64", "arm64")
        require(arm || architecture in setOf("amd64", "x86_64")) { "Unsupported semantic component architecture" }
        return when {
            os.startsWith("windows") && !arm -> "windows-x64"
            os.startsWith("linux") -> if (arm) "linux-aarch64" else "linux-x64"
            os.startsWith("mac") && arm -> "macos-arm64"
            else -> error("Unsupported semantic component platform")
        }
    }
}
