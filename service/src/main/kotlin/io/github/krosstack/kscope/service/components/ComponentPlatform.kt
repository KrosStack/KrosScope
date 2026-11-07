package io.github.krosstack.kscope.service.components

public object ComponentPlatform {
    internal val supported = setOf("any", "windows-x64", "linux-x64", "linux-aarch64", "macos-arm64", "macos-x64")
    private val architectures = mapOf(
        "amd64" to "x64",
        "x86_64" to "x64",
        "x64" to "x64",
        "aarch64" to "arm64",
        "arm64" to "arm64",
    )

    public fun current(osName: String, osArch: String): String {
        val arch = architectures[osArch.lowercase()] ?: "unsupported"
        val os = osName.lowercase()
        val platform = when {
            os.startsWith("windows") -> "windows-$arch"
            os == "linux" && arch == "arm64" -> "linux-aarch64"
            os == "linux" -> "linux-$arch"
            os.startsWith("mac") -> "macos-$arch"
            else -> "unsupported"
        }
        return platform.takeIf { it in supported } ?: "unsupported"
    }
}
