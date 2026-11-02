package io.github.krosstack.kscope.analyzer.android.aidl.stub

import io.github.krosstack.kscope.analyzer.android.Golden
import io.github.krosstack.kscope.analyzer.android.aidl.AidlModel
import io.github.krosstack.kscope.analyzer.android.aidl.AidlPaths
import io.github.krosstack.kscope.analyzer.android.aidl.AidlSamples
import io.github.krosstack.kscope.analyzer.android.aidl.AidlWorkspace
import io.github.krosstack.kscope.analyzer.android.aidl.syncSample
import io.github.krosstack.kscope.model.DiagnosticSeverity
import io.github.krosstack.kscope.model.Fact
import kotlin.test.Test
import kotlin.test.assertEquals

class AidlStubTest {
    private val synthesis = AidlWorkspace(syncSample()).synthesize()

    @Test
    fun `the surface of an interface follows the template of the generator`() {
        Golden.assertMatches("aidl-ISyncService.java", synthesis.source("ISyncService").text)
    }

    @Test
    fun `the surface of a oneway interface is the same template`() {
        Golden.assertMatches("aidl-ISyncCallback.java", synthesis.source("ISyncCallback").text)
    }

    @Test
    fun `a structured parcelable becomes a class with its fields and constants`() {
        Golden.assertMatches("aidl-Report.java", synthesis.source("Report").text)
    }

    @Test
    fun `an unstructured parcelable has no generated class`() {
        val names = synthesis.sources.map { it.virtualPath.substringAfterLast('/').removeSuffix(".java") }
        assertEquals(listOf("ISyncCallback", "ISyncService", "Report"), names.sorted())
    }

    @Test
    fun `a stub lies in the source set of its file under a path made from the package`() {
        val source = synthesis.source("ISyncService")
        assertEquals("aidl/sample/tasktide/sync/service/ISyncService.java", source.virtualPath)
        assertEquals("java", source.language)
        assertEquals("android.aidl", source.generator)
        assertEquals(AidlModel.sourceSetId(":svc", "main"), source.sourceSet)
    }

    @Test
    fun `types are written as the generator writes them`() {
        val text = AidlWorkspace(mapOf(AidlPaths.SERVICE to AidlSamples.javaTypes)).synthesize().source("IMapped").text
        Golden.assertMatches("aidl-IMapped.java", text)
    }

    @Test
    fun `a file that does not parse gives no surface`() {
        val broken = AidlWorkspace(mapOf(AidlPaths.SERVICE to "interface I { void f() }"))
        assertEquals(emptyList(), broken.synthesize().sources)
        assertEquals(emptyList(), broken.synthesize().facts)
    }

    @Test
    fun `a module that switches the aidl feature off gets no surface and a note`() {
        val off = AidlWorkspace(syncSample(), AidlModel.standard(mapOf("android.buildFeatures.aidl" to "false")))
        val result = off.synthesize()
        assertEquals(emptyList(), result.sources)
        val notes = result.facts.map { (it as Fact.OfDiagnostic).value }
        assertEquals(setOf("aidl.surface-disabled"), notes.map { it.code.value }.toSet())
        assertEquals(DiagnosticSeverity.INFO, notes.first().severity)
    }

    @Test
    fun `a module that switches the aidl feature on, or says nothing, gets the surface`() {
        val on = AidlWorkspace(syncSample(), AidlModel.standard(mapOf("android.buildFeatures.aidl" to "true")))
        assertEquals(3, on.synthesize().sources.size)
        assertEquals(3, synthesis.sources.size)
    }
}
