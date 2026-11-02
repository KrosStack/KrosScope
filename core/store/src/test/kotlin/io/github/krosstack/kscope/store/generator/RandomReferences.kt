package io.github.krosstack.kscope.store.generator

import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RepositoryId
import io.github.krosstack.kscope.model.Scope
import io.github.krosstack.kscope.model.SourceLocation
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.TargetId
import io.github.krosstack.kscope.model.TextRange
import io.github.krosstack.kscope.model.VariantId

internal class RandomReferences(private val values: RandomValues) {
    fun artifactRef(): ArtifactRef = ArtifactRef(RepositoryId(values.name()), values.repoPath())

    fun location(): SourceLocation = SourceLocation(
        artifactRef(),
        TextRange(values.line(), values.line(), values.line(), values.line()),
    )

    fun scope(): Scope = Scope(
        sourceSet = values.maybe { SourceSetId(values.name()) },
        target = values.maybe { TargetId(values.name()) },
        variant = values.maybe { VariantId(values.name()) },
    )

    fun attributes(): Map<String, String> = values.listOfUpTo(MAX_ATTRIBUTES) { values.name() to values.text() }.toMap()

    fun nodeRef(): NodeRef = when (values.random.nextInt(NODE_KINDS)) {
        0 -> NodeRef.OfSymbol(SymbolId(values.name()))
        1 -> NodeRef.OfArtifact(artifactRef())
        2 -> NodeRef.OfModule(ModuleId(values.name()))
        3 -> NodeRef.OfSourceSet(SourceSetId(values.name()))
        else -> NodeRef.OfExternal(values.name())
    }

    private companion object {
        const val MAX_ATTRIBUTES = 3
        const val NODE_KINDS = 5
    }
}
