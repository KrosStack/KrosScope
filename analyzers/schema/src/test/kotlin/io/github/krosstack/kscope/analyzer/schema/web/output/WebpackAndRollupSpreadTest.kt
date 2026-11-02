package io.github.krosstack.kscope.analyzer.schema.web.output

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebpackAndRollupSpreadTest {
    private fun webpack(body: String) = outputOf(withConfig("webpack.config.js", webpackConfig(body)))

    private fun rollup(body: String) = outputOf(withConfig("rollup.config.mjs", rollupConfig(body)))

    @Test
    fun `a shorthand output of the config object is computed, not absent`() {
        val config = "const output = { path: \"build\" };\nmodule.exports = { output };\n"

        assertNull(outputOf(withConfig("webpack.config.js", config)))
        assertNull(rollup("output"))
        assertNull(webpack("output, entry: \"./src/index.js\""))
    }

    @Test
    fun `a spread that may set the output makes it computed`() {
        assertNull(webpack("...base"))
        assertNull(webpack("""output: { path: "build" }, ...base"""))
        assertNull(webpack("output: { ...shared }"))
        assertNull(rollup("...base"))
        assertEquals("webpack build declared", webpack("""...base, output: { path: "build" }"""))
        assertEquals("rollup lib declared", rollup("""...base, output: { dir: "lib" }"""))
    }
}
