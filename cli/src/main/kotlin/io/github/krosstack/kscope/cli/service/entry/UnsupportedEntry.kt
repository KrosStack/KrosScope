package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.launch.ServiceLaunch

internal class UnsupportedEntry(osName: String) : SessionEntry {
    private val refusal = "Starting with the session is not supported on $osName; use `kscope service start`."

    override fun install(launch: ServiceLaunch): Installation = Installation.Refused(refusal)

    override fun uninstall(): Removal = Removal.Refused(refusal)

    override fun state(): EntryState = EntryState.NotRegistered
}
