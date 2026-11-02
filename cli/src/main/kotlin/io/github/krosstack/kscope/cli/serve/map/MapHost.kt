package io.github.krosstack.kscope.cli.serve.map

import io.github.krosstack.kscope.cli.serve.IndexStamp
import io.github.krosstack.kscope.cli.serve.ServeServices
import io.github.krosstack.kscope.cli.serve.watch.WatchState
import io.github.krosstack.kscope.service.map.MapJson
import io.github.krosstack.kscope.service.map.WorkspaceMap
import io.github.krosstack.kscope.service.map.workspaceMap
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * What the serve thread keeps for the map. The threads of the map read [current] and the [states] and ask the
 * serve thread for everything else through [run]. The serve thread calls [tick] between requests.
 */
internal class MapHost(workspace: Path, private val watch: WatchState?, private val clock: () -> Long) :
    AutoCloseable {
    private val workspaceName = workspace.fileName?.toString() ?: "workspace"
    private val work = MapWork()
    private var version = 0
    private var nextCheckAt = 0L
    private var failedAt: IndexStamp? = null
    val states = MapStates()

    @Volatile
    var current: MapSnapshot? = null
        private set

    @Volatile
    var scanning = false
        private set

    fun tick(services: ServeServices) {
        work.drain(services)
        if (states.viewers == 0) return
        if (clock() >= nextCheckAt) {
            nextCheckAt = clock() + TimeUnit.MILLISECONDS.toNanos(CHECK_MILLIS)
            refreshQuietly(services)
        }
        publish(services)
    }

    /** The map of the index as it is now; builds it when the index is not the one that [current] was built from. */
    fun refresh(services: ServeServices): MapSnapshot {
        val stamp = services.indexStamp()
        current?.takeIf { it.stamp == stamp }?.let { return it }
        val map = if (stamp.indexed) services.use { it.workspaceMap() } else WorkspaceMap.empty(workspaceName)
        val built = MapSnapshot(++version, map, MapJson.encode(map.overview).toByteArray(Charsets.UTF_8), stamp)
        current = built
        publish(services)
        return built
    }

    fun snapshot(timeoutMillis: Long): MapOutcome<MapSnapshot> {
        val known = current
        if (scanning && known != null) return MapOutcome.Done(known)
        return run(timeoutMillis, ::refresh)
    }

    fun <T> run(timeoutMillis: Long, job: (ServeServices) -> T): MapOutcome<T> = work.run(timeoutMillis, job)

    fun scanStarted() {
        scanning = true
        states.update { it.copy(scanning = true) }
    }

    fun scanFinished() {
        scanning = false
        states.update { it.copy(scanning = false) }
    }

    override fun close() {
        states.close()
        work.close()
    }

    private fun refreshQuietly(services: ServeServices) {
        val stamp = runCatching { services.indexStamp() }.getOrNull() ?: return
        if (stamp == failedAt) return
        failedAt = if (runCatching { refresh(services) }.isSuccess) null else stamp
    }

    private fun publish(services: ServeServices) {
        val known = current
        val next = MapState(
            version = known?.version ?: 0,
            indexed = known?.stamp?.indexed ?: services.indexed,
            level = known?.map?.overview?.level ?: services.indexLevel,
            scanning = scanning,
            watching = watch != null,
            watch = watch?.status(),
        )
        states.update { next }
    }

    private companion object {
        const val CHECK_MILLIS = 1_000L
    }
}
