package sample.tasktide.app

import android.util.Log
import sample.tasktide.sync.engine.FakeSyncTransport
import sample.tasktide.sync.engine.SyncTransport
import sample.tasktide.sync.proto.SyncRequest
import sample.tasktide.sync.proto.SyncResponse

class AppSyncTransport(
    private val endpoint: String,
    private val verbose: Boolean,
    private val delegate: SyncTransport = FakeSyncTransport(),
) : SyncTransport {
    override suspend fun exchange(request: SyncRequest): SyncResponse {
        if (verbose) Log.d(TAG, "exchange with $endpoint: ${request.tasks.size} task(s) since ${request.since}")
        return delegate.exchange(request)
    }

    private companion object {
        const val TAG = "AppSyncTransport"
    }
}
