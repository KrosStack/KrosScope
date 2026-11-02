package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.service.home.ServiceLimits
import io.github.krosstack.kscope.cli.service.home.WorkspacePaths
import java.nio.file.Path
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class ServiceHost(requested: ServiceSettings) {
    private val settings = requested.copy(
        serve = requested.serve.copy(watchEnabled = true, hosted = true, idleTimeoutMillis = 0),
        scanRunner = SingleScanRunner(requested.scanRunner),
    )
    private val lifecycle = Any()

    @Volatile
    var entries: List<HostedWorkspace> = emptyList()
        private set

    @Volatile
    var limits = ServiceLimits()
        private set
    private val policy = ReleasePolicy(settings.heap, settings.serve.clock)
    private val looks = ServiceLooks(settings.lookMillis, ::look)
    private val wakeups = Semaphore(0)
    private val stopping = AtomicBoolean()
    private val opener = Thread(::openWhenDue, "kscope-service-open").apply { isDaemon = true }

    fun start(workspaces: List<Path>, kept: ServiceLimits) {
        synchronized(lifecycle) {
            limits = kept
            entries = canonical(workspaces).map(::create)
            opener.start()
            looks.start()
        }
    }

    fun reload(workspaces: List<Path>, kept: ServiceLimits) {
        synchronized(lifecycle) {
            if (stopping.get()) return
            limits = kept
            val before = entries
            val after = canonical(workspaces).map { path -> before.firstOrNull { it.path == path } ?: create(path) }
            entries = after
            before.filterNot { it in after }.forEach(HostedWorkspace::stop)
            after.forEach(HostedWorkspace::retryAtOnce)
            retryNow()
        }
    }

    fun stop() {
        synchronized(lifecycle) {
            stopping.set(true)
            retryNow()
            val hosted = entries
            entries = emptyList()
            hosted.forEach(HostedWorkspace::stop)
        }
        opener.join()
        looks.close()
    }

    fun lines(): List<String> {
        val workspaces = entries.map { "${it.path}: ${it.state.text}" }.ifEmpty { listOf(NO_WORKSPACE) }
        return listOf(limits.text) + workspaces
    }

    fun look() = policy.look(limits, entries.mapNotNull(HostedWorkspace::candidate))

    fun retryNow() = wakeups.release()

    private fun canonical(workspaces: List<Path>): List<Path> = workspaces.map(WorkspacePaths::real).distinct()

    private fun create(path: Path): HostedWorkspace =
        HostedWorkspace(path, settings, ::retryNow).also { settings.onChange(it.path, it.state) }

    private fun openWhenDue() {
        while (!stopping.get()) {
            entries.filter { it.due(settings.clock()) }.forEach(HostedWorkspace::open)
            wakeups.tryAcquire(nextWaitMillis(), TimeUnit.MILLISECONDS)
            wakeups.drainPermits()
        }
    }

    private fun nextWaitMillis(): Long {
        val now = settings.clock()
        val soonest = entries.mapNotNull { it.waitMillis(now) }.minOrNull() ?: settings.retryMillis
        return soonest.coerceIn(1, settings.retryMillis)
    }

    companion object {
        const val NO_WORKSPACE = "It keeps no workspace. Add one with `kscope service add`."
    }
}
