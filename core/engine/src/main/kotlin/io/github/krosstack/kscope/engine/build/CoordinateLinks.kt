package io.github.krosstack.kscope.engine.build

import io.github.krosstack.kscope.model.Diagnostic
import io.github.krosstack.kscope.model.Relationship

/**
 * The dependencies that resolved to modules of other repositories, and what was found wrong with them. All of the
 * [relationships] are edges that analysis may rely on; those whose pinned version is not the one built from source,
 * or cannot be compared with it, are heuristic.
 */
internal class CoordinateLinks(val relationships: List<Relationship>, val diagnostics: List<Diagnostic>) {
    companion object {
        const val SUB_KIND = "maven-coordinates"
        const val VERSION_MATCH = "versionMatch"
        val none = CoordinateLinks(emptyList(), emptyList())
    }
}
