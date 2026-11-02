package io.github.krosstack.kscope.cli.serve

import io.github.krosstack.kscope.cli.command.ScanRunner
import io.github.krosstack.kscope.cli.serve.watch.WatchSettings
import io.github.krosstack.kscope.service.KrosScope
import io.github.krosstack.kscope.service.OpenOptions
import io.github.krosstack.kscope.service.ScanLevel
import io.github.krosstack.kscope.service.ScanResult
import io.github.krosstack.kscope.service.indexLevel
import io.github.krosstack.kscope.service.indexRevision
import io.github.krosstack.kscope.service.level.automaticScanLevel
import io.github.krosstack.kscope.service.refreshIndex
import java.nio.file.Path

internal class ServeServices(
    private val workspace: Path,
    private val scanRunner: ScanRunner,
    private val onLoadChange: (Boolean) -> Unit = {},
    private val beforeScan: () -> Unit = {},
    private val afterScan: () -> Unit = {},
) : AutoCloseable {
    private var service = KrosScope.open(workspace)

    @Volatile
    var loaded = false
        private set

    @Volatile
    var indexed = false
        private set

    @Volatile
    var indexLevel: String? = null
        private set

    @Volatile
    var lastScan: ScanRecord? = null
        private set

    var completedScans = 0L
        private set
    var attemptedScans = 0L
        private set
    var queries = 0L
        private set
    private var generation = 0L

    fun indexStamp(): IndexStamp {
        service.refreshIndex()
        return IndexStamp(generation, service.indexRevision(), service.isIndexed())
    }

    fun warm() {
        service.refreshIndex()
        indexed = service.isIndexed()
        indexLevel = service.indexLevel()?.wireName
        if (indexed) service.graphView()
        loaded = true
    }

    fun release() {
        if (!loaded) return
        try {
            service.close()
        } finally {
            service = KrosScope.open(workspace)
            generation++
            loaded = false
        }
        onLoadChange(false)
    }

    fun refresh() = service.refreshIndex()

    fun <T> use(work: (KrosScope) -> T): T {
        queries++
        if (!loaded) {
            warm()
            onLoadChange(true)
        }
        return work(service)
    }

    fun requiresTrust(settings: WatchSettings): Boolean = settings.requiresTrust(service)

    // An explicit exact level is the user's own request and runs the build whatever the trust is, and the scan may
    // wait for its turn long enough for the trust to be revoked. Without a level it reads the trust when it starts.
    fun automaticLevelArguments(): List<String> {
        val level = service.automaticScanLevel()
        return if (level == ScanLevel.EXACT) emptyList() else listOf("--level", level.wireName)
    }

    fun isShallow(settings: WatchSettings): Boolean = settings.isShallow(service)

    fun scan(options: OpenOptions, rebuild: Boolean, verify: Boolean): ScanResult {
        val started = System.nanoTime()
        val outcome = runCatching {
            service.close()
            beforeScan()
            attemptedScans++
            scanRunner.run(workspace, options, rebuild, verify).also { completedScans++ }
        }
        lastScan = ScanRecord.since(started, outcome.exceptionOrNull())
        try {
            reopen()
        } finally {
            afterScan()
        }
        return outcome.getOrThrow()
    }

    private fun reopen() {
        service = KrosScope.open(workspace)
        generation++
        indexed = service.isIndexed()
        indexLevel = service.indexLevel()?.wireName
        if (loaded) warm()
    }

    override fun close() = service.close()
}
