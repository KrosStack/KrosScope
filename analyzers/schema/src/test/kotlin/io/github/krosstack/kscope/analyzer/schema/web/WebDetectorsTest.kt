package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.analyzer.schema.SchemaCapabilities
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.CapabilityRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class WebDetectorsTest {
    private val detectors = CapabilityRegistry(
        detectors = WebBundleCapabilities.detectors() + SchemaCapabilities.detectors(),
    ).detectors

    private fun typeOf(path: String, head: String = ""): String {
        val bytes = head.toByteArray()
        val candidate = ArtifactCandidate(RepoPath(path), bytes.size.toLong()) { bytes.copyOf(minOf(bytes.size, 4096)) }
        return detectors.firstNotNullOfOrNull { it.detect(candidate) }?.id ?: "unknown"
    }

    @Test
    fun `the capabilities name five detectors, two producers and one linker`() {
        assertEquals(
            listOf("bundler.config", "npm.manifest", "typescript.source", "javascript.source", "source-map"),
            WebBundleCapabilities.detectors().map { it.id },
        )
        assertEquals(
            listOf("kscope.web.copy-sources", "kscope.web.bundles"),
            WebBundleCapabilities.producers().map { it.descriptor.producer.id },
        )
        assertEquals(
            listOf("kscope.web.bundle-linker"),
            WebBundleCapabilities.linkers().map { it.descriptor.producer.id },
        )
    }

    @Test
    fun `the config files of Vite, webpack and Rollup are bundler configs, whatever their script extension`() {
        listOf("vite", "webpack", "rollup").forEach { bundler ->
            listOf("js", "mjs", "cjs", "ts", "mts", "cts").forEach { extension ->
                assertEquals("bundler.config", typeOf("web/$bundler.config.$extension"), "$bundler $extension")
            }
        }
        assertEquals("bundler.config", typeOf("Vite.Config.TS"))
    }

    @Test
    fun `a config that is no bundler config stays a plain script or nothing`() {
        assertEquals("typescript.source", typeOf("vitest.config.ts"))
        assertEquals("javascript.source", typeOf("webpack.common.js"))
        assertEquals("unknown", typeOf("vite.config.json"))
        assertEquals("unknown", typeOf("vite.config"))
    }

    @Test
    fun `a package json is a manifest unless it was installed as a dependency`() {
        assertEquals("npm.manifest", typeOf("package.json"))
        assertEquals("npm.manifest", typeOf("web/package.json"))
        assertEquals("npm.manifest", typeOf("web/Package.JSON"))
        assertEquals("unknown", typeOf("web/node_modules/left-pad/package.json"))
        assertEquals("unknown", typeOf("node_modules/package.json"))
        assertEquals("npm.manifest", typeOf("web/node_modules_cache/package.json"))
        assertEquals("unknown", typeOf("web/package-lock.json"))
    }

    @Test
    fun `TypeScript and JavaScript sources are told by their extensions, and declaration files are none`() {
        listOf("ts", "tsx", "mts", "cts").forEach { assertEquals("typescript.source", typeOf("src/a.$it"), it) }
        listOf("js", "jsx", "mjs", "cjs").forEach { assertEquals("javascript.source", typeOf("src/a.$it"), it) }
        listOf("d.ts", "d.mts", "d.cts").forEach { assertEquals("unknown", typeOf("types/a.$it"), it) }
        assertEquals("unknown", typeOf("src/a.tsm"))
        assertEquals("unknown", typeOf("src/ts"))
    }

    @Test
    fun `a map file is a source map when its head is a JSON object with a version`() {
        assertEquals("source-map", typeOf("dist/a.js.map", """{"version":3,"file":"a.js","sources":[]}"""))
        assertEquals("source-map", typeOf("dist/a.js.map", """{"sources":["x"],"version":3}"""))
        assertEquals("source-map", typeOf("dist/a.js.map", Char(0xFEFF) + """  {"version": 3}"""))
        assertEquals("source-map", typeOf("dist/a.js.map", """{"version":3,"sourcesContent":["${"x".repeat(5000)}"""))
    }

    @Test
    fun `a map file that is not a source map is no artifact of that type`() {
        assertEquals("unknown", typeOf("assets/world.map", "binary data"))
        assertEquals("unknown", typeOf("assets/world.map", """{"tiles":[1,2,3]}"""))
        assertEquals("unknown", typeOf("assets/world.map", """[{"version":3}]"""))
        assertEquals("javascript.source", typeOf("dist/a.js", """{"version":3}"""))
        assertEquals("unknown", typeOf("assets/empty.map"))
    }

    @Test
    fun `the id order lets a bundler config and a manifest win over the other detectors of the same file`() {
        assertEquals("bundler.config", typeOf("vite.config.ts"))
        assertEquals("npm.manifest", typeOf("package.json", """{"openapi":"3.0.0","name":"x"}"""))
        assertEquals(
            "npm.manifest",
            typeOf("package.json", """{"formatVersion":1,"database":{},"entities":[]}"""),
        )
    }

    @Test
    fun `the schema detectors keep their files`() {
        assertEquals("openapi.document", typeOf("api/openapi.json", """{"openapi":"3.0.0"}"""))
        assertEquals("room.schema", typeOf("schemas/1.json", """{"formatVersion":1,"database":{},"entities":[]}"""))
        assertEquals("unknown", typeOf("schemas/1.json", """{"name":"x"}"""))
    }
}
