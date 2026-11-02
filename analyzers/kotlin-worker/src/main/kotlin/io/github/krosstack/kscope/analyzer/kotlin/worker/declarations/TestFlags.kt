package io.github.krosstack.kscope.analyzer.kotlin.worker.declarations

import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolId

/** A type that contains a test function, directly or through a nested type, is a test type as well. */
internal fun propagateTestFlag(symbols: List<Symbol>): List<Symbol> {
    val byId = symbols.associateBy { it.id }
    val testContainers = mutableSetOf<SymbolId>()
    symbols.filter { SymbolFlag.TEST in it.flags && it.kind.category == SymbolCategory.CALLABLE }.forEach { test ->
        var container = test.container
        while (container != null && testContainers.add(container)) container = byId[container]?.container
    }
    return symbols.map { if (it.id in testContainers) it.copy(flags = it.flags + SymbolFlag.TEST) else it }
}
