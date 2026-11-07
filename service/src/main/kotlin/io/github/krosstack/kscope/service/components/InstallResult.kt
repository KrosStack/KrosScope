package io.github.krosstack.kscope.service.components

import java.nio.file.Path

public sealed interface InstallResult {
    public data class Installed(public val component: InstalledComponent, public val selfTest: String? = null) :
        InstallResult
    public data class Replaced(
        public val component: InstalledComponent,
        public val retainedBackup: Path? = null,
        public val selfTest: String? = null,
    ) : InstallResult
    public data class SelfTestFailed(public val installation: InstallResult) : InstallResult
    public data class ChecksumMismatch(public val file: String) : InstallResult
    public data class Oversize(public val file: String) : InstallResult
    public data class UnsafeEntry(public val file: String, public val entry: String) : InstallResult
    public data class Unavailable(public val file: String, public val reason: String) : InstallResult
}
