package io.github.krosstack.kscope.service.android

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.model.Symbol
import io.github.krosstack.kscope.model.SymbolOrigin
import io.github.krosstack.kscope.service.allRelationships
import io.github.krosstack.kscope.service.analysis.SampleWorkspace
import io.github.krosstack.kscope.service.symbolOf
import io.github.krosstack.kscope.spi.graph.GraphReader
import io.github.krosstack.kscope.spi.graph.RelationshipQuery
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the tests on the `viewdroid` sample share: the workspace, the places of its files and the reading of the graph
 * a scan makes of it. The sample is an application and a library with a manifest, layouts that name classes, view
 * binding and `BuildConfig`, and Kotlin and Java consumers of the generated classes.
 */
internal open class SurfacesWorkspace {
    protected val sample = SampleWorkspace("viewdroid")
    protected val app = "sample.viewdroid.app"
    protected val mainKotlin = "app/src/main/kotlin/sample/viewdroid/app/MainActivity.kt"
    protected val legacyJava = "app/src/main/java/sample/viewdroid/app/Legacy.java"
    protected val libKotlin = "lib/src/main/kotlin/sample/viewdroid/lib/LibService.kt"
    protected val manifest = "app/src/main/AndroidManifest.xml"
    protected val layout = "app/src/main/res/layout/activity_main.xml"
    protected val navigation = "app/src/main/res/navigation/nav_main.xml"

    @AfterTest
    fun cleanUp() {
        sample.close()
    }

    protected fun GraphReader.allRelationships(): List<Relationship> =
        relationships(RelationshipQuery(limit = Int.MAX_VALUE))

    protected fun GraphReader.symbolOf(relationship: Relationship): Symbol? =
        ((relationship.resolution as? Resolution.Resolved)?.target as? NodeRef.OfSymbol)?.let { symbol(it.id) }

    protected fun GraphReader.generated(qualifiedName: String): Symbol =
        symbols(SymbolQuery(qualifiedName = qualifiedName)).single { it.origin is SymbolOrigin.Generated }

    protected fun GraphReader.edgesInto(file: String, qualifiedName: String): List<Relationship> =
        allRelationships().filter { it.site?.artifact?.path?.value == file }
            .filter { symbolOf(it)?.qualifiedName == qualifiedName }

    protected fun GraphReader.bindsFrom(file: String): Map<String, Relationship> = allRelationships()
        .filter { it.kind == RelationKind.BINDS && it.site?.artifact?.path?.value == file }
        .associateBy { symbolOf(it)?.qualifiedName.orEmpty() }

    protected fun assertCompilerExact(edges: List<Relationship>, what: String) {
        assertTrue(edges.isNotEmpty(), "No edge to $what")
        edges.forEach {
            assertEquals(EvidenceClass.COMPILER, it.evidenceClass, what)
            assertEquals(Certainty.EXACT, (it.resolution as Resolution.Resolved).certainty, what)
        }
    }

    protected fun assertTextualExact(edge: Relationship?, what: String) {
        assertNotNull(edge, what)
        assertEquals(EvidenceClass.TEXTUAL, edge.evidenceClass, what)
        assertEquals(Certainty.EXACT, (edge.resolution as Resolution.Resolved).certainty, what)
    }
}
