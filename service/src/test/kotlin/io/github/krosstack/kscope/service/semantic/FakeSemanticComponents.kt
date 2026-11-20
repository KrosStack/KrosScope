package io.github.krosstack.kscope.service.semantic

import io.github.krosstack.kscope.service.components.ComponentDirectory
import io.github.krosstack.kscope.service.semantic.query.QueryDeadline
import java.io.IOException
import java.nio.file.Path

internal class FakeSemanticComponents(
    scratch: Path,
    val provider: RecordingEmbedding = RecordingEmbedding(),
    var problem: SemanticComponents.Opening.Unavailable? = null,
) : SemanticComponents(ComponentDirectory(scratch), OptionalProcessBudget(scratch), { emptyMap() }) {
    var opened = 0
    var starts = 0
    var closed = 0
    var lost: String? = null
    var openFailure: RuntimeException? = null
    var component = "component"
    val queriesEmbedded = mutableListOf<String>()
    var onQuery: () -> Unit = {}
    var queryRefusal: WorkerEmbeddingProvider.Refusal? = null
    val closingDeadlines = mutableListOf<QueryDeadline?>()
    var closeFailure: IOException? = null
    var fingerprintFailure: IOException? = null
    var onFingerprint: () -> Unit = {}
    var onClose: () -> Unit = {}

    override fun unavailable(): SemanticComponents.Opening.Unavailable? = problem

    override fun open(): SemanticComponents.Opening {
        opened++
        openFailure?.let { throw it }
        return problem ?: SemanticComponents.Opening.Ready(
            LazyWorkerProvider(provider.identity, provider.id) { _, _ ->
                starts++
                LazyWorkerProvider.Started(provider, { lost }, { text, _, _, _ ->
                    queriesEmbedded += text
                    onQuery()
                    queryRefusal?.let { WorkerEmbeddingProvider.QueryResult.Refused(it) }
                        ?: WorkerEmbeddingProvider.QueryResult.Embedded(provider.embedQuery(text))
                }) { deadline ->
                    closingDeadlines += deadline
                    closed++
                    onClose()
                    closeFailure?.let { throw it }
                }
            },
            ::fingerprint,
        )
    }

    private fun fingerprint(): String {
        onFingerprint()
        fingerprintFailure?.let { throw it }
        return component
    }
}
