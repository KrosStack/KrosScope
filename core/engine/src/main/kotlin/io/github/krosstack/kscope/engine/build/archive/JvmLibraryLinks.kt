package io.github.krosstack.kscope.engine.build.archive

import io.github.krosstack.kscope.model.Relationship
import io.github.krosstack.kscope.model.Resolution

/**
 * The dependencies on JAR files that were linked to the module that builds them, and those with several. [inputs] name
 * the registered folders and the archives that were looked up in them, with their size and modified time, so that the
 * unit that keeps the links changes when they do.
 */
internal class JvmLibraryLinks(val relationships: List<Relationship>, val inputs: List<String> = emptyList()) {
    /** The links that analysis may rely on: those that name a module. */
    fun resolved(): List<Relationship> = relationships.filter { it.resolution is Resolution.Resolved }

    companion object {
        const val SUB_KIND = "jvm-library"
    }
}
