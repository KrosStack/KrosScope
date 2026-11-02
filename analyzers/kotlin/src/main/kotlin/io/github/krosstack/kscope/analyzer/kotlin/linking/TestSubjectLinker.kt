package io.github.krosstack.kscope.analyzer.kotlin.linking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSetRole
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery

private val subjectCategories = setOf(SymbolCategory.TYPE, SymbolCategory.CALLABLE, SymbolCategory.VARIABLE)

internal const val SUBJECT_BASIS = "tests.subject-basis"
internal const val BASIS_NAME = "name"
internal const val BASIS_REFERENCES = "references"

/** How a subject was found, and what that says about the edge: its [certainty] and the class of [evidence]. */
private class Subject(val symbol: Symbol, val basis: String, val certainty: Certainty, val evidence: EvidenceClass)

/** What the linker works with while it looks for the subjects of the tests of one graph. */
private class Context(val search: SubjectSearch, val references: TestReferences, val sourceSets: SourceSetModel)

/**
 * Binds each test class to the class it tests. The class a test is named after is the subject (`FooTest` tests
 * `Foo`): probable by convention, and exact when the compiler also states that the test uses it. A test that no name
 * leads to is bound to the declaration of its own module that its compiler references reach most, if exactly one
 * leads, as a heuristic: a class, or a top-level function or property; a test that uses none of those is bound to the
 * class of any module that it uses most.
 */
internal class TestSubjectLinker : BoundaryLinker {
    override val descriptor =
        workspaceLinker("kscope.kotlin.test-subject-linker", AnalyzerCapability.RESOLVED_REFERENCES)

    override fun link(graph: GraphReader, sink: FactSink) {
        val structure = graph.structure()
        val sourceSets = SourceSetModel(structure.sourceSets)
        val dependencies = graph.relationships(RelationshipQuery(kinds = setOf(RelationKind.DEPENDS_ON), limit = ALL))
        val modules = ModuleDependencies(structure.sourceSets, dependencies)
        val search = SubjectSearch(graph, sourceSets, modules, TargetCompatibility(structure.targets))
        val context = Context(search, TestReferences(graph), sourceSets)
        val relationships = TestClasses(graph, sourceSets).outermost().mapNotNull { test ->
            subjectOf(test, context)?.let { testing(test, it) }
        }
        sink.acceptSorted(descriptor, relationships)
    }

    private fun subjectOf(test: Symbol, context: Context): Subject? {
        val reached = context.references.reachedBy(test)
        val named = SubjectNames.of(test.name)?.let { context.search.find(test, it, reached) }
        return if (named != null) byName(named, reached) else mostReferenced(test, reached, context.sourceSets)
    }

    private fun byName(subject: Symbol, reached: List<Reached>): Subject {
        val key = subject.declarationKey()
        val isConfirmed = reached.any { use -> use.chain.any { it.declarationKey() == key } }
        return if (isConfirmed) {
            Subject(subject, BASIS_NAME, Certainty.EXACT, EvidenceClass.COMPILER)
        } else {
            Subject(subject, BASIS_NAME, Certainty.PROBABLE, EvidenceClass.CONVENTION)
        }
    }

    // A test is about the code of its own module: the classes and the top-level functions and properties of that module
    // that it uses come first, and only a test that uses none of them is about a class of another module.
    private fun mostReferenced(test: Symbol, reached: List<Reached>, sourceSets: SourceSetModel): Subject? {
        val module = test.scope.sourceSet?.let { sourceSets[it] }?.module
        val candidates = reached.map { it.outermost }.filter { isSubjectCandidate(it, sourceSets) }
        val own = candidates.filter { sourceSets[checkNotNull(it.scope.sourceSet)]?.module == module }
        val leader = leaderOf(own.ifEmpty { candidates.filter { it.kind.category == SymbolCategory.TYPE } })
        return leader?.let { Subject(it, BASIS_REFERENCES, Certainty.HEURISTIC, EvidenceClass.COMPILER) }
    }

    // The declaration that most uses reach, if one declaration has more of them than any other.
    private fun leaderOf(uses: List<Symbol>): Symbol? {
        val counts = uses.groupingBy { it.declarationKey() }.eachCount()
        val top = counts.values.maxOrNull()
        val leader = counts.entries.singleOrNull { it.value == top }?.key
        return leader?.let { key -> uses.first { it.declarationKey() == key } }
    }

    private fun isSubjectCandidate(symbol: Symbol, sourceSets: SourceSetModel): Boolean {
        val role = symbol.scope.sourceSet?.let { sourceSets[it] }?.role
        val isDeclaration = symbol.kind.category in subjectCategories && symbol.isFromSource()
        return isDeclaration && role == SourceSetRole.MAIN && SymbolFlag.TEST !in symbol.flags
    }

    private fun testing(test: Symbol, subject: Subject) = Relationship(
        from = NodeRef.OfSymbol(test.id),
        kind = RelationKind.TESTS,
        subKind = null,
        resolution = Resolution.Resolved(NodeRef.OfSymbol(subject.symbol.id), subject.certainty),
        evidenceClass = subject.evidence,
        site = null,
        scope = test.scope,
        attributes = mapOf(SUBJECT_BASIS to subject.basis),
    )
}
