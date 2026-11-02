package io.github.krosstack.kscope.analyzer.schema.web.output

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebpackAndRollupOutputTest {
    private fun webpack(body: String) = outputOf(withConfig("webpack.config.js", webpackConfig(body)))

    private fun rollup(body: String) = outputOf(withConfig("rollup.config.mjs", rollupConfig(body)))

    @Test
    fun `webpack reads a string literal path`() {
        assertEquals("webpack build declared", webpack("""output: { path: "build", filename: "app.js" }"""))
        assertEquals("webpack build declared", webpack("output: { filename: 'app.js', path: 'build' }"))
    }

    @Test
    fun `webpack reads a path made from dirname and literals`() {
        assertEquals("webpack out declared", webpack("""output: { path: path.resolve(__dirname, "out") }"""))
        assertEquals("webpack out declared", webpack("""output: { path: path.join(__dirname, 'out') }"""))
        assertEquals("webpack out declared", webpack("""output: { path: resolve(__dirname, "out") }"""))
        assertEquals("webpack dist/web declared", webpack("""output: { path: path.join(__dirname, "dist", "web") }"""))
        assertEquals("webpack dist declared", webpack("""output: { path: path.resolve(__dirname, "./dist") }"""))
    }

    @Test
    fun `webpack falls back to the default and does not read what is computed`() {
        assertEquals("webpack dist default", webpack("entry: \"./src/index.js\""))
        assertEquals("webpack dist default", webpack("""output: { filename: "app.js" }"""))
        assertNull(webpack("output: { path: outputPath }"))
        assertNull(webpack("output: { path: process.cwd() }"))
        assertNull(webpack("""output: { path: path.resolve(process.cwd(), "out") }"""))
        assertNull(webpack("""output: { path: __dirname + "/out" }"""))
        assertNull(webpack("output: outputOptions"))
    }

    @Test
    fun `the first output block of the file decides for webpack`() {
        assertEquals(
            "webpack one declared",
            webpack("""entry: [], output: { path: "one" }, extra: { output: { path: "two" } }"""),
        )
    }

    @Test
    fun `rollup reads dir, and the directory of file`() {
        assertEquals("rollup lib declared", rollup("""output: { dir: "lib", format: "es" }"""))
        assertEquals("rollup lib declared", rollup("""output: { dir: "lib", file: "other/bundle.js" }"""))
        assertEquals("rollup build/js declared", rollup("""output: { file: "build/js/bundle.js" }"""))
        assertEquals("rollup lib declared", rollup("""output: [{ dir: "lib" }, { dir: "other" }]"""))
        assertEquals("rollup build declared", rollup("""output: [{ file: "build/a.js" }, { file: "other/b.js" }]"""))
    }

    @Test
    fun `rollup has no default and does not read what is computed`() {
        assertNull(rollup(""))
        assertNull(rollup("""output: { format: "es" }"""))
        assertNull(rollup("output: { dir: outputDirectory }"))
        assertNull(rollup("output: outputs"))
        assertNull(rollup("""output: { file: "bundle.js" }"""))
    }

    @Test
    fun `an output that holds the package itself, or lies outside the repository, is dropped`() {
        val inside = withConfig("vite.config.ts", viteConfig("""build: { outDir: "../server/static" }"""), "web")
        val above = withConfig("vite.config.ts", viteConfig("""build: { outDir: "../../x" }"""), "web")
        val absolute = withConfig("vite.config.ts", viteConfig("""build: { outDir: "/var/www" }"""), "web")
        val self = withConfig("vite.config.ts", viteConfig("""build: { outDir: "." }"""), "web")
        val parent = withConfig("vite.config.ts", viteConfig("""build: { outDir: ".." }"""), "web")
        val empty = withConfig("vite.config.ts", viteConfig("""build: { outDir: "" }"""), "web")

        assertEquals("vite server/static declared", outputOf(inside, "web/package.json"))
        listOf(above, absolute, self, parent, empty).forEach { assertNull(outputOf(it, "web/package.json")) }
    }

    @Test
    fun `a package in the repository root keeps an output below it`() {
        val below = withConfig("vite.config.ts", viteConfig("""build: { outDir: "dist" }"""))
        val parent = withConfig("vite.config.ts", viteConfig("""build: { outDir: ".." }"""))

        assertEquals("vite dist declared", outputOf(below))
        assertNull(outputOf(parent))
    }
}
