package io.github.krosstack.kscope.cli.service.home

internal sealed interface ServiceFileReading {
    data class Read(val workspaces: List<String>, val limits: ServiceLimits = ServiceLimits()) : ServiceFileReading

    data class Unreadable(val message: String) : ServiceFileReading
}
