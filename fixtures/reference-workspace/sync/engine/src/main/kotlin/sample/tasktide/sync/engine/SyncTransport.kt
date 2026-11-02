package sample.tasktide.sync.engine

import sample.tasktide.sync.proto.SyncRequest
import sample.tasktide.sync.proto.SyncResponse
import java.io.IOException

interface SyncTransport {
    @Throws(IOException::class)
    suspend fun exchange(request: SyncRequest): SyncResponse
}
