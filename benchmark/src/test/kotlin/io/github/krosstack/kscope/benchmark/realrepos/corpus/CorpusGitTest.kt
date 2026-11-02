package io.github.krosstack.kscope.benchmark.realrepos.corpus

import io.github.krosstack.kscope.benchmark.realrepos.measurement.CorpusRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class CorpusGitTest {
    @Test
    fun `a read answers for its own checkout when the environment selects another repository`() {
        CorpusRepository("own").use { own ->
            CorpusRepository("other").use { other ->
                val head = own.commit("Own", mapOf("README.md" to "own\n"))
                other.commit("Other", mapOf("README.md" to "other\n"))
                val selection = mapOf(
                    "GIT_DIR" to other.root.resolve(".git").toString(),
                    "GIT_WORK_TREE" to other.root.toString(),
                )

                val read = CorpusGit(own.root, System.getenv() + selection).read("rev-parse", "HEAD").trim()

                assertEquals(head, read)
            }
        }
    }
}
