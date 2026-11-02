package io.github.krosstack.kscope.cli.service.host

internal interface HeapStatistics {
    val maximumBytes: Long

    fun reading(): HeapReading
}
