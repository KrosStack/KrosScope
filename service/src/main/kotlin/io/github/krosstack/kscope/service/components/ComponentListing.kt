package io.github.krosstack.kscope.service.components

import io.github.krosstack.kscope.service.semantic.WorkerSelfTest
import io.github.krosstack.kscope.service.settings.WorkspaceSettings

public data class ComponentListing(
    public val entry: ComponentEntry,
    public val state: ComponentState,
    public val installed: InstalledComponent?,
)

public fun ComponentManifest.listing(
    directory: ComponentDirectory,
    platform: String,
    settings: WorkspaceSettings,
): List<ComponentListing> {
    val versionsByName = directory.installed()
        .filter { it.directory.fileName.toString() == it.version }.groupBy { it.name }
    return components.sortedBy { it.name }.map { entry ->
        val versions = versionsByName[entry.name].orEmpty()
        val installed = installed(entry.name, versions) ?: versions.firstOrNull()
        val state = when {
            !entry.supports(platform) -> ComponentState.UNSUPPORTED
            installed?.problem?.startsWith(WorkerSelfTest.FAILURE_PREFIX) == true -> ComponentState.SELF_TEST_FAILED
            installed?.problem != null -> ComponentState.CORRUPT
            settings.components[entry.name] == false -> ComponentState.DISABLED
            installed == null -> ComponentState.NOT_INSTALLED
            installed.version == entry.version -> ComponentState.INSTALLED
            else -> ComponentState.OTHER_VERSION
        }
        ComponentListing(entry, state, installed)
    }
}
