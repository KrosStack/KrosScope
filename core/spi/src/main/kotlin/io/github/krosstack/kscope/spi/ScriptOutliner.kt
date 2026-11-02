package io.github.krosstack.kscope.spi

import io.github.krosstack.kscope.model.Artifact
import io.github.krosstack.kscope.model.ArtifactRef
import io.github.krosstack.kscope.spi.worker.ScriptOutline

/**
 * Turns build scripts and the Kotlin sources of build logic into syntactic outlines without evaluating them; files it
 * cannot parse are absent. Sources that are outlined in one call and share a project directory can inline each
 * other's functions into the plugin classes they outline.
 */
public fun interface ScriptOutliner {
    public fun outline(scripts: List<Artifact>, files: WorkspaceFiles): Map<ArtifactRef, ScriptOutline>
}
