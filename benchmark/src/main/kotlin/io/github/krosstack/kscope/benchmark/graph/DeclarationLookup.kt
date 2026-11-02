package io.github.krosstack.kscope.benchmark.graph

import io.github.krosstack.kscope.benchmark.gold.GoldAddress
import io.github.krosstack.kscope.model.Symbol

/** What a search for the symbol of an address found. */
internal sealed interface Lookup {
    data class Single(val symbol: Symbol, val approximateLine: Boolean) : Lookup

    data class Missing(val reason: UnmappedReason) : Lookup
}

/**
 * Finds the symbol a code address names. The file and the qualified name select the candidates; the line, then the
 * module, the source set and the parameter types, choose among overloads. A constructor of a class that declares none
 * is the class, and a member the compiler makes is attributed to its class, as the analyzer does.
 */
internal class DeclarationLookup(private val index: GraphIndex) {
    private val overloads = OverloadNarrowing(index)

    fun find(address: GoldAddress): Lookup {
        val inFile = address.file?.let { index.symbolsByPath[it] }.orEmpty()
        return when {
            address.file == null -> Lookup.Missing(UnmappedReason.INCOMPLETE_ADDRESS)
            inFile.isEmpty() -> Lookup.Missing(UnmappedReason.NO_SYMBOL_IN_FILE)
            else -> choose(candidates(address, inFile), address)
        }
    }

    private fun candidates(address: GoldAddress, inFile: List<Symbol>): List<Symbol> {
        val fqName = address.fqName.orEmpty()
        val isConstructor = address.kind?.contains(CONSTRUCTOR) == true
        return when {
            address.compilerGenerated -> named(inFile, fqName.substringBeforeLast('.'))
            isConstructor -> named(inFile, "$fqName.$INIT").ifEmpty { named(inFile, fqName) }
            else -> named(inFile, fqName)
        }
    }

    private fun named(symbols: List<Symbol>, qualifiedName: String) =
        symbols.filter { it.qualifiedName == qualifiedName }

    private fun choose(candidates: List<Symbol>, address: GoldAddress): Lookup {
        val onLine = candidates.filter { startsOnLine(it, address.line) }
        return when {
            candidates.isEmpty() -> Lookup.Missing(UnmappedReason.NO_MATCHING_SYMBOL)
            onLine.isNotEmpty() -> narrowed(onLine, address, approximate = false)
            else -> narrowed(candidates, address, approximate = true)
        }
    }

    private fun narrowed(candidates: List<Symbol>, address: GoldAddress, approximate: Boolean): Lookup {
        val unique = candidates.singleOrNull()
            ?: overloads.byModule(candidates, address).singleOrNull()
            ?: overloads.bySourceSet(candidates, address).singleOrNull()
            ?: overloads.bySignature(candidates, address).singleOrNull()
        return unique?.let { Lookup.Single(it, approximate) } ?: Lookup.Missing(UnmappedReason.AMBIGUOUS)
    }

    private fun startsOnLine(symbol: Symbol, line: Int?): Boolean =
        line != null && (symbol.nameLocation ?: symbol.declaration)?.range?.startLine == line

    private companion object {
        const val CONSTRUCTOR = "constructor"
        const val INIT = "<init>"
    }
}
