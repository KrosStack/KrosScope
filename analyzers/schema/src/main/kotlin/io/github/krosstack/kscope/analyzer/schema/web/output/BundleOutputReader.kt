package io.github.krosstack.kscope.analyzer.schema.web.output

import io.github.krosstack.kscope.analyzer.schema.web.Bundler
import io.github.krosstack.kscope.analyzer.schema.web.BundlerConfigDetector
import io.github.krosstack.kscope.analyzer.schema.web.RepoPaths
import io.github.krosstack.kscope.analyzer.schema.web.WebArtifactTypes
import io.github.krosstack.kscope.analyzer.schema.web.manifest.PackageManifest
import io.github.krosstack.kscope.analyzer.schema.web.settingsTextOf
import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.spi.WorkspaceFiles

private class Reading(val bundler: Bundler, val setting: ConfigSetting, val root: ConfigSetting = ConfigSetting.Absent)

/**
 * Finds the directory that the bundler of a package writes to. The config beside the `package.json` decides: when the
 * package has several, the Vite one before the webpack one before the Rollup one. A package without a config is
 * bundled with the bundler that its manifest names, if that has a default directory. The output of Vite lies below the
 * `root` of its config, else below the package; a path made from `__dirname` lies below the package whatever the root
 * is. The config is read lexically, as [ConfigTokens] does; an option that is computed, a config that cannot be read,
 * and an output that lies outside the repository or holds the package itself have no value. Only hand-written
 * configs count.
 */
internal class BundleOutputReader(artifacts: List<Artifact>, private val files: WorkspaceFiles) {
    private val configs = artifacts.filter { it.type == WebArtifactTypes.bundlerConfig && it.generated == null }
        .groupBy { RepoPaths.directoryOf(it.ref.path.value) }

    fun outputOf(manifest: Artifact, parsed: PackageManifest): BundleOutput? {
        val packageDirectory = RepoPaths.directoryOf(manifest.ref.path.value)
        val config = configBeside(packageDirectory)
        val reading = if (config == null) namedReading(parsed) else readingOf(config)
        return reading?.let { outputFrom(it, packageDirectory) }
    }

    private fun namedReading(parsed: PackageManifest): Reading? =
        parsed.namedBundler?.let { Reading(it, ConfigSetting.Absent) }

    private fun configBeside(directory: String): Artifact? {
        val order = compareBy<Artifact>({ bundlerOf(it)?.ordinal }, { it.ref.path.value })
        return configs[directory].orEmpty().minWithOrNull(order)
    }

    private fun bundlerOf(config: Artifact): Bundler? =
        BundlerConfigDetector.bundlerIdOf(config.ref.path.value)?.let(Bundler::byId)

    private fun readingOf(config: Artifact): Reading? {
        val bundler = bundlerOf(config)
        val text = files.settingsTextOf(config)
        if (bundler == null || text == null) return null
        val tokens = ConfigTokens(text)
        return Reading(bundler, BundlerSettings.outputOf(bundler, tokens), BundlerSettings.rootOf(bundler, tokens))
    }

    private fun outputFrom(reading: Reading, packageDirectory: String): BundleOutput? {
        val declared = reading.setting as? ConfigSetting.Literal
        val directory = if (declared == null) {
            defaultDirectory(reading, packageDirectory)
        } else {
            declaredDirectory(declared, reading, packageDirectory)
        }
        return directory?.takeUnless { holds(it, packageDirectory) }
            ?.let { BundleOutput(reading.bundler, it, isDeclared = declared != null) }
    }

    private fun declaredDirectory(
        declared: ConfigSetting.Literal,
        reading: Reading,
        packageDirectory: String,
    ): String? {
        val base = if (declared.isConfigRelative) packageDirectory else rootDirectory(reading, packageDirectory)
        return base?.let { RepoPaths.resolve(it, declared.text) }
    }

    private fun defaultDirectory(reading: Reading, packageDirectory: String): String? {
        val relative = reading.bundler.defaultDirectory.takeIf { reading.setting == ConfigSetting.Absent }
        return relative?.let { path -> rootDirectory(reading, packageDirectory)?.let { RepoPaths.resolve(it, path) } }
    }

    private fun rootDirectory(reading: Reading, packageDirectory: String): String? = when (val root = reading.root) {
        ConfigSetting.Absent -> packageDirectory
        is ConfigSetting.Literal -> RepoPaths.resolve(packageDirectory, root.text)
        else -> null
    }

    private fun holds(output: String, packageDirectory: String): Boolean =
        output == packageDirectory || RepoPaths.isInside(packageDirectory, output)
}
