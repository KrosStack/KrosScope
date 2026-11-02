package io.github.krosstack.kscope.service.android

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.allRelationships
import io.github.krosstack.kscope.service.symbolOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Scans the `viewdroid` sample with the real analyzer worker. The manifests, layouts and navigation graph name classes
 * of the workspace in both languages and in both modules, and the manifest uses resources.
 */
internal class AndroidClassReferencesIntegrationTest : SurfacesWorkspace() {

    @Test
    fun `manifest components are bound to the classes that implement them, in either language and module`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val binds = graph.bindsFrom(manifest)
            assertEquals(
                setOf("$app.ViewApp", "$app.MainActivity", "$app.SyncService", "sample.viewdroid.lib.LibService"),
                binds.keys,
            )
            binds.forEach { (name, edge) -> assertTextualExact(edge, name) }
            assertEquals("kotlin.class", graph.symbolOf(binds.getValue("$app.ViewApp"))?.kind?.id)
            assertEquals("java.class", graph.symbolOf(binds.getValue("$app.SyncService"))?.kind?.id)
        }
    }

    @Test
    fun `layouts and navigation graphs reference the classes they name`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val fromLayout = graph.allRelationships().filter { it.site?.artifact?.path?.value == layout }
                .filter { it.subKind == "custom-view" || it.subKind == "tools-context" }
            assertEquals(
                setOf("$app.BadgeView", "$app.MainActivity"),
                fromLayout.mapNotNull { graph.symbolOf(it)?.qualifiedName }.toSet(),
            )
            fromLayout.forEach { assertTextualExact(it, it.subKind.orEmpty()) }
            val destination = graph.allRelationships().single {
                it.site?.artifact?.path?.value == navigation && it.subKind == "destination-class"
            }
            assertEquals("$app.HomeFragment", graph.symbolOf(destination)?.qualifiedName)
            assertTextualExact(destination, "the destination")
        }
    }

    @Test
    fun `the manifest uses the resources of its attributes`() {
        KrosScope.open(sample.root).use { service ->
            service.scan()
            val graph = service.graph()
            val label = graph.allRelationships().single {
                it.site?.artifact?.path?.value == manifest && graph.symbolOf(it)?.name == "app_name"
            }
            assertEquals("android.resource.string", graph.symbolOf(label)?.kind?.id)
            assertTextualExact(label, "the label")
        }
    }
}
