package sample.tasktide.core.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface SyncState {
    @Serializable
    data object Pending : SyncState

    @Serializable
    data class Synced(val at: Long) : SyncState

    @Serializable
    data object Conflict : SyncState
}
