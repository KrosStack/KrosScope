package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanResult
import java.nio.file.Path

internal fun interface ScanRunner {
    fun run(workspace: Path, options: OpenOptions, rebuild: Boolean, verify: Boolean): ScanResult

    companion object {
        val DEFAULT = ScanRunner { workspace, options, rebuild, verify ->
            KrosScope.open(workspace, options).use { it.scan(rebuild, verify) }
        }
    }
}
