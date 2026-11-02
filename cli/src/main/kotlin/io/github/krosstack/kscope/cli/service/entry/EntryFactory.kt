package io.github.krosstack.kscope.cli.service.entry

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory

internal fun interface EntryFactory {
    fun entryFor(directory: ServiceDirectory): SessionEntry
}
