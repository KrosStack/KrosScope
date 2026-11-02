package sample.tasktide.sync.engine

import sample.tasktide.core.model.SyncState
import sample.tasktide.data.db.TaskRepository
import sample.tasktide.sync.proto.SyncRequest
import java.io.IOException

class SyncEngine(
    private val repository: TaskRepository,
    private val transport: SyncTransport,
    private val deviceId: String,
) {
    suspend fun sync(): SyncResult {
        val pending = repository.pending()
        val request = SyncRequest(
            device_id = deviceId,
            since = repository.lastSyncedAt(),
            tasks = pending.map(TaskDtoMapper::toDto),
        )
        val response = try {
            transport.exchange(request)
        } catch (error: IOException) {
            return SyncResult.Failed(error.message ?: "transport failure")
        }

        val incoming = response.tasks.map(TaskDtoMapper::fromDto)
        val pendingIds = pending.map { it.id }.toSet()
        val conflicts = pending.filter { local -> incoming.any { it.id == local.id && it.updatedAt > local.updatedAt } }
        val syncedAt = response.server_time

        incoming.filter { it.id !in pendingIds }
            .forEach { repository.upsert(it.copy(syncState = SyncState.Synced(syncedAt))) }
        repository.markSynced((pendingIds - conflicts.map { it.id }.toSet()).toList(), syncedAt)
        conflicts.forEach { repository.upsert(it.copy(syncState = SyncState.Conflict)) }

        return SyncResult.Success(
            pushed = pending.size - conflicts.size,
            pulled = incoming.size,
            conflicts = conflicts.size,
        )
    }
}
