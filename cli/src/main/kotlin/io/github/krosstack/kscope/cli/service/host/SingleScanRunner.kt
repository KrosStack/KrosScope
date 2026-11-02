package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanResult
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class SingleScanRunner(private val delegate: ScanRunner) : ScanRunner {
    private val turn = ReentrantLock(true)

    override fun run(workspace: Path, options: OpenOptions, rebuild: Boolean, verify: Boolean): ScanResult =
        turn.withLock { delegate.run(workspace, options, rebuild, verify) }
}
