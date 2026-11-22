package io.github.krosstack.kscope.cli.freshness

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanResult
import java.nio.file.Path

internal object PatientScans : ScanRunner {
    private const val LIMIT_MILLIS = 60_000L

    override fun run(workspace: Path, options: OpenOptions, rebuild: Boolean, verify: Boolean): ScanResult =
        ScanRunner.DEFAULT.run(workspace, options, rebuild, verify)

    override fun open(workspace: Path, options: OpenOptions): KrosScope =
        KrosScope.open(workspace, options.copy(freshnessLimitMillis = LIMIT_MILLIS))
}
