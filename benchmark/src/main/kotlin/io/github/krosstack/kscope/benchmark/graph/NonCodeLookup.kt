package io.github.krosstack.kscope.benchmark.graph

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.benchmark.gold.GoldSymbolKinds
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin

/**
 * Maps the addresses of things that are not code: Gradle modules, convention plugins, version catalog entries,
 * resources, AIDL declarations, manifest components and the fields of `BuildConfig`.
 */
internal class NonCodeLookup(private val index: GraphIndex) {
    fun find(address: GoldAddress): AddressResolution {
        val kind = address.kind.orEmpty()
        return when {
            kind == MODULE -> module(address)
            kind == CONVENTION_PLUGIN || kind == ROOM_SCHEMA -> conventionPlugin(address)
            kind.startsWith(CATALOG_PREFIX) -> catalogEntry(address, kind)
            kind == BUILD_CONFIG_FIELD -> buildConfigField(address)
            else -> declaredSymbol(address, kind)
        }
    }

    private fun declaredSymbol(address: GoldAddress, kind: String): AddressResolution {
        val symbolKind = GoldSymbolKinds.declaredKindOf(kind)
            ?: return AddressResolution.Unmapped(address, UnmappedReason.KIND_NOT_SUPPORTED)
        return resource(address, symbolKind)
    }

    private fun module(address: GoldAddress): AddressResolution {
        val module = (address.module ?: address.name)?.let(index.modulesByGoldPath::get)
            ?: return AddressResolution.Unmapped(address, UnmappedReason.MODULE_NOT_FOUND)
        return AddressResolution.Found(NodeRef.OfModule(module.id))
    }

    private fun conventionPlugin(address: GoldAddress): AddressResolution {
        val artifact = address.file?.let(index.artifactsByPath::get)
            ?: return AddressResolution.Unmapped(address, UnmappedReason.FILE_NOT_INDEXED)
        return AddressResolution.Found(NodeRef.OfArtifact(artifact.ref))
    }

    private fun catalogEntry(address: GoldAddress, kind: String): AddressResolution {
        val symbolKind = GoldSymbolKinds.catalogKindOf(kind)
        val symbol = index.symbolsByPath[address.file].orEmpty()
            .singleOrNull { it.kind.id == symbolKind && it.name == address.name }
            ?: return AddressResolution.Unmapped(address, UnmappedReason.NO_MATCHING_SYMBOL)
        return AddressResolution.Found(NodeRef.OfSymbol(symbol.id))
    }

    private fun buildConfigField(address: GoldAddress): AddressResolution {
        val module = address.module?.let(index.modulesByGoldPath::get)
        val fields = index.symbols.filter { symbol ->
            val origin = symbol.origin as? SymbolOrigin.Generated
            val script = (origin?.sourceOfTruth as? NodeRef.OfArtifact)?.ref?.path?.value
            origin?.generator == BUILD_CONFIG_GENERATOR && symbol.name == address.name && script == address.file &&
                symbol.kind.id == JAVA_FIELD && module != null && unitOf(symbol) == module.id.value
        }
        val field = fields.singleOrNull()
            ?: return AddressResolution.Unmapped(address, UnmappedReason.NO_MATCHING_SYMBOL)
        return AddressResolution.Found(NodeRef.OfSymbol(field.id))
    }

    private fun unitOf(symbol: Symbol): String? = (symbol.id.identity() as? ParsedSymbolId.Valid)?.identity?.unit

    private fun resource(address: GoldAddress, kind: String): AddressResolution {
        val named = index.symbolsByPath[address.file].orEmpty().filter { it.kind.id == kind && it.name == address.name }
        val onLine = named.filter { (it.nameLocation ?: it.declaration)?.range?.startLine == address.line }
        val symbol = onLine.ifEmpty { named }.singleOrNull()
            ?: return AddressResolution.Unmapped(address, UnmappedReason.NO_MATCHING_SYMBOL)
        return AddressResolution.Found(NodeRef.OfSymbol(symbol.id))
    }

    private companion object {
        const val BUILD_CONFIG_FIELD = "buildconfig.field"
        const val ROOM_SCHEMA = "room.schema"
        const val BUILD_CONFIG_GENERATOR = "android.buildconfig"
        const val JAVA_FIELD = "java.field"
        const val MODULE = "gradle.module"
        const val CONVENTION_PLUGIN = "gradle.convention-plugin"
        const val CATALOG_PREFIX = "catalog."
    }
}
