package io.github.krosstack.kscope.service.components

import io.github.krosstack.kscope.model.KscopeJson
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
public data class ComponentManifest(public val schemaVersion: Int, public val components: List<ComponentEntry>) {
    internal fun installed(name: String, candidates: List<InstalledComponent>): InstalledComponent? =
        components.firstOrNull { it.name == name }?.let { entry ->
            candidates.firstOrNull {
                it.name == entry.name && it.version == entry.version && it.directory.fileName.toString() == it.version
            }
        }

    public companion object {
        public fun read(json: String): ComponentManifest = KscopeJson.decodeFromString<ComponentManifest>(json).also {
            require(it.schemaVersion == 1) { "Unsupported component manifest schema" }
            require(it.components.map(ComponentEntry::name).distinct().size == it.components.size)
            it.components.forEach(::validate)
        }

        public fun validate(entry: ComponentEntry) {
            ComponentStaging.reservedInfixes.forEach { infix ->
                require(!entry.name.contains(infix) && !entry.version.contains(infix)) {
                    "Component name and version must not contain reserved infix $infix"
                }
            }
            require(InstalledComponent.validSegment(entry.name) && InstalledComponent.validSegment(entry.version))
            require(InstalledComponent.relative(entry.licenceFile))
            require(entry.files.isNotEmpty())
            require(entry.files.map { file -> file.path.lowercase() }.distinct().size == entry.files.size)
            entry.files.forEach(::validate)
            entry.platforms?.let { platforms ->
                require(platforms.isNotEmpty() && platforms.distinct().size == platforms.size)
                require(platforms.all { it != "any" && it in ComponentPlatform.supported && entry.supports(it) })
                require(entry.files.all { it.platform == "any" || it.platform in platforms })
            }
        }

        public fun shipped(): ComponentManifest = read(
            checkNotNull(ComponentManifest::class.java.classLoader.getResourceAsStream("components.json")) {
                "Missing component manifest"
            }.bufferedReader().use { it.readText() },
        )

        private fun validate(file: ComponentFile) {
            require(InstalledComponent.relative(file.path) && !file.path.equals(InstalledComponent.MARKER, true))
            require(!file.path.equals(ComponentStaging.MARKER, true))
            require(file.platform in ComponentPlatform.supported)
            require(file.size > 0 && file.size <= Long.MAX_VALUE / ComponentInstaller.EXPANSION_LIMIT)
            require(file.sha256.matches(Regex("[a-fA-F0-9]{64}")))
            require(file.urls.isNotEmpty() && file.urls.all { URI(it).scheme == "https" && URI(it).host != null })
        }
    }
}
