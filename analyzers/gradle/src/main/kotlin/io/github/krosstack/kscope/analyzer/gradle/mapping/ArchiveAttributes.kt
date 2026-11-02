package io.github.krosstack.kscope.analyzer.gradle.mapping

/**
 * The module attribute that records the archives a JVM module builds, shared by the static and the Tooling API
 * provider.
 */
internal object ArchiveAttributes {
    const val ARCHIVES = "jvm.archives"

    /** The file names of the archives, sorted and separated by commas; nothing when there is none. */
    fun of(fileNames: Collection<String>): Map<String, String> {
        val sorted = fileNames.filter(String::isNotBlank).distinct().sorted()
        return if (sorted.isEmpty()) emptyMap() else mapOf(ARCHIVES to sorted.joinToString(","))
    }
}
