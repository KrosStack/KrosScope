package io.github.krosstack.kscope.analyzer.kotlin.planning

import io.github.krosstack.kscope.model.Fingerprint
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.SourceSet
import io.github.krosstack.kscope.model.SourceSetId
import io.github.krosstack.kscope.spi.SyntheticSource
import io.github.krosstack.kscope.spi.worker.WorkerFile
import io.github.krosstack.kscope.spi.worker.WorkerModule
import io.github.krosstack.kscope.spi.worker.WorkerSourceSet
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Turns a build model into the modules of one worker request: the source sets of the requested modules and of
 * everything they depend on, grouped, wired by refinement, dependency and friend edges, and given a platform.
 * Without dependency information every source set of the workspace is analysed.
 */
internal class AnalysisModulePlanner {
    fun plan(input: PlanInput): AnalysisPlan {
        val edges = ModuleEdges(input.model)
        val layouts = layoutsOf(input, edges)
        val groups = layouts.values.flatMap { it.groups }
        val graph = GroupGraph(groups, input.model.sourceSets)
        val wiring = GroupWiring(layouts, graph, edges)
        val wirings = groups.associateWith(wiring::wiringOf)
        val synthetic = input.syntheticSources.groupBy { it.sourceSet }
        val modules = groups.sortedBy { it.name }.map { group ->
            workerModule(group, wirings.getValue(group), graph.refined(group), input, synthetic)
        }
        val reach = reachOf(wirings, GroupWiring(layouts, graph, edges.withoutHeuristic()))
        return AnalysisPlan(modules, dependenciesAssumed = !edges.hasInformation, reach = reach)
    }

    private fun reachOf(wirings: Map<SourceSetGroup, Wiring>, sure: GroupWiring): HeuristicReach {
        val bySourceSet = wirings.flatMap { (group, wiring) ->
            val heuristic = modulesOf(wiring) - modulesOf(sure.wiringOf(group))
            group.ids.map { it to heuristic }
        }
        return HeuristicReach(bySourceSet.toMap())
    }

    private fun modulesOf(wiring: Wiring): Set<ModuleId> = wiring.dependencies.map { it.module }.toSet()

    private fun layoutsOf(input: PlanInput, edges: ModuleEdges): Map<ModuleId, ModuleLayout> {
        val model = input.model
        val platforms = PlatformResolver(model.targets)
        val inScope = modulesInScope(input, edges)
        val analysable = model.sourceSets.filter { it.module in inScope && hasSources(it.id, input) }
        return analysable.groupBy { it.module }.toSortedMap(compareBy { it.value }).mapValues { (module, sourceSets) ->
            SourceSetGrouping.layoutOf(sourceSets, model.variants.filter { it.module == module }, platforms)
        }
    }

    private fun modulesInScope(input: PlanInput, edges: ModuleEdges): Set<ModuleId> {
        val model = input.model
        if (!edges.hasInformation) return model.modules.map { it.id }.toSet()
        return edges.reachableFrom(model.sourceSets.filter { it.id in input.requested }.map { it.module }.distinct())
    }

    private fun hasSources(id: SourceSetId, input: PlanInput): Boolean =
        input.files[id].orEmpty().isNotEmpty() || input.syntheticSources.any { it.sourceSet == id }

    private fun workerModule(
        group: SourceSetGroup,
        wiring: Wiring,
        refined: List<SourceSetGroup>,
        input: PlanInput,
        synthetic: Map<SourceSetId, List<SyntheticSource>>,
    ) = WorkerModule(
        name = group.name,
        platform = group.platform,
        sourceSets = group.members.map { workerSourceSet(it, input, synthetic[it.id].orEmpty()) },
        dependsOn = refined.names(),
        dependencies = wiring.dependencies.names(),
        friends = wiring.friends.names(),
        classpath = group.members.flatMap { input.classpaths[it.id].orEmpty() }.distinct(),
    )

    private fun workerSourceSet(
        sourceSet: SourceSet,
        input: PlanInput,
        synthetic: List<SyntheticSource>,
    ): WorkerSourceSet {
        val files = input.files[sourceSet.id].orEmpty().sortedBy { it.ref.path.value }
        val stubs = synthetic.sortedBy { it.virtualPath }
        return WorkerSourceSet(
            sourceSet = sourceSet.id,
            module = sourceSet.module,
            files = files,
            syntheticSources = stubs,
            emitFactsFor = sourceSet.id in input.requested,
            fingerprint = fingerprintOf(sourceSet.id, files, stubs),
        )
    }

    private fun fingerprintOf(id: SourceSetId, files: List<WorkerFile>, stubs: List<SyntheticSource>): Fingerprint {
        val parts = listOf(id.value) +
            files.flatMap { listOf(it.ref.path.value, it.contentHash.value) } +
            stubs.flatMap { listOf(it.virtualPath, it.text) }
        val digest = MessageDigest.getInstance(DIGEST).digest(parts.joinToString(SEPARATOR).toByteArray())
        return Fingerprint(HexFormat.of().formatHex(digest))
    }

    private fun List<SourceSetGroup>.names(): List<String> = map { it.name }.distinct().sorted()

    private companion object {
        const val DIGEST = "SHA-256"
        const val SEPARATOR = "\u0000"
    }
}
