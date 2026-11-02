package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.build.BuildModelCodec
import io.github.krosstack.kscope.engine.build.BuildModelFingerprint
import io.github.krosstack.kscope.engine.build.RepositoryModule
import io.github.krosstack.kscope.engine.hashing.FingerprintBuilder
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.KscopeJson
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.Repository
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.BuildModelProvider
import io.github.krosstack.kscope.spi.BuildModelRequest
import io.github.krosstack.kscope.spi.BuildModelResult
import io.github.krosstack.kscope.spi.WorkspaceFiles

/** A build model together with the runtime-only compile classpaths its provider resolved. */
internal class ResolvedBuildModel(val model: BuildModel, val classpaths: Map<SourceSetId, List<String>>) {
    companion object {
        val empty = ResolvedBuildModel(BuildModelCodec.empty, emptyMap())
    }
}

/**
 * Asks the eligible providers, those that execute build logic first and each group in registry order; the first that
 * provides a model wins and a failed provider is reported and skipped. When none provides one, a repository of a
 * workspace of several repositories gets a [RepositoryModule], so that its files are anchored to a module; a workspace
 * of one repository gets no model.
 *
 * A provider that executes nothing and whose inputs are unchanged is not asked again: its stored model is read back
 * instead. A provider that executes build logic is asked on every update, because the engine cannot see all of its
 * inputs and does not store its classpaths; it keeps its own cache, and its stored model is current when its content
 * is unchanged.
 */
internal class BuildModelStage(
    private val context: ScanContext,
    private val session: UpdateSession,
    private val files: WorkspaceFiles,
    private val repository: Repository,
    private val workspaceClasspaths: WorkspaceClasspaths,
) {
    private val owner = OwnershipUnit(OwnershipKind.REPOSITORY, repository.id.value)

    fun resolve(artifacts: List<Artifact>): ResolvedBuildModel {
        val eligible = context.registry.buildModelProviders
            .filter { context.config.trusted || !it.requiresTrust }
            .sortedByDescending { it.requiresTrust }
        return eligible.firstNotNullOfOrNull { attempt(it, artifacts) } ?: withoutProvider()
    }

    private fun withoutProvider(): ResolvedBuildModel {
        if (context.repositories.size < 2) {
            session.notices += noModelNotice
            return ResolvedBuildModel.empty
        }
        return repositoryModule()
    }

    private fun repositoryModule(): ResolvedBuildModel {
        val module = RepositoryModule(repository)
        val model = BuildModelCodec.canonical(module.model())
        val unit = PlannedUnit(RepositoryModule.producer, owner, module.fingerprint())
        if (session.ledger.claimIfStale(unit)) {
            val facts = BuildModelCodec.toFacts(model, emptyList())
            session.ledger.commit(FactBatch(unit.producer, owner, unit.fingerprint, facts))
        }
        session.notices += module.notice()
        return ResolvedBuildModel(model, emptyMap())
    }

    private fun attempt(provider: BuildModelProvider, artifacts: List<Artifact>): ResolvedBuildModel? {
        val unit = PlannedUnit(provider.producer, owner, BuildModelFingerprint(repository.id).of(provider, artifacts))
        val stored = if (provider.requiresTrust) null else storedModel(unit)
        return stored ?: freshModel(provider, unit, artifacts)
    }

    private fun storedModel(unit: PlannedUnit): ResolvedBuildModel? {
        val stored = unit.takeIf(session.ledger::isCurrent)?.let { session.store.batch(it.producer, it.owner) }
        if (stored != null) session.ledger.claimIfStale(unit)
        return stored?.let { ResolvedBuildModel(BuildModelCodec.fromFacts(it.facts), emptyMap()) }
    }

    private fun freshModel(
        provider: BuildModelProvider,
        unit: PlannedUnit,
        artifacts: List<Artifact>,
    ): ResolvedBuildModel? {
        val request = BuildModelRequest(repository, artifacts, files, context.config.trusted)
        return when (val result = provider.provide(request)) {
            is BuildModelResult.Provided -> persist(provider, unit, result)
            BuildModelResult.NotApplicable -> null
            is BuildModelResult.Failed -> reportFailure(result)
        }
    }

    private fun reportFailure(failure: BuildModelResult.Failed): ResolvedBuildModel? {
        session.notices += Diagnostic(DiagnosticSeverity.WARNING, failure.reason, failure.message, null)
        return null
    }

    private fun persist(
        provider: BuildModelProvider,
        inputUnit: PlannedUnit,
        result: BuildModelResult.Provided,
    ): ResolvedBuildModel {
        val model = BuildModelCodec.canonical(result.model)
        val facts = BuildModelCodec.toFacts(model, result.diagnostics, result.facts)
        val unit = if (provider.requiresTrust) contentUnit(inputUnit, facts) else inputUnit
        if (session.ledger.claimIfStale(unit)) {
            session.ledger.commit(FactBatch(unit.producer, unit.owner, unit.fingerprint, facts))
        }
        return ResolvedBuildModel(model, workspaceClasspaths.withoutBuildOutput(result.classpaths))
    }

    private fun contentUnit(unit: PlannedUnit, facts: List<Fact>): PlannedUnit {
        val content = FingerprintBuilder()
            .add(unit.producer.id)
            .add(unit.producer.version)
            .addAll(facts.map { KscopeJson.encodeToString(Fact.serializer(), it) })
            .build()
        return unit.copy(fingerprint = content)
    }

    private companion object {
        val noModelNotice = Diagnostic(
            severity = DiagnosticSeverity.INFO,
            code = ReasonCode("build-model.not-available"),
            message = "No build model provider applies to this workspace; no modules or source sets are modelled.",
            location = null,
        )
    }
}
