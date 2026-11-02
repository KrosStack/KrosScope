package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.service.entry.EntryState
import io.github.krosstack.kscope.cli.service.entry.Installation
import io.github.krosstack.kscope.cli.service.entry.Removal
import io.github.krosstack.kscope.cli.service.entry.SessionEntry
import io.github.krosstack.kscope.cli.service.launch.ServiceLaunch

internal class FakeSessionEntry(
    private val state: EntryState = EntryState.NotRegistered,
    private val installation: () -> Installation = { Installation.Installed },
    private val removal: () -> Removal = { Removal.Removed },
) : SessionEntry {
    val installed = mutableListOf<ServiceLaunch>()
    var uninstalls = 0
        private set

    override fun install(launch: ServiceLaunch): Installation {
        installed += launch
        return installation()
    }

    override fun uninstall(): Removal {
        uninstalls++
        return removal()
    }

    override fun state(): EntryState = state
}
