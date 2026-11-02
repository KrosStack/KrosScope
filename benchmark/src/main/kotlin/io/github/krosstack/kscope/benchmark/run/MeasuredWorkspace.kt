package io.github.krosstack.kscope.benchmark.run

import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.settings.WorkspaceSettings
import java.nio.file.Path

/**
 * The options to open a workspace that a benchmark measures. Its settings are fixed, so that a `kscope.toml` inside a
 * measured repository can neither change a run nor stop it.
 */
internal fun measuredOpenOptions(
    indexDirectory: Path,
    trustBuild: Boolean = false,
    level: ScanLevel? = null,
): OpenOptions = OpenOptions(
    trustBuild = trustBuild,
    level = level,
    indexDirectory = indexDirectory,
    settings = WorkspaceSettings.DEFAULTS,
)
