package io.github.krosstack.kscope.analyzer.schema.ktor

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.analyzer.schema.Render
import io.github.krosstack.kscope.analyzer.schema.TestWorkspace
import io.github.krosstack.kscope.analyzer.schema.facts
import io.github.krosstack.kscope.analyzer.schema.symbols
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.FactBatch
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.OwnershipUnit
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.spi.AnalysisUnit
import io.github.krosstack.kscope.spi.WorkspaceFiles
import java.io.IOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ROUTES = "server/src/main/kotlin/sample/tasktide/server/Routes.kt"

private val routesSource = """
    package sample.tasktide.server

    import io.ktor.server.http.content.staticResources
    import io.ktor.server.response.respond
    import io.ktor.server.routing.Route
    import io.ktor.server.routing.get

    fun Route.taskRoutes(tasks: TaskStore) {
        get("/tasks") {
            call.respond(tasks.newestFirst())
        }
        staticResources("/", "static")
    }
""".trimIndent() + "\n"

private fun Symbol.line() = requireNotNull(nameLocation).range.startLine

class KtorRouteProducerTest {
    private val workspace = TestWorkspace(
        mapOf(
            ROUTES to routesSource,
            "server/src/main/kotlin/sample/tasktide/server/TaskStore.kt" to "package sample\nclass TaskStore\n",
            "server/src/test/kotlin/sample/tasktide/server/RoutesTest.kt" to "package sample\nclass RoutesTest\n",
        ),
        listOf(ModuleSpec(":server", sourceSets = listOf("main", "test"))),
    )

    private fun symbols(): List<Symbol> = workspace.produceBySourceSet(KtorRouteProducer()).facts().symbols()

    @Test
    fun `a Kotlin source is the artifact that the producer reads, one source set at a time`() {
        val descriptor = KtorRouteProducer().descriptor

        assertEquals("kotlin.source", workspace.artifact(ROUTES).type.id)
        assertEquals(setOf("kotlin.source"), descriptor.consumes.map { it.id }.toSet())
        assertEquals(OwnershipKind.SOURCE_SET, descriptor.granularity)
    }

    @Test
    fun `a route becomes a ktor route symbol named by its method and path, at the line of the call`() {
        val route = symbols().single()

        assertEquals("ktor.route", route.kind.id)
        assertEquals("GET /tasks", route.name)
        assertEquals("GET /tasks", route.qualifiedName)
        assertEquals("GET /tasks", route.signature)
        assertEquals(9, route.line())
        assertEquals(9 to 11, requireNotNull(route.declaration).range.let { it.startLine to it.endLine })
        assertEquals(mapOf("ktor.method" to "GET", "ktor.path" to "/tasks"), route.attributes)
        assertEquals("CONTRACT", route.kind.category.name)
        assertEquals("TEXTUAL", route.evidenceClass.name)
    }

    @Test
    fun `the route is placed in its module and its source set, and its id carries the file`() {
        val route = symbols().single()

        assertEquals(":server@main:ktor/`$ROUTES`:`GET /tasks`.", Render.id(route.id))
        assertEquals("repo//:server@main", route.scope.sourceSet?.value)
    }

    @Test
    fun `every source set answers with a batch, empty when it declares no route`() {
        val batches = workspace.produceBySourceSet(KtorRouteProducer())

        val symbolsBySourceSet = batches.associate { it.owner.key to it.facts.symbols().size }

        assertEquals(mapOf("repo//:server@main" to 1, "repo//:server@test" to 0), symbolsBySourceSet)
        assertTrue(batches.all { it.owner.kind == OwnershipKind.SOURCE_SET })
    }

    @Test
    fun `routes of one file with the same method and path get distinct ids`() {
        val twice = TestWorkspace(
            mapOf(
                ROUTES to "import io.ktor.server.routing.*\nfun Route.a() { get(\"/a\") { } }\n" +
                    "fun Route.b() { authenticate { get(\"/a\") { } } }\n",
            ),
            listOf(ModuleSpec(":server")),
        )
        val routes = twice.produceBySourceSet(KtorRouteProducer()).facts().symbols()

        assertEquals(listOf("GET /a", "GET /a"), routes.map { it.name })
        assertEquals(2, routes.map { it.id }.toSet().size)
        assertEquals(listOf(2, 3), routes.map { it.line() })
    }

    @Test
    fun `a file that no module contains is noted when it has routes`() {
        val stray = TestWorkspace(
            mapOf(
                "elsewhere/Routes.kt" to routesSource,
                "elsewhere/Plain.kt" to "class Plain\n",
            ),
            listOf(ModuleSpec(":server")),
        )
        val facts = stray.produceBySourceSet(KtorRouteProducer()).facts()
        val note = facts.filterIsInstance<Fact.OfDiagnostic>().single().value

        assertEquals(emptyList(), facts.symbols())
        assertEquals("ktor.module-unknown", note.code.value)
        assertEquals(DiagnosticSeverity.INFO, note.severity)
    }

    @Test
    fun `a file that cannot be read is noted and the others of its source set are still read`() {
        val units = workspace.artifacts.filter { it.sourceSet?.value == "repo//:server@main" }
        val failing = object : WorkspaceFiles {
            override fun read(ref: ArtifactRef): ByteArray =
                if (ref.path.value.endsWith("TaskStore.kt")) throw IOException("gone") else workspace.files.read(ref)

            override fun absolutePath(ref: ArtifactRef): Path = Path.of(ref.path.value)
        }
        val unit = AnalysisUnit(
            OwnershipUnit(OwnershipKind.SOURCE_SET, "repo//:server@main"),
            units,
            workspace.model.sourceSets.first { it.name == "main" },
            workspace.model.modules.single(),
        )
        val batches = mutableListOf<FactBatch>()
        KtorRouteProducer().produce(workspace.request(listOf(unit)).copy(files = failing)) { batches += it }
        val facts = batches.single().facts

        assertEquals(listOf("GET /tasks"), facts.symbols().map { it.name })
        assertEquals("ktor.unreadable", facts.filterIsInstance<Fact.OfDiagnostic>().single().value.code.value)
    }
}
