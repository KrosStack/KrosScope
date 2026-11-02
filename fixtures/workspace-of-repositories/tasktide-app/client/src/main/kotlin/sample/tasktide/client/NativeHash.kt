package sample.tasktide.client

object NativeHash {
    init {
        System.loadLibrary("tasktide_hash")
    }

    external fun fnv1a(text: String): Long
}
