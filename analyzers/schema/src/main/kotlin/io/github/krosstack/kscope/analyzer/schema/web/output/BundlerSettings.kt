package io.github.krosstack.kscope.analyzer.schema.web.output

import io.github.krosstack.kscope.analyzer.schema.web.Bundler
import io.github.krosstack.kscope.analyzer.schema.web.RepoPaths

/**
 * Where the config of each bundler says the output goes: Vite `build.outDir`, webpack `output.path`, and Rollup
 * `output.dir` or the directory of `output.file`. A block that is mentioned and is no object literal is computed, and
 * so is an option that is no literal. The options are read from the config object itself: a shorthand property or a
 * spread in it may set them and so makes them computed. A Rollup output may be the first object of an array. Vite
 * resolves its output against its `root`, which is the directory of the package unless the config sets it.
 */
internal object BundlerSettings {
    fun outputOf(bundler: Bundler, config: ConfigTokens): ConfigSetting = when (bundler) {
        Bundler.VITE -> inside(config, "build") { config.within(it, "outDir") }
        Bundler.WEBPACK -> inside(config, "output") { config.within(it, "path") }
        Bundler.ROLLUP -> inside(config, "output") { rollupDirectory(config, it) }
    }

    fun rootOf(bundler: Bundler, config: ConfigTokens): ConfigSetting =
        if (bundler == Bundler.VITE) config.topLevel("root") else ConfigSetting.Absent

    private fun inside(config: ConfigTokens, key: String, read: (ConfigSetting.Block) -> ConfigSetting) =
        when (val block = config.ofConfig(key)) {
            is ConfigSetting.Block -> read(block)
            ConfigSetting.Absent -> ConfigSetting.Absent
            else -> ConfigSetting.Computed
        }

    private fun rollupDirectory(config: ConfigTokens, output: ConfigSetting.Block): ConfigSetting {
        val directory = config.within(output, "dir")
        if (directory != ConfigSetting.Absent) return directory
        val file = config.within(output, "file")
        return if (file is ConfigSetting.Literal) file.copy(text = RepoPaths.directoryOf(file.text)) else file
    }
}
