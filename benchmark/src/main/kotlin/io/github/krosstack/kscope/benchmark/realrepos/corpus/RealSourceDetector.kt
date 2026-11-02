package io.github.krosstack.kscope.benchmark.realrepos.corpus

import io.github.krosstack.kscope.model.ArtifactCategory
import io.github.krosstack.kscope.model.ArtifactType
import io.github.krosstack.kscope.spi.ArtifactCandidate
import io.github.krosstack.kscope.spi.ArtifactDetector
import java.util.Locale

internal class RealSourceDetector : ArtifactDetector {
    override val id = "kscope.benchmark.real-source"

    override fun detect(candidate: ArtifactCandidate): ArtifactType? = if (suffix(
            candidate.path.value,
        ) in SUFFIXES
    ) {
        ArtifactType("benchmark.source", ArtifactCategory.SOURCE)
    } else {
        null
    }

    fun statementLabels(paths: List<String>): List<String> = paths.flatMap { path ->
        val name = path.substringAfterLast('/')
        listOf(path, name)
    }.distinct().sortedByDescending { it.length }

    fun withoutPaths(message: String): String = PATHS.replace(message, " ")

    fun supportsHorizontalFormatting(path: String): Boolean = suffix(path) in TOKEN_WHITESPACE

    private fun suffix(path: String): String = path.substringAfterLast('.', "").lowercase(Locale.ROOT)

    private companion object {
        val SUFFIXES = setOf(
            "kt", "kts", "java", "gradle", "xml", "aidl", "proto", "sq", "sql", "c", "cc", "cpp",
            "cxx", "h", "hpp",
        )
        val TOKEN_WHITESPACE = setOf("kt", "kts", "java", "aidl", "proto")
        val PATHS = Regex("(?:[\\p{L}\\p{N}_.-]+/)+[\\p{L}\\p{N}_./-]+")
    }
}
