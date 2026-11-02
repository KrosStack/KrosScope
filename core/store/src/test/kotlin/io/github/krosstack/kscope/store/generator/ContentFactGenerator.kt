package io.github.krosstack.kscope.store.generator

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.ContentHash
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.GeneratedInfo
import io.github.krosstack.kscope.model.Metric
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolFlag
import io.github.krosstack.kscope.model.SymbolId
import io.github.krosstack.kscope.model.SymbolKind
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.SymbolicReference
import io.github.krosstack.kscope.model.Visibility
import io.github.krosstack.kscope.store.codec.attributes
import io.github.krosstack.kscope.store.codec.location
import io.github.krosstack.kscope.store.codec.nodeRef
import io.github.krosstack.kscope.store.codec.resolution
import io.github.krosstack.kscope.store.codec.scope

internal class ContentFactGenerator(private val values: RandomValues, private val refs: RandomReferences) {
    private var symbolCounter = 0

    fun artifact() = Artifact(
        ref = refs.artifactRef(),
        type = ArtifactType(values.name(), values.pick(ArtifactCategory.entries)),
        contentHash = ContentHash(values.hex()),
        sizeBytes = values.random.nextLong(0, Long.MAX_VALUE),
        lineCount = values.random.nextInt(0, Int.MAX_VALUE),
        sourceSet = values.maybe { SourceSetId(values.name()) },
        generated = values.maybe { GeneratedInfo(values.name(), values.maybe { refs.nodeRef() }) },
    )

    fun symbol() = Symbol(
        id = SymbolId("symbol-${symbolCounter++} ${values.text()}"),
        kind = SymbolKind(values.name(), values.pick(SymbolCategory.entries)),
        name = values.name(),
        qualifiedName = values.maybe { values.name() },
        signature = values.maybe { values.text() },
        container = values.maybe { SymbolId(values.name()) },
        visibility = values.maybe { values.pick(Visibility.entries) },
        flags = SymbolFlag.entries.filter { values.random.nextBoolean() }.toSet(),
        origin = origin(),
        declaration = values.maybe { refs.location() },
        nameLocation = values.maybe { refs.location() },
        documentation = values.maybe { values.text() },
        scope = refs.scope(),
        evidenceClass = values.pick(EvidenceClass.entries),
        certainty = values.pick(Certainty.entries),
        attributes = refs.attributes(),
    )

    fun relationship() = Relationship(
        from = refs.nodeRef(),
        kind = values.pick(RelationKind.entries),
        subKind = values.maybe { values.name() },
        resolution = resolution(),
        evidenceClass = values.pick(EvidenceClass.entries),
        site = values.maybe { refs.location() },
        scope = refs.scope(),
        attributes = refs.attributes(),
    )

    fun symbolicReference() = SymbolicReference(
        from = refs.nodeRef(),
        kind = values.pick(RelationKind.entries),
        subKind = values.maybe { values.name() },
        namespace = values.name(),
        key = values.text(),
        site = values.maybe { refs.location() },
        scope = refs.scope(),
        evidenceClass = values.pick(EvidenceClass.entries),
    )

    fun metric() = Metric(
        node = refs.nodeRef(),
        name = values.name(),
        value = values.random.nextDouble(-METRIC_RANGE, METRIC_RANGE),
    )

    fun diagnostic() = Diagnostic(
        severity = values.pick(DiagnosticSeverity.entries),
        code = ReasonCode(values.name()),
        message = values.text(),
        location = values.maybe { refs.location() },
    )

    private fun origin(): SymbolOrigin = when (values.random.nextInt(ORIGINS)) {
        0 -> SymbolOrigin.Source
        1 -> SymbolOrigin.Generated(values.name(), values.maybe { refs.nodeRef() })
        2 -> SymbolOrigin.Synthetic(values.name())
        else -> SymbolOrigin.Library(values.maybe { values.name() })
    }

    private fun resolution(): Resolution = when (values.random.nextInt(RESOLUTIONS)) {
        0 -> Resolution.Resolved(refs.nodeRef(), values.pick(Certainty.entries))
        1 -> Resolution.Unresolved(values.text(), ReasonCode(values.name()))
        else -> Resolution.NotAnalysed(values.text(), ReasonCode(values.name()))
    }

    private companion object {
        const val ORIGINS = 4
        const val RESOLUTIONS = 3
        const val METRIC_RANGE = 1.0E6
    }
}
