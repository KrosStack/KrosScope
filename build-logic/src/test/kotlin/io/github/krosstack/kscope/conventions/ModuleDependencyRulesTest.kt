package io.github.krosstack.kscope.conventions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModuleDependencyRulesTest {
    @Test
    fun `core model may not depend on any project module`() {
        assertTrue(ModuleDependencyRules.violations(":core:model", emptyList()).isEmpty())
        assertEquals(
            listOf(":core:model must not depend on :core:spi"),
            ModuleDependencyRules.violations(":core:model", listOf(":core:spi")),
        )
    }

    @Test
    fun `core spi may depend on core model only`() {
        assertTrue(ModuleDependencyRules.violations(":core:spi", listOf(":core:model")).isEmpty())
        assertEquals(
            listOf(":core:spi must not depend on :core:store"),
            ModuleDependencyRules.violations(":core:spi", listOf(":core:model", ":core:store")),
        )
    }

    @Test
    fun `analyzers and intelligence modules may not see each other or the engine`() {
        val forbidden = listOf(":analyzers:gradle", ":intelligence:retrieval", ":core:store", ":core:engine")
        assertTrue(ModuleDependencyRules.violations(":analyzers:kotlin", listOf(":core:model", ":core:spi")).isEmpty())
        assertEquals(forbidden.size, ModuleDependencyRules.violations(":analyzers:kotlin", forbidden).size)
        assertEquals(forbidden.size, ModuleDependencyRules.violations(":intelligence:context", forbidden).size)
    }

    @Test
    fun `the gradle analyzer may bundle its model plugin and nothing else from the analyzers`() {
        val bundled = listOf(":core:model", ":core:spi", ":analyzers:gradle-model-plugin")
        assertTrue(ModuleDependencyRules.violations(":analyzers:gradle", bundled).isEmpty())
        assertEquals(1, ModuleDependencyRules.violations(":analyzers:gradle", listOf(":analyzers:kotlin")).size)
        assertEquals(1, ModuleDependencyRules.violations(":analyzers:kotlin", listOf(":analyzers:gradle-model-plugin")).size)
    }

    @Test
    fun `the kotlin analyzer and the cli may resolve the worker runtime for tests and the distribution`() {
        val worker = listOf(":core:model", ":core:spi", ":analyzers:kotlin-worker")
        assertTrue(ModuleDependencyRules.violations(":analyzers:kotlin", worker).isEmpty())
        assertTrue(ModuleDependencyRules.violations(":cli", listOf(":service", ":analyzers:kotlin-worker")).isEmpty())
        assertEquals(1, ModuleDependencyRules.violations(":analyzers:gradle", listOf(":analyzers:kotlin-worker")).size)
    }

    @Test
    fun `core engine may depend on model spi and store`() {
        val allowed = listOf(":core:model", ":core:spi", ":core:store")
        assertTrue(ModuleDependencyRules.violations(":core:engine", allowed).isEmpty())
        assertEquals(1, ModuleDependencyRules.violations(":core:engine", listOf(":analyzers:kotlin")).size)
    }

    @Test
    fun `service sees everything except frontends`() {
        val allowed = listOf(":core:engine", ":analyzers:android", ":intelligence:context", ":core:model")
        assertTrue(ModuleDependencyRules.violations(":service", allowed).isEmpty())
        assertEquals(2, ModuleDependencyRules.violations(":service", listOf(":cli", ":benchmark")).size)
    }

    @Test
    fun `cli reads product use cases from service and benchmark use cases from benchmark`() {
        assertTrue(ModuleDependencyRules.violations(":cli", listOf(":service", ":benchmark")).isEmpty())
        assertEquals(1, ModuleDependencyRules.violations(":cli", listOf(":service", ":core:engine")).size)
    }

    @Test
    fun `the benchmark reads the graph through service and the model and resolves the worker runtime`() {
        val allowed = listOf(":service", ":core:model", ":core:spi", ":analyzers:kotlin-worker")
        assertTrue(ModuleDependencyRules.violations(":benchmark", allowed).isEmpty())
        assertEquals(2, ModuleDependencyRules.violations(":benchmark", listOf(":core:engine", ":core:store")).size)
    }

    @Test
    fun `the detekt rules depend on no project module`() {
        assertTrue(ModuleDependencyRules.violations(":tools:detekt-rules", emptyList()).isEmpty())
        assertEquals(1, ModuleDependencyRules.violations(":tools:detekt-rules", listOf(":core:model")).size)
    }

    @Test
    fun `wildcard patterns match a single path segment`() {
        assertEquals(1, ModuleDependencyRules.violations(":service", listOf(":core:store:nested")).size)
        assertEquals(1, ModuleDependencyRules.violations(":service", listOf(":core")).size)
    }

    @Test
    fun `a module without a rule is reported`() {
        assertEquals(
            listOf(":unknown has no entry in ModuleDependencyRules"),
            ModuleDependencyRules.violations(":unknown", emptyList()),
        )
    }
}
