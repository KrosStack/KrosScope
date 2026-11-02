package io.github.krosstack.kscope.store.read

import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.spi.graph.GraphStore
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import io.github.krosstack.kscope.store.Builders
import io.github.krosstack.kscope.store.SqliteGraphStore

internal object SymbolFixtures {
    val alpha = Builders.symbol("alpha", name = "Alpha", artifact = "src/A.kt").copy(
        qualifiedName = "p.Alpha",
        kind = SymbolKind("kotlin.class", SymbolCategory.TYPE),
        flags = setOf(SymbolFlag.OPEN),
        container = null,
    )

    val beta = Builders.symbol("beta", name = "Beta", artifact = "src/B.kt").copy(
        qualifiedName = "p.Alpha.Beta",
        kind = SymbolKind("kotlin.function", SymbolCategory.CALLABLE),
        flags = setOf(SymbolFlag.SUSPEND, SymbolFlag.OPEN),
        container = alpha.id,
    )

    val gamma = Builders.symbol("gamma", name = "Alpha", artifact = null).copy(
        qualifiedName = null,
        kind = SymbolKind("kotlin.property", SymbolCategory.VARIABLE),
        flags = emptySet(),
        container = alpha.id,
    )

    fun withSymbols(check: (GraphStore) -> Unit) = SqliteGraphStore.inMemory().use { store ->
        store.replace(Builders.symbols("unit", beta, gamma, alpha))
        check(store)
    }

    fun GraphStore.ids(query: SymbolQuery): List<String> = symbols(query).map { it.id.value }
}
