package io.github.krosstack.kscope.analyzer.android.aidl

import io.github.krosstack.kscope.analyzer.android.Golden
import io.github.krosstack.kscope.analyzer.android.Render
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import io.github.krosstack.kscope.model.SymbolCategory
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.model.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val SERVICE_ID = ":svc@main:aidl/sample/tasktide/sync/service/ISyncService#"

class AidlProducerTest {
    private val workspace = AidlWorkspace(syncSample())

    private fun symbolsOf(path: String) = workspace.produce { it.ref.path.value == path }
        .flatMap { it.facts }.filterIsInstance<Fact.OfSymbol>().map { it.value }

    @Test
    fun `the facts of the sync contracts match the golden file`() {
        Golden.assertMatches("aidl-producer.jsonl", Golden.facts(workspace.produce()))
    }

    @Test
    fun `an interface and its methods are contract symbols below the aidl namespace`() {
        val lines = Render.lines(workspace.produce { it.ref.path.value == AidlPaths.SERVICE }.flatMap { it.facts })
        assertEquals(
            listOf(
                "symbol aidl.interface $SERVICE_ID 6:11-6:23",
                "symbol aidl.method ${SERVICE_ID}startSync(). 7:10-7:19",
                "symbol aidl.method ${SERVICE_ID}getStatus(). 9:16-9:25",
                "symbol aidl.method ${SERVICE_ID}registerCallback(ISyncCallback). 11:10-11:26",
                "symbol aidl.method ${SERVICE_ID}unregisterCallback(ISyncCallback). 13:10-13:28",
            ),
            lines,
        )
    }

    @Test
    fun `symbols carry their names, containers, signatures and the contract category`() {
        val symbols = symbolsOf(AidlPaths.CALLBACK)
        val contract = symbols.first()
        assertEquals("sample.tasktide.sync.service.ISyncCallback", contract.qualifiedName)
        assertEquals("oneway interface ISyncCallback", contract.signature)
        assertTrue(symbols.all { it.kind.category == SymbolCategory.CONTRACT && it.origin == SymbolOrigin.Source })
        val method = symbols.single { it.name == "onSyncFinished" }
        assertEquals("sample.tasktide.sync.service.ISyncCallback.onSyncFinished", method.qualifiedName)
        assertEquals("void onSyncFinished(in SyncStatus status)", method.signature)
        assertEquals(contract.id, method.container)
        assertEquals(mapOf("aidl.oneway" to "true"), method.attributes)
        assertEquals(TextRange(8, 5, 8, 47), method.declaration?.range)
    }

    @Test
    fun `a method of a plain interface is not marked oneway unless it says so`() {
        val text = AidlSamples.everything.replace("package sample.aidl.svc", "package p")
        val methods = AidlWorkspace(mapOf(AidlPaths.SERVICE to text)).produce()
            .flatMap { it.facts }.filterIsInstance<Fact.OfSymbol>().map { it.value }
            .filter { it.kind.id == "aidl.method" }
        assertEquals(listOf("fire"), methods.filter { it.attributes["aidl.oneway"] == "true" }.map { it.name })
    }

    @Test
    fun `an unstructured and a structured parcelable are told apart`() {
        val unstructured = symbolsOf(AidlPaths.STATUS).single()
        assertEquals("aidl.parcelable", unstructured.kind.id)
        assertEquals("sample.tasktide.sync.service.SyncStatus", unstructured.qualifiedName)
        assertEquals(mapOf("aidl.structured" to "false"), unstructured.attributes)
        val structured = symbolsOf(AidlPaths.REPORT)
        assertEquals(mapOf("aidl.structured" to "true"), structured.first().attributes)
        assertEquals("aidl.constant", structured.last().kind.id)
        assertEquals("const int STATE_OPEN = 1", structured.last().signature)
    }

    @Test
    fun `constants and overloads get distinct identities`() {
        val text = """
            package p;
            interface I {
                const int LIMIT = 1;
                void f(int a);
                void f(String a);
                void f(in List<String> a, out int[] b);
            }
        """.trimIndent()
        val facts = AidlWorkspace(mapOf(AidlPaths.SERVICE to text)).produce().flatMap { it.facts }
        val ids = facts.filterIsInstance<Fact.OfSymbol>().map { Render.id(it.value.id) }
        assertEquals(
            listOf(
                ":svc@main:aidl/p/I#",
                ":svc@main:aidl/p/I#LIMIT.",
                ":svc@main:aidl/p/I#f(List,int[]).",
                ":svc@main:aidl/p/I#f(String).",
                ":svc@main:aidl/p/I#f(int).",
            ),
            ids.sorted(),
        )
    }

    @Test
    fun `a file that cannot be read gives one warning at the problem and no symbols`() {
        val workspace = AidlWorkspace(mapOf(AidlPaths.SERVICE to "package p;\ninterface I { void f() }\n"))
        val facts = workspace.produce().flatMap { it.facts }
        val diagnostic = (facts.single() as Fact.OfDiagnostic).value
        assertEquals(DiagnosticSeverity.WARNING, diagnostic.severity)
        assertEquals("aidl.parse-error", diagnostic.code.value)
        assertEquals(TextRange(2, 24, 2, 25), diagnostic.location?.range)
        assertEquals("${AidlPaths.SERVICE}: Expected ';' but found '}'.", diagnostic.message)
    }

    @Test
    fun `an enum is reported as skipped and does not hide the interface`() {
        val facts = AidlWorkspace(mapOf(AidlPaths.SERVICE to AidlSamples.everything)).produce().flatMap { it.facts }
        val notes = facts.filterIsInstance<Fact.OfDiagnostic>().map { it.value }
        assertEquals(listOf("aidl.declaration-skipped", "aidl.declaration-skipped"), notes.map { it.code.value })
        assertEquals(DiagnosticSeverity.INFO, notes.first().severity)
        assertTrue(facts.filterIsInstance<Fact.OfSymbol>().any { it.value.name == "IEverything" })
    }

    @Test
    fun `a file outside every source root only gets a note`() {
        val facts = AidlWorkspace(mapOf("stray/aidl/I.aidl" to "interface I {}\n")).produce().flatMap { it.facts }
        assertEquals(listOf("aidl.module-unknown"), facts.map { (it as Fact.OfDiagnostic).value.code.value })
    }
}
