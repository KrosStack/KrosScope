package io.github.krosstack.kscope.analyzer.android.aidl.linking

import io.github.krosstack.kscope.analyzer.android.aidl.AidlModel
import io.github.krosstack.kscope.analyzer.android.aidl.AidlPaths
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals

class AidlLinkerTest {
    private val scenario = AidlScenario()
    private val service = scenario.service

    @Test
    fun `an object that extends the stub implements the interface and a class that holds it is its client`() {
        scenario.serviceWithBinder()
        scenario.client()
        assertEquals(
            listOf(
                "client ISyncCallback -> $PACKAGE.SyncService EXACT CONVENTION",
                "implementer ISyncCallback -> $APP.SyncServiceClient.callback EXACT CONVENTION",
                "client ISyncService -> $APP.SyncServiceClient EXACT CONVENTION",
                "implementer ISyncService -> $PACKAGE.SyncService.binder EXACT CONVENTION",
            ),
            scenario.lines("implementer", "client"),
        )
    }

    @Test
    fun `the type in front of dot Stub in a supertype list does not make a client`() {
        scenario.serviceWithBinder()
        assertEquals(emptyList(), scenario.lines("client").filter { it.contains("ISyncService") })
    }

    @Test
    fun `a reference in a nested class makes its outermost class the client`() {
        val outer = service.symbol("$PACKAGE.Outer")
        val companion = service.symbol("$PACKAGE.Outer.Companion", "kotlin.object", outer)
        val constant = service.symbol("$PACKAGE.Outer.Companion.LIMIT", "kotlin.property", companion)
        val contract = scenario.generated("$PACKAGE.ISyncService")
        service.edge(Edge(constant, RelationKind.REFERENCES, contract, range(9, 9, 21)))
        assertEquals(listOf("client ISyncService -> $PACKAGE.Outer EXACT CONVENTION"), scenario.lines("client"))
    }

    @Test
    @Timeout(10)
    fun `a reference in a class that is its own container makes that class the client`() {
        val loop = service.symbol("$PACKAGE.Loop")
        service.facts.replaceAll { fact ->
            val looped = Fact.OfSymbol(loop.copy(container = loop.id))
            if ((fact as? Fact.OfSymbol)?.value?.id == loop.id) looped else fact
        }
        service.edge(Edge(loop, RelationKind.REFERENCES, scenario.generated("$PACKAGE.ISyncService"), range(9, 9, 21)))
        assertEquals(listOf("client ISyncService -> $PACKAGE.Loop EXACT CONVENTION"), scenario.lines("client"))
    }

    @Test
    fun `a binding is expressed in the aidl file, keeps the scope of the compiler edge and names the supertype`() {
        scenario.serviceWithBinder()
        val binding = scenario.link().second.single { it.subKind == "implementer" }
        assertEquals(RelationKind.BINDS, binding.kind)
        assertEquals(AidlPaths.SERVICE, binding.site?.artifact?.path?.value)
        assertEquals(range(6, 11, 23), binding.site?.range)
        assertEquals(AidlModel.sourceSetId(":svc", "main"), binding.scope.sourceSet)
        assertEquals(mapOf("aidl.supertype" to "stub"), binding.attributes)
    }

    @Test
    fun `a named implementer and the methods it overrides are bound to the contract methods`() {
        val named = service.symbol("$PACKAGE.NamedBinder")
        val start = service.symbol("$PACKAGE.NamedBinder.startSync", owner = named)
        val status = service.symbol("$PACKAGE.NamedBinder.getStatus", owner = named)
        val stub = scenario.generated("$PACKAGE.ISyncService.Stub")
        service.edge(Edge(named, RelationKind.EXTENDS, stub, range(5, 30, 34)))
        val startSync = scenario.generated("$PACKAGE.ISyncService.startSync")
        service.edge(Edge(start, RelationKind.OVERRIDES, startSync, range(7, 18, 27)))
        val getStatus = scenario.generated("$PACKAGE.ISyncService.getStatus")
        service.edge(Edge(status, RelationKind.OVERRIDES, getStatus, range(9, 18, 27), null, Certainty.PROBABLE))
        assertEquals(
            listOf(
                "implementer ISyncService -> $PACKAGE.NamedBinder EXACT CONVENTION",
                "override getStatus -> $PACKAGE.NamedBinder.getStatus PROBABLE CONVENTION",
                "override startSync -> $PACKAGE.NamedBinder.startSync EXACT CONVENTION",
            ),
            scenario.lines(),
        )
    }

    @Test
    fun `a probable compiler edge makes a probable binding`() {
        val shaky = service.symbol("$PACKAGE.Shaky")
        val stub = scenario.generated("$PACKAGE.ISyncCallback.Stub")
        service.edge(Edge(shaky, RelationKind.EXTENDS, stub, range(3, 20, 24), null, Certainty.PROBABLE))
        assertEquals(listOf("implementer ISyncCallback -> $PACKAGE.Shaky PROBABLE CONVENTION"), scenario.lines())
    }

    @Test
    fun `a class that implements the interface or extends the default implementation is an implementer`() {
        val fake = service.symbol("$PACKAGE.Fake")
        val lazy = service.symbol("$PACKAGE.Lazy")
        val contract = scenario.generated("$PACKAGE.ISyncService")
        val default = scenario.generated("$PACKAGE.ISyncService.Default")
        service.edge(Edge(fake, RelationKind.IMPLEMENTS, contract, range(4, 14, 26)))
        service.edge(Edge(lazy, RelationKind.EXTENDS, default, range(6, 14, 21)))
        val bySupertype = scenario.link().second.filter { it.subKind == "implementer" }
            .associate { it.attributes.getValue("aidl.supertype") to (it.resolution as Resolution.Resolved).target }
        assertEquals(setOf("interface", "default"), bySupertype.keys)
    }

    @Test
    fun `interfaces that nothing uses have no bindings`() {
        assertEquals(emptyList(), scenario.lines())
    }

    @Test
    fun `the output does not depend on the order of the facts`() {
        scenario.serviceWithBinder()
        scenario.client()
        val forward = scenario.link().second
        val backward = scenario.link((service.facts + scenario.app.facts).reversed()).second
        assertEquals(forward, backward)
    }
}
