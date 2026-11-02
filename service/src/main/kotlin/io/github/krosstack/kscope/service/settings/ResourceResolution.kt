package io.github.krosstack.kscope.service.settings

import io.github.krosstack.kscope.analyzer.kotlin.WorkerEnvironment

/**
 * Decides the value of each resource setting: the environment variable that exists for it, else the value of the
 * settings, else the default. The environment wins because it belongs to the person at this machine, while a settings
 * file may come from a repository. An environment variable that is set but not usable counts as not set.
 */
internal class ResourceResolution(private val environment: (String) -> String?) {
    fun resolve(values: ResourceSettings, origin: String): ResourcesReport = ResourcesReport(
        workers = choose(
            fromEnvironment = WorkerEnvironment.workers(environment),
            variable = WorkerEnvironment.WORKERS_VARIABLE,
            configured = values.workers?.let { SettingValue(it, origin) },
            default = SettingValue(null, DEFAULT_ORIGIN),
        ),
        workerHeapMib = choose(
            fromEnvironment = WorkerEnvironment.heapMebibytes(environment),
            variable = WorkerEnvironment.HEAP_VARIABLE,
            configured = values.workerHeapMib?.let { SettingValue(it, origin) },
            default = SettingValue(WorkerEnvironment.DEFAULT_HEAP_MEBIBYTES, DEFAULT_ORIGIN),
        ),
    )

    private fun choose(
        fromEnvironment: Int?,
        variable: String,
        configured: SettingValue?,
        default: SettingValue,
    ): SettingValue = if (fromEnvironment != null) SettingValue(fromEnvironment, variable) else configured ?: default

    companion object {
        const val DEFAULT_ORIGIN = "default"
    }
}
