package io.github.krosstack.kscope.service.android

import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Resolution
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.spi.graph.SymbolQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ViewBindingFlavorIntegrationTest : SurfacesWorkspace() {
    @Test
    fun `flavors defining the same layout scan and resolve to their own bindings`() {
        writeFlavors()
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            val graph = service.graph()
            val name = "$app.databinding.SamplePanel3Binding"
            val bindings = graph.symbols(SymbolQuery(qualifiedName = name))
            assertEquals(
                listOf("red", "tan"),
                bindings.map { it.scope.sourceSet?.value?.substringAfterLast('@') }.sortedBy { it },
            )
            listOf("red", "tan").forEach { flavor ->
                val file = "app/src/$flavor/kotlin/sample/viewdroid/app/Welcome.kt"
                val edges = graph.edgesInto(file, name)
                assertCompilerExact(edges, "$flavor binding")
                val target = bindings.single { it.scope.sourceSet?.value?.endsWith("@$flavor") == true }
                assertTrue(
                    edges.all { (it.resolution as Resolution.Resolved).target == NodeRef.OfSymbol(target.id) },
                    "$flavor references: $edges",
                )
                assertCompilerExact(graph.edgesInto(file, "$name.${flavor}Title"), "$flavor field")
            }
        }
    }

    @Test
    fun `different layout names that generate the same class in main and flavors scan without duplicate ids`() {
        writeFlavors()
        sample.write("app/src/main/res/layout/sample_panel_3.xml", layout("main_title"))
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            val bindings = service.graph().symbols(SymbolQuery(qualifiedName = "$app.databinding.SamplePanel3Binding"))
            assertEquals(
                listOf("main", "red", "tan"),
                bindings.map { it.scope.sourceSet?.value?.substringAfterLast('@') }.sortedBy { it },
            )
        }
    }

    @Test
    fun `a flavor layout overrides main for Kotlin references in the default variant`() {
        writeFlavors()
        sample.write("app/src/main/res/layout/sample_panel3.xml", layout("main_title"))
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            val graph = service.graph()
            val name = "$app.databinding.SamplePanel3Binding"
            val file = "app/src/red/kotlin/sample/viewdroid/app/Welcome.kt"
            val binding = graph.symbols(SymbolQuery(qualifiedName = name))
                .single { it.scope.sourceSet?.value?.endsWith("@red") == true }
            val edges = graph.edgesInto(file, name)
            assertCompilerExact(edges, "the overriding binding")
            assertTrue(edges.all { (it.resolution as Resolution.Resolved).target == NodeRef.OfSymbol(binding.id) })
            assertCompilerExact(graph.edgesInto(file, "$name.redTitle"), "the overriding field")
        }
    }

    @Test
    fun `a non default flavor resolves binding fields returned by shared Kotlin code`() {
        writeFlavors()
        sample.write(
            "app/src/main/kotlin/sample/viewdroid/app/SharedBinding.kt",
            """
            package sample.viewdroid.app
            import sample.viewdroid.app.databinding.SamplePanel3Binding
            fun sharedBinding(binding: SamplePanel3Binding) = binding
            """.trimIndent(),
        )
        val file = "app/src/tan/kotlin/sample/viewdroid/app/Welcome.kt"
        sample.write(
            file,
            """
            package sample.viewdroid.app
            import sample.viewdroid.app.databinding.SamplePanel3Binding
            fun welcome(binding: SamplePanel3Binding) = sharedBinding(binding).tanTitle
            """.trimIndent(),
        )
        KrosScope.open(sample.root).use { service ->
            val scan = service.scan()
            assertEquals(0, scan.diagnostics.errors, "${scan.diagnostics.entries}")
            assertCompilerExact(
                service.graph().edgesInto(file, "$app.databinding.SamplePanel3Binding.tanTitle"),
                "the flavor field returned by shared code",
            )
        }
    }

    private fun writeFlavors() {
        sample.write(
            "app/build.gradle.kts",
            sample.root.resolve("app/build.gradle.kts").toFile().readText() + """

            android {
                flavorDimensions += "edition"
                productFlavors {
                    create("red") { dimension = "edition" }
                    create("tan") { dimension = "edition" }
                }
            }
            """.trimIndent(),
        )
        listOf("red", "tan").forEach { flavor ->
            sample.write("app/src/$flavor/res/layout/sample_panel3.xml", layout("${flavor}_title"))
            sample.write(
                "app/src/$flavor/kotlin/sample/viewdroid/app/Welcome.kt",
                """
                package sample.viewdroid.app
                import sample.viewdroid.app.databinding.SamplePanel3Binding
                fun welcome(binding: SamplePanel3Binding) = binding.${flavor}Title
                """.trimIndent(),
            )
        }
    }

    private fun layout(id: String): String =
        "<TextView xmlns:android=\"http://schemas.android.com/apk/res/android\" android:id=\"@+id/$id\"/>"
}
