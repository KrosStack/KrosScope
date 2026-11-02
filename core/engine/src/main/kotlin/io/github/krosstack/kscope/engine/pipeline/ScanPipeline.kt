package io.github.krosstack.kscope.engine.pipeline

import io.github.krosstack.kscope.engine.build.ModelIndex
import io.github.krosstack.kscope.engine.build.SourceSetAssigner
import io.github.krosstack.kscope.engine.detector.ArtifactClassifier
import io.github.krosstack.kscope.engine.discovery.BuildFileProbe
import io.github.krosstack.kscope.engine.discovery.DiscoveryStage
import io.github.krosstack.kscope.engine.discovery.FileLister
import io.github.krosstack.kscope.engine.discovery.GeneratedRootPolicy
import io.github.krosstack.kscope.engine.discovery.Inventory
import io.github.krosstack.kscope.engine.incremental.ApiDigestCache
import io.github.krosstack.kscope.engine.incremental.ApiDigestStage
import io.github.krosstack.kscope.engine.incremental.ClasspathContent
import io.github.krosstack.kscope.engine.incremental.DiscoveryUnit
import io.github.krosstack.kscope.engine.incremental.ScopeDigests
import io.github.krosstack.kscope.engine.incremental.SyntheticSourceCache
import io.github.krosstack.kscope.engine.incremental.UnitPlanner
import io.github.krosstack.kscope.engine.progress.ScanStages
import io.github.krosstack.kscope.engine.progress.advance
import io.github.krosstack.kscope.engine.progress.stage
import io.github.krosstack.kscope.engine.scope.AnalyzerUnit
import io.github.krosstack.kscope.engine.scope.ScopeUnit
import io.github.krosstack.kscope.engine.scope.TestFiles
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.spi.BuildModel
import io.github.krosstack.kscope.spi.graph.GraphStore

internal data class PipelineResult(
    val artifacts: Int,
    val notices: List<Diagnostic>,
    val unitsProduced: Int,
    val unitsSkipped: Int,
    val unitsRemoved: Int,
    val stageMillis: Map<String, Long>,
)

/** One fingerprint-driven update of a store: the stages in order, each producing only what changed. */
internal class ScanPipeline(
    private val context: ScanContext,
    private val store: GraphStore,
    private val synthesisCache: SyntheticSourceCache,
    private val apiDigestCache: ApiDigestCache,
) {
    private val files = WorkspaceFileAccess(context.checkouts)
    private val discoveries = context.repositories.map { repository ->
        val root = repository.root
        val buildFiles = BuildFileProbe(ArtifactClassifier(context.registry.detectors, root), root)
        val lister = FileLister(
            root,
            context.config.indexDirectory,
            repository.git,
            context.scope,
            buildFiles::isBuildFile,
        )
        DiscoveryStage(root, lister, context.registry.detectors, context.scope, root == context.config.root)
    }

    fun run(): PipelineResult {
        val session = UpdateSession(store, context.progress)
        session.notices += context.layout.notices
        val inventories = session.stage(ScanStages.DISCOVERY) { discover(session) }
        val models = session.stage(ScanStages.BUILD_MODEL) { resolveBuildModels(session, inventories) }
        val artifacts = session.stage(ScanStages.SOURCE_SETS) { commitInventories(session, models, inventories) }
        val coordinated = CoordinateStage(context, session).run(BuildModelMerge.merge(models))
        val resolved = JvmLibraryStage(context, session).run(coordinated, artifacts)
        val level = LevelUnit.plan(context.level)?.takeIf { session.ledger.claimIfStale(it) }
        CapabilityPipeline(context, session, files, synthesisCache, apiDigestCache).run(resolved, artifacts)
        level?.let { session.ledger.commit(LevelUnit.batch(it)) }
        session.stage(ScanStages.SEARCH_INDEX) {
            store.refreshSearchIndex(WorkspaceSearchContent(files, context.config.maxArtifactBytes))
        }
        return PipelineResult(
            artifacts = artifacts.size,
            notices = session.notices.toList(),
            unitsProduced = session.ledger.produced,
            unitsSkipped = session.ledger.skipped,
            unitsRemoved = session.ledger.removed,
            stageMillis = session.timer.snapshot(),
        )
    }

    private fun discover(session: UpdateSession): List<Inventory> {
        val inventories = discoveries.zip(context.repositories) { stage, repository ->
            stage.inventory(repository.repository).also { session.notices += it.notices }
        }
        val count = inventories.sumOf { it.artifacts.size }.toLong()
        session.progress.advance(ScanStages.DISCOVERY, count, count, "files")
        return inventories
    }

    private fun resolveBuildModels(session: UpdateSession, inventories: List<Inventory>): List<ResolvedBuildModel> {
        val classpaths = WorkspaceClasspaths(
            context.repositories.zip(inventories) { repository, inventory ->
                CheckoutFiles(repository.root, inventory.candidates.toSet())
            },
        )
        val models = context.repositories.zip(inventories) { repository, inventory ->
            BuildModelStage(context, session, files, repository.repository, classpaths).resolve(inventory.artifacts)
        }
        val count = models.sumOf { it.model.sourceSets.size }.toLong()
        session.progress.advance(ScanStages.BUILD_MODEL, count, count, "source sets")
        return models
    }

    private fun commitInventories(
        session: UpdateSession,
        models: List<ResolvedBuildModel>,
        inventories: List<Inventory>,
    ): List<Artifact> = context.repositories.indices.flatMap { index ->
        commitInventory(session, index, models[index].model, inventories[index])
    }

    private fun commitInventory(
        session: UpdateSession,
        index: Int,
        model: BuildModel,
        inventory: Inventory,
    ): List<Artifact> {
        val repository = context.repositories[index].repository
        val complete = discoveries[index].withBuildOutputs(repository, inventory, model.sourceSets)
        session.notices += complete.notices.drop(inventory.notices.size)
        val assigned = SourceSetAssigner(model.sourceSets).assign(complete.artifacts)
        val inScope = if (context.scope.tests) assigned else TestFiles.without(assigned, model.sourceSets)
        commit(session, DiscoveryUnit.plan(repository, inScope, context.config.maxArtifactBytes))
        ScopeUnit.plan(repository, context.scope)?.let { commit(session, it) }
        AnalyzerUnit.plan(repository, context.disabledAnalyzers)?.let { commit(session, it) }
        return inScope
    }

    private fun commit(session: UpdateSession, batch: FactBatch) {
        if (session.ledger.claimIfStale(PlannedUnit(batch.producer, batch.owner, batch.inputFingerprint))) {
            session.ledger.commit(batch)
        }
    }
}

