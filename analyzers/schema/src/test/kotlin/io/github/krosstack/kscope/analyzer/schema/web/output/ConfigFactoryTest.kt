package io.github.krosstack.kscope.analyzer.schema.web.output

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfigFactoryTest {
    private fun vite(text: String) = outputOf(withConfig("vite.config.ts", text))

    private fun webpack(text: String) = outputOf(withConfig("webpack.config.js", text))

    private fun rollup(text: String) = outputOf(withConfig("rollup.config.mjs", text))

    @Test
    fun `a local object before the returned config is not the config`() {
        val config = "export default defineConfig(() => { const options = {}; " +
            "return { build: { outDir: 'public' } }; });\n"

        assertEquals("vite public declared", vite(config))
    }

    @Test
    fun `a local object is ignored for webpack and rollup too`() {
        val webpackConfig = "module.exports = (env) => { const output = { path: 'wrong' };\n" +
            "  return { output: { path: 'build' } };\n};\n"
        val rollupConfig = "export default () => { const output = { dir: 'wrong' };\n" +
            "  return { output: { dir: 'lib' } };\n};\n"
        val named = "export default function config() { const output = { dir: 'wrong' };\n" +
            "  return { output: { dir: 'lib' } }; }\n"

        assertEquals("webpack build declared", webpack(webpackConfig))
        assertEquals("rollup lib declared", rollup(rollupConfig))
        assertEquals("rollup lib declared", rollup(named))
    }

    @Test
    fun `a factory with several returns has no config object that can be told`() {
        val branching = "export default defineConfig(({ mode }) => {\n" +
            "  if (mode === 'a') { return { build: { outDir: 'a' } }; }\n" +
            "  return { build: { outDir: 'b' } };\n});\n"
        val local = "export default defineConfig(() => { const options = { build: { outDir: 'x' } };\n" +
            "  if (flag) return {}; return options; });\n"

        assertNull(vite(branching))
        assertNull(vite(local))
    }

    @Test
    fun `a factory that returns no object literal has no output`() {
        val returnedLocal = "export default defineConfig(() => {\n" +
            "  const base = { build: { outDir: 'x' } };\n  return base;\n});\n"
        val noReturn = "export default defineConfig(() => { const build = { outDir: 'x' }; });\n"
        val named = "const base = { build: { outDir: 'x' } };\nexport default base;\n"
        val wrapped = "export default defineConfig(() => base({ build: { outDir: 'x' } }));\n"

        listOf(returnedLocal, noReturn, named, wrapped).forEach { assertNull(vite(it)) }
    }

    @Test
    fun `a return inside an inner function is not a return of the factory`() {
        val config = "export default defineConfig(() => {\n" +
            "  const pick = () => { return { build: { outDir: 'wrong' } }; };\n" +
            "  const names = [1].map((n) => { return n; });\n" +
            "  return { build: { outDir: 'out' }, names, pick };\n});\n"

        assertEquals("vite out declared", vite(config))
    }

    @Test
    fun `an arrow with an expression body yields its object`() {
        val asynchronous = "export default defineConfig(async ({ mode }) => ({ build: { outDir: 'out' } }));\n"

        assertEquals("vite out declared", vite("export default () => ({ build: { outDir: 'out' } });\n"))
        assertEquals("vite out declared", vite(asynchronous))
        assertEquals("webpack build declared", webpack("module.exports = env => ({ output: { path: 'build' } });\n"))
    }

    @Test
    fun `a return of the factory in a block of control flow is counted`() {
        val config = "export default defineConfig(({ mode }) => {\n" +
            "  switch (mode) { case 'a': return { build: { outDir: 'a' } }; default: break; }\n" +
            "  return { build: { outDir: 'b' } };\n});\n"

        assertNull(vite(config))
    }
}
