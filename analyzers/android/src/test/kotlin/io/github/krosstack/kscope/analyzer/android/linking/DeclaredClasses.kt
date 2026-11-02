package io.github.krosstack.kscope.analyzer.android.linking

import io.github.krosstack.kscope.analyzer.android.DroidModel
import io.github.krosstack.kscope.analyzer.android.Render
import io.github.krosstack.kscope.analyzer.android.ResourceProducer
import io.github.krosstack.kscope.analyzer.android.SampleTree
import io.github.krosstack.kscope.analyzer.android.facts
import io.github.krosstack.kscope.analyzer.android.manifest.ManifestProducer
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolDescriptor
import io.github.krosstack.kscope.model.SymbolIdentity
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.model.Visibility
import io.github.krosstack.kscope.model.answerKey

internal val kotlinClass = SymbolKind("kotlin.class", SymbolCategory.TYPE)

/** A declaration that a Kotlin or Java source set makes, as the semantic analyzer would report it. */
internal fun declared(qualifiedName: String, module: String, sourceSet: String, kind: SymbolKind = kotlinClass): Fact {
    val descriptor = qualifiedName.substringBeforeLast('.').split('.')
        .fold(SymbolDescriptor()) { d, name -> d.namespace(name) }.type(qualifiedName.substringAfterLast('.'))
    return Fact.OfSymbol(
        Symbol(
            id = SymbolIdentity.workspace(ModuleId("repo//$module"), sourceSet, descriptor).toSymbolId(),
            kind = kind,
            name = qualifiedName.substringAfterLast('.'),
            qualifiedName = qualifiedName,
            signature = null,
            container = null,
            visibility = Visibility.PUBLIC,
            flags = emptySet(),
            origin = SymbolOrigin.Source,
            declaration = null,
            nameLocation = null,
            documentation = null,
            scope = Scope(sourceSet = DroidModel.sourceSetId(module, sourceSet)),
            evidenceClass = EvidenceClass.STRUCTURAL,
            certainty = Certainty.EXACT,
        ),
    )
}

internal fun Relationship.targetText(): String = Render.node((resolution as Resolution.Resolved).target)

internal fun Relationship.certaintyOf(): Certainty = (resolution as Resolution.Resolved).certainty

/** The class references of a tree's manifests and layouts, and the answers of the class linker over [declarations]. */
internal class ClassAnswers(tree: SampleTree, declarations: List<Fact>, extra: List<Fact> = emptyList()) {
    val references: List<SymbolicReference>
    val relationships: List<Relationship>

    init {
        val produced = tree.produce(ManifestProducer()).facts() + tree.produce(ResourceProducer()).facts() + extra
        val out = mutableListOf<Fact>()
        JvmClassLinker().link(FakeGraph(produced + declarations, tree.model)) { out += it.facts }
        references = produced.filterIsInstance<Fact.OfSymbolicReference>().map { it.value }
            .filter { it.namespace == "jvm.class" }
        relationships = out.filterIsInstance<Fact.OfRelationship>().map { it.value }
    }

    fun of(key: String, subKind: String? = null, inFile: String? = null): Relationship {
        val reference = references.single {
            it.key == key && it.subKind == subKind && (inFile == null || it.site?.artifact?.path?.value == inFile)
        }
        return relationships.single { it.answerKey() == reference.answerKey() }
    }
}
