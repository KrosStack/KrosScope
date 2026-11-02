package io.github.krosstack.kscope.cli.service.launch

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory

internal sealed interface StartOutcome {
    data class Started(val pid: Long) : StartOutcome

    data class NotStarted(val reason: String? = null) : StartOutcome
}

internal fun interface ServiceStarter {
    fun start(directory: ServiceDirectory, launch: ServiceLaunch): StartOutcome
}
