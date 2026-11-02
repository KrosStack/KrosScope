package io.github.krosstack.kscope.analyzer.android.aidl.linking

import io.github.krosstack.kscope.analyzer.android.aidl.AidlPaths
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals

class AidlParcelableLinkerTest {
    private val scenario = AidlScenario()
    private val service = scenario.service

    @Test
    fun `an unstructured parcelable is bound to the class of its name with textual evidence`() {
        service.symbol("$PACKAGE.SyncStatus")
        val binding = scenario.link().second.single { it.subKind == "parcelable-class" }
        assertEquals(EvidenceClass.TEXTUAL, binding.evidenceClass)
        assertEquals(Certainty.EXACT, (binding.resolution as Resolution.Resolved).certainty)
        assertEquals(AidlPaths.STATUS, binding.site?.artifact?.path?.value)
        assertEquals(range(3, 12, 22), binding.site?.range)
    }

    @Test
    fun `a parcelable class that several source sets define gives a probable binding for each`() {
        service.symbol("$PACKAGE.SyncStatus")
        val debug = CodeSide(":svc", "debug").also { it.symbol("$PACKAGE.SyncStatus") }
        val bindings = scenario.link(service.facts + debug.facts).second.filter { it.subKind == "parcelable-class" }
        val certainties = bindings.map { (it.resolution as Resolution.Resolved).certainty }
        assertEquals(listOf(Certainty.PROBABLE, Certainty.PROBABLE), certainties)
    }

    @Test
    fun `an unstructured parcelable without a class is unresolved and a structured one has no binding`() {
        assertEquals(
            listOf("parcelable-class SyncStatus -> unresolved aidl.parcelable-class-not-found TEXTUAL"),
            scenario.lines("parcelable-class"),
        )
    }

    @Test
    fun `the class that matches is the one the user wrote`() {
        service.symbol("$PACKAGE.SyncStatus")
        assertEquals(
            listOf("parcelable-class SyncStatus -> $PACKAGE.SyncStatus EXACT TEXTUAL"),
            scenario.lines("parcelable-class"),
        )
    }
}
