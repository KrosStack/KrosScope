package io.github.krosstack.kscope.cli.service

import io.github.krosstack.kscope.cli.service.home.ServiceDirectory
import io.github.krosstack.kscope.cli.service.launch.ServiceLaunch
import io.github.krosstack.kscope.cli.service.launch.ServiceStarter
import io.github.krosstack.kscope.cli.service.launch.StartOutcome

internal class RecordingStarter(private val outcome: StartOutcome = StartOutcome.NotStarted()) : ServiceStarter {
    val launches = mutableListOf<ServiceLaunch>()
    val directories = mutableListOf<ServiceDirectory>()

    override fun start(directory: ServiceDirectory, launch: ServiceLaunch): StartOutcome {
        directories += directory
        launches += launch
        return outcome
    }
}
