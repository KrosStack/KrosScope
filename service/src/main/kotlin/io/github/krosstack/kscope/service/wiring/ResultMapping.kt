package io.github.krosstack.kscope.service.wiring

import io.github.krosstack.kscope.engine.DifferenceKind
import io.github.krosstack.kscope.engine.IndexCounts
import io.github.krosstack.kscope.engine.UnitDifference
import io.github.krosstack.kscope.engine.UpdateReport
import io.github.krosstack.kscope.engine.VerifyReport
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.service.CoverageEntry
import io.github.krosstack.kscope.service.DiagnosticEntry
import io.github.krosstack.kscope.service.DiagnosticSummary
import io.github.krosstack.kscope.service.DifferenceEntry
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.ScanResult
import io.github.krosstack.kscope.service.Severity
import io.github.krosstack.kscope.service.StructureSummary
import io.github.krosstack.kscope.service.VerificationResult
import io.github.krosstack.kscope.service.level.LevelPolicy
import io.github.krosstack.kscope.spi.graph.CoverageCell
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonPrimitive

internal fun UpdateReport.toScanResult(indexLocation: String, level: ScanLevel) = ScanResult(
    artifacts = artifacts,
    unitsProduced = unitsProduced,
    unitsSkipped = unitsSkipped,
    unitsRemoved = unitsRemoved,
    diagnostics = diagnostics.toSummary(),
    verification = verify?.toResult(),
    stageMillis = stageMillis,
    indexLocation = indexLocation,
    indexLevel = level.wireName,
)

internal fun ScanResult.withNotice(message: String): ScanResult {
    val entry = DiagnosticEntry(Severity.WARNING, LevelPolicy.NOTICE_CODE, message)
    val entries = diagnostics.entries + entry
    return copy(diagnostics = diagnostics.copy(warnings = diagnostics.warnings + 1, entries = entries))
}

internal fun List<Diagnostic>.toSummary(): DiagnosticSummary {
    val entries = map { DiagnosticEntry(it.severity.toSeverity(), it.code.value, it.message) }
    return DiagnosticSummary(
        errors = entries.count { it.severity == Severity.ERROR },
        warnings = entries.count { it.severity == Severity.WARNING },
        infos = entries.count { it.severity == Severity.INFO },
        entries = entries,
    )
}

internal fun IndexCounts.toSummary() = StructureSummary(
    repositories = repositories,
    buildRoots = buildRoots,
    modules = modules,
    sourceSets = sourceSets,
    targets = targets,
    variants = variants,
)

internal fun CoverageCell.toEntry() = CoverageEntry(
    relation = wireName(RelationKind.serializer(), kind),
    evidence = wireName(EvidenceClass.serializer(), evidenceClass),
    state = state,
    count = count,
)

private fun DiagnosticSeverity.toSeverity() = when (this) {
    DiagnosticSeverity.ERROR -> Severity.ERROR
    DiagnosticSeverity.WARNING -> Severity.WARNING
    DiagnosticSeverity.INFO -> Severity.INFO
}

private fun VerifyReport.toResult() = VerificationResult(
    matches = matches,
    differingUnits = differingUnits,
    differences = differences.map(UnitDifference::toEntry),
    differingSearchDocuments = differingSearchDocuments,
)

private fun UnitDifference.toEntry() = DifferenceEntry(
    producer = "${producer.id} ${producer.version}",
    unit = "${wireName(OwnershipKind.serializer(), owner.kind)} ${owner.key}",
    difference = wireName(DifferenceKind.serializer(), kind),
)

private fun <T> wireName(serializer: KSerializer<T>, value: T): String =
    (KscopeJson.encodeToJsonElement(serializer, value) as JsonPrimitive).content
