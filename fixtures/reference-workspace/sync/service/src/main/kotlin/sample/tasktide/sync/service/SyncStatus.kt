package sample.tasktide.sync.service

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SyncStatus(
    val state: Int,
    val lastSyncAt: Long,
    val conflicts: Int,
) : Parcelable {
    companion object {
        const val STATE_IDLE = 0
        const val STATE_RUNNING = 1
        const val STATE_FAILED = 2
    }
}
