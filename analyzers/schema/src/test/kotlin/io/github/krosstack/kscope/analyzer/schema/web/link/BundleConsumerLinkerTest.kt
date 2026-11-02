package io.github.krosstack.kscope.analyzer.schema.web.link

import io.github.krosstack.kscope.analyzer.schema.ModuleSpec
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.OwnershipKind
import io.github.krosstack.kscope.model.Platform
import io.github.krosstack.kscope.model.ReasonCode
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals

private val ambiguous = ReasonCode("artifact.ambiguous-producer")

class BundleConsumerLinkerTest {
    @Test
    fun `the linker is the one workspace unit that reads the graph of the workspace`() {
        val descriptor = BundleConsumerLinker().descriptor

        assertEquals("kscope.web.bundle-linker", descriptor.producer.id)
        assertEquals("0.1.0", descriptor.producer.version)
        assertEquals(OwnershipKind.WORKSPACE, descriptor.granularity)
    }

    @Test
    fun `several packages whose directories hold the path are an unresolved ambiguity by convention`() = world(
        copyScript("web/out/b/assets", "web/out/other"),
        repository(
            "web",
            mapOf(
                "a/package.json" to manifest("@tasktide/a"),
                "a/vite.config.ts" to viteConfig("../out"),
                "b/package.json" to manifest("@tasktide/b"),
                "b/vite.config.ts" to viteConfig("../out/b"),
            ),
        ),
    ).use { world ->
        val packages = world.packages().associate { it.name to NodeRef.OfSymbol(it.id) }
        val links = world.links().associateBy { world.copyOf(it).name }
        val both = links.getValue("../web/out/b/assets")

        assertEquals(setOf("../web/out/b/assets", "../web/out/other"), links.keys)
        assertEquals(Resolution.Unresolved("\$rootDir/../web/out/b/assets", ambiguous), both.resolution)
        assertEquals(EvidenceClass.CONVENTION, both.evidenceClass)
        assertEquals("web-bundle", both.subKind)
        assertEquals(mapOf("bundle.into" to "static"), both.attributes)
        assertEquals(packages.getValue("@tasktide/a"), links.getValue("../web/out/other").target())
    }

    @Test
    fun `a package and a module that both hold the path are an unresolved ambiguity`() = world(
        copyScript("kmp/web/build/dist"),
        kotlinWeb(
            ModuleSpec(":web", platforms = listOf(Platform.JS)),
            files = mapOf(
                "web/package.json" to manifest("@tasktide/kmp"),
                "web/vite.config.ts" to viteConfig("build/dist"),
            ),
        ),
    ).use { world ->
        val link = world.links().single()

        assertEquals(ambiguous, (link.resolution as Resolution.Unresolved).reason)
    }

    @Test
    fun `a workspace whose scripts copy nothing gets no batch`() =
        world("plugins { }\n", repository("web", declared)).use { world ->
            assertEquals(emptyList(), world.linkBatches())
        }

    @Test
    fun `the links come in the order of the ids of the copy sources`() = world(
        copyScript("web/dist/z", "web/dist/a", "web/dist/m"),
        repository("web", declared),
    ).use { world ->
        val order = world.copies().sortedBy { it.id.value }.map { it.name }

        assertEquals(order, world.links().map { world.copyOf(it).name })
    }
}
