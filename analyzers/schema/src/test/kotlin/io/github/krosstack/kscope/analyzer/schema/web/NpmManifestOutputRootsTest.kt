package io.github.krosstack.kscope.analyzer.schema.web

import io.github.krosstack.kscope.analyzer.schema.web.output.viteConfig
import io.github.krosstack.kscope.analyzer.schema.web.output.withConfig
import io.github.krosstack.kscope.model.RepoPath
import io.github.krosstack.kscope.spi.OutputRoot
import kotlin.test.Test
import kotlin.test.assertEquals

private fun rootsOf(files: Map<String, String>, outputDirectories: List<String> = emptyList()) =
    WebWorkspace(files, outputDirectories).outputRoots()

class NpmManifestOutputRootsTest {
    @Test
    fun `a package names the directory its bundler writes to, with the bundler as generator`() {
        assertEquals(listOf(OutputRoot(RepoPath("dist"), "vite")), rootsOf(tasktideWeb))
    }

    @Test
    fun `the label of the root names the bundler`() {
        assertEquals("output:vite", rootsOf(tasktideWeb).single().label)
    }

    @Test
    fun `the root of a package below the repository root is below that package`() {
        val files = withConfig("vite.config.ts", viteConfig("""build: { outDir: "out" }"""), "web") +
            mapOf("web/package.json" to """{"name": "web"}""")

        assertEquals(listOf(OutputRoot(RepoPath("web/out"), "vite")), rootsOf(files))
    }

    @Test
    fun `a package that names no bundler, computes its output or writes outside the repository names no root`() {
        val computed = withConfig("vite.config.ts", viteConfig("build: { outDir: dir }"))
        val outside = withConfig("vite.config.ts", viteConfig("""build: { outDir: "../../x" }"""), "web")

        assertEquals(emptyList(), rootsOf(mapOf("package.json" to """{"name": "lib"}""")))
        assertEquals(emptyList(), rootsOf(computed))
        assertEquals(emptyList(), rootsOf(outside))
    }

    @Test
    fun `a manifest that is generated, unreadable as JSON or installed as a dependency names no root`() {
        val copied = mapOf("dist/package.json" to """{"name": "copy", "devDependencies": {"vite": "5"}}""")
        val broken = mapOf("package.json" to "{ not json")
        val dependency = """{"name": "lib", "devDependencies": {"vite": "5"}}"""
        val installed = mapOf("node_modules/lib/package.json" to dependency)

        assertEquals(emptyList(), rootsOf(copied, outputDirectories = listOf("dist")))
        assertEquals(emptyList(), rootsOf(broken))
        assertEquals(emptyList(), rootsOf(installed))
    }

    @Test
    fun `two packages name their own roots, and a package in the root next to another is not mixed up`() {
        val files = mapOf(
            "package.json" to """{"name": "root", "devDependencies": {"webpack": "5"}}""",
            "web/package.json" to """{"name": "web", "scripts": {"build": "vite build"}}""",
        )

        assertEquals(
            listOf(OutputRoot(RepoPath("dist"), "webpack"), OutputRoot(RepoPath("web/dist"), "vite")),
            rootsOf(files),
        )
    }
}