/** Synthesis, production and linking: the stages that run capabilities from the registry. */
internal class CapabilityPipeline(
    private val context: ScanContext,
    private val session: UpdateSession,
    private val files: WorkspaceFileAccess,
    private val synthesisCache: SyntheticSourceCache,
    private val apiDigestCache: ApiDigestCache,
) {
    fun run(resolved: ResolvedBuildModel, artifacts: List<Artifact>) {
        val registry = context.registry
        val repositories = context.repositories.map { it.repository }
        val index = ModelIndex(resolved.model)
        val handedOver = artifacts.filter {
            it.sizeBytes <= context.config.maxArtifactBytes && GeneratedRootPolicy.isHandedToProducers(it)
        }
        val runner = CapabilityRunner(session, resolved, files, handedOver, context.checkouts)
        val timer = session.timer
        val classpathContent = ClasspathContent()
        val synthetic = session.stage(ScanStages.SYNTHESIS) {
            val digests = ScopeDigests(emptyList(), resolved.classpaths, classpathContent)
            val planner = UnitPlanner(repositories, handedOver, index, digests)
            SynthesisStage(registry.synthesizers, planner, session, runner, synthesisCache).run()
        }
        val productionDigests = ScopeDigests(synthetic, resolved.classpaths, classpathContent)
        val production = ProductionStage(
            registry.producers,
            UnitPlanner(repositories, handedOver, index, productionDigests),
            session,
            runner,
            RepositoryJobs(index),
        )
        val linking = LinkingStage(registry.linkers, session)
        val pendingLinks = session.progress.stage(ScanStages.PRODUCTION) {
            val apiDigests = timer.time(Stages.API_DIGESTS) {
                ApiDigestStage(apiDigestCache, handedOver, files).run(registry.producers)
            }
            val pendingProduction = timer.time(Stages.PRODUCTION) { production.plan(apiDigests) }
            val links = timer.time(Stages.LINKING) { linking.plan() }
            timer.time(Stages.CLEANUP) { session.ledger.removeUnwanted() }
            timer.time(Stages.PRODUCTION) { production.execute(pendingProduction, synthetic) }
            links
        }
        session.stage(ScanStages.LINKING) { linking.execute(pendingLinks) }
        synthesisCache.save(registry.synthesizers.map { it.descriptor.producer })
    }
}

internal object Stages {
    const val DISCOVERY = "discovery"
    const val BUILD_MODEL = "build-model"
    const val SOURCE_SETS = "source-sets"
    const val SYNTHESIS = "synthesis"
    const val API_DIGESTS = "api-digests"
    const val PRODUCTION = "production"
    const val LINKING = "linking"
    const val CLEANUP = "cleanup"
    const val SEARCH_INDEX = "search-index"
}
