package sample.tasktide.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import sample.tasktide.sync.service.ISyncCallback
import sample.tasktide.sync.service.ISyncService
import sample.tasktide.sync.service.SyncService
import sample.tasktide.sync.service.SyncStatus

class SyncServiceClient(
    private val context: Context,
    private val onStatus: (SyncStatus) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var service: ISyncService? = null

    private val callback = object : ISyncCallback.Stub() {
        override fun onSyncStarted() = publish(SyncStatus(SyncStatus.STATE_RUNNING, lastSyncAt = 0L, conflicts = 0))

        override fun onSyncFinished(status: SyncStatus) = publish(status)

        override fun onSyncFailed(reason: String) {
            Log.w(TAG, "sync failed: $reason")
            publish(SyncStatus(SyncStatus.STATE_FAILED, lastSyncAt = 0L, conflicts = 0))
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val connected = ISyncService.Stub.asInterface(binder)
            connected.registerCallback(callback)
            publish(connected.status)
            service = connected
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    fun bind() {
        context.bindService(Intent(context, SyncService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        service?.unregisterCallback(callback)
        context.unbindService(connection)
        service = null
    }

    fun requestSync() {
        service?.startSync()
    }

    private fun publish(status: SyncStatus) {
        mainHandler.post { onStatus(status) }
    }

    private companion object {
        const val TAG = "SyncServiceClient"
    }
}
