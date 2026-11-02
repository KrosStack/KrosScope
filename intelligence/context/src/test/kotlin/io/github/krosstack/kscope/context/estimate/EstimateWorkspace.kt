package io.github.krosstack.kscope.context.estimate

import io.github.krosstack.kscope.context.ContextFixture
import io.github.krosstack.kscope.context.declaration
import io.github.krosstack.kscope.context.sourceArtifact
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange

internal class EstimateWorkspace {
    private val artifacts = mutableListOf<Artifact>()
    private val symbols = mutableListOf<Symbol>()
    private val edges = mutableListOf<Relationship>()
    private val content = mutableMapOf<ArtifactRef, String>()

    fun file(
        path: String,
        text: String,
        category: ArtifactCategory = ArtifactCategory.SOURCE,
        indexed: String = text,
        generated: GeneratedInfo? = null,
    ): Artifact {
        val artifact = sourceArtifact(path, indexed).copy(
            type = ArtifactType("test.${category.name.lowercase()}", category),
            generated = generated,
        )
        artifacts += artifact
        content[artifact.ref] = text
        return artifact
    }

    fun declare(
        name: String,
        file: Artifact,
        range: TextRange,
        certainty: Certainty = Certainty.EXACT,
        origin: SymbolOrigin = SymbolOrigin.Source,
    ): Symbol = declaration(name, file, range, "fun $name()").copy(certainty = certainty, origin = origin)
        .also { symbols += it }

    fun declareLine(file: Artifact, text: String, line: Int, certainty: Certainty = Certainty.EXACT): Symbol {
        val length = text.split('\n')[line - 1].length
        return declare("member${symbols.size}", file, TextRange(line, 1, line, length + 1), certainty)
    }

    fun declareLines(file: Artifact, text: String): List<Symbol> =
        text.split('\n').withIndex().filter { it.value.isNotBlank() }.map { declareLine(file, text, it.index + 1) }

    fun calls(from: Symbol, to: Symbol): Relationship = relationship(
        NodeRef.OfSymbol(from.id),
        Resolution.Resolved(NodeRef.OfSymbol(to.id), Certainty.EXACT),
    ).also { edges += it }

    fun unresolvedFrom(node: NodeRef): Relationship =
        relationship(node, Resolution.Unresolved("missing", ReasonCode("test.missing")))

    fun fixture(): ContextFixture =
        ContextFixture(artifacts.toList(), symbols.toList(), content.toMap(), edges.toList())

    private fun relationship(from: NodeRef, resolution: Resolution): Relationship = Relationship(
        from = from,
        kind = RelationKind.CALLS,
        subKind = null,
        resolution = resolution,
        evidenceClass = EvidenceClass.COMPILER,
        site = null,
        scope = Scope(),
    )
}
