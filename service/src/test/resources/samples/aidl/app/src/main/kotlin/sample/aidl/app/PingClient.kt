package sample.aidl.app

import sample.aidl.svc.IPing
import sample.aidl.svc.Payload

class PingClient(binder: android.os.IBinder) {
    private val remote: IPing = IPing.Stub.asInterface(binder)

    fun send(body: String) {
        remote.ping(Payload(body))
    }
}
