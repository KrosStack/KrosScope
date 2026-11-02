package io.github.krosstack.kscope.analyzer.schema.web.link

import io.github.krosstack.kscope.model.Certainty
import io.github.krosstack.kscope.model.EvidenceClass
import io.github.krosstack.kscope.model.NodeRef
import io.github.krosstack.kscope.model.RelationKind
import io.github.krosstack.kscope.model.Resolution
import kotlin.test.Test
import kotlin.test.assertEquals

class NpmBundleLinksTest {
    @Test
    fun `a copy from the declared output of a package depends on the package by build model and probably`() =
        world(copyScript("web/dist"), repository("web", declared)).use { world ->
            val copy = world.copies().single()
            val link = world.links().single()

            assertEquals(NodeRef.OfSymbol(copy.id), link.from)
            assertEquals(RelationKind.DEPENDS_ON, link.kind)
            assertEquals("web-bundle", link.subKind)
            assertEquals(NodeRef.OfSymbol(world.packages().single().id), link.target())
            assertEquals(Certainty.PROBABLE, (link.resolution as Resolution.Resolved).certainty)
            assertEquals(EvidenceClass.BUILD_MODEL, link.evidenceClass)
            assertEquals(copy.declaration, link.site)
            assertEquals(copy.scope, link.scope)
            assertEquals(mapOf("bundle.into" to "static"), link.attributes)
        }

    @Test
    fun `a copy without a destination has a link without the bundle destination`() =
        world(copyScript("web/dist", into = null), repository("web", declared)).use { world ->
            assertEquals(emptyMap(), world.links().single().attributes)
        }

    @Test
    fun `a copy from the default output of a bundler depends on the package by convention and heuristically`() =
        world(copyScript("web/dist"), repository("web", mapOf("package.json" to UNDECLARED_MANIFEST))).use { world ->
            val link = world.links().single()

            assertEquals("false", world.packages().single().attributes["bundle.output-declared"])
            assertEquals(NodeRef.OfSymbol(world.packages().single().id), link.target())
            assertEquals(Certainty.HEURISTIC, (link.resolution as Resolution.Resolved).certainty)
            assertEquals(EvidenceClass.CONVENTION, link.evidenceClass)
        }

    @Test
    fun `a copy from the output directory or from a path below it depends on the package`() = world(
        copyScript("web/dist", "web/dist/assets", "web/dist/assets/img"),
        repository("web", declared),
    ).use { world ->
        val web = NodeRef.OfSymbol(world.packages().single().id)

        assertEquals(
            mapOf("../web/dist" to web, "../web/dist/assets" to web, "../web/dist/assets/img" to web),
            world.targets(),
        )
    }

    @Test
    fun `a copy from beside, above or outside the output directory depends on nothing`() = world(
        copyScript("web", "web/src", "web/distinct", "web/dist2/assets", "web/../elsewhere/dist", "../outside/dist"),
        repository("web", declared),
    ).use { world ->
        assertEquals(6, world.copies().size)
        assertEquals(emptyList(), world.links())
    }

    @Test
    fun `the output directory of a package is a repository path, whichever directory the package is in`() = world(
        copyScript("web/packages/ui/out/assets", "web/packages/ui/dist", "web/out"),
        repository(
            "web",
            mapOf(
                "packages/ui/package.json" to manifest("@tasktide/ui"),
                "packages/ui/vite.config.ts" to viteConfig("out"),
            ),
        ),
    ).use { world ->
        val ui = NodeRef.OfSymbol(world.packages().single().id)

        assertEquals("packages/ui/out", world.packages().single().attributes["bundle.output"])
        assertEquals(mapOf("../web/packages/ui/out/assets" to ui), world.targets())
    }

    @Test
    fun `only the package of the repository that holds the path is a producer`() = world(
        copyScript("web/dist", "other/dist"),
        repository("web", declared),
        repository("other", mapOf("package.json" to manifest("@tasktide/other"))),
    ).use { world ->
        val packages = world.packages().associate { it.name to NodeRef.OfSymbol(it.id) }

        assertEquals(
            mapOf(
                "../web/dist" to packages.getValue("@tasktide/web"),
                "../other/dist" to packages.getValue("@tasktide/other"),
            ),
            world.targets(),
        )
    }
}
