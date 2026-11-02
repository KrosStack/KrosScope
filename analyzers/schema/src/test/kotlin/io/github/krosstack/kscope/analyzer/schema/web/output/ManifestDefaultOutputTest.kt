package io.github.krosstack.kscope.analyzer.schema.web.output

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManifestDefaultOutputTest {
    private fun manifest(extra: String) = outputOf(mapOf("package.json" to """{"name": "app", $extra}"""))

    @Test
    fun `a package without a config whose dependencies name Vite or webpack gets the default directory`() {
        assertEquals("vite dist default", manifest(""""devDependencies": {"vite": "5.4.10"}"""))
        assertEquals("vite dist default", manifest(""""dependencies": {"vite": "5.4.10"}"""))
        assertEquals("webpack dist default", manifest(""""devDependencies": {"webpack": "5.0.0"}"""))
    }

    @Test
    fun `a script that runs Vite or webpack names it as well`() {
        assertEquals("vite dist default", manifest(""""scripts": {"build": "vite build"}"""))
        assertEquals("vite dist default", manifest(""""scripts": {"build": "tsc && npx vite build --mode x"}"""))
        assertEquals("webpack dist default", manifest(""""scripts": {"build": "webpack --mode production"}"""))
        assertEquals("webpack dist default", manifest(""""scripts": {"build": "cross-env A=1 webpack"}"""))
    }

    @Test
    fun `other tools are not bundlers with a default directory`() {
        assertNull(manifest(""""scripts": {"test": "vitest run", "build": "tsc"}"""))
        assertNull(manifest(""""devDependencies": {"vitest": "1.0.0", "@vitejs/plugin-react": "4.0.0"}"""))
        assertNull(manifest(""""devDependencies": {"rollup": "4.0.0"}"""))
        assertNull(manifest(""""scripts": {"dev": "webpack-dev-server"}"""))
        assertNull(manifest(""""version": "1.0.0""""))
    }

    @Test
    fun `Vite is preferred when a manifest names both`() {
        assertEquals("vite dist default", manifest(""""devDependencies": {"webpack": "5", "vite": "5"}"""))
    }

    @Test
    fun `a config decides even when the manifest names another bundler`() {
        val files = withConfig("rollup.config.js", rollupConfig("""output: { dir: "lib" }""")) +
            mapOf("package.json" to """{"name": "app", "devDependencies": {"vite": "5"}}""")

        assertEquals("rollup lib declared", outputOf(files))
    }

    @Test
    fun `a config that does not tell leaves no output, whatever the manifest names`() {
        val files = withConfig("rollup.config.js", rollupConfig("")) +
            mapOf("package.json" to """{"name": "app", "devDependencies": {"vite": "5"}}""")

        assertNull(outputOf(files))
    }

    @Test
    fun `with several configs the Vite one is read before the webpack one before the Rollup one`() {
        val files = mapOf(
            "package.json" to PLAIN_MANIFEST,
            "rollup.config.js" to rollupConfig("""output: { dir: "r" }"""),
            "webpack.config.js" to webpackConfig("""output: { path: "w" }"""),
        )

        assertEquals("webpack w declared", outputOf(files))
        val withVite = files + mapOf("vite.config.ts" to viteConfig("""build: { outDir: "v" }"""))

        assertEquals("vite v declared", outputOf(withVite))
    }
}
