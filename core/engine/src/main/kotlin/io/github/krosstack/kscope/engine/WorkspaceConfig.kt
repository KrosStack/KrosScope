package io.github.krosstack.kscope.engine

import io.github.krosstack.kscope.engine.scope.ScanScope
import io.github.krosstack.kscope.model.RepositoryId
import java.nio.file.Path

/**
 * [trusted] allows build model providers that execute build logic. Files above [maxArtifactBytes] are inventoried
 * but never handed to producers. [scope] is asked for once per scan and says which files that scan reads.
 * [disabledAnalyzers] is asked for once per scan and names the analyzer groups that the registry of that scan leaves
 * out; the engine records the names in the index and does not interpret them. [level] is asked for once per scan and
 * says how deep it goes: a scan at [ScanLevel.SYNTAX] reads declarations without resolving, and a scan at
 * [ScanLevel.EXACT] runs as if [trusted] were true. [registered] is asked for once per scan and names the repositories
 * outside the root that the user registered with the workspace. [checkoutsAccepted] hears, at the end of each scan, the
 * checkout directory of every registered repository that the scan indexed, by repository id; it is called also when
 * there are none. [artifactFolders] is asked for once per scan and names the folders of built JAR and AAR files that
 * the user registered, such as a Maven local repository or a Gradle cache, in the order they are looked in; nothing
 * is read from a folder that is not listed.
 */
public data class WorkspaceConfig(
    public val root: Path,
    public val indexDirectory: Path = root.resolve(DEFAULT_INDEX_DIRECTORY),
    public val trusted: Boolean = false,
    public val maxArtifactBytes: Long = DEFAULT_MAX_ARTIFACT_BYTES,
    public val scope: () -> ScanScope = { ScanScope.ALL },
    public val disabledAnalyzers: () -> Set<String> = { emptySet() },
    public val level: () -> ScanLevel = { if (trusted) ScanLevel.EXACT else ScanLevel.RESOLVED },
    public val registered: () -> List<RegisteredRepository> = { emptyList() },
    public val checkoutsAccepted: (Map<RepositoryId, Path>) -> Unit = {},
    public val artifactFolders: () -> List<Path> = { emptyList() },
) {
    internal fun withSettingsFixed(): WorkspaceConfig {
        val fixedScope = scope()
        val fixedAnalyzers = disabledAnalyzers()
        val fixedLevel = level()
        val fixedRegistered = registered()
        val fixedFolders = artifactFolders()
        return copy(
            trusted = fixedLevel == ScanLevel.EXACT,
            scope = { fixedScope },
            disabledAnalyzers = { fixedAnalyzers },
            level = { fixedLevel },
            registered = { fixedRegistered },
            artifactFolders = { fixedFolders },
        )
    }

    public companion object {
        public const val DEFAULT_INDEX_DIRECTORY: String = ".kscope"
        public const val SETTINGS_FILE_NAME: String = "kscope.toml"

        /** The settings file of the workspace at [root]: the file that the reader opens and the listing leaves out. */
        public fun settingsFile(root: Path): Path = root.resolve(SETTINGS_FILE_NAME)
        public const val DEFAULT_MAX_ARTIFACT_BYTES: Long = 2L * 1024 * 1024
    }
}
