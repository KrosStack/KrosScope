package io.github.krosstack.kscope.benchmark.realrepos.corpus

import io.github.krosstack.kscope.benchmark.realrepos.measurement.CorpusRepository
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CorpusProtocolRegressionTest {
    @Test
    fun `redaction preserves bare domain words beside removed file paths`() {
        CorpusRepository("sample").use { repo ->
            repo.commit("Initial", mapOf("src/Engine.kt" to "val value = 0\n"))
            repo.commit("Fix Engine logic in src/Engine.kt (#12)", mapOf("src/Engine.kt" to "val value = 1\n"))

            assertEquals(
                "Fix Engine logic in",
                RealRepositoryCorpus.prepare(listOf(repo.spec())).tasks.single().statement,
            )
        }
    }

    @Test
    fun `literal whitespace changes are functional changes`() {
        CorpusRepository("sample").use { repo ->
            repo.commit("Initial", mapOf("Source.kt" to "val text = \"a b\"\n"))
            val change = repo.commit("Correct separator", mapOf("Source.kt" to "val text = \"ab\"\n"))

            assertEquals(listOf(change), RealRepositoryCorpus.prepare(listOf(repo.spec())).tasks.map { it.commit })
        }
    }

    @Test
    fun `whitespace can separate distinct operators`() {
        CorpusRepository("sample").use { repo ->
            repo.commit("Initial", mapOf("Source.java" to "int f(int x) { return - -x; }\n"))
            val change = repo.commit(
                "Decrement before returning",
                mapOf("Source.java" to "int f(int x) { return --x; }\n"),
            )

            assertEquals(listOf(change), RealRepositoryCorpus.prepare(listOf(repo.spec())).tasks.map { it.commit })
        }
    }

    @Test
    fun `formatting application data is a functional task`() {
        CorpusRepository("sample").use { repo ->
            repo.commit("Initial", mapOf("Source.kt" to "val offset = 0\n"))
            val change = repo.commit("Format dates in local time", mapOf("Source.kt" to "val offset = 1\n"))

            assertEquals(listOf(change), RealRepositoryCorpus.prepare(listOf(repo.spec())).tasks.map { it.commit })
        }
    }

    @Test
    fun `mixed creation changes keep their existing parent file gold`() {
        CorpusRepository("sample").use { repo ->
            repo.commit("Initial", mapOf("Existing.kt" to "val value = 0\n"))
            repo.commit(
                "Correct existing behavior with helper",
                mapOf(
                    "Existing.kt" to "val value = 1\n",
                    "Helper.kt" to "val helper = 1\n",
                ),
            )

            assertEquals(
                listOf("Existing.kt"),
                RealRepositoryCorpus.prepare(listOf(repo.spec())).tasks.single().goldFiles,
            )
        }
    }

    @Test
    fun `deleted source files remain gold because they exist at the parent`() {
        CorpusRepository("sample").use { repo ->
            repo.commit("Initial", mapOf("Obsolete.kt" to "val value = 0\n"))
            repo.commit("Remove obsolete behavior", mapOf("Obsolete.kt" to null))

            assertEquals(
                listOf("Obsolete.kt"),
                RealRepositoryCorpus.prepare(listOf(repo.spec())).tasks.single().goldFiles,
            )
        }
    }

    @Test
    fun `spaces in Kotlin backtick names are preserved`() {
        assertEligible("Source.kt", "fun `a  b`() = Unit\n", "fun `a b`() = Unit\n")
    }

    @Test
    fun `Groovy slashy strings do not use code whitespace normalization`() {
        assertEligible("build.gradle", "def value = /a  b/\n", "def value = /a b/\n")
    }

    @Test
    fun `Cxx raw strings do not use code whitespace normalization`() {
        assertEligible("source.cpp", "auto value = R\"(a \"  b)\";\n", "auto value = R\"(a \" b)\";\n")
    }

    @Test
    fun `Java Unicode escapes are conservatively preserved`() {
        assertEligible("Source.java", "String value = \\u0022a  b\\u0022;\n", "String value = \\u0022a b\\u0022;\n")
    }

    private fun assertEligible(path: String, before: String, after: String) {
        CorpusRepository("sample").use { repo ->
            repo.commit("Initial", mapOf(path to before))
            val change = repo.commit("Correct significant spacing", mapOf(path to after))

            assertEquals(listOf(change), RealRepositoryCorpus.prepare(listOf(repo.spec())).tasks.map { it.commit })
        }
    }
}
