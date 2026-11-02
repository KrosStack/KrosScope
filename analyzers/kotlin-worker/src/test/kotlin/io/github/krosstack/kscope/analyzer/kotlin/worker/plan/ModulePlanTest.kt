package io.github.krosstack.kscope.analyzer.kotlin.worker.plan

import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.spi.worker.WorkerModule
import kotlin.test.Test
import kotlin.test.assertEquals

class ModulePlanTest {
    private fun module(
        name: String,
        dependsOn: List<String> = emptyList(),
        dependencies: List<String> = emptyList(),
        friends: List<String> = emptyList(),
    ) = WorkerModule(name, Platform.JVM, emptyList(), dependsOn, dependencies, friends, emptyList())

    private fun ModulePlan.planned(name: String) = ordered.single { it.module.name == name }

    @Test
    fun `dependencies come before the modules that use them`() {
        val app = module("app", dependencies = listOf("lib"))
        val plan = ModulePlan.of(listOf(app, module("lib", dependencies = listOf("core")), module("core")))
        assertEquals(listOf("core", "lib", "app"), plan.ordered.map { it.module.name })
    }

    @Test
    fun `a regular dependency is expanded with its depends-on closure`() {
        val plan = ModulePlan.of(
            listOf(
                module("common"),
                module("middle", dependsOn = listOf("common")),
                module("jvm", dependsOn = listOf("middle")),
                module("app", dependencies = listOf("jvm")),
            ),
        )
        assertEquals(listOf("jvm", "middle", "common"), plan.planned("app").regularDependencies)
    }

    @Test
    fun `a friend is expanded with its depends-on closure and stays separate from regular dependencies`() {
        val plan = ModulePlan.of(
            listOf(
                module("commonMain"),
                module("jvmMain", dependsOn = listOf("commonMain")),
                module("jvmTest", dependencies = listOf("jvmMain"), friends = listOf("jvmMain")),
            ),
        )
        assertEquals(listOf("jvmMain", "commonMain"), plan.planned("jvmTest").friends)
        assertEquals(listOf("jvmMain", "commonMain"), plan.planned("jvmTest").regularDependencies)
    }

    @Test
    fun `refinement edges are kept as they are`() {
        val plan = ModulePlan.of(listOf(module("common"), module("jvm", dependsOn = listOf("common"))))
        assertEquals(listOf("common"), plan.planned("jvm").dependsOn)
        assertEquals(emptyList(), plan.planned("jvm").regularDependencies)
    }

    @Test
    fun `references to unknown modules are dropped and reported`() {
        val plan = ModulePlan.of(listOf(module("app", dependencies = listOf("missing"), friends = listOf("ghost"))))
        assertEquals(emptyList(), plan.planned("app").regularDependencies)
        assertEquals(
            listOf(
                PlanProblem("app", PlanProblemKind.UNKNOWN_MODULE, "missing"),
                PlanProblem("app", PlanProblemKind.UNKNOWN_MODULE, "ghost"),
            ),
            plan.problems,
        )
    }

    @Test
    fun `a cycle is cut at the edge that closes it and reported`() {
        val plan = ModulePlan.of(
            listOf(module("a", dependencies = listOf("b")), module("b", dependencies = listOf("a"))),
        )
        assertEquals(listOf("b", "a"), plan.ordered.map { it.module.name })
        assertEquals(emptyList(), plan.planned("b").regularDependencies)
        assertEquals(listOf(PlanProblem("b", PlanProblemKind.CYCLE, "a")), plan.problems)
    }

    @Test
    fun `a module is never its own dependency`() {
        val plan = ModulePlan.of(listOf(module("a", dependencies = listOf("a"), friends = listOf("a"))))
        assertEquals(emptyList(), plan.planned("a").regularDependencies)
        assertEquals(emptyList(), plan.planned("a").friends)
    }
}
