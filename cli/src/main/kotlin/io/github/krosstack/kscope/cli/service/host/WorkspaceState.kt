package io.github.krosstack.kscope.cli.service.host

import io.github.krosstack.kscope.cli.serve.ServeServer

internal sealed interface WorkspaceState {
    val text: String

    data object Starting : WorkspaceState {
        override val text = "starting."
    }

    class Served(val server: ServeServer) : WorkspaceState {
        override val text: String
            get() {
                val loaded = if (server.loaded) "served." else "served, not in memory."
                val level = server.indexLevel?.let { "Index level: $it." }
                return listOfNotNull(loaded, server.watchState?.status(), level).joinToString(" ")
            }
    }

    data object Waiting : WorkspaceState {
        override val text = "waiting, because a server of this workspace runs in another process."
    }

    data class NotServed(val reason: String) : WorkspaceState {
        override val text = "not served, because ${reason.trimEnd('.')}."
    }
}
