package io.github.krosstack.kscope.analyzer.schema.web.link

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.ModuleId
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals

class JsModuleBundleLinksTest {
    @Test
    fun `a copy from the build directory of a Kotlin Wasm module depends on the module by convention`() = world(
        copyScript("kmp/web/build/dist/wasmJs/productionExecutable"),
        kotlinWeb(ModuleSpec(":web", platforms = listOf(Platform.WASM))),
    ).use { world ->
        val link = world.links().single()

        assertEquals(NodeRef.OfModule(ModuleId("kmp//:web")), link.target())
        assertEquals(Certainty.HEURISTIC, (link.resolution as Resolution.Resolved).certainty)
        assertEquals(EvidenceClass.CONVENTION, link.evidenceClass)
        assertEquals("web-bundle", link.subKind)
        assertEquals(mapOf("bundle.into" to "static"), link.attributes)
    }

    @Test
    fun `the build directories of a Kotlin JS module are its dist, distributions and webpack directories`() = world(
        copyScript(
            "kmp/build/dist/js/productionExecutable",
            "kmp/build/distributions",
            "kmp/build/kotlin-webpack/js/productionExecutable",
            "kmp/build/dist",
            "kmp/build",
            "kmp/build/classes",
            "kmp/src/jsMain",
        ),
        repository("kmp", mapOf("build.gradle.kts" to ""), ModuleSpec(":", platforms = listOf(Platform.JS))),
    ).use { world ->
        val module = NodeRef.OfModule(ModuleId("kmp//:"))

        assertEquals(
            mapOf(
                "../kmp/build/dist/js/productionExecutable" to module,
                "../kmp/build/distributions" to module,
                "../kmp/build/kotlin-webpack/js/productionExecutable" to module,
                "../kmp/build/dist" to module,
            ),
            world.targets(),
        )
    }

    @Test
    fun `a module without a JS or Wasm target is no producer of a bundle`() = world(
        copyScript("kmp/jvm/build/dist/app"),
        kotlinWeb(ModuleSpec(":jvm", platforms = listOf(Platform.JVM, Platform.ANDROID))),
    ).use { world ->
        assertEquals(1, world.copies().size)
        assertEquals(emptyList(), world.links())
    }

    @Test
    fun `a build directory belongs to the module of the repository that holds it`() = world(
        copyScript("kmp/web/build/dist", "other/web/build/dist"),
        kotlinWeb(ModuleSpec(":web", platforms = listOf(Platform.JS))),
        repository(
            "other",
            mapOf("build.gradle.kts" to ""),
            ModuleSpec(":"),
            ModuleSpec(":web", platforms = listOf(Platform.WASM)),
        ),
    ).use { world ->
        assertEquals(
            mapOf(
                "../kmp/web/build/dist" to NodeRef.OfModule(ModuleId("kmp//:web")),
                "../other/web/build/dist" to NodeRef.OfModule(ModuleId("other//:web")),
            ),
            world.targets(),
        )
    }
}
