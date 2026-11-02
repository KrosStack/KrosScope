package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

private data class Rank(val isOtherPackage: Boolean, val moduleDistance: Int) : Comparable<Rank> {
    override fun compareTo(other: Rank): Int =
        compareValuesBy(this, other, { it.isOtherPackage }, { it.moduleDistance })
}

/**
 * Looks for the class a test is named after in the main source sets of the module of the test and of the modules it
 * depends on. Of several classes of that name, those in the package of the test come first, then those of the nearest
 * module; the classes of one declaration, such as an `expect` class and its `actual` classes, are one candidate. When
 * several declarations remain, the one that the test uses most is the candidate, if there is one.
 */
internal class SubjectSearch(
    private val graph: GraphReader,
    private val sourceSets: SourceSetModel,
    private val modules: ModuleDependencies,
    private val targets: TargetCompatibility,
) {
    fun find(test: Symbol, name: String, reached: List<Reached>): Symbol? {
        val testSet = test.scope.sourceSet?.let { sourceSets[it] } ?: return null
        val visible = listOf(testSet.module) + modules.closure(testSet.module)
        val candidates = graph.symbols(SymbolQuery(name = name, limit = ALL))
            .filter { isClassUnderTest(it) && isVisibleFrom(testSet, it, visible) }
        val nearest = candidates.groupBy { rankOf(test, it, visible) }.toSortedMap().values.firstOrNull()
        return nearest?.let { chooseAmong(it, reached) }
    }

    private fun isClassUnderTest(symbol: Symbol): Boolean =
        symbol.kind.category == SymbolCategory.TYPE && symbol.isFromSource() && SymbolFlag.TEST !in symbol.flags

    private fun isVisibleFrom(testSet: SourceSet, candidate: Symbol, visible: List<ModuleId>): Boolean {
        val set = candidate.scope.sourceSet?.let { sourceSets[it] }
        val isMain = set?.role == SourceSetRole.MAIN
        return set != null && isMain && set.module in visible && targets.shareTarget(testSet, set)
    }

    private fun rankOf(test: Symbol, candidate: Symbol, visible: List<ModuleId>): Rank {
        val module = candidate.scope.sourceSet?.let { sourceSets[it] }?.module
        return Rank(packageOf(test) != packageOf(candidate), visible.indexOf(module))
    }

    private fun packageOf(symbol: Symbol): String = symbol.qualifiedName?.substringBeforeLast('.', "").orEmpty()

    private fun chooseAmong(candidates: List<Symbol>, reached: List<Reached>): Symbol? {
        val declarations = candidates.groupBy { it.declarationKey() }.values
        val declaration = declarations.singleOrNull() ?: mostUsed(declarations, reached)
        return declaration?.let { sameDeclaration ->
            sameDeclaration.firstOrNull { SymbolFlag.EXPECT in it.flags } ?: sameDeclaration.minBy { it.id.value }
        }
    }

    private fun mostUsed(declarations: Collection<List<Symbol>>, reached: List<Reached>): List<Symbol>? {
        val uses = declarations.associateWith { declaration ->
            val key = declaration.first().declarationKey()
            reached.count { use -> use.chain.any { it.declarationKey() == key } }
        }
        val most = uses.values.maxOrNull() ?: 0
        return uses.entries.singleOrNull { it.value == most && most > 0 }?.key
    }
}
