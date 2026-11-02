package io.github.krosstack.kscope.analyzer.jni

import io.github.krosstack.kscope.analyzer.jni.mangling.JvmDescriptors
import io.github.krosstack.kscope.analyzer.jni.mangling.binaryNameOf
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ParsedSymbolId
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.ProducerRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.spi.AnalyzerCapability
import io.github.krosstack.kscope.spi.BoundaryLinker
import io.github.krosstack.kscope.spi.FactSink
import io.github.krosstack.kscope.spi.ProducerDescriptor
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.SymbolQuery

internal const val DEFINITION_NOT_FOUND = "jni.definition-not-found"
internal const val NATIVE_SIDE_NOT_ANALYSED = "jni.native-side-not-analysed"
private const val ALL = Int.MAX_VALUE
private const val DEFINITION = "definition"
private val jvmPlatforms = setOf(Platform.JVM, Platform.ANDROID)

/**
 * Joins every Kotlin `external` function and Java `native` method to the C, C++ or Rust function that implements it.
 * The name of that function follows from the declaration by the JNI specification, so each declaration gets a contract
 * symbol with the mangled name, and the contract is bound to the native definitions of the workspace by name, in
 * whichever repository they are. A definition makes the binding exact and a prototype alone makes it probable; a
 * workspace with native sources and no function of that name leaves it unresolved, because the function may be
 * registered at run time; a workspace without native sources says that the native side was not looked at.
 */
internal class JniLinker : BoundaryLinker {
    override val descriptor = ProducerDescriptor(
        producer = ProducerRef("kscope.jni.linker", "0.2.0"),
        consumes = NativeArtifactTypes.sources,
        capabilities = setOf(AnalyzerCapability.CONTRACT_ENDPOINTS),
        granularity = OwnershipKind.WORKSPACE,
    )

    override fun link(graph: GraphReader, sink: FactSink) {
        val contracts = ExternalDeclarations(graph).contracts()
        if (contracts.isEmpty()) return
        val natives = NativeFunctions(graph)
        val overloaded = contracts.groupBy { it.shortName }.filterValues { it.size > 1 }.keys
        val facts = contracts.flatMap { contract ->
            val contractFacts = ContractFacts(contract)
            contractFacts.facts() + natives.bindings(contract, contractFacts.symbolId, contract.shortName in overloaded)
        }
        val owner = OwnershipUnit(OwnershipKind.WORKSPACE, "workspace")
        sink.accept(FactBatch(descriptor.producer, owner, Fingerprint(""), facts))
    }
}

/** The declarations that stand for native methods: Kotlin `external` functions and Java `native` methods. */
private class ExternalDeclarations(private val graph: GraphReader) {
    private val sourceSets = graph.structure().sourceSets.associateBy { it.id }
    private val targets = graph.structure().targets.associateBy { it.id }
    private val simpleNames = HashMap<String, String?>()
    private val descriptors = JvmDescriptors(::binaryNameOfClass)

    fun contracts(): List<JniContract> = graph.symbols(SymbolQuery(flag = SymbolFlag.EXTERNAL, limit = ALL))
        .filter { it.origin is SymbolOrigin.Source && it.kind.id in methodKinds && isOnJvm(it) }
        .sortedBy { it.id.value }
        .mapNotNull { JniContract.of(it, isInCompanion(it), descriptors) }

    private fun isInCompanion(symbol: Symbol): Boolean {
        val owner = symbol.container?.let(graph::symbol)
        return owner?.kind?.id == "kotlin.object" && owner.signature.orEmpty().contains("companion")
    }

    private fun isOnJvm(symbol: Symbol): Boolean {
        val sourceSet = symbol.scope.sourceSet?.let(sourceSets::get)
        val platforms = sourceSet?.targets?.mapNotNull { targets[it]?.platform }.orEmpty()
        return symbol.kind.id.startsWith("java.") || platforms.isEmpty() || platforms.any { it in jvmPlatforms }
    }

    private fun binaryNameOfClass(simpleName: String): String? = simpleNames.getOrPut(simpleName) {
        val classes = graph.symbols(SymbolQuery(name = simpleName, category = SymbolCategory.TYPE, limit = ALL))
            .filter { it.origin is SymbolOrigin.Source }.mapNotNull { binaryNameOf(it.id) }.distinct()
        classes.singleOrNull()
    }

    private companion object {
        val methodKinds = setOf("kotlin.function", "java.method")
    }
}

/** The native functions of the workspace by name, and whether it has any native source at all. */
private class NativeFunctions(graph: GraphReader) {
    private val hasSources = graph.artifacts().any { it.type in NativeArtifactTypes.sources }
    private val byName: Map<String, List<Symbol>> = graph
        .symbols(SymbolQuery(kindId = NATIVE_FUNCTION_KIND, limit = ALL)).groupBy { it.name }

    fun bindings(contract: JniContract, contractId: SymbolId, isOverloaded: Boolean): List<Fact> {
        val candidates = contract.names.flatMap { byName[it.value].orEmpty() }.distinct()
        val module = moduleOf(contract.external)
        val local = candidates.filter { moduleOf(it) == module }.ifEmpty { candidates }
        val definitions = local.filter { it.attributes[ROLE_ATTRIBUTE] == DEFINITION }
        val chosen = definitions.ifEmpty { local }
        val relationships = if (chosen.isEmpty()) {
            listOf(unmatched(contract, contractId))
        } else {
            chosen.map { resolved(contract, contractId, it, certaintyOf(contract, it, definitions.size, isOverloaded)) }
        }
        return relationships.map { Fact.OfRelationship(it) }
    }

    private fun certaintyOf(contract: JniContract, native: Symbol, definitions: Int, isOverloaded: Boolean): Certainty {
        val name = contract.nameOf(native.name)
        val isDefinition = native.attributes[ROLE_ATTRIBUTE] == DEFINITION
        val isAmbiguous = isDefinition && definitions > 1
        val isGuessedClass = name?.isCertain == false
        val isAmbiguousName = isOverloaded && name?.isLong == false
        val isDoubtful = !isDefinition || isAmbiguous || isGuessedClass || isAmbiguousName
        return if (isDoubtful) Certainty.PROBABLE else Certainty.EXACT
    }

    private fun resolved(contract: JniContract, from: SymbolId, native: Symbol, certainty: Certainty) =
        binding(contract, from, Resolution.Resolved(NodeRef.OfSymbol(native.id), certainty))

    private fun unmatched(contract: JniContract, from: SymbolId): Relationship {
        val resolution = if (hasSources) {
            Resolution.Unresolved(contract.shortName, ReasonCode(DEFINITION_NOT_FOUND))
        } else {
            Resolution.NotAnalysed(contract.shortName, ReasonCode(NATIVE_SIDE_NOT_ANALYSED))
        }
        return binding(contract, from, resolution)
    }

    private fun binding(contract: JniContract, from: SymbolId, resolution: Resolution) = Relationship(
        from = NodeRef.OfSymbol(from),
        kind = RelationKind.BINDS,
        subKind = null,
        resolution = resolution,
        evidenceClass = EvidenceClass.CONVENTION,
        site = contract.external.nameLocation,
        scope = contract.external.scope,
    )

    private fun moduleOf(symbol: Symbol): String? = (symbol.id.identity() as? ParsedSymbolId.Valid)?.identity?.unit
}
