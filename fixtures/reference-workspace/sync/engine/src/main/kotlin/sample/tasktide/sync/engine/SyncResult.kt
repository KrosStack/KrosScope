package sample.tasktide.sync.engine

sealed interface SyncResult {
    data class Success(val pushed: Int, val pulled: Int, val conflicts: Int = 0) : SyncResult

    data class Failed(val reason: String) : SyncResult
}
