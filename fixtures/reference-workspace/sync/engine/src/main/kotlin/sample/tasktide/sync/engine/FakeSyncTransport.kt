package sample.tasktide.sync.engine

import sample.tasktide.core.model.currentTimeMillis
import sample.tasktide.sync.proto.SyncRequest
import sample.tasktide.sync.proto.SyncResponse
import sample.tasktide.sync.proto.TaskDto
import java.io.IOException

class FakeSyncTransport(
    seed: List<TaskDto> = emptyList(),
    private val clock: () -> Long = { currentTimeMillis() },
) : SyncTransport {
    private val remote = seed.associateBy { it.id }.toMutableMap()

    var failNext: Boolean = false

    override suspend fun exchange(request: SyncRequest): SyncResponse {
        if (failNext) {
            failNext = false
            throw IOException("simulated transport failure")
        }
        val newer = remote.values.filter { it.updated_at > request.since }
        request.tasks
            .filter { incoming -> (remote[incoming.id]?.updated_at ?: Long.MIN_VALUE) <= incoming.updated_at }
            .forEach { remote[it.id] = it }
        return SyncResponse(tasks = newer, server_time = clock())
    }

    fun stored(id: String): TaskDto? = remote[id]
}
