package sample.tasktide.nativebridge

object Checksum {
    init {
        System.loadLibrary("tasktide_checksum")
    }

    external fun crc32(data: ByteArray): Int

    external fun nativeVersion(): String
}
