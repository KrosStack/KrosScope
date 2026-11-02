package io.github.krosstack.kscope.analyzer.schema.spec

import io.github.krosstack.kscope.analyzer.schema.FakeGraph
import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.TestWorkspace
import io.github.krosstack.kscope.analyzer.schema.facts
import io.github.krosstack.kscope.analyzer.schema.ktor.KtorRouteProducer
import io.github.krosstack.kscope.analyzer.schema.openapi.OpenApiProducer
import io.github.krosstack.kscope.analyzer.schema.relationships
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.SymbolId

internal const val SPEC = "spec/openapi/tasktide.yaml"
internal const val ROUTES = "server/src/main/kotlin/Routes.kt"
internal const val IMPORT = "import io.ktor.server.routing.*\n"
internal const val TASKS_ROUTE = IMPORT + "fun Route.t() { get(\"/tasks\") { } }\n"
internal const val AMBIGUOUS = "unresolved spec.ambiguous-implementation"

/** A document with the operations given, each named after its method: `getOperation`. */
internal fun specification(vararg operations: String, servers: String = ""): String =
    "openapi: 3.0.3\n$servers" + "paths:\n" + operations.joinToString("") { it.trimEnd() + "\n" }

internal fun operation(path: String, vararg methods: String): String =
    "  $path:\n" + methods.joinToString("") { "    $it:\n      operationId: ${it}Operation\n" }

internal fun routes(vararg calls: String): String =
    IMPORT + "fun Route.t() {\n" + calls.joinToString("") { "  $it\n" } + "}\n"

/** What the linker made of one workspace: the batches it sent, and the names of the symbols they point to. */
internal class Linked(val batches: List<FactBatch>, private val names: Map<SymbolId, String>) {
    val relationships: List<Relationship> = batches.flatMap { it.facts }.relationships()

    fun outcomes(): Map<String, String> = relationships.associate { relationship ->
        names.getValue((relationship.from as NodeRef.OfSymbol).id) to outcomeOf(relationship.resolution)
    }

    private fun outcomeOf(resolution: Resolution): String = when (resolution) {
        is Resolution.Resolved -> names.getValue((resolution.target as NodeRef.OfSymbol).id)
        is Resolution.Unresolved -> "unresolved ${resolution.reason.value}"
        is Resolution.NotAnalysed -> "not analysed ${resolution.reason.value}"
    }
}

/** Reads the files with the producers, in three modules, and runs the linker on what they found. */
internal fun link(files: Map<String, String>): Linked {
    val workspace = TestWorkspace(files, listOf(ModuleSpec(":spec"), ModuleSpec(":server"), ModuleSpec(":other")))
    val specs = workspace.produce(OpenApiProducer()).facts()
    val facts = specs + workspace.produceBySourceSet(KtorRouteProducer()).facts()
    val batches = mutableListOf<FactBatch>()
    OpenApiRouteLinker().link(FakeGraph(facts, workspace.model)) { batches += it }
    return Linked(batches, facts.symbols().associate { it.id to it.name })
}
