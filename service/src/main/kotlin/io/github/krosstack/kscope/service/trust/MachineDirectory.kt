package io.github.krosstack.kscope.service.trust

import java.nio.file.Path

/** Where the files of the user that belong to the machine and not to a workspace are kept. */
public object MachineDirectory {
    public const val VARIABLE: String = "KSCOPE_SERVICE_HOME"
    public const val PROPERTY: String = "kscope.service.home"

    public fun resolve(property: (String) -> String?, environment: (String) -> String?): Path {
        val configured = listOf(property(PROPERTY), environment(VARIABLE)).firstOrNull { !it.isNullOrBlank() }
        val root = configured?.let { Path.of(it) } ?: Path.of(System.getProperty("user.home"), ".kscope", "service")
        return root.toAbsolutePath().normalize()
    }
}
