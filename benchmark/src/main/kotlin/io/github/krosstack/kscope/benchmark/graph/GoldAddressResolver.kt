package io.github.krosstack.kscope.benchmark.graph

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.graph.GraphReader

/**
 * Maps the addresses of gold, which name a module, source set, qualified name, signature, file and line, to nodes of
 * the graph. An address that cannot be mapped is returned as [AddressResolution.Unmapped] with the reason; callers
 * report it and never drop it.
 */
public class GoldAddressResolver internal constructor(private val index: GraphIndex) {
    public constructor(reader: GraphReader) : this(GraphIndex(reader))

    private val declarations = DeclarationLookup(index)
    private val nonCode = NonCodeLookup(index)
    private val overloads = OverloadNarrowing(index)

    public fun resolve(address: GoldAddress): AddressResolution = when {
        address.fileFacade -> fileFacade(address)
        address.isGeneratedSurface -> generatedSurface(address)
        address.fqName != null -> declaration(address)
        address.kind != null && address.name != null -> nonCode.find(address)
        else -> AddressResolution.Unmapped(address, UnmappedReason.INCOMPLETE_ADDRESS)
    }

    private fun declaration(address: GoldAddress): AddressResolution = when (val found = declarations.find(address)) {
        is Lookup.Single -> AddressResolution.Found(NodeRef.OfSymbol(found.symbol.id), found.approximateLine)
        is Lookup.Missing -> AddressResolution.Unmapped(address, found.reason)
    }

    private fun fileFacade(address: GoldAddress): AddressResolution {
        val artifact = address.file?.let(index.artifactsByPath::get)
            ?: return AddressResolution.Unmapped(address, UnmappedReason.FILE_NOT_INDEXED)
        return AddressResolution.Found(NodeRef.OfArtifact(artifact.ref))
    }

    private fun generatedSurface(address: GoldAddress): AddressResolution {
        val candidates = generatedCandidates(address)
        val symbol = candidates.singleOrNull() ?: overloads.bySignature(candidates, address).singleOrNull()
            ?: plainOverload(candidates)
            ?: return AddressResolution.Unmapped(address, UnmappedReason.GENERATED_SURFACE_NOT_PRODUCED)
        return AddressResolution.Found(NodeRef.OfSymbol(symbol.id))
    }

    // Gold names a function once, though a generator may make a second overload that takes a mapper of the columns.
    private fun plainOverload(candidates: List<Symbol>): Symbol? =
        candidates.singleOrNull { it.kind.category == SymbolCategory.CALLABLE && "->" !in it.signature.orEmpty() }

    // Gold names a property that Kotlin reads through the getter of a generated Java class by the property.
    private fun generatedCandidates(address: GoldAddress): List<Symbol> {
        val produced = index.symbols.filter { it.origin !is SymbolOrigin.Source }
        return produced.filter { it.qualifiedName == address.fqName }
            .ifEmpty { produced.filter { it.qualifiedName == getterNameOf(address) } }
    }

    private fun getterNameOf(address: GoldAddress): String? = address.fqName?.let {
        it.substringBeforeLast('.') + ".get" + it.substringAfterLast('.').replaceFirstChar(Char::uppercaseChar)
    }
}
