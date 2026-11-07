package io.github.krosstack.kscope.cli.components

import io.github.krosstack.kscope.service.components.ComponentEntry
import io.github.krosstack.kscope.service.components.ComponentListing
import io.github.krosstack.kscope.service.components.ComponentManifest
import io.github.krosstack.kscope.service.components.ComponentState
import io.github.krosstack.kscope.service.components.InstallResult
import io.github.krosstack.kscope.service.components.InstalledComponent
import io.github.krosstack.kscope.service.components.RemoveResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal object ComponentsText {
    fun render(entry: ComponentEntry, platform: String): String {
        val files = entry.files.filter { it.platform == "any" || it.platform == platform }
        return "${entry.name} ${entry.version}  ${entry.licence}  ${files.sumOf { it.size }} bytes\n" +
            files.joinToString("\n") { "  ${it.path}: ${it.urls.joinToString(", ")}" }
    }

    fun render(rows: List<ComponentListing>, json: Boolean, platform: String): String = if (json) {
        JsonArray(
            rows.map { row ->
                val size = row.entry.files.filter { it.platform == "any" || it.platform == platform }.sumOf { it.size }
                buildJsonObject {
                    put("name", row.entry.name)
                    put("version", row.entry.version)
                    put("licence", row.entry.licence)
                    put("size", size)
                    put("state", state(row))
                }
            },
        ).toString()
    } else {
        rows.joinToString("\n") { "${render(it.entry, platform).lineSequence().first()}  ${state(it)}" }
            .ifEmpty { "No optional components in this release." }
    }

    fun render(installed: List<InstalledComponent>, manifest: ComponentManifest, json: Boolean): String {
        val rows = installed.map { component ->
            val matches = manifest.components.any { it.name == component.name && it.version == component.version }
            buildJsonObject {
                put("name", component.name)
                put("version", component.version)
                put("directory", component.directory.toString())
                put("matchesManifest", matches)
                component.problem?.let { put("problem", it) }
                if (component.directory.fileName.toString() != component.version) {
                    put("state", "interrupted install (backup kept)")
                }
            }
        }
        return if (json) {
            JsonArray(rows).toString()
        } else {
            installed.zip(rows).joinToString("\n") { (component, row) ->
                "${component.name} ${component.version}  ${component.directory}  " +
                    when {
                        component.directory.fileName.toString() != component.version ->
                            "interrupted install (backup kept)"

                        component.problem?.startsWith("Self-test failed:") == true -> "installed; ${component.problem}"

                        component.problem != null -> "corrupt installation: ${component.problem}"

                        else -> "matches manifest: ${row.getValue("matchesManifest").jsonPrimitive.content}"
                    }
            }.ifEmpty { "No optional components installed." }
        }
    }

    fun render(result: InstallResult): String = when (result) {
        is InstallResult.Installed -> listOfNotNull(
            "Installed ${result.component.name} ${result.component.version}.",
            result.selfTest,
        ).joinToString(" ")

        is InstallResult.Replaced -> listOfNotNull(
            result.retainedBackup?.let {
                "${result.component.name} ${result.component.version} installed; previous version kept at $it"
            } ?: "Replaced ${result.component.name} ${result.component.version}.",
            result.selfTest,
        ).joinToString(" ")

        is InstallResult.SelfTestFailed -> render(result.installation)

        is InstallResult.ChecksumMismatch -> "Checksum mismatch: ${result.file}"

        is InstallResult.Oversize -> "Size limit exceeded: ${result.file}"

        is InstallResult.UnsafeEntry -> "Unsafe archive entry: ${result.file}: ${result.entry}"

        is InstallResult.Unavailable -> "Unavailable: ${result.file}: ${result.reason}"
    }

    fun render(name: String, result: RemoveResult): String = when (result) {
        RemoveResult.Removed -> "Removed $name."
        RemoveResult.NotInstalled -> "$name is not installed."
        is RemoveResult.Refused -> "Refused: ${result.reason}"
    }

    private fun state(row: ComponentListing): String = when {
        row.state == ComponentState.SELF_TEST_FAILED -> "installed; ${row.installed?.problem}"
        row.installed?.problem != null -> "${row.state.text}: ${row.installed?.problem}"
        row.state == ComponentState.OTHER_VERSION -> "${row.state.text}: ${row.installed?.version}"
        else -> row.state.text
    }
}
