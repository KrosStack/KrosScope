package sample.aidl.svc

class PingService {
    var received: Int = 0

    private val pongs = object : IPongCallback.Stub() {
        override fun onPong(sequence: Int) {
            received += sequence
        }
    }

    fun callback(): IPongCallback = pongs
}
