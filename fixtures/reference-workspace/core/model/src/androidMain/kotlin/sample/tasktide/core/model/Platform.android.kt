package sample.tasktide.core.model

import java.util.UUID

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual class UuidGenerator actual constructor() {
    actual fun next(): String = UUID.randomUUID().toString()
}

actual object PlatformInfo {
    actual val name: String = "android"
    actual val isMobile: Boolean = true
}
