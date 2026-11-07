package io.github.krosstack.kscope.service.components

import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

internal object ComponentStaging {
    internal const val MARKER = "kscope-staging.json"
    internal val reservedInfixes = listOf(".installing-", ".replacing-")

    internal fun write(directory: Path, entry: ComponentEntry) {
        val marker = mapOf("name" to entry.name, "version" to entry.version)
        Files.writeString(directory.resolve(MARKER), KscopeJson.encodeToString(marker))
    }

    internal fun owns(directory: Path): Boolean {
        val folder = directory.fileName.toString()
        val version = folder.substringBefore(".installing-")
        val installed = InstalledComponent.read(directory)?.takeIf { it.problem == null }
        val marker = directory.resolve(MARKER)
        return when {
            !InstalledComponent.validSegment(version) || reservedInfixes.any(version::contains) -> false

            !folder.matches(Regex("${Regex.escape(version)}\\.installing-[a-zA-Z0-9_-]+")) -> false

            installed?.version == folder -> false

            !InstalledComponent.unlinked(directory) || !Files.isRegularFile(marker, NOFOLLOW_LINKS) -> false

            else -> try {
                val expected = mapOf("name" to directory.parent.fileName.toString(), "version" to version)
                KscopeJson.decodeFromString<Map<String, String>>(Files.readString(marker)) == expected
            } catch (_: IllegalArgumentException) {
                false
            } catch (_: IOException) {
                false
            }
        }
    }
}
