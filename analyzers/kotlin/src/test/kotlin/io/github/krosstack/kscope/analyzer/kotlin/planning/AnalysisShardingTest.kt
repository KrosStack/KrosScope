package io.github.krosstack.kscope.analyzer.kotlin.planning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnalysisShardingTest {
    private val layered = model {
        for (path in listOf(":core", ":a", ":b", ":c", ":d")) {
            module(path) { sourceSet("main") }
        }
        dependency(from = ":a", to = ":core")
        dependency(from = ":b", to = ":core")
        dependency(from = ":c", to = ":a")
    }
    private val heavy = 3L * 1024 * 1024
    private val halfMebibyte = 512L * 1024

    @Test
    fun `each module that asks for facts belongs to exactly one request`() {
        val plan = layered.planAll()

        val requests = AnalysisSharding().split(plan, everyModule(heavy), limit = 3)

        assertEquals(3, requests.size)
        assertEquals(plan.modules.map { it.name }.sorted(), requests.flatMap(::emitting).sorted())
    }

    @Test
    fun `a request brings what its modules refer to as context without facts`() {
        val plan = layered.planAll()

        val requests = AnalysisSharding().split(plan, everyModule(heavy), limit = 5)

        for (request in requests) {
            val names = request.map { it.name }.toSet()
            assertTrue(request.all { module -> referencesOf(module).all(names::contains) })
            val asked = emitting(request)
            assertTrue(asked.isNotEmpty())
            request.filter { it.name !in asked }.forEach { context ->
                assertTrue(context.sourceSets.none { it.emitFactsFor }, context.name)
            }
        }
        val third = requests.single { moduleName(":c", "main") in emitting(it) }
        assertEquals(
            listOf(moduleName(":a", "main"), moduleName(":c", "main"), moduleName(":core", "main")),
            third.map { it.name }.sorted(),
        )
    }

    @Test
    fun `modules are shared out by weight with the heaviest first`() {
        val plan = layered.planAll()
        val weight = weights(
            moduleName(":a", "main") to 9 * halfMebibyte,
            moduleName(":b", "main") to 5 * halfMebibyte,
            moduleName(":c", "main") to 4 * halfMebibyte,
            moduleName(":d", "main") to 3 * halfMebibyte,
            moduleName(":core", "main") to halfMebibyte,
        )

        val requests = AnalysisSharding().split(plan, weight, limit = 2)

        val shares = requests.map { emitting(it).toSet() }
        assertEquals(
            setOf(
                setOf(moduleName(":a", "main"), moduleName(":d", "main")),
                setOf(moduleName(":b", "main"), moduleName(":c", "main"), moduleName(":core", "main")),
            ),
            shares.toSet(),
        )
    }

    @Test
    fun `sharing does not change the order of modules inside a request`() {
        val plan = layered.planAll()

        val requests = AnalysisSharding().split(plan, everyModule(heavy), limit = 2)

        for (request in requests) {
            assertEquals(request.map { it.name }.sorted(), request.map { it.name })
        }
    }

    @Test
    fun `little source or a limit of one keeps a single request with every module as planned`() {
        val plan = layered.planAll()

        assertEquals(listOf(plan.modules), AnalysisSharding().split(plan, everyModule(10L * 1024), limit = 8))
        assertEquals(listOf(plan.modules), AnalysisSharding().split(plan, everyModule(heavy), limit = 1))
    }

    @Test
    fun `a single requested module keeps a single request`() {
        val plan = layered.plan(listOf(sourceSetId(":c", "main")))

        assertEquals(listOf(plan.modules), AnalysisSharding().split(plan, everyModule(heavy), limit = 8))
    }

    @Test
    fun `guessed dependencies give every request all modules`() {
        val structural = model {
            module(":app") { sourceSet("main") }
            module(":lib") { sourceSet("main") }
        }
        val plan = structural.planAll()

        val requests = AnalysisSharding().split(plan, everyModule(heavy), limit = 8)

        assertTrue(plan.dependenciesAssumed)
        assertEquals(2, requests.size)
        requests.forEach { request -> assertEquals(plan.modules.map { it.name }, request.map { it.name }) }
        assertEquals(plan.modules.map { it.name }.sorted(), requests.flatMap(::emitting).sorted())
    }

    @Test
    fun `a cycle between modules gives every request all modules in the planned order`() {
        val cyclic = model {
            module(":a") { sourceSet("main") }
            module(":b") { sourceSet("main") }
            module(":c") { sourceSet("main") }
            dependency(from = ":a", to = ":b")
            dependency(from = ":b", to = ":a")
        }
        val plan = cyclic.planAll()

        val requests = AnalysisSharding().split(plan, everyModule(heavy), limit = 3)

        assertEquals(3, requests.size)
        requests.forEach { request ->
            assertEquals(plan.modules.map { it.name }, request.map { it.name })
            assertEquals(1, emitting(request).size)
            assertTrue(request.map { it.dependencies } == plan.modules.map { it.dependencies })
        }
    }

    @Test
    fun `units follow the request that asks for their source set`() {
        val plan = layered.planAll()
        val sharding = AnalysisSharding()
        val requests = sharding.split(plan, everyModule(heavy), limit = 5)

        val perRequest = sharding.unitsPerShard(requests, unitsOf(layered))

        assertEquals(requests.size, perRequest.size)
        perRequest.forEachIndexed { index, units ->
            val asked = requests[index].flatMap { it.sourceSets }.filter { it.emitFactsFor }.map { it.sourceSet }
            assertEquals(asked.toSet(), units.map { it.sourceSet?.id }.toSet())
        }
    }
}
