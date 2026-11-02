package sample.tasktide.client

object NativeChecksum {
    init {
        System.loadLibrary("tasktide_checksum")
    }

    external fun crc32(bytes: ByteArray): Long
}
