package io.github.krosstack.kscope.analyzer.android.aidl.linking

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals

class AnonymousOverridesTest {
    private val scenario = AidlScenario()
    private val service = scenario.service

    @Test
    fun `an object that extends the stub overrides every method of the interface, as probable and inferred`() {
        scenario.serviceWithBinder()
        val names = listOf("getStatus", "registerCallback", "startSync", "unregisterCallback")
        assertEquals(
            names.map { "override $it -> $PACKAGE.SyncService.binder PROBABLE CONVENTION" },
            scenario.lines("override"),
        )
        val relationships = scenario.link().second.filter { it.subKind == "override" }
        assertEquals(setOf(mapOf("aidl.inferred" to "anonymous-object")), relationships.map { it.attributes }.toSet())
    }

    @Test
    fun `an object that implements the interface itself overrides every method too`() {
        val holder = service.symbol("$PACKAGE.Holder")
        val fake = service.symbol("$PACKAGE.Holder.fake", "kotlin.property", holder)
        val contract = scenario.generated("$PACKAGE.ISyncCallback")
        service.edge(Edge(fake, RelationKind.IMPLEMENTS, contract, range(4, 20, 33), "anonymous"))
        assertEquals(
            listOf("override onSyncFailed", "override onSyncFinished", "override onSyncStarted")
                .map { "$it -> $PACKAGE.Holder.fake PROBABLE CONVENTION" },
            scenario.lines("override"),
        )
    }

    @Test
    fun `an override that a relationship states wins over the inference`() {
        val binder = scenario.serviceWithBinder()
        val startSync = scenario.generated("$PACKAGE.ISyncService.startSync")
        service.edge(Edge(binder, RelationKind.OVERRIDES, startSync, range(23, 18, 27)))
        assertEquals(
            listOf(
                "override getStatus -> $PACKAGE.SyncService.binder PROBABLE CONVENTION",
                "override registerCallback -> $PACKAGE.SyncService.binder PROBABLE CONVENTION",
                "override startSync -> $PACKAGE.SyncService.binder EXACT CONVENTION",
                "override unregisterCallback -> $PACKAGE.SyncService.binder PROBABLE CONVENTION",
            ),
            scenario.lines("override"),
        )
    }

    @Test
    fun `an object that extends the default implementation overrides only some methods and is left out`() {
        val holder = service.symbol("$PACKAGE.Holder")
        val lazy = service.symbol("$PACKAGE.Holder.lazy", "kotlin.property", holder)
        val default = scenario.generated("$PACKAGE.ISyncService.Default")
        service.edge(Edge(lazy, RelationKind.EXTENDS, default, range(6, 14, 21), "anonymous"))
        assertEquals(emptyList(), scenario.lines("override"))
    }

    @Test
    fun `a named class gets overrides only from the relationships that state them`() {
        val named = service.symbol("$PACKAGE.NamedBinder")
        val stub = scenario.generated("$PACKAGE.ISyncService.Stub")
        service.edge(Edge(named, RelationKind.EXTENDS, stub, range(5, 30, 34)))
        assertEquals(emptyList(), scenario.lines("override"))
    }

    @Test
    fun `the inference is not stronger than the edge it rests on`() {
        val holder = service.symbol("$PACKAGE.Holder")
        val shaky = service.symbol("$PACKAGE.Holder.shaky", "kotlin.property", holder)
        val stub = scenario.generated("$PACKAGE.ISyncCallback.Stub")
        service.edge(Edge(shaky, RelationKind.EXTENDS, stub, range(6, 14, 21), "anonymous", Certainty.HEURISTIC))
        val overrides = scenario.link().second.filter { it.subKind == "override" }
        val certainties = overrides.map { (it.resolution as Resolution.Resolved).certainty }
        assertEquals(listOf(Certainty.HEURISTIC), certainties.distinct())
    }
}
