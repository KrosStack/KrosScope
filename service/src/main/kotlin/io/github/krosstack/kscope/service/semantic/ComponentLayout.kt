package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.service.components.ComponentPlatform
import io.github.krosstack.kscope.spi.embedding.ModelIdentity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readText

internal class ComponentLayout(
    private val directory: Path,
    private val model: Path,
    private val jdkHome: Path = Path.of(System.getProperty("java.home")),
    private val device: String = System.getProperty("kscope.semantic.device", "cpu"),
) {
    init {
        require(device in setOf("cpu", "cuda")) { "Unsupported semantic device: $device; expected cpu or cuda" }
    }

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
        ) + if (device == "cuda") listOf(device) else emptyList()
    }

    private fun readIdentity(): ModelIdentity = try {
        val descriptorText = model.resolve("model.json").readText()
        val json = KscopeJson.parseToJsonElement(descriptorText)
        require(json is JsonObject) { "Expected a model descriptor object" }
        val identity = ModelIdentity(
            json.string("name"),
            json.string("revision"),
            json.positiveInt("dimensions"),
            json.string("quantization"),
            if ("chunkBuilderVersion" in json) json.positiveInt("chunkBuilderVersion") else 1,
        )
        identity.copy(revision = pipelineRevision(json, descriptorText))
    } catch (failure: IOException) {
        throw IOException("Missing or unreadable model.json", failure)
    } catch (failure: IllegalArgumentException) {
        throw IllegalArgumentException("${failure.message} (model.json)", failure)
    }

    private fun pipelineRevision(json: JsonObject, descriptorText: String): String {
        val revision = json.string("revision")
        val precision = json.string("quantization")
        if (device == "cpu" && precision !in setOf("fp16", "fp32")) return revision
        val recipe = "$descriptorText\nort=1.31.1;threads=2,1;batch=16,1;device=$device"
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(recipe.toByteArray(Charsets.UTF_8)).toHexString()
        return "$revision;pipeline-v1:$device:$precision:$fingerprint"
    }

    private fun JsonObject.string(field: String): String = requireNotNull(
        (get(field) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() },
    ) { "Missing or malformed model field: $field" }

    private fun JsonObject.positiveInt(field: String): Int = requireNotNull(
        (get(field) as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull?.takeIf { it > 0 },
    ) { "Missing or malformed model field: $field" }

    private fun platform(): String {
        val platform = ComponentPlatform.current(System.getProperty("os.name"), System.getProperty("os.arch"))
        check(platform in ComponentPlatform.semanticPlatforms) { "Unsupported semantic component platform" }
        return platform
    }
}
