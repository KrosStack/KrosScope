package io.github.krosstack.kscope.analyzer.kotlin

/**
 * The worker values that a settings file of the workspace sets; null means the file does not set it. The environment
 * variables of [WorkerEnvironment] take precedence over these values.
 */
public class WorkerSettings(public val workers: Int? = null, public val heapMebibytes: Int? = null) {
    init {
        require(workers == null || workers >= 1) { "At least one worker is needed" }
        require(heapMebibytes == null || heapMebibytes >= 1) { "The heap of a worker needs at least one mebibyte" }
    }

    public companion object {
        public val NONE: WorkerSettings = WorkerSettings()
    }
}
