package sample.jni.bridge

object Checksum {
    external fun crc32(data: ByteArray): Int

    external fun nativeVersion(): String

    external fun missing(seed: Long): Long
}
