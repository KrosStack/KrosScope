package io.github.krosstack.kscope.cli.command

import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanResult
import io.github.krosstack.kscope.service.semantic.semanticNotice
import java.nio.file.Path

internal fun interface ScanRunner {
    fun run(workspace: Path, options: OpenOptions, rebuild: Boolean, verify: Boolean): ScanResult

    fun open(workspace: Path, options: OpenOptions): KrosScope = KrosScope.open(workspace, options)

    companion object {
        val DEFAULT = ScanRunner { workspace, options, rebuild, verify ->
            KrosScope.open(workspace, options).use { scope ->
                scope.scan(rebuild, verify).also { scope.semanticNotice()?.let(System.err::println) }
            }
        }
    }
}
