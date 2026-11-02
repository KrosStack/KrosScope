package io.github.krosstack.kscope.analyzer.schema.web.output

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun viteExport(expression: String) = "export default $expression;\n"

class ViteRootTest {
    private fun vite(body: String) = outputOf(withConfig("vite.config.ts", viteConfig(body)))

    @Test
    fun `the output lies below the root of the config, which is the package directory when it says nothing`() {
        assertEquals("vite frontend/dist declared", vite("""root: "frontend", build: { outDir: "dist" }"""))
        assertEquals("vite frontend/dist default", vite("""root: "frontend""""))
        assertEquals("vite frontend/dist default", vite("""root: './frontend/', build: {}"""))
        assertEquals("vite out declared", vite("""root: "frontend", build: { outDir: "../out" }"""))
        assertEquals("vite dist declared", vite("""root: ".", build: { outDir: "dist" }"""))
        assertEquals("vite frontend/dist declared", vite("""build: { outDir: "dist" }, root: "frontend""""))
    }

    @Test
    fun `an output anchored at the directory of the config does not move below the root`() {
        val resolved = """root: "frontend", build: { outDir: path.resolve(__dirname, "dist") }"""
        val joined = """root: "frontend", build: { outDir: path.join(__dirname, "out", "web") }"""
        val computedRoot = """root: process.cwd(), build: { outDir: path.resolve(__dirname, "dist") }"""

        assertEquals("vite dist declared", vite(resolved))
        assertEquals("vite out/web declared", vite(joined))
        assertEquals("vite dist declared", vite(computedRoot))
        assertEquals("vite frontend/dist declared", vite("""root: "frontend", build: { outDir: "dist" }"""))
    }

    @Test
    fun `the root of the config is read from the package directory`() {
        val files = withConfig("vite.config.ts", viteConfig("""root: "frontend""""), directory = "web")

        assertEquals("vite web/frontend/dist default", outputOf(files, "web/package.json"))
    }

    @Test
    fun `a root that is computed or leaves the package has no output`() {
        assertNull(vite("root: process.cwd(), build: { outDir: \"dist\" }"))
        assertNull(vite("""root: "frontend" + suffix"""))
        assertNull(vite("""root: "/abs/frontend""""))
        assertNull(vite("""root: "../..""""))
    }

    @Test
    fun `only the root of the config object counts, not one of a nested object`() {
        assertEquals("vite dist default", vite("test: { root: 'x' }"))
        assertEquals("vite dist declared", vite("""test: { root: "x" }, build: { outDir: "dist" }"""))
        assertEquals("vite frontend/dist default", vite("""test: { root: "x" }, root: "frontend""""))
        assertEquals("vite dist default", vite("""server: { fs: { root: "x" } }"""))
    }

    @Test
    fun `the root is found in a config that is a function or a plain export`() {
        val function = viteExport("""defineConfig(({ mode }) => ({ root: "frontend", test: { root: "x" } }))""")
        val plain = viteExport("""{ root: "frontend" }""")
        val returned = viteExport("defineConfig(() => {\n  return { root: \"frontend\" };\n})")

        assertEquals("vite frontend/dist default", outputOf(withConfig("vite.config.ts", function)))
        assertEquals("vite frontend/dist default", outputOf(withConfig("vite.config.ts", plain)))
        assertEquals("vite frontend/dist default", outputOf(withConfig("vite.config.ts", returned)))
    }
}
