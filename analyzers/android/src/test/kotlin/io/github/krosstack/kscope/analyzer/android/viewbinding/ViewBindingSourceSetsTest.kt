package io.github.krosstack.kscope.analyzer.android.viewbinding

import io.github.krosstack.kscope.analyzer.android.DroidModel
import io.github.krosstack.kscope.analyzer.android.SampleTree
import io.github.krosstack.kscope.model.RepoPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ViewBindingSourceSetsTest : ViewBindingTestBase() {
    @Test
    fun `two flavors defining the same layout each own a binding class`() {
        val layouts = mapOf(
            "app/src/demo/res/layout/sample_panel3.xml" to layout("demo_title"),
            "app/src/full/res/layout/sample_panel3.xml" to layout("full_title"),
        )
        val result = run(flavorTree(layouts))
        val bindings = result.sources.filter { it.virtualPath.endsWith("/SamplePanel3Binding.java") }
        assertEquals(listOf("repo//:app@demo", "repo//:app@full"), bindings.map { it.sourceSet.value }.sorted())
        assertTrue("demoTitle" in bindings.single { it.sourceSet.value.endsWith("@demo") }.text)
        assertTrue("fullTitle" in bindings.single { it.sourceSet.value.endsWith("@full") }.text)
        val classes = result.symbols().filter { it.name == "SamplePanel3Binding" }
        assertEquals(bindings.map { it.sourceSet }.toSet(), classes.map { it.scope.sourceSet }.toSet())
        assertEquals(result.symbols().size, result.symbols().map { it.id }.toSet().size)
    }

    @Test
    fun `qualifier layouts in one flavor merge their fields into one class`() {
        val layouts = mapOf(
            "app/src/demo/res/layout/sample_panel3.xml" to layout("portrait_title"),
            "app/src/demo/res/layout-land/sample_panel3.xml" to layout("landscape_title"),
        )
        val result = run(flavorTree(layouts))
        val binding = result.source("SamplePanel3Binding")
        assertEquals("repo//:app@demo", binding.sourceSet.value)
        assertTrue("portraitTitle" in binding.text)
        assertTrue("landscapeTitle" in binding.text)
        assertEquals(1, result.symbols().count { it.name == "SamplePanel3Binding" })
    }

    @Test
    fun `a flavor overriding a main layout keeps the declarations and fields separate`() {
        val layouts = mapOf(
            "app/src/main/res/layout/sample_panel3.xml" to layout("main_title"),
            "app/src/demo/res/layout/sample_panel3.xml" to layout("flavor_title"),
        )
        val result = run(flavorTree(layouts))
        val bindings = result.sources.filter { it.virtualPath.endsWith("/SamplePanel3Binding.java") }
        assertEquals(listOf("repo//:app@demo", "repo//:app@main"), bindings.map { it.sourceSet.value }.sorted())
        val flavor = bindings.single { it.sourceSet.value.endsWith("@demo") }
        assertTrue("flavorTitle" in flavor.text)
        assertTrue("mainTitle" !in flavor.text)
        assertEquals(result.symbols().size, result.symbols().map { it.id }.toSet().size)
    }

    @Test
    fun `a flavor override inherits main configurations that it does not replace`() {
        val layouts = mapOf(
            "app/src/main/res/layout/sample_panel3.xml" to layout("main_title"),
            "app/src/main/res/layout-land/sample_panel3.xml" to layout("landscape_title"),
            "app/src/demo/res/layout/sample_panel3.xml" to layout("flavor_title"),
        )
        val result = run(flavorTree(layouts))
        val flavor = result.sources.single {
            it.virtualPath.endsWith("/SamplePanel3Binding.java") && it.sourceSet.value.endsWith("@demo")
        }
        assertTrue("flavorTitle" in flavor.text)
        assertTrue("landscapeTitle" in flavor.text)
        assertTrue("mainTitle" !in flavor.text)
    }

    @Test
    fun `a build type binding inherits flavor configurations that replace main`() {
        val layouts = mapOf(
            "app/src/main/res/layout/sample_panel3.xml" to layout("main_title"),
            "app/src/demo/res/layout/sample_panel3.xml" to layout("flavor_title"),
            "app/src/full/res/layout/sample_panel3.xml" to layout("flavor_title"),
            "app/src/debug/res/layout-land/sample_panel3.xml" to layout("landscape_title"),
        )
        val result = run(flavorTree(layouts))
        val debug = result.sources.single {
            it.virtualPath.endsWith("/SamplePanel3Binding.java") && it.sourceSet.value.endsWith("@debug")
        }
        assertTrue("flavorTitle" in debug.text)
        assertTrue("landscapeTitle" in debug.text)
        assertTrue("mainTitle" !in debug.text)
    }

    private fun flavorTree(layouts: Map<String, String>): SampleTree {
        val model = DroidModel.standard(
            appAttributes = mapOf(VIEW_BINDING_FEATURE_ON to "true"),
            flavors = listOf("demo", "full"),
        )
        val template = model.sourceSets.single { it.id == DroidModel.sourceSetId(":app", "debug") }
        val flavors = listOf("demo", "full").map { name ->
            template.copy(
                id = DroidModel.sourceSetId(":app", name),
                name = name,
                roots = template.roots.map {
                    it.copy(directory = RepoPath(it.directory.value.replace("/debug/", "/$name/")))
                },
                variants = model.variants.filter { name in it.flavors }.map { it.id },
            )
        }
        return SampleTree("ui", model.copy(sourceSets = model.sourceSets + flavors), layouts)
    }

    private fun layout(id: String): String =
        "<TextView xmlns:android=\"http://schemas.android.com/apk/res/android\" android:id=\"@+id/$id\"/>"
}
