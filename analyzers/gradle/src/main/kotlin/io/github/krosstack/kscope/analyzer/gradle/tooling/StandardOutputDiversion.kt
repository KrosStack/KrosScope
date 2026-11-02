package io.github.krosstack.kscope.analyzer.gradle.tooling

/**
 * Runs code with the standard output of the process pointed at its standard error. The Tooling API reports the
 * download of a Gradle distribution (`Downloading ...` and a line of dots) on the standard output of the process, where
 * a client that prints its result there, as `kscope --format json` does, would find it ahead of the result.
 */
internal object StandardOutputDiversion {
    fun <T> toStandardError(action: () -> T): T {
        val original = System.out
        System.setOut(System.err)
        try {
            return action()
        } finally {
            System.setOut(original)
        }
    }
}
