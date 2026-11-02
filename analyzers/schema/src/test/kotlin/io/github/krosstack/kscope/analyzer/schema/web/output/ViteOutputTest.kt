package io.github.krosstack.kscope.analyzer.schema.web.output

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ViteOutputTest {
    private fun vite(body: String) = outputOf(withConfig("vite.config.ts", viteConfig(body)))

    @Test
    fun `a string literal outDir is the declared output`() {
        assertEquals("vite build/web declared", vite("""build: { outDir: "build/web" }"""))
        assertEquals("vite build/web declared", vite("build: { outDir: 'build/web' }"))
        assertEquals("vite build/web declared", vite("build: { outDir: `build/web` }"))
        assertEquals("vite dist declared", vite("""build: { outDir: "./dist/" }"""))
        assertEquals("vite dist declared", vite("""build: { "outDir": "dist" }"""))
    }

    @Test
    fun `a config without the option leaves the default, which is not declared`() {
        assertEquals("vite dist default", vite(""))
        assertEquals("vite dist default", vite("plugins: []"))
        assertEquals("vite dist default", vite("build: { sourcemap: true }"))
        assertEquals("vite dist default", vite("build: {}"))
    }

    @Test
    fun `a computed option is not read`() {
        assertNull(vite("build: { outDir: process.env.OUT_DIR }"))
        assertNull(vite("""build: { outDir: "dist" + suffix }"""))
        assertNull(vite("build: { outDir: `dist-${'$'}{mode}` }"))
        assertNull(vite("build: { outDir }"))
        assertNull(vite("build: buildOptions"))
        assertNull(vite("build: { outDir: dir, sourcemap: true }"))
    }

    @Test
    fun `comments and strings are not code`() {
        assertEquals("vite dist default", vite("""// build: { outDir: "commented" }"""))
        assertEquals("vite dist default", vite("""/* build: { outDir: "commented" } */"""))
        assertEquals("vite real declared", vite("""/* outDir: "no" */ build: { /* outDir: "no" */ outDir: "real" }"""))
        assertEquals("vite dist default", vite("""define: { text: 'build: { outDir: "in a string" }' }"""))
        assertEquals("vite dist declared", vite("build: { // outDir: \"no\"\n outDir: \"dist\" }"))
    }

    @Test
    fun `only the option of the build itself counts, not one of a nested object`() {
        val nested = """rollupOptions: { output: { dir: "no", outDir: "no" } }"""

        assertEquals("vite real declared", vite("""build: { $nested, outDir: "real" }"""))
        assertEquals("vite dist default", vite("build: { $nested, minify: true }"))
    }

    @Test
    fun `a shorthand property of the config object is computed, not absent`() {
        val declared = "const build = { outDir: \"public\" };\nexport default defineConfig({ build });\n"
        val config = "import { defineConfig } from \"vite\";\n$declared"

        assertNull(outputOf(withConfig("vite.config.ts", config)))
        assertNull(vite("build, plugins: []"))
        assertEquals("vite dist default", vite("plugins"))
    }

    @Test
    fun `a spread that may set the build or the root makes the output computed`() {
        assertNull(vite("...base"))
        assertNull(vite("""build: { outDir: "dist" }, ...base"""))
        assertNull(vite("build: { ...shared }"))
        assertNull(vite("""build: { outDir: "out", ...shared }"""))
        assertEquals("vite out declared", vite("""...base, root: ".", build: { outDir: "out" }"""))
        assertEquals("vite out declared", vite("""build: { ...shared, outDir: "out" }"""))
    }

    @Test
    fun `an option inside a function of the mode is still found`() {
        val config = "export default defineConfig(({ mode }) => ({ build: { outDir: \"out\" } }));\n"

        assertEquals("vite out declared", outputOf(withConfig("vite.config.ts", config)))
    }

    @Test
    fun `the config beside the manifest decides, and a config elsewhere does not`() {
        val beside = withConfig("vite.config.ts", viteConfig("""build: { outDir: "dist" }"""), directory = "web")
        val elsewhere = withConfig("vite.config.ts", viteConfig("""build: { outDir: "x" }"""), directory = "other")

        assertEquals("vite web/dist declared", outputOf(beside, "web/package.json"))
        assertNull(outputOf(elsewhere + mapOf("package.json" to PLAIN_MANIFEST)))
    }
}
