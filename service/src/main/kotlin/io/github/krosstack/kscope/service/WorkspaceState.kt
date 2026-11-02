package io.github.krosstack.kscope.service

import io.github.krosstack.kscope.engine.indexRequiresTrust
import io.github.krosstack.kscope.engine.workspaceStamp
import io.github.krosstack.kscope.engine.workspaceState
import io.github.krosstack.kscope.model.Fingerprint
import java.nio.file.Path

/** Reuses the scan's file listing resources without opening the index. */
public class WorkspaceState internal constructor(private val source: io.github.krosstack.kscope.engine.WorkspaceState) {
    public val gitDirectory: Path? get() = source.gitDirectory

    public val gitDirectories: List<Path> get() = source.gitDirectories

    public fun stamp(): Fingerprint = source.stamp()
}

/** Probes Git once and reuses its result for the lifetime of the observer. */
public fun KrosScope.workspaceState(): WorkspaceState = WorkspaceState(engine.workspaceState())

/** The scan's file listing, sizes and modification times; contents and the index are never read. */
public fun KrosScope.workspaceStamp(): Fingerprint = engine.workspaceStamp()

/** Reads the stored build model's provider rather than the options used to open this service. */
public fun KrosScope.indexRequiresTrust(): Boolean = engine.indexRequiresTrust()
