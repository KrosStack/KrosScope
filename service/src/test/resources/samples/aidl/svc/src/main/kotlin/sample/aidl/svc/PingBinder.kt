package sample.aidl.svc

class PingBinder : IPing.Stub() {
    private var last: Payload? = null

    override fun ping(payload: Payload) {
        last = payload
    }

    override fun latest(): Payload? = last

    override fun watch(callback: IPongCallback) {
        callback.onPong(MAX)
    }

    private companion object {
        const val MAX = IPing.MAX_PAYLOADS
    }
}
