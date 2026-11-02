package sample.tasktide.core.model

expect fun currentTimeMillis(): Long

expect class UuidGenerator() {
    fun next(): String
}

expect object PlatformInfo {
    val name: String
    val isMobile: Boolean
}
