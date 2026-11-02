package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.launch.ServiceLaunch

internal interface SessionEntry {
    fun install(launch: ServiceLaunch): Installation

    fun uninstall(): Removal

    fun state(): EntryState
}
