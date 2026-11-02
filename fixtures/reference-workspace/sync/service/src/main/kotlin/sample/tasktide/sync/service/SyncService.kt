package sample.tasktide.sync.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteCallbackList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sample.tasktide.core.model.currentTimeMillis
import sample.tasktide.sync.engine.SyncResult

class SyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val callbacks = RemoteCallbackList<ISyncCallback>()

    @Volatile
    private var lastStatus = SyncStatus(SyncStatus.STATE_IDLE, lastSyncAt = 0L, conflicts = 0)

    private val binder = object : ISyncService.Stub() {
        override fun startSync() = runSync()

        override fun getStatus(): SyncStatus = lastStatus

        override fun registerCallback(callback: ISyncCallback) {
            callbacks.register(callback)
        }

        override fun unregisterCallback(callback: ISyncCallback) {
            callbacks.unregister(callback)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SYNC) runSync()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        callbacks.kill()
        super.onDestroy()
    }

    private fun runSync() {
        val engine = (application as SyncEngineProvider).syncEngine
        scope.launch {
            lastStatus = lastStatus.copy(state = SyncStatus.STATE_RUNNING)
            broadcast { it.onSyncStarted() }
            when (val result = engine.sync()) {
                is SyncResult.Success -> {
                    lastStatus = SyncStatus(SyncStatus.STATE_IDLE, currentTimeMillis(), result.conflicts)
                    broadcast { it.onSyncFinished(lastStatus) }
                }
                is SyncResult.Failed -> {
                    lastStatus = lastStatus.copy(state = SyncStatus.STATE_FAILED)
                    broadcast { it.onSyncFailed(result.reason) }
                }
            }
        }
    }

    private fun broadcast(action: (ISyncCallback) -> Unit) {
        val count = callbacks.beginBroadcast()
        try {
            repeat(count) { action(callbacks.getBroadcastItem(it)) }
        } finally {
            callbacks.finishBroadcast()
        }
    }

    companion object {
        const val ACTION_SYNC = "sample.tasktide.sync.action.SYNC"
    }
}
